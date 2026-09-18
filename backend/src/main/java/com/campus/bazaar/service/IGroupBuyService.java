package com.campus.bazaar.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.bazaar.dto.Result;
import com.campus.bazaar.entity.GroupBuy;

/**
 * 拼单服务
 */
public interface IGroupBuyService extends IService<GroupBuy> {

    /**
     * 拼单大厅：进行中的拼单列表（带商品卡片信息、已参人数、剩余毫秒）
     */
    Result activeGroups();

    /**
     * 某商品的进行中拼单（商品详情页展示"参与拼单"用），同时懒惰过期
     */
    Result activeByGoods(Long goodsId);

    /**
     * 开团：校验商品在售/未重复开团，创建拼单并把当前用户记为第 1 个成员
     */
    Result createGroup(Long goodsId);

    /**
     * 参团：Redisson 锁防超员 + 唯一键兜底，人满自动成团
     */
    Result joinGroup(Long groupId);

    /**
     * 我的拼单：我发起的 + 我参加的（带商品信息与拼单状态）
     */
    Result myGroups();
}
