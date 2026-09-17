package com.campus.bazaar.dto;

import lombok.Data;

/**
 * 用户卡片
 * <p>
 * 个人主页（other-info）、关注列表、粉丝列表共用一份结构：
 * 列表场景只填 id/nickName/icon/introduce/followed，计数留空（序列化时按 non_null 省略）；
 * 个人主页场景额外填 followers/following。
 */
@Data
public class UserCardDTO {

    private Long id;

    private String nickName;

    private String icon;

    /** 个性签名（tb_user_info.introduce，可能为空） */
    private String introduce;

    /** 粉丝数 */
    private Long followers;

    /** 关注数 */
    private Long following;

    /** 在售商品数（tb_goods status=1） */
    private Long goodsCount;

    /** 已售出商品数（tb_goods status=2） */
    private Long soldCount;

    /** 当前登录用户是否已关注 TA（未登录恒为 false） */
    private Boolean followed;
}
