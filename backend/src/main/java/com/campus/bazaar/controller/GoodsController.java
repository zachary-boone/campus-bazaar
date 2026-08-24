package com.campus.bazaar.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.campus.bazaar.service.IGoodsService;
import com.campus.bazaar.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Resource
    public IGoodsService goodsService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryGoodsById(@PathVariable("id") Long id) {
        return goodsService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param goods 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveGoods(@RequestBody Goods goods) {
        // 写入数据库
        goodsService.save(goods);
        // 返回店铺id
        return Result.ok(goods.getId());
    }

    /**
     * 更新商铺信息
     * @param goods 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateGoods(@RequestBody Goods goods) {
        // 写入数据库
        goodsService.updateById(goods);
        return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryGoodsByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询 (typeId<=0 视为全部)
        Page<Goods> page = goodsService.query()
                .eq(typeId != null && typeId > 0, "type_id", typeId)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     * @param name 商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryGoodsByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Goods> page = goodsService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
