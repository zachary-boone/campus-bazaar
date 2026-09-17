-- ====================================================================
--  校园小黑市 - 2026-09-17 升级脚本：站内消息（我的消息）
--  适用场景：已建库环境增量执行；全新安装请直接用 campus_bazaar.sql
--  本脚本幂等，可重复执行。
--
--  【为什么需要】底栏「消息」tab 原来只是个占位（toast"即将上线"）。
--  本次新增站内消息：系统通知 / 交易动态 / 互动消息 三类，
--  支持 未读数、单条已读、全部已读。
-- ====================================================================

USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. 建表（已存在则跳过；列不会被 CREATE TABLE IF NOT EXISTS 补齐，
--     新库直接建全，老库本脚本只负责"从无到有"）
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_message` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL                COMMENT '收件人用户id',
  `type`        TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1系统 2交易 3互动',
  `title`       VARCHAR(64)  NOT NULL                COMMENT '标题（列表一行显示）',
  `content`     VARCHAR(255)          DEFAULT NULL   COMMENT '正文摘要',
  `link`        VARCHAR(128)          DEFAULT NULL   COMMENT '点击跳转链接（可空）',
  `is_read`     TINYINT      NOT NULL DEFAULT 0      COMMENT '0未读 1已读',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read`  (`user_id`, `is_read`),
  KEY `idx_user_time`  (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='站内消息';

-- --------------------------------------------------------------------
--  2. 种子数据（显式 id + INSERT IGNORE，幂等）
--     内容与库中真实数据对齐：用户 1=大四学长 2=大三学姐 4=小萌新 5=可爱多；
--     商品 1=iPad Air 5  2=小米手环8  4=算法导论  26=匡威帆布鞋；
--     帖子 6/25 属用户 1，5 属用户 2；关注关系 4→2 已存在（第 16 条补 5→1）
-- --------------------------------------------------------------------

-- ===== 用户 1（大四学长）：12 条，其中 5 条未读 =====
INSERT IGNORE INTO `tb_message` (`id`,`user_id`,`type`,`title`,`content`,`link`,`is_read`,`create_time`) VALUES
(1, 1, 1, '欢迎来到校园小黑市', '在这里可以发布闲置、砍价秒杀，祝淘到心仪好物～', '/info.html', 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
(2, 1, 1, '签到提醒', '今天还没签到哦，连续签到 7 天可得运费券', '/sign.html', 0, DATE_SUB(NOW(), INTERVAL 5 HOUR)),
(3, 1, 1, '秒杀频道已上线', '首页「限时秒杀」每晚 8 点开抢，手慢无', NULL, 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(4, 1, 2, '买家拍下了你的商品', '小萌新 拍下了你的「九成新 iPad Air 5 64G」，请及时确认', '/shop-detail.html?id=1', 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(5, 1, 2, '商品已售出', '「二手小米手环8 NFC版」已售出，货款将在买家确认收货后到账', '/shop-detail.html?id=2', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(6, 1, 2, '卖家已发货', '你购买的「匡威帆布鞋 39码 高帮」已发货，请注意查收', '/shop-detail.html?id=26', 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(7, 1, 2, '运费券到账', '连续签到奖励：运费券 ×1 已放入你的卡包', '/sign.html', 0, DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 3 HOUR),
(8, 1, 3, '新粉丝', '「可爱多」关注了你，点击查看 TA 的主页', '/other-info.html?id=5', 0, DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
(9, 1, 3, '收到新的赞', '你的帖子《学弟学妹看过来｜跳绳瑜伽垫打包出》收到 12 个赞', '/post-detail.html?id=6', 1, DATE_SUB(NOW(), INTERVAL 6 HOUR)),
(10, 1, 3, '收到新的评论', '小萌新 评论了你的帖子《求购 | 考研数学复习全书 有无笔记都行》：我有笔记，可以私聊吗', '/post-detail.html?id=25', 1, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(11, 1, 3, '求购帖有新回复', '有人想出考研数学复习全书，快去看看吧', '/post-detail.html?id=25', 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(12, 1, 1, '账号安全提醒', '你的账号于新设备登录，如非本人操作请及时修改密码', NULL, 1, DATE_SUB(NOW(), INTERVAL 4 DAY));

-- ===== 用户 2（大三学姐）=====
INSERT IGNORE INTO `tb_message` (`id`,`user_id`,`type`,`title`,`content`,`link`,`is_read`,`create_time`) VALUES
(13, 2, 2, '买家拍下了你的商品', '小萌新 拍下了你的「《算法导论》第三版」，请及时确认', '/shop-detail.html?id=4', 0, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(14, 2, 3, '新粉丝', '「小萌新」关注了你，点击查看 TA 的主页', '/other-info.html?id=4', 1, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(15, 2, 2, '商品已售出', '「桌面收纳盒 三层可叠」已售出，货款将在买家确认收货后到账', '/shop-detail.html?id=6', 1, DATE_SUB(NOW(), INTERVAL 3 DAY));

-- ===== 用户 4（小萌新）=====
INSERT IGNORE INTO `tb_message` (`id`,`user_id`,`type`,`title`,`content`,`link`,`is_read`,`create_time`) VALUES
(16, 4, 1, '欢迎来到校园小黑市', '在这里可以发布闲置、砍价秒杀，祝淘到心仪好物～', '/info.html', 1, DATE_SUB(NOW(), INTERVAL 4 DAY)),
(17, 4, 3, '关注的人有新动态', '你关注的「大三学姐」发布了新帖《毕业出闲置｜iPad Air 5 便宜出》', '/post-detail.html?id=5', 0, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ===== 用户 5（可爱多）=====
INSERT IGNORE INTO `tb_message` (`id`,`user_id`,`type`,`title`,`content`,`link`,`is_read`,`create_time`) VALUES
(18, 5, 1, '欢迎来到校园小黑市', '在这里可以发布闲置、砍价秒杀，祝淘到心仪好物～', '/info.html', 1, DATE_SUB(NOW(), INTERVAL 4 DAY));

-- --------------------------------------------------------------------
--  3. 补一条关注关系，让用户 1 的「可爱多关注了你」消息有据可查
--     （uk_user_follow 唯一键兜底，重复执行不会插重）
-- --------------------------------------------------------------------
INSERT IGNORE INTO `tb_follow` (`user_id`, `follow_user_id`, `create_time`)
VALUES (5, 1, DATE_SUB(NOW(), INTERVAL 10 MINUTE));

-- --------------------------------------------------------------------
--  4. 验证
-- --------------------------------------------------------------------
SELECT user_id, COUNT(*) AS total, SUM(is_read = 0) AS unread
FROM `tb_message` GROUP BY user_id ORDER BY user_id;
