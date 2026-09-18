-- ====================================================================
--  校园小黑市 - 2026-09-18 升级脚本：商品"想要" + 求购榜
--  适用场景：已建库环境增量执行；全新安装请直接用 campus_bazaar.sql
--  本脚本幂等，可重复执行。
--
--  【为什么需要】详情页的"♥ 想要"原来是纯前端摆设（只翻转本地状态，
--  不调任何接口），"求购榜单"没有数据来源。本次：
--  · tb_goods_like 明细表：谁想要哪件商品（uk_goods_user 唯一，一人一件一次）
--  · tb_goods.wants 计数列：想要人数，与明细同事务维护，可随时从明细回填修正
--  · 排序白名单新增 sortBy=wants（求购榜：按想要人数降序）
-- ====================================================================

USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. tb_goods 加 wants 计数列（已存在则跳过；
--     CREATE TABLE IF NOT EXISTS 不会给已存在的表补列，必须动态 SQL）
-- --------------------------------------------------------------------
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar'
              AND TABLE_NAME = 'tb_goods' AND COLUMN_NAME = 'wants');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_goods` ADD COLUMN `wants` INT NOT NULL DEFAULT 0 COMMENT ''想要人数（求购榜排序依据）''',
  'SELECT 1');
PREPARE s1 FROM @sql; EXECUTE s1; DEALLOCATE PREPARE s1;

-- --------------------------------------------------------------------
--  2. 明细表
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_goods_like` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `goods_id`    BIGINT   NOT NULL                COMMENT '商品id',
  `user_id`     BIGINT   NOT NULL                COMMENT '想要该商品的用户id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_goods_user` (`goods_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='商品想要（求购）明细';

-- --------------------------------------------------------------------
--  3. 种子明细（确定性伪随机，幂等：uk_goods_user 兜底重复插入）
--     · 用户池 = tb_user 全体注册用户；排除卖家点自己商品
--     · 命中规则 (g.id*3 + u.id) % (2 + g.id % 8) = 0：
--       除数随商品在 2~9 间变化 → 每件商品 2~10 人想要，榜单有梯度
--     · 时间取 (g.id*13 + u.id*3) % 168 小时内（近 7 天）
-- --------------------------------------------------------------------
INSERT IGNORE INTO `tb_goods_like` (`goods_id`, `user_id`, `create_time`)
SELECT g.`id`, u.`id`,
       DATE_SUB(NOW(), INTERVAL ((g.`id` * 13 + u.`id` * 3) % 168) HOUR)
FROM `tb_goods` g
JOIN `tb_user` u
  ON ((g.`id` * 3 + u.`id`) % (2 + g.`id` % 8)) = 0
 AND u.`id` <> g.`seller_id`;

-- --------------------------------------------------------------------
--  4. 从明细回填计数（与明细严格一致；like/unlike 接口会实时维护，
--     本语句用于初始化和人工修正，可重复执行）
-- --------------------------------------------------------------------
UPDATE `tb_goods` g
SET g.`wants` = (SELECT COUNT(*) FROM `tb_goods_like` l WHERE l.`goods_id` = g.`id`);

-- --------------------------------------------------------------------
--  5. 验证（求购榜 Top10）
-- --------------------------------------------------------------------
SELECT g.id, g.name, g.wants FROM tb_goods g
WHERE g.status = 1 ORDER BY g.wants DESC, g.id DESC LIMIT 10;
