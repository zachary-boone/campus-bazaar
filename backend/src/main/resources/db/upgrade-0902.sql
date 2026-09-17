-- ====================================================================
--  校园小黑市 - 2026-09-02 阶段一升级脚本（已建库环境增量执行）
--  对应方案文档：任务 1.1/1.4/1.6
--  全新安装请直接使用 campus_bazaar.sql（已包含以下全部变更）
--
--  兼容性说明(2026-09-02 v2)：
--  MySQL 8 不支持 ALTER TABLE ... ADD COLUMN IF NOT EXISTS（那是 MariaDB 语法），
--  已改用 information_schema 检查列是否存在 + 动态 SQL 执行，幂等可重复运行。
--  在 DataGrip / Navicat 中整段执行即可。
-- ====================================================================

USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. tb_user 增加 role（任务 1.4，管理后台用）——已存在则跳过
-- --------------------------------------------------------------------
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_user' AND COLUMN_NAME = 'role');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_user` ADD COLUMN `role` TINYINT DEFAULT 0 COMMENT ''角色 0普通用户 1管理员'' AFTER `icon`',
  'SELECT 1');
PREPARE s1 FROM @sql;
EXECUTE s1;
DEALLOCATE PREPARE s1;

-- --------------------------------------------------------------------
--  2. tb_coupon_order 补充支付/核销列（任务 1.6）——逐列检查，已存在则跳过
-- --------------------------------------------------------------------
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_coupon_order' AND COLUMN_NAME = 'pay_type');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_coupon_order` ADD COLUMN `pay_type` TINYINT DEFAULT 0 COMMENT ''支付方式 0未支付 1余额 2支付宝 3微信'' AFTER `status`',
  'SELECT 1');
PREPARE s2 FROM @sql;
EXECUTE s2;
DEALLOCATE PREPARE s2;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_coupon_order' AND COLUMN_NAME = 'pay_time');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_coupon_order` ADD COLUMN `pay_time` DATETIME DEFAULT NULL COMMENT ''支付时间'' AFTER `pay_type`',
  'SELECT 1');
PREPARE s3 FROM @sql;
EXECUTE s3;
DEALLOCATE PREPARE s3;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_coupon_order' AND COLUMN_NAME = 'use_time');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_coupon_order` ADD COLUMN `use_time` DATETIME DEFAULT NULL COMMENT ''核销时间'' AFTER `pay_time`',
  'SELECT 1');
PREPARE s4 FROM @sql;
EXECUTE s4;
DEALLOCATE PREPARE s4;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_coupon_order' AND COLUMN_NAME = 'refund_time');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_coupon_order` ADD COLUMN `refund_time` DATETIME DEFAULT NULL COMMENT ''退款时间'' AFTER `use_time`',
  'SELECT 1');
PREPARE s5 FROM @sql;
EXECUTE s5;
DEALLOCATE PREPARE s5;

-- --------------------------------------------------------------------
--  3. tb_order 建表（修复原 add_order_table.sql 乱码/表名不符问题，任务 1.4）
--     上一版已执行成功则自动跳过（IF NOT EXISTS 为 MySQL 原生支持）
-- --------------------------------------------------------------------
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

-- --------------------------------------------------------------------
--  4. 验证：确认 5 个新列 + tb_order 表已就绪
-- --------------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'campus_bazaar'
  AND ((TABLE_NAME = 'tb_user' AND COLUMN_NAME = 'role')
    OR (TABLE_NAME = 'tb_coupon_order' AND COLUMN_NAME IN ('pay_type','pay_time','use_time','refund_time'))
    OR TABLE_NAME = 'tb_order')
ORDER BY TABLE_NAME, COLUMN_NAME;
