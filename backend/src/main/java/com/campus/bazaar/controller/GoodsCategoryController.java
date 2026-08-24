package com.campus.bazaar.controller;


import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.GoodsCategory;
import com.campus.bazaar.service.IGoodsCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/goods/category")
public class GoodsCategoryController {
    @Resource
    private IGoodsCategoryService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<GoodsCategory> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
