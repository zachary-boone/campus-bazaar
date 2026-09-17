-- ====================================================================
--  校园小黑市 - 2026-09-17 升级脚本：关注关系表加固
--  适用场景：已建库环境增量执行；全新安装请直接用 campus_bazaar.sql
--  本脚本幂等，可重复执行。
--
--  【为什么需要】本机 tb_follow 只有 PRIMARY(id)，没有 (user_id, follow_user_id)
--  唯一键。前端"关注"按钮是可以连点的，缺唯一键时同一对关系会插入多行，
--  关注数/粉丝数就会被算多。这里补上唯一键，让 DB 成为最后一道防线
--  （Service 层已做幂等判断，唯一键用于兜住并发）。
-- ====================================================================

USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. 先清理历史重复行（同一 (user_id, follow_user_id) 只保留最早的一条）
--     若本身没有重复，这条 DELETE 不影响任何数据
-- --------------------------------------------------------------------
DELETE t1 FROM `tb_follow` t1
  JOIN `tb_follow` t2
    ON t1.`user_id` = t2.`user_id`
   AND t1.`follow_user_id` = t2.`follow_user_id`
   AND t1.`id` > t2.`id`;

-- --------------------------------------------------------------------
--  2. 补唯一键 uk_user_follow（已存在则跳过）
-- --------------------------------------------------------------------
SET @idx = (SELECT COUNT(*) FROM information_schema.STATISTICS
            WHERE TABLE_SCHEMA = 'campus_bazaar'
              AND TABLE_NAME = 'tb_follow' AND INDEX_NAME = 'uk_user_follow');
SET @sql = IF(@idx = 0,
  'ALTER TABLE `tb_follow` ADD UNIQUE KEY `uk_user_follow` (`user_id`, `follow_user_id`)',
  'SELECT 1');
PREPARE s1 FROM @sql;
EXECUTE s1;
DEALLOCATE PREPARE s1;

-- --------------------------------------------------------------------
--  3. 验证
-- --------------------------------------------------------------------
SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = 'campus_bazaar' AND TABLE_NAME = 'tb_follow'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

SELECT COUNT(*) AS follow_rows FROM `tb_follow`;

-- --------------------------------------------------------------------
--  4. 清理指向"已不存在用户"的关注关系（历史脏数据）
--     只删 user_id / follow_user_id 已经不在 tb_user 里的行；
--     正常账号之间的关注关系不会受影响。
-- --------------------------------------------------------------------
DELETE FROM `tb_follow`
WHERE `user_id` NOT IN (SELECT `id` FROM `tb_user`)
   OR `follow_user_id` NOT IN (SELECT `id` FROM `tb_user`);
