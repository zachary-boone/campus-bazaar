package com.campus.bazaar.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.entity.Order;
import com.campus.bazaar.mapper.GoodsMapper;
import com.campus.bazaar.mapper.MqIdempotentMapper;
import com.campus.bazaar.mapper.OrderMapper;
import com.campus.bazaar.mq.GoodsSeckillMessage;
import com.campus.bazaar.mq.PayCheckMessage;
import com.campus.bazaar.service.IOrderService;
import com.campus.bazaar.utils.MqConstants;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.SystemConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    @Resource
    private GoodsMapper goodsMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Resource
    private MqIdempotentMapper mqIdempotentMapper;

    /**
     * 清除商品详情缓存（预占/售出/释放后旧缓存会残留"在售"状态，需立即删 + 延迟再删兜底）
     */
    private void evictGoodsCache(Long goodsId) {
        String key = com.campus.bazaar.utils.RedisConstants.CACHE_GOODS_KEY + goodsId;
        stringRedisTemplate.delete(key);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignore) {
                Thread.currentThread().interrupt();
            }
            stringRedisTemplate.delete(key);
        });
    }

    @Override
    @Transactional
    public Result createOrder(Order order) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 查询商品
        Goods goods = goodsMapper.selectById(order.getGoodsId());
        if (goods == null) {
            return Result.fail("商品不存在");
        }
        if (goods.getStatus() != 1) {
            return Result.fail("商品已下架或已被下单");
        }
        if (goods.getSellerId().equals(userId)) {
            return Result.fail("不能购买自己的商品");
        }

        // 3. 原子预占商品（在售1 → 交易中4，CAS 防超卖）：
        //    UPDATE 持有行锁直到事务提交，并发下单只有一人成功，其余影响行数为 0
        if (goodsMapper.preemptForOrder(goods.getId()) == 0) {
            return Result.fail("手慢了，商品刚被别人下单");
        }
        evictGoodsCache(goods.getId());

        // 4. 生成订单号
        String orderNo = generateOrderNo();

        // 5. 设置订单信息
        order.setOrderNo(orderNo);
        order.setBuyerId(userId);
        order.setSellerId(goods.getSellerId());
        order.setAmount(goods.getPrice());
        order.setStatus(1); // 待支付
        order.setPayType(0); // 未支付
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        // 6. 保存订单
        save(order);

        // 7. 投递支付状态轮询延迟消息（第 1 级：5s 后首次检查，之后指数退避 10s/20s/40s/80s）
        try {
            rabbitTemplate.convertAndSend(
                    MqConstants.PAY_DELAY_EXCHANGE,
                    MqConstants.PAY_DELAY_ROUTING_PREFIX + 1,
                    new PayCheckMessage(orderNo, 1));
        } catch (Exception e) {
            log.warn("投递支付轮询消息失败（不影响下单）: orderNo={}, err={}", orderNo, e.getMessage());
        }

        log.info("📦 创建订单成功: orderNo={}, goodsId={}, buyerId={}, amount={}",
                orderNo, goods.getId(), userId, goods.getPrice());

        return Result.ok(orderNo);
    }

    /**
     * 商品秒杀异步建单（MQ 消费者调用）
     * <p>
     * 与券秒杀的 createSeckillOrder 一一对应，只是落库对象换成 tb_order：
     * 幂等去重 → 一人一单二次校验 → CAS 扣商品库存 → 创建待支付订单。
     * 由消费者用 Redisson 锁（锁用户维度）包住本方法，解决事务锁失效。
     */
    @Override
    @Transactional
    public void createGoodsSeckillOrder(GoodsSeckillMessage message) {
        Long goodsId = message.getGoodsId();
        Long userId = message.getUserId();

        // 0. 消息幂等：msg_id 唯一键抢占，已处理过的消息直接跳过（防 MQ 重复投递导致重复下单）
        int idem = mqIdempotentMapper.insertIgnore(message.getMsgId(), "goods_seckill_order");
        if (idem == 0) {
            log.info("[GoodsSeckill] 重复消息，幂等跳过: msgId={}", message.getMsgId());
            return;
        }

        // 1. 校验商品是否存在
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            log.warn("[GoodsSeckill] 商品不存在，补偿库存: goodsId={}", goodsId);
            compensateGoodsStock(goodsId, userId);
            return;
        }

        // 2. DB 层一人一单二次校验（Redis 可能被清，DB 是最终真相；已取消(4)的单不占名额，可重新抢）
        Integer count = query().eq("goods_id", goodsId).eq("buyer_id", userId)
                .ne("status", 4).count();
        if (count > 0) {
            log.warn("[GoodsSeckill] 重复下单，补偿库存: goodsId={}, userId={}", goodsId, userId);
            compensateGoodsStock(goodsId, userId);
            return;
        }

        // 3. DB 乐观扣减库存（CAS：库存 > 0 才扣）
        int update = goodsMapper.update(null, new UpdateWrapper<Goods>()
                .eq("id", goodsId)
                .gt("stock", 0)
                .setSql("stock = stock - 1"));
        if (update == 0) {
            log.warn("[GoodsSeckill] DB 库存不足，补偿 Redis: goodsId={}, userId={}", goodsId, userId);
            compensateGoodsStock(goodsId, userId);
            return;
        }

        // 4. 库存扣完则商品置为已售（status=2），并清商品缓存
        Goods after = goodsMapper.selectById(goodsId);
        if (after != null && (after.getStock() == null || after.getStock() <= 0)) {
            goodsMapper.update(null, new UpdateWrapper<Goods>()
                    .eq("id", goodsId).set("status", 2));
        }
        evictGoodsCache(goodsId);

        // 5. 创建订单（待支付）
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setGoodsId(goodsId);
        order.setBuyerId(userId);
        order.setSellerId(goods.getSellerId());
        order.setAmount(goods.getPrice());
        order.setStatus(1);      // 待支付
        order.setPayType(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        save(order);

        log.info("[GoodsSeckill] 异步下单成功: orderNo={}, goodsId={}, userId={}",
                order.getOrderNo(), goodsId, userId);
    }

    /**
     * 补偿：秒杀下单失败时回滚 Redis 预扣的商品库存与用户记录
     */
    private void compensateGoodsStock(Long goodsId, Long userId) {
        stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_GOODS_STOCK_KEY + goodsId);
        stringRedisTemplate.opsForSet().remove(RedisConstants.SECKILL_GOODS_USER_KEY + goodsId, userId.toString());
    }

    @Override
    @Transactional
    public Result mockPay(String orderNo, Integer payType) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 查询订单
        Order order = query().eq("order_no", orderNo).one();
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 3. 校验是否是买家
        if (!order.getBuyerId().equals(userId)) {
            return Result.fail("只能支付自己的订单");
        }

        // 4. 校验订单状态
        if (order.getStatus() != 1) {
            return Result.fail("订单状态异常，无法支付");
        }

        // 5. 商品原子置为已售（交易中4 → 已售2，sold+1），防止重复支付/状态竞态
        if (goodsMapper.markSold(order.getGoodsId()) == 0) {
            return Result.fail("商品状态异常，无法完成支付");
        }
        evictGoodsCache(order.getGoodsId());

        // 6. 模拟支付(直接标记为已支付)
        order.setStatus(2); // 已支付
        order.setPayType(payType);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);

        log.info("💰 模拟支付成功: orderNo={}, payType={}, amount={}",
                orderNo, payType, order.getAmount());

        return Result.ok("支付成功");
    }

    @Override
    @Transactional
    public Result cancelOrder(String orderNo) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 查询订单
        Order order = query().eq("order_no", orderNo).one();
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 3. 校验是否是买家或卖家
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            return Result.fail("无权操作此订单");
        }

        // 4. 校验订单状态(只有待支付可以取消)
        if (order.getStatus() != 1) {
            return Result.fail("当前订单状态无法取消");
        }

        // 5. 取消订单
        order.setStatus(4); // 已取消
        order.setCancelTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);

        // 6. 释放商品预占（交易中4 → 在售1），商品恢复可售
        if (goodsMapper.releasePreempt(order.getGoodsId()) == 1) {
            evictGoodsCache(order.getGoodsId());
        }

        log.info("❌ 订单已取消: orderNo={}", orderNo);

        return Result.ok("订单已取消");
    }

    @Override
    @Transactional
    public void timeoutCancelOrder(String orderNo) {
        Order order = query().eq("order_no", orderNo).one();
        if (order == null || order.getStatus() != 1) {
            return;
        }
        order.setStatus(4); // 已取消
        order.setCancelTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);

        // 超时关单同样释放商品预占，避免商品被长期锁死
        if (goodsMapper.releasePreempt(order.getGoodsId()) == 1) {
            evictGoodsCache(order.getGoodsId());
        }
    }

    @Override
    @Transactional
    public Result confirmOrder(String orderNo) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 查询订单
        Order order = query().eq("order_no", orderNo).one();
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 3. 校验是否是买家
        if (!order.getBuyerId().equals(userId)) {
            return Result.fail("只能确认自己的订单");
        }

        // 4. 校验订单状态(只有已支付可以确认收货)
        if (order.getStatus() != 2) {
            return Result.fail("当前订单状态无法确认收货");
        }

        // 5. 确认收货
        order.setStatus(3); // 已完成
        order.setFinishTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);

        log.info("✅ 订单已完成: orderNo={}", orderNo);

        return Result.ok("确认收货成功");
    }

    @Override
    public Result queryOrder(String orderNo) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 查询订单
        Order order = query().eq("order_no", orderNo).one();
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 3. 校验是否是买家或卖家
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            return Result.fail("无权查看此订单");
        }

        return Result.ok(order);
    }

    @Override
    public Order queryOrderInternal(String orderNo) {
        return query().eq("order_no", orderNo).one();
    }

    @Override
    public Result queryMyOrders(Integer status, Integer current) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 构建查询条件
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("buyer_id", userId);
        if (status != null && status > 0) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("create_time");

        // 3. 分页查询
        Page<Order> page = page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), wrapper);
        List<Order> records = page.getRecords();

        return Result.ok(records, page.getTotal());
    }

    @Override
    public Result querySellOrders(Integer status, Integer current) {
        // 1. 获取登录用户
        Long userId = UserHolder.getUserId();
        if (userId == null) {
            return Result.fail("请先登录");
        }

        // 2. 构建查询条件
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("seller_id", userId);
        if (status != null && status > 0) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("create_time");

        // 3. 分页查询
        Page<Order> page = page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE), wrapper);
        List<Order> records = page.getRecords();

        return Result.ok(records, page.getTotal());
    }

    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
