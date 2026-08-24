package com.campus.bazaar.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.service.IGoodsService;
import com.campus.bazaar.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Resource
    public IGoodsService goodsService;

    /**
     * 根据id查询商品详情
     * @param id 商品id
     * @return 商品详情
     */
    @GetMapping("/{id}")
    public Result queryGoodsById(@PathVariable("id") Long id) {
        return goodsService.queryById(id);
    }

    /**
     * 发布商品
     * @param goods 商品信息
     * @return 商品id
     */
    @PostMapping
    public Result saveGoods(@RequestBody Goods goods) {
        return goodsService.publishGoods(goods);
    }

    /**
     * 编辑商品
     * @param goods 商品信息
     * @return 操作结果
     */
    @PutMapping
    public Result updateGoods(@RequestBody Goods goods) {
        return goodsService.updateGoods(goods);
    }

    /**
     * 下架商品
     * @param id 商品id
     * @return 操作结果
     */
    @PutMapping("/off/{id}")
    public Result offShelfGoods(@PathVariable("id") Long id) {
        return goodsService.offShelfGoods(id);
    }

    /**
     * 上架商品
     * @param id 商品id
     * @return 操作结果
     */
    @PutMapping("/on/{id}")
    public Result onShelfGoods(@PathVariable("id") Long id) {
        return goodsService.onShelfGoods(id);
    }

    /**
     * 删除商品
     * @param id 商品id
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result deleteGoods(@PathVariable("id") Long id) {
        return goodsService.deleteGoods(id);
    }

    /**
     * 根据商品类型分页查询
     * @param typeId 商品类型
     * @param current 页码
     * @return 商品列表
     */
    @GetMapping("/of/type")
    public Result queryGoodsByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Goods> page = goodsService.query()
                .eq(typeId != null && typeId > 0, "type_id", typeId)
                .eq("status", 1) // 只查在售商品
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商品名称关键字分页查询
     * @param name 商品名称关键字
     * @param current 页码
     * @return 商品列表
     */
    @GetMapping("/of/name")
    public Result queryGoodsByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Goods> page = goodsService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .eq("status", 1) // 只查在售商品
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 查询我发布的商品
     * @param current 页码
     * @return 商品列表
     */
    @GetMapping("/of/me")
    public Result queryMyGoods(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return goodsService.queryMyGoods(current);
    }
}
