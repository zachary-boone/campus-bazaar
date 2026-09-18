package com.campus.bazaar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 拼单（GroupBuy）
 * <p>
 * 规则：拼单价 = 商品价 9 折（开团时算好存库）；required_num 人成团（默认 2）；
 * 有效期 24 小时，到期未成团自动过期（{@code GroupBuyExpireTask} + 查询懒惰过期双保险）。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_group_buy")
public class GroupBuy implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：拼单中 */
    public static final int STATUS_ONGOING = 1;
    /** 状态：已成团 */
    public static final int STATUS_SUCCESS = 2;
    /** 状态：已过期 */
    public static final int STATUS_EXPIRED = 3;

    /**
     * 拼单id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商品id
     */
    private Long goodsId;

    /**
     * 团长用户id
     */
    private Long leaderUserId;

    /**
     * 拼单价（9 折，单位元）
     */
    private Long groupPrice;

    /**
     * 成团所需人数
     */
    private Integer requiredNum;

    /**
     * 1拼单中 2已成团 3已过期
     */
    private Integer status;

    /**
     * 开团时间
     */
    private LocalDateTime createTime;

    /**
     * 成团截止时间（开团 + 24h）
     */
    private LocalDateTime expireTime;
}
