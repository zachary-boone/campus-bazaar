package com.campus.bazaar.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Order;

public interface IOrderService extends IService<Order> {

    /**
     * 创建订单
     * @param order 订单信息
     * @return 订单号
     */
    Result createOrder(Order order);

    /**
     * 模拟支付
     * @param orderNo 订单号
     * @param payType 支付方式
     * @return 支付结果
     */
    Result mockPay(String orderNo, Integer payType);

    /**
     * 取消订单
     * @param orderNo 订单号
     * @return 操作结果
     */
    Result cancelOrder(String orderNo);

    /**
     * 确认收货/完成订单
     * @param orderNo 订单号
     * @return 操作结果
     */
    @SuppressWarnings(\"unused\")
    Result confirmOrder(String orderNo);

    /**
     * 查询订单详情
     * @param orderNo 订单号
     * @return 订单详情
     */
    Result queryOrder(String orderNo);

    /**
     * 查询我的订单(买家)
     * @param status 订单状态(可选)
     * @param current 页码
     * @return 订单列表
     */
    Result queryMyOrders(Integer status, Integer current);

    /**
     * 查询我卖出的订单(卖家)
     * @param status 订单状态(可选)
     * @param current 页码
     * @return 订单列表
     */
    Result querySellOrders(Integer status, Integer current);
}
