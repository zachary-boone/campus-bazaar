package com.campus.bazaar.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.entity.User;
import com.campus.bazaar.mapper.GoodsMapper;
import com.campus.bazaar.service.IGoodsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.netty.util.internal.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements IGoodsService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private com.campus.bazaar.mapper.UserMapper userMapper;

    @Override
    public Result queryById(Long id) {
        Goods goods = queryWithMutex(id);
        if (goods == null){
            return Result.fail("商品不存在");
        }
        // 填充卖家信息（前端直接读）
        if (goods.getSellerId() != null) {
            try {
                User seller = userMapper.selectById(goods.getSellerId());
                if (seller != null) {
                    goods.setSellerName(seller.getNickName());
                    goods.setSellerIcon(seller.getIcon());
                }
            } catch (Exception ignore) { }
        }
        if (goods.getSellerName() == null) goods.setSellerName("校园同学");
        if (goods.getSellerIcon() == null) goods.setSellerIcon("/imgs/icons/default-icon.svg");
        return Result.ok(goods);
    }

    //解决缓存穿透和使用互斥锁解决缓存击穿
    public Goods queryWithMutex(Long id){
        String key = "goods:"+id;

        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)){//判断是否为null，空字符串，对象
            //redis中存在，直接返回
            return JSONUtil.toBean(shopJson, Goods.class);
        }

        //如果redis中是空字符串
        if (shopJson != null){
            //不为null，那就是空字符串
            return null;
        }

        //为空
        String lockKey = "lock:goods"+id;
        Goods goods = null;
        try {
            boolean lock = tryLock(lockKey);
            if (!lock){
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            goods = getById(id);
            //模拟重建的延时
            Thread.sleep(200);
            if (goods == null){
                //解决穿透，缓存控制
                stringRedisTemplate.opsForValue().set(key,"",10, TimeUnit.MICROSECONDS);
                return null;
            }
            //存在，写入redis
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(goods),30,TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
        return goods;
    }


    //获取锁

    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    public void unLock(String key){
        stringRedisTemplate.delete(key);
    }
}




























