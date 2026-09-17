-- ====================================================================
--  校园小黑市 - 2026-09-17 升级脚本：每日签到（Redis Bitmap）+ 签到运费券
--  适用场景：已建库环境增量执行；全新安装请直接用 campus_bazaar.sql
--
--  本脚本幂等，可重复执行。在 DataGrip / Navicat / mysql 客户端整段执行即可。
--
--  做三件事：
--   1) tb_sign 兜底建表（表不存在时）
--   2) tb_sign 补 create_time 列（兼容历史表结构，见下方说明）
--   3) 插入"签到运费券"券模板（id = 9001，与 SignServiceImpl.SIGN_COUPON_ID 一致）
--
--  【为什么需要第 2 步】老 hmdp 版 tb_sign 长这样：
--      id / user_id / year / month / date / is_backup      —— 没有 create_time
--    而本项目实体 Sign 带 createTime，签到 INSERT 会带上该列，
--    缺列时 MySQL 直接报 Unknown column 'create_time' in 'field list'，
--    接口 500（GlobalExceptionHandler 兜成"服务器异常"）。故此处必须补齐。
--
--  MySQL 8 不支持 ADD COLUMN IF NOT EXISTS（MariaDB 语法），
--  改用 information_schema 检查 + 动态 SQL，保证可重复执行。
-- ====================================================================

USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. tb_sign 兜底建表（全新环境）
--     Redis Bitmap 是签到主存储，本表用于落库兜底，
--     uk_user_date 保证同一用户同一天只有一条记录。
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_sign` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT   NOT NULL                COMMENT '用户',
  `year`        INT      NOT NULL                COMMENT '年',
  `month`       INT      NOT NULL                COMMENT '月',
  `date`        DATE     NOT NULL                COMMENT '签到日期',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日签到';

-- --------------------------------------------------------------------
--  2. 兼容历史表结构：补齐 create_time 列（已存在则跳过）
-- --------------------------------------------------------------------
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar'
              AND TABLE_NAME = 'tb_sign' AND COLUMN_NAME = 'create_time');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_sign` ADD COLUMN `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT ''创建时间'' AFTER `date`',
  'SELECT 1');
PREPARE s1 FROM @sql;
EXECUTE s1;
DEALLOCATE PREPARE s1;

-- --------------------------------------------------------------------
--  2.1 唯一键兜底：老表若没有 uk_user_date，并发重复签到就拦不住
-- --------------------------------------------------------------------
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'campus_bazaar'
              AND TABLE_NAME = 'tb_sign' AND INDEX_NAME = 'uk_user_date');
SET @sql = IF(@idx = 0,
  'ALTER TABLE `tb_sign` ADD UNIQUE KEY `uk_user_date` (`user_id`, `date`)',
  'SELECT 1');
PREPARE s2 FROM @sql;
EXECUTE s2;
DEALLOCATE PREPARE s2;

-- --------------------------------------------------------------------
--  3. 签到运费券模板（固定 id = 9001）
--     goods_id 为 NULL 表示全场通用；type 0 普通券；status 2 已审核
--     pay_value / actual_value 单位为元（与 tb_coupon 表定义一致）
--
--     注意：只写实体真正映射的列。Coupon 实体把 stock / beginTime / endTime
--     标了 @TableField(exist = false)，实际库里没有这三列
--     （campus_bazaar.sql 里虽然声明了，但环境建表脚本可能有出入），
--     写进去会直接报 Unknown column 'stock' in 'field list'。
-- --------------------------------------------------------------------
INSERT INTO `tb_coupon`
  (`id`, `goods_id`, `title`, `sub_title`, `rules`, `pay_value`, `actual_value`, `type`, `status`)
SELECT 9001, NULL, '签到运费券', '连续签到 7 天专享', '全场商品运费抵扣 5 元，不可叠加使用，有效期 30 天', 0, 5, 0, 2
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tb_coupon` WHERE `id` = 9001);

-- --------------------------------------------------------------------
--  4. 验证：tb_sign 列齐全 + 券模板已就绪
-- --------------------------------------------------------------------
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_sign'
ORDER BY ORDINAL_POSITION;

SELECT `id`, `title`, `sub_title`, `actual_value`, `status`
FROM `tb_coupon` WHERE `id` = 9001;
