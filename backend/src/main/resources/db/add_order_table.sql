-- 订单表
CREATE TABLE IF NOT EXISTS 	b_order (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no       VARCHAR(32)  NOT NULL                COMMENT '订单号',
  goods_id       BIGINT       NOT NULL                COMMENT '商品ID',
  uyer_id       BIGINT       NOT NULL                COMMENT '买家ID',
  seller_id      BIGINT       NOT NULL                COMMENT '卖家ID',
  mount         BIGINT       NOT NULL                COMMENT '订单金额(分)',
  status         TINYINT      DEFAULT 1               COMMENT '订单状态: 1-待支付 2-已支付 3-已完成 4-已取消 5-退款中 6-已退款',
  pay_type       TINYINT      DEFAULT 0               COMMENT '支付方式: 0-未支付 1-余额 2-支付宝 3-微信',
  	rade_location VARCHAR(128) DEFAULT NULL            COMMENT '交易地点',
  	rade_time     VARCHAR(64)  DEFAULT NULL            COMMENT '交易时间',
  emark         VARCHAR(256) DEFAULT NULL            COMMENT '备注',
  pay_time       DATETIME     DEFAULT NULL            COMMENT '支付时间',
  inish_time    DATETIME     DEFAULT NULL            COMMENT '完成时间',
  cancel_time    DATETIME     DEFAULT NULL            COMMENT '取消时间',
  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_buyer_id (uyer_id),
  KEY idx_seller_id (seller_id),
  KEY idx_goods_id (goods_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';
