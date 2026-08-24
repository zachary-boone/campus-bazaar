package com.campus.bazaar.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.dto.UserDTO;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.mapper.GoodsMapper;
import com.campus.bazaar.mapper.UserMapper;
import com.campus.bazaar.service.IGoodsService;
import com.campus.bazaar.utils.RedisConstants;
import com.campus.bazaar.utils.SystemConstants;
import com.campus.bazaar.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
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

    @Override
    public Result queryById(Long id) {
        Goods goods = queryWithMutex(id);
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
        goods.setCreateTime(LocalDateTime.now());
        goods.setUpdateTime(LocalDateTime.now());

        // 4. 保存商品
        save(goods);

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

        // 5. 清除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_GOODS_KEY + goods.getId());

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

        // 4. 下架商品
        goods.setStatus(3); // 下架
        goods.setUpdateTime(LocalDateTime.now());
        updateById(goods);

        // 5. 清除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_GOODS_KEY + goodsId);

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

        // 4. 上架商品
        goods.setStatus(1); // 在售
        goods.setUpdateTime(LocalDateTime.now());
        updateById(goods);

        // 5. 清除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_GOODS_KEY + goodsId);

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

        // 4. 删除商品
        removeById(goodsId);

        // 5. 清除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_GOODS_KEY + goodsId);

        return Result.ok();
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

    /**
     * 缓存击穿防护 - 互斥锁
     */
    private Goods queryWithMutex(Long id) {
        String key = RedisConstants.CACHE_GOODS_KEY + id;

        String goodsJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(goodsJson)) {
            return JSONUtil.toBean(goodsJson, Goods.class);
        }

        if (goodsJson != null) {
            return null;
        }

        String lockKey = RedisConstants.LOCK_GOODS_KEY + id;
        Goods goods = null;
        // Redisson 分布式锁（替代手写 SETNX）：可重入、自动续期、原子解锁
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
            if (!locked) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            goods = getById(id);
            Thread.sleep(200);
            if (goods == null) {
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(goods), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        return goods;
    }
}
