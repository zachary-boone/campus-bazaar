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
 * 拼单成员。唯一键 uk_group_user(group_id, user_id)：一人一团单只参一次。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_group_member")
public class GroupMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 拼单id
     */
    private Long groupId;

    /**
     * 参团用户id
     */
    private Long userId;

    /**
     * 参团时间
     */
    private LocalDateTime createTime;
}
