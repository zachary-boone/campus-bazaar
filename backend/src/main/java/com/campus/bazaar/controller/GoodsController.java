package com.campus.bazaar.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.bazaar.dto.GoodsDTO;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.service.IGoodsService;
import com.campus.bazaar.utils.SystemConstants;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;

@Validated
@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Resource
    public IGoodsService goodsService;

    /**
     * 根据id查询商品详情
     */
    @GetMapping("/{id}")
    public Result queryGoodsById(@PathVariable("id") Long id) {
        return goodsService.queryById(id);
    }

    /**
     * 发布商品
     */
    @PostMapping
    public Result saveGoods(@Valid @RequestBody GoodsDTO goodsDTO) {
        Goods goods = new Goods();
        BeanUtils.copyProperties(goodsDTO, goods);
        return goodsService.publishGoods(goods);
    }

    /**
     * 编辑商品
     */
    @PutMapping
    public Result updateGoods(@Valid @RequestBody GoodsDTO goodsDTO) {
        if (goodsDTO.getId() == null) {
            return Result.fail("商品ID不能为空");
        }
        Goods goods = new Goods();
        BeanUtils.copyProperties(goodsDTO, goods);
        return goodsService.updateGoods(goods);
    }

    /**
     * 下架商品
     */
    @PutMapping("/off/{id}")
    public Result offShelfGoods(@PathVariable("id") Long id) {
        return goodsService.offShelfGoods(id);
    }

    /**
     * 上架商品
     */
    @PutMapping("/on/{id}")
    public Result onShelfGoods(@PathVariable("id") Long id) {
        return goodsService.onShelfGoods(id);
    }

    /**
     * 删除商品
     */
    @DeleteMapping("/{id}")
    public Result deleteGoods(@PathVariable("id") Long id) {
        return goodsService.deleteGoods(id);
    }

    /**
     * 根据商品类型分页查询
     */
    @GetMapping("/of/type")
    public Result queryGoodsByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Goods> page = goodsService.query()
                .eq(typeId != null && typeId > 0, "type_id", typeId)
                .eq("status", 1)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商品名称关键字分页查询
     */
    @GetMapping("/of/name")
    public Result queryGoodsByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current) {
        Page<Goods> page = goodsService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .eq("status", 1)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }

    /**
     * 查询我发布的商品
     */
    @GetMapping("/of/me")
    public Result queryMyGoods(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return goodsService.queryMyGoods(current);
    }

    /**
     * 附近商品（Redis GEO 半径检索，按距离升序）
     * @param x      中心经度
     * @param y      中心纬度
     * @param radius 半径（公里，默认 3）
     * @param area   校区（可空，空则全校区）
     */
    @GetMapping("/of/nearby")
    public Result queryNearbyGoods(@RequestParam("x") Double x,
                                   @RequestParam("y") Double y,
                                   @RequestParam(value = "radius", defaultValue = "3") Double radius,
                                   @RequestParam(value = "area", required = false) String area) {
        return goodsService.queryNearbyGoods(x, y, radius, area);
    }
}
