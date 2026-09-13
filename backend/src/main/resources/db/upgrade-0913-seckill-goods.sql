-- ====================================================================
--  校园小黑市 - 2026-09-13 秒杀迁移到商品 升级脚本
--  适用：已建库环境增量执行（在 DataGrip / Navicat 中整段执行即可）
--  全新安装请直接使用 campus_bazaar.sql（已包含以下全部变更）
--
--  背景：原秒杀链路挂在"优惠券"上（tb_seckill_coupon.stock），
--        现迁移到"商品"上，商品自带库存，可多件售卖、可秒杀。
--        优惠券相关表与代码保留（不影响原有功能），仅新增商品库存能力。
-- ====================================================================

USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. tb_goods 增加库存与秒杀时间窗（逐列检查，幂等可重复运行）
-- --------------------------------------------------------------------
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_goods' AND COLUMN_NAME = 'stock');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_goods` ADD COLUMN `stock` INT DEFAULT 1 COMMENT ''库存(秒杀扣减，0为已抢光)'' AFTER `trade_time`',
  'SELECT 1');
PREPARE s1 FROM @sql;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_goods' AND COLUMN_NAME = 'seckill_begin');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_goods` ADD COLUMN `seckill_begin` DATETIME DEFAULT NULL COMMENT ''秒杀开始时间(为空则不限制)'' AFTER `stock`',
  'SELECT 1');
PREPARE s2 FROM @sql;
EXECUTE s2;
DEALLOCATE PREPARE s2;

SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_goods' AND COLUMN_NAME = 'seckill_end');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_goods` ADD COLUMN `seckill_end` DATETIME DEFAULT NULL COMMENT ''秒杀结束时间(为空则不限制)'' AFTER `seckill_begin`',
  'SELECT 1');
PREPARE s3 FROM @sql;
EXECUTE s3;
DEALLOCATE PREPARE s3;

-- --------------------------------------------------------------------
--  2. MQ 消费幂等表（秒杀异步建单去重，msg_id 唯一键）
--     原券秒杀依赖此表但建库脚本缺失，此处一并补上
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_mq_idempotent` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `msg_id`        VARCHAR(64)  NOT NULL                COMMENT '消息唯一ID(生产者生成)',
  `business_type` VARCHAR(32)  DEFAULT NULL            COMMENT '业务类型 seckill_order/goods_seckill_order',
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_id` (`msg_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MQ消费幂等表';

-- --------------------------------------------------------------------
--  3. 秒杀演示数据：商品 1 设置 100 件库存并开启秒杀时间窗
-- --------------------------------------------------------------------
UPDATE `tb_goods`
SET `stock` = 100,
    `seckill_begin` = DATE_SUB(NOW(), INTERVAL 1 DAY),
    `seckill_end`   = DATE_ADD(NOW(), INTERVAL 30 DAY)
WHERE `id` = 1;

-- --------------------------------------------------------------------
--  4. 验证
-- --------------------------------------------------------------------
SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'campus_bazaar'
  AND TABLE_NAME IN ('tb_goods', 'tb_mq_idempotent')
  AND COLUMN_NAME IN ('stock', 'seckill_begin', 'seckill_end', 'msg_id')
ORDER BY TABLE_NAME, COLUMN_NAME;

SELECT `id`, `name`, `stock`, `seckill_begin`, `seckill_end` FROM `tb_goods` WHERE `id` = 1;
