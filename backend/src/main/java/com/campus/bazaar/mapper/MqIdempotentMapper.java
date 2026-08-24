package com.campus.bazaar.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.bazaar.entity.MqIdempotent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * MQ 消费幂等 Mapper
 */
public interface MqIdempotentMapper extends BaseMapper<MqIdempotent> {

    /**
     * 幂等抢占：INSERT IGNORE，msg_id 唯一键冲突返回 0 表示已处理过
     */
    @Insert("INSERT IGNORE INTO tb_mq_idempotent (msg_id, business_type, create_time) VALUES (#{msgId}, #{businessType}, NOW())")
    int insertIgnore(@Param("msgId") String msgId, @Param("businessType") String businessType);
}
