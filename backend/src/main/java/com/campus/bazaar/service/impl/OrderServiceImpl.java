package com.campus.bazaar.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.entity.Order;
import com.campus.bazaar.mapper.GoodsMapper;
import com.campus.bazaar.mapper.OrderMapper;
import com.campus.bazaar.mq.PayCheckMessage;
import com.campus.bazaar.service.IOrderService;
import com.campus.bazaar.utils.MqConstants;
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
            return Result.fail("商品已下架");
        }
        if (goods.getSellerId().equals(userId)) {
            return Result.fail("不能购买自己的商品");
        }

        // 3. 生成订单号
        String orderNo = generateOrderNo();

        // 4. 设置订单信息
        order.setOrderNo(orderNo);
        order.setBuyerId(userId);
        order.setSellerId(goods.getSellerId());
        order.setAmount(goods.getPrice());
        order.setStatus(1); // 待支付
        order.setPayType(0); // 未支付
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        // 5. 保存订单
        save(order);

        // 6. 投递支付状态轮询延迟消息（第 1 级：5s 后首次检查，之后指数退避 10s/20s/40s/80s）
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

        // 5. 模拟支付(直接标记为已支付)
        order.setStatus(2); // 已支付
        order.setPayType(payType);
        order.setPayTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        updateById(order);

        // 6. 更新商品状态为已售
        Goods goods = new Goods();
        goods.setId(order.getGoodsId());
        goods.setStatus(2); // 已售
        goods.setSold(1);
        goods.setUpdateTime(LocalDateTime.now());
        goodsMapper.updateById(goods);

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
