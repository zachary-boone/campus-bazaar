-- ====================================================================
--  校园小黑市 - 2026-09-18 升级脚本：卖家私信（联系卖家）
--  适用场景：已建库环境增量执行；全新安装请直接用 campus_bazaar.sql
--  本脚本幂等，可重复执行。
--
--  【设计】
--  · tb_chat_message：私信对话真相（双向：sender/receiver），is_read 按收件人维度
--  · 发送时同步写一条 tb_message（type=4 私信，带 sender_id 与跳转 link），
--    收件人在"我的消息"看到未读，点进去直达对话页
--  · tb_message 加 sender_id 列（动态 SQL 幂等；系统消息为 NULL）
-- ====================================================================

USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. tb_message 加 sender_id（发件人，私信专用；系统消息为 NULL）
-- --------------------------------------------------------------------
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = 'campus_bazaar'
              AND TABLE_NAME = 'tb_message' AND COLUMN_NAME = 'sender_id');
SET @sql = IF(@col = 0,
  'ALTER TABLE `tb_message` ADD COLUMN `sender_id` BIGINT DEFAULT NULL COMMENT ''发件人用户id（仅 type=4 私信）'' AFTER `user_id`',
  'SELECT 1');
PREPARE s1 FROM @sql; EXECUTE s1; DEALLOCATE PREPARE s1;

-- --------------------------------------------------------------------
--  2. 对话表
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_chat_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sender_id`   BIGINT       NOT NULL                COMMENT '发件人用户id',
  `receiver_id` BIGINT       NOT NULL                COMMENT '收件人用户id',
  `goods_id`    BIGINT                DEFAULT NULL   COMMENT '关联商品id（可空）',
  `content`     VARCHAR(255) NOT NULL                COMMENT '消息内容',
  `is_read`     TINYINT      NOT NULL DEFAULT 0      COMMENT '收件人是否已读：0未读 1已读',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_pair`        (`sender_id`, `receiver_id`),
  KEY `idx_receiver`    (`receiver_id`, `is_read`),
  KEY `idx_receiver_time` (`receiver_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='卖家私信对话';

-- --------------------------------------------------------------------
--  3. 种子数据（用户2 → 用户1，围绕商品16 罗技鼠标；幂等用显式 id）
--     同步给用户1 一条 tb_message(type=4) 未读，消息中心可见可跳转
-- --------------------------------------------------------------------
INSERT IGNORE INTO `tb_chat_message` (`id`,`sender_id`,`receiver_id`,`goods_id`,`content`,`is_read`,`create_time`) VALUES
(1, 2, 1, 16, '同学你好，罗技鼠标还在吗？可以面交吗？', 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(2, 1, 2, 16, '在的在的，东校区宿舍楼 2 栋，晚上 6 点后都可以', 1, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(3, 2, 1, 16, '好嘞，那我今晚 7 点过去，微信联系？', 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE));

INSERT IGNORE INTO `tb_message` (`id`,`user_id`,`sender_id`,`type`,`title`,`content`,`link`,`is_read`,`create_time`) VALUES
(19, 1, 2, 4, '收到新私信', '大三学姐：同学你好，罗技鼠标还在吗？可以面交吗？', '/chat.html?userId=2&goodsId=16', 0, DATE_SUB(NOW(), INTERVAL 2 HOUR));

-- --------------------------------------------------------------------
--  4. 验证
-- --------------------------------------------------------------------
SELECT id, sender_id, receiver_id, content, is_read FROM tb_chat_message ORDER BY id;
