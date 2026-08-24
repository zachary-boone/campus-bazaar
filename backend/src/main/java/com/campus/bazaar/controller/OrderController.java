package com.campus.bazaar.controller;

import com.campus.bazaar.dto.OrderDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Order;
import com.campus.bazaar.service.IOrderService;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/order")
public class OrderController {

    @Resource
    private IOrderService orderService;

    /**
     * 创建订单（防重复提交）
     */
    @PostMapping
    @com.campus.bazaar.utils.Idempotent(ttl = 3, message = "订单创建中，请勿重复提交")
    public Result createOrder(@Valid @RequestBody OrderDTO orderDTO) {
        Order order = new Order();
        BeanUtils.copyProperties(orderDTO, order);
        return orderService.createOrder(order);
    }

    /**
     * 模拟支付（防重复提交）
     * @param orderNo 订单号
     * @param payType 支付方式: 1-余额 2-支付宝 3-微信
     */
    @PostMapping("/pay/{orderNo}")
    @com.campus.bazaar.utils.Idempotent(ttl = 3, message = "支付处理中，请勿重复提交")
    public Result mockPay(@PathVariable("orderNo") String orderNo,
                          @RequestParam(value = "payType", defaultValue = "1") Integer payType) {
        return orderService.mockPay(orderNo, payType);
    }

    /**
     * 取消订单
     */
    @PutMapping("/cancel/{orderNo}")
    public Result cancelOrder(@PathVariable("orderNo") String orderNo) {
        return orderService.cancelOrder(orderNo);
    }

    /**
     * 确认收货
     */
    @PutMapping("/confirm/{orderNo}")
    public Result confirmOrder(@PathVariable("orderNo") String orderNo) {
        return orderService.confirmOrder(orderNo);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderNo}")
    public Result queryOrder(@PathVariable("orderNo") String orderNo) {
        return orderService.queryOrder(orderNo);
    }

    /**
     * 查询我的订单(买家)
     * @param status 订单状态(可选): 1-待支付 2-已支付 3-已完成 4-已取消
     * @param current 页码
     */
    @GetMapping("/my/")
    public Result queryMyOrders(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return orderService.queryMyOrders(status, current);
    }

    /**
     * 查询我卖出的订单(卖家)
     * @param status 订单状态(可选)
     * @param current 页码
     */
    @GetMapping("/sell/")
    public Result querySellOrders(
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        return orderService.querySellOrders(status, current);
    }
}
