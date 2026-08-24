package com.campus.bazaar.service;

import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.Goods;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IGoodsService extends IService<Goods> {

    Result queryById(Long id);
}
