-- ====================================================================
--  订单表 tb_order（增量脚本，已建库环境执行；全量库请直接使用 campus_bazaar.sql）
--  修复说明(2026-09-02)：原脚本字段乱码损坏(buyer_id→uyer_id 等)、表名 b_order 与实体不符，
--  已按 Order.java 实体对齐重写。
-- ====================================================================
CREATE TABLE IF NOT EXISTS `tb_order` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no`       VARCHAR(32)  NOT NULL                COMMENT '订单号',
  `goods_id`       BIGINT       NOT NULL                COMMENT '商品ID',
  `buyer_id`       BIGINT       NOT NULL                COMMENT '买家ID',
  `seller_id`      BIGINT       NOT NULL                COMMENT '卖家ID',
  `amount`         BIGINT       NOT NULL                COMMENT '订单金额(分)',
  `status`         TINYINT      DEFAULT 1               COMMENT '订单状态: 1-待支付 2-已支付 3-已完成 4-已取消 5-退款中 6-已退款',
  `pay_type`       TINYINT      DEFAULT 0               COMMENT '支付方式: 0-未支付 1-余额 2-支付宝 3-微信',
  `trade_location` VARCHAR(128) DEFAULT NULL            COMMENT '交易地点',
  `trade_time`     VARCHAR(64)  DEFAULT NULL            COMMENT '交易时间',
  `remark`         VARCHAR(256) DEFAULT NULL            COMMENT '备注',
  `pay_time`       DATETIME     DEFAULT NULL            COMMENT '支付时间',
  `finish_time`    DATETIME     DEFAULT NULL            COMMENT '完成时间',
  `cancel_time`    DATETIME     DEFAULT NULL            COMMENT '取消时间',
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_buyer_id` (`buyer_id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_goods_id` (`goods_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
