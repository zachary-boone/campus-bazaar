package com.campus.bazaar.dto;

import lombok.Data;

/**
 * 拼单卡片 DTO：拼单大厅 / 商品详情 / 我的拼单 共用
 */
@Data
public class GroupCardDTO {

    /** 拼单id */
    private Long id;

    /** 商品id */
    private Long goodsId;

    /** 商品名 */
    private String goodsName;

    /** 商品首图 */
    private String goodsImg;

    /** 商品原价（元） */
    private Long price;

    /** 拼单价（9 折，元） */
    private Long groupPrice;

    /** 成团所需人数 */
    private Integer requiredNum;

    /** 已参人数 */
    private Integer joinedNum;

    /** 团长用户id */
    private Long leaderId;

    /** 拼单状态：1拼单中 2已成团 3已过期 */
    private Integer status;

    /** 成团截止时间的毫秒值（前端做倒计时） */
    private Long expireMs;

    /** 当前用户在该团的角色：leader=我发起的 member=我参加的 null=与我无关（仅"我的拼单"里会带） */
    private String myRole;
}
