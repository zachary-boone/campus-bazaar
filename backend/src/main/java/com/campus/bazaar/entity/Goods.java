package com.campus.bazaar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 校园小黑市商品
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_goods")
public class Goods implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商品标题
     */
    private String name;

    /**
     * 商品分类id
     */
    private Long typeId;

    /**
     * 卖家用户id
     */
    private Long sellerId;

    /**
     * 商品状态 1在售 2已售 3下架 4交易中(下单锁定,待支付)
     */
    private Integer status;

    /**
     * 商品图片，多个图片以','隔开
     */
    private String images;

    /**
     * 校区/区域
     */
    private String area;

    /**
     * 楼栋定位，例如3号宿舍楼
     */
    private String address;

    /**
     * 经度
     */
    private Double x;

    /**
     * 维度
     */
    private Double y;

    /**
     * 售价(单位分)
     */
    private Long price;

    /**
     * 销量
     */
    private Integer sold;

    /**
     * 评论数量
     */
    private Integer comments;

    /**
     * 想要人数（求购榜排序依据；tb_goods_like 明细的同事务计数）
     */
    private Integer wants;

    /**
     * 评分，1~5分，乘10保存，避免小数
     */
    private Integer score;

    /**
     * 可交易时间
     */
    private String tradeTime;

    /**
     * 库存（秒杀扣减，0 为已抢光；一物一件的商品固定为 1）
     */
    private Integer stock;

    /**
     * 秒杀开始时间（为空则不限制）
     */
    private LocalDateTime seckillBegin;

    /**
     * 秒杀结束时间（为空则不限制）
     */
    private LocalDateTime seckillEnd;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


    @TableField(exist = false)
    private Double distance;

    /** 卖家昵称 (前端展示用，DB 中没有该列) */
    @TableField(exist = false)
    private String sellerName;

    /** 卖家头像 (前端展示用，DB 中没有该列) */
    @TableField(exist = false)
    private String sellerIcon;
}
