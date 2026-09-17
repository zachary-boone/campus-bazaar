package com.campus.bazaar.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.dto.UserDTO;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.mapper.GoodsMapper;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.mq.GoodsSeckillMessage;
import com.campus.bazaar.service.IGoodsService;
import com.campus.bazaar.utils.MqConstants;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.SystemConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements IGoodsService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    /** 默认坐标（前端未传经纬度时兜底，可配置） */
    private static final double DEFAULT_X = 114.3055;
    private static final double DEFAULT_Y = 30.5928;

    /**
     * 商品秒杀 Lua 脚本（原子完成"查库存 + 查重复 + 预扣库存 + 记用户"）：
     * KEYS[1] = seckill:goods:stock:{goodsId}
     * KEYS[2] = seckill:goods:user:{goodsId}
     * ARGV[1] = userId
     * 返回：1=成功；-1=库存不足；-2=每人限购一件
     */
    private static final String GOODS_SECKILL_LUA =
            "if tonumber(redis.call('get', KEYS[1]) or '0') <= 0 then return -1 end " +
            "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then return -2 end " +
            "redis.call('incrby', KEYS[1], -1) " +
            "redis.call('sadd', KEYS[2], ARGV[1]) " +
            "return 1";

    @Override
    public Result queryById(Long id) {
        // 逻辑过期方案：热点数据异步重建，读多写少场景性能最好
        Goods goods = queryWithLogicalExpire(id);
        if (goods == null) {
            return Result.fail("商品不存在");
        }
        // 填充卖家信息
        fillSellerInfo(goods);
        return Result.ok(goods);
    }

    @Override
    @Transactional
    public Result publishGoods(Goods goods) {
        // 1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }

        // 2. 设置卖家信息
        goods.setSellerId(user.getId());

        // 3. 设置默认值
        if (goods.getStatus() == null) {
            goods.setStatus(1); // 默认在售
        }
        if (goods.getSold() == null) {
            goods.setSold(0);
        }
        if (goods.getComments() == null) {
            goods.setComments(0);
        }
        if (goods.getScore() == null) {
            goods.setScore(50);
        }
        // 库存默认 1 件（传统"一物一件"商品）；批量商品由卖家显式填写，供秒杀使用
        if (goods.getStock() == null || goods.getStock() < 0) {
            goods.setStock(1);
        }
        if (goods.getX() == null || goods.getY() == null) {
            goods.setX(DEFAULT_X);
            goods.setY(DEFAULT_Y);
        }
        goods.setCreateTime(LocalDateTime.now());
        goods.setUpdateTime(LocalDateTime.now());

        // 4. 保存商品
        save(goods);

        // 5. 缓存预热（逻辑过期格式）+ GEO 坐标写入
        saveGoodsWithLogicalExpire(RedisConstants.CACHE_GOODS_KEY + goods.getId(), goods);
        addGoodsGeo(goods);

        return Result.ok(goods.getId());
    }

    @Override
    @Transactional
    public Result updateGoods(Goods goods) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 查询原商品
        Goods oldGoods = getById(goods.getId());
        if (oldGoods == null) {
            return Result.fail("商品不存在");
        }

        // 3. 判断是否是卖家
        if (!oldGoods.getSellerId().equals(userId)) {
            return Result.fail("只能编辑自己的商品");
        }

        // 4. 更新商品
        goods.setUpdateTime(LocalDateTime.now());
        updateById(goods);

        // 5. 延迟双删缓存：先删 → 更库已提交 → 延迟再删，防止期间旧值回写
        deleteCacheWithDelay(goods.getId());

        // 6. 更新 GEO 坐标
        if (goods.getX() != null && goods.getY() != null) {
            removeGoodsGeo(oldGoods);
            addGoodsGeo(goods);
        }

        return Result.ok();
    }

    @Override
    @Transactional
    public Result offShelfGoods(Long goodsId) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 查询商品
        Goods goods = getById(goodsId);
        if (goods == null) {
            return Result.fail("商品不存在");
        }

        // 3. 判断是否是卖家
        if (!goods.getSellerId().equals(userId)) {
            return Result.fail("只能操作自己的商品");
        }

        // 3.5 状态约束：已售/交易中的商品不允许下架
        if (goods.getStatus() != null && goods.getStatus() == 2) {
            return Result.fail("商品已售出，无需下架");
        }
        if (goods.getStatus() != null && goods.getStatus() == 4) {
            return Result.fail("商品交易中，无法下架");
        }

        // 4. 下架商品
        goods.setStatus(3); // 下架
        goods.setUpdateTime(LocalDateTime.now());
        updateById(goods);

        // 5. 延迟双删缓存 + 移除 GEO
        deleteCacheWithDelay(goodsId);
        removeGoodsGeo(goods);

        return Result.ok();
    }

    @Override
    @Transactional
    public Result onShelfGoods(Long goodsId) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 查询商品
        Goods goods = getById(goodsId);
        if (goods == null) {
            return Result.fail("商品不存在");
        }

        // 3. 判断是否是卖家
        if (!goods.getSellerId().equals(userId)) {
            return Result.fail("只能操作自己的商品");
        }

        // 3.5 状态约束：只有下架(3)状态可重新上架，防止把已售/交易中商品改回在售
        if (goods.getStatus() != null && goods.getStatus() != 3) {
            return Result.fail("仅下架商品可以重新上架");
        }

        // 4. 上架商品
        goods.setStatus(1); // 在售
        goods.setUpdateTime(LocalDateTime.now());
        updateById(goods);

        // 5. 延迟双删缓存 + 恢复 GEO
        deleteCacheWithDelay(goodsId);
        addGoodsGeo(goods);

        return Result.ok();
    }

    @Override
    public Result queryMyGoods(Integer current) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 分页查询
        Page<Goods> page = query()
                .eq("seller_id", userId)
                .orderByDesc("create_time")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));

        List<Goods> records = page.getRecords();

        // 3. 填充卖家信息
        for (Goods goods : records) {
            fillSellerInfo(goods);
        }

        return Result.ok(records, page.getTotal());
    }

    @Override
    public Result queryGoodsOfSeller(Long sellerId, Integer current) {
        if (sellerId == null) {
            return Result.fail("参数不合法");
        }
        int pageNo = (current == null || current < 1) ? 1 : current;
        // 只返回在售商品（1在售 2已售 3下架 4交易中），已售/下架的不再展示
        Page<Goods> page = query()
                .eq("seller_id", sellerId)
                .eq("status", 1)
                .orderByDesc("create_time")
                .page(new Page<>(pageNo, SystemConstants.MAX_PAGE_SIZE));

        List<Goods> records = page.getRecords();
        for (Goods goods : records) {
            fillSellerInfo(goods);
        }
        return Result.ok(records, page.getTotal());
    }

    @Override
    @Transactional
    public Result deleteGoods(Long goodsId) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();

        // 2. 查询商品
        Goods goods = getById(goodsId);
        if (goods == null) {
            return Result.fail("商品不存在");
        }

        // 3. 判断是否是卖家
        if (!goods.getSellerId().equals(userId)) {
            return Result.fail("只能删除自己的商品");
        }

        // 3.5 状态约束：交易中的商品不允许删除（有未完成订单）
        if (goods.getStatus() != null && goods.getStatus() == 4) {
            return Result.fail("商品交易中，无法删除");
        }

        // 4. 删除商品
        removeById(goodsId);

        // 5. 延迟双删缓存 + 移除 GEO
        deleteCacheWithDelay(goodsId);
        removeGoodsGeo(goods);

        return Result.ok();
    }

    // ==================== GEO 附近商品 ====================

    /**
     * 附近商品检索：Redis GEO 半径查询（按距离升序）
     * @param x      中心经度
     * @param y      中心纬度
     * @param radius 半径（公里）
     * @param area   校区（可空，空则全校区）
     */
    public Result queryNearbyGoods(Double x, Double y, Double radius, String area) {
        if (x == null || y == null || radius == null) {
            return Result.fail("缺少定位参数");
        }
        // 具体检索逻辑抽到 geoRadiusGoods，与 /goods/map 共用
        return Result.ok(geoRadiusGoods(x, y, radius, area));
    }

    // ==================== 商品秒杀（由券秒杀迁移而来，二者完全独立） ====================

    /**
     * 商品秒杀生产端：Redis Lua 原子预扣库存 + 一人一单校验，成功后发 MQ 异步建单。
     * <p>
     * 流程与原券秒杀一致，只把作用对象从"券"换成"商品"：
     * 校验时间窗 → 预热库存 → 商品维度 Redisson 锁 → Lua 预扣 → 发 MQ → 返回"已受理"。
     * 注意返回的 0 是"已受理"，不是下单成功——订单由消费者异步创建。
     */
    @Override
    public Result seckillGoods(Long goodsId) {
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }
        if (com.campus.bazaar.metrics.BizMetrics.seckillRequests != null) {
            com.campus.bazaar.metrics.BizMetrics.seckillRequests.increment();
        }

        // 1. 校验商品与秒杀时间窗
        Goods goods = getById(goodsId);
        if (goods == null) {
            return Result.fail("商品不存在");
        }
        if (goods.getStatus() == null || goods.getStatus() != 1) {
            return Result.fail("商品已下架或已售出");
        }
        LocalDateTime now = LocalDateTime.now();
        if (goods.getSeckillBegin() != null && now.isBefore(goods.getSeckillBegin())) {
            return Result.fail("秒杀尚未开始");
        }
        if (goods.getSeckillEnd() != null && now.isAfter(goods.getSeckillEnd())) {
            return Result.fail("秒杀已经结束");
        }

        // 2. Redis 库存预热（首次访问从 DB 拉取，之后以 Redis 为准做预扣）
        Integer dbStock = goods.getStock() == null ? 0 : goods.getStock();
        stringRedisTemplate.opsForValue().setIfAbsent(
                RedisConstants.SECKILL_GOODS_STOCK_KEY + goodsId, String.valueOf(dbStock));

        // 3. Redisson 分布式锁（商品维度）：串行化"检查库存 → 预扣 → 发消息"的编排
        RLock lock = redissonClient.getLock("lock:goods:seckill:" + goodsId);
        try {
            if (!lock.tryLock(0, 5, TimeUnit.SECONDS)) {
                return Result.fail("系统繁忙，请稍后再试");
            }
            // 4. Lua 原子预扣库存 + 一人一单校验
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(GOODS_SECKILL_LUA, Long.class);
            Long result = stringRedisTemplate.execute(script, Arrays.asList(
                            RedisConstants.SECKILL_GOODS_STOCK_KEY + goodsId,
                            RedisConstants.SECKILL_GOODS_USER_KEY + goodsId),
                    userId.toString());

            if (result == null || result == -1L) {
                return Result.fail("库存不足，已被抢光");
            }
            if (result == -2L) {
                return Result.fail("每人限购一件");
            }

            // 5. 发送 MQ 异步建单，立即返回（削峰）
            GoodsSeckillMessage message = new GoodsSeckillMessage(
                    goodsId, userId, UUID.randomUUID().toString().replace("-", ""));
            try {
                rabbitTemplate.convertAndSend(
                        MqConstants.GOODS_SECKILL_EXCHANGE,
                        MqConstants.GOODS_SECKILL_ROUTING_KEY, message);
            } catch (Exception e) {
                // 发消息失败：回滚 Redis 预扣（库存 +1、移除用户），避免"库存扣了订单没建"
                log.error("[GoodsSeckill] MQ 发送失败，回滚预扣: goodsId={}, userId={}, err={}",
                        goodsId, userId, e.getMessage());
                compensateGoodsStock(goodsId, userId);
                return Result.fail("系统繁忙，请稍后再试");
            }

            log.info("[GoodsSeckill] 预扣成功，异步下单受理: goodsId={}, userId={}", goodsId, userId);
            if (com.campus.bazaar.metrics.BizMetrics.seckillSuccess != null) {
                com.campus.bazaar.metrics.BizMetrics.seckillSuccess.increment();
            }
            return Result.ok(0L); // 0 表示已受理，订单由消费者异步创建
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("系统繁忙，请稍后再试");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 补偿：下单失败时回滚 Redis 预扣的商品库存与用户记录
     */
    public void compensateGoodsStock(Long goodsId, Long userId) {
        stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_GOODS_STOCK_KEY + goodsId);
        stringRedisTemplate.opsForSet().remove(RedisConstants.SECKILL_GOODS_USER_KEY + goodsId, userId.toString());
    }

    private String geoKey(String area) {
        return RedisConstants.GOODS_GEO_KEY + (StrUtil.isBlank(area) ? "all" : area);
    }

    private void addGoodsGeo(Goods goods) {
        if (goods == null || goods.getId() == null || goods.getX() == null || goods.getY() == null) {
            return;
        }
        try {
            Point point = new Point(goods.getX(), goods.getY());
            String member = goods.getId().toString();
            // 同时写"校区"索引和"全部"索引：
            // 只写校区索引的话，前端不带 area 查询（走 goods:geo:all）会永远查不到数据
            stringRedisTemplate.opsForGeo().add(geoKey(goods.getArea()), point, member);
            stringRedisTemplate.opsForGeo().add(geoKey(null), point, member);
        } catch (Exception e) {
            log.warn("[GEO] 写入坐标失败: goodsId={}, err={}", goods.getId(), e.getMessage());
        }
    }

    private void removeGoodsGeo(Goods goods) {
        if (goods == null || goods.getId() == null) {
            return;
        }
        try {
            String member = goods.getId().toString();
            stringRedisTemplate.opsForGeo().remove(geoKey(goods.getArea()), member);
            stringRedisTemplate.opsForGeo().remove(geoKey(null), member);
        } catch (Exception e) {
            log.warn("[GEO] 移除坐标失败: goodsId={}, err={}", goods.getId(), e.getMessage());
        }
    }

    // ==================== 地图（Redis GEO） ====================

    /**
     * 全量重建 GEO 索引（以 DB 为准，只收在售商品）
     * <p>
     * 为什么需要：GEO 索引只在"经接口发布/编辑商品"时才写入，
     * 直接跑 SQL 导入的种子数据不会进索引，于是「附近/地图」永远查不到东西。
     * 启动时跑一次（见 GeoIndexInitializer）即可自愈，也可手动触发。
     * @return 写入索引的商品数
     */
    @Override
    public int rebuildGeoIndex() {
        List<Goods> onSale = query().eq("status", 1).list();
        // 先清掉旧 key，保证索引与 DB 完全一致（商品数很少，KEYS 可接受）
        Set<String> oldKeys = stringRedisTemplate.keys(RedisConstants.GOODS_GEO_KEY + "*");
        if (oldKeys != null && !oldKeys.isEmpty()) {
            stringRedisTemplate.delete(oldKeys);
        }
        int count = 0;
        for (Goods goods : onSale) {
            if (goods.getX() == null || goods.getY() == null) {
                continue;
            }
            addGoodsGeo(goods);
            count++;
        }
        log.info("[GEO] 索引重建完成: 在售商品 {} 件，写入坐标 {} 件", onSale.size(), count);
        return count;
    }

    @Override
    public int syncGeoIndex() {
        // GEO key 本质是 ZSet，成员就是商品 id；"all" 索引的基数应等于 DB 在售且有坐标的商品数
        long dbCount = query().eq("status", 1).isNotNull("x").isNotNull("y").count();
        Long indexed = stringRedisTemplate.opsForZSet().zCard(RedisConstants.GOODS_GEO_KEY + "all");
        if (indexed != null && indexed > 0 && indexed == dbCount) {
            log.info("[GEO] 索引已同步({} 条)，跳过重建", indexed);
            return 0;
        }
        return rebuildGeoIndex();
    }

    /**
     * 地图页数据：中心点 + 半径内商品 + 各校区在售数量
     * <p>
     * 不传 x/y 时自动用"在售商品坐标中位数"当校园中心，
     * 前端的"校园视角"和"我的位置"共用这一个接口。
     */
    @Override
    public Result queryMapGoods(Double x, Double y, Double radius, String area) {
        boolean located = x != null && y != null;
        double cx;
        double cy;
        if (located) {
            cx = x;
            cy = y;
        } else {
            double[] center = campusCenter();
            cx = center[0];
            cy = center[1];
        }
        double r = (radius == null || radius <= 0) ? 3D : radius;

        // 半径检索走 Redis GEO（与 /goods/of/nearby 同一套逻辑）
        List<Goods> goods = geoRadiusGoods(cx, cy, r, area);

        Map<String, Object> centerPoint = new LinkedHashMap<>();
        centerPoint.put("x", cx);
        centerPoint.put("y", cy);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("center", centerPoint);
        data.put("located", located);
        data.put("radius", r);
        data.put("count", goods.size());
        data.put("areas", geoAreaCounts());
        data.put("goods", goods);
        return Result.ok(data);
    }

    /**
     * 校园中心点：取在售商品坐标的<b>中位数</b>
     * <p>
     * 用中位数而不是平均值——种子里有 1 件商品的坐标在几百公里外（114.3/30.5），
     * 平均值会被它带偏，中位数不受影响。
     */
    private double[] campusCenter() {
        List<Goods> onSale = query().select("x", "y").eq("status", 1).list();
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (Goods goods : onSale) {
            if (goods.getX() != null && goods.getY() != null) {
                xs.add(goods.getX());
                ys.add(goods.getY());
            }
        }
        if (xs.isEmpty()) {
            return new double[]{DEFAULT_X, DEFAULT_Y};
        }
        return new double[]{median(xs), median(ys)};
    }

    private double median(List<Double> values) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int size = sorted.size();
        if (size % 2 == 1) {
            return sorted.get(size / 2);
        }
        return (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2;
    }

    /** 各校区在售数量（GEO key 基数，供前端做校区筛选；不含 all） */
    private List<Map<String, Object>> geoAreaCounts() {
        List<Map<String, Object>> areas = new ArrayList<>();
        Set<String> keys = stringRedisTemplate.keys(RedisConstants.GOODS_GEO_KEY + "*");
        if (keys == null || keys.isEmpty()) {
            return areas;
        }
        for (String key : keys) {
            String area = key.substring(RedisConstants.GOODS_GEO_KEY.length());
            if ("all".equals(area)) {
                continue;
            }
            Long size = stringRedisTemplate.opsForZSet().zCard(key);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("area", area);
            item.put("count", size == null ? 0L : size);
            areas.add(item);
        }
        areas.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        return areas;
    }

    /**
     * Redis GEO 半径检索（在售商品，按距离升序，回填 distance 公里数 + 卖家信息）
     * <p>
     * 抽出来给 /goods/of/nearby 与 /goods/map 共用，避免两处各写一遍。
     */
    private List<Goods> geoRadiusGoods(double x, double y, double radius, String area) {
        List<Goods> result = new ArrayList<>();
        GeoOperations<String, String> geo = stringRedisTemplate.opsForGeo();
        Circle circle = new Circle(new Point(x, y),
                new Distance(radius, RedisGeoCommands.DistanceUnit.KILOMETERS));
        try {
            RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                    .newGeoRadiusArgs().includeDistance().sortAscending();
            GeoResults<RedisGeoCommands.GeoLocation<String>> results =
                    geo.geoRadius(geoKey(area), circle, args);
            log.info("[GEO] 查询 key={}, 半径={}km, 命中={}", geoKey(area), radius,
                    results == null ? 0 : results.getContent().size());
            if (results != null && results.getContent() != null) {
                for (GeoResult<RedisGeoCommands.GeoLocation<String>> geoResult : results.getContent()) {
                    String goodsId = geoResult.getContent().getName();
                    Goods goods = getById(Long.valueOf(goodsId));
                    if (goods != null && goods.getStatus() == 1) {
                        double dist = geoResult.getDistance() == null ? 0 : geoResult.getDistance().getValue();
                        goods.setDistance(dist); // 距离（公里）
                        fillSellerInfo(goods);
                        result.add(goods);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[GEO] 附近检索异常: {}", e.getMessage());
        }
        return result;
    }

    // ==================== 缓存：逻辑过期 + 延迟双删 ====================

    /**
     * 逻辑过期查询：缓存未过期直接返回；过期则抢 Redisson 锁异步重建，先返回旧数据。
     * 适合读多写少的商品详情热点，相比互斥锁方案不会阻塞读请求。
     */
    private Goods queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_GOODS_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            // 缓存未命中（如重启后未预热）：走 DB 并重建缓存
            return rebuildFromDb(id, key);
        }
        JSONObject obj = JSONUtil.parseObj(json);
        LocalDateTime expireTime = LocalDateTime.parse(obj.getStr("expireTime"));
        Goods goods = obj.getJSONObject("data").toBean(Goods.class);

        if (expireTime.isAfter(LocalDateTime.now())) {
            return goods; // 未过期
        }

        // 过期：尝试抢锁重建（只允许一个线程查 DB，其余返回旧数据）
        RLock lock = redissonClient.getLock(RedisConstants.LOCK_GOODS_KEY + id);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            if (locked) {
                // 双检：可能已被其他线程重建
                String json2 = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(json2)) {
                    JSONObject obj2 = JSONUtil.parseObj(json2);
                    if (LocalDateTime.parse(obj2.getStr("expireTime")).isAfter(LocalDateTime.now())) {
                        return obj2.getJSONObject("data").toBean(Goods.class);
                    }
                }
                Long goodsId = id;
                CompletableFuture.runAsync(() -> {
                    Goods fresh = getById(goodsId);
                    if (fresh != null) {
                        saveGoodsWithLogicalExpire(key, fresh);
                        log.info("[Cache] 逻辑过期异步重建: goodsId={}", goodsId);
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return goods; // 重建期间返回旧数据（过期但可用）
    }

    /** 缓存未命中兜底：查 DB、写逻辑过期缓存 */
    private Goods rebuildFromDb(Long id, String key) {
        Goods goods = getById(id);
        if (goods != null) {
            saveGoodsWithLogicalExpire(key, goods);
        }
        return goods;
    }

    /** 写入逻辑过期缓存：{expireTime(ISO字符串), data} */
    private void saveGoodsWithLogicalExpire(String key, Goods goods) {
        JSONObject obj = new JSONObject();
        // expireTime 存 ISO 字符串，保证读取时 LocalDateTime.parse 可解析
        obj.set("expireTime", LocalDateTime.now().plusSeconds(RedisConstants.CACHE_SHOP_TTL * 60).toString());
        obj.set("data", JSONUtil.parseObj(JSONUtil.toJsonStr(goods)));
        stringRedisTemplate.opsForValue().set(key, obj.toString());
    }

    /**
     * 延迟双删：先删缓存，异步 500ms 后再删一次，
     * 覆盖"请求 A 在更新期间把旧数据写回缓存"的窗口，保证最终一致。
     */
    private void deleteCacheWithDelay(Long goodsId) {
        String key = RedisConstants.CACHE_GOODS_KEY + goodsId;
        stringRedisTemplate.delete(key);
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            stringRedisTemplate.delete(key);
            log.debug("[Cache] 延迟双删完成: goodsId={}", goodsId);
        });
    }

    /**
     * 填充卖家信息
     */
    private void fillSellerInfo(Goods goods) {
        if (goods.getSellerId() != null) {
            try {
                User seller = userMapper.selectById(goods.getSellerId());
                if (seller != null) {
                    goods.setSellerName(seller.getNickName());
                    goods.setSellerIcon(seller.getIcon());
                }
            } catch (Exception ignore) {
            }
        }
        if (goods.getSellerName() == null) goods.setSellerName("校园同学");
        if (goods.getSellerIcon() == null) goods.setSellerIcon("/imgs/icons/default-icon.svg");
    }
}
