-- ====================================================================
--  校园小黑市 - 2026-09-18 升级脚本：拼单购买（GroupBuy）
--  适用场景：已建库环境增量执行；全新安装请直接用 campus_bazaar.sql
--  本脚本幂等，可重复执行。
--
--  【规则】
--  · 拼单价 = 商品价 9 折（开团时按 ROUND(price*0.9) 算好存库，之后不变）
--  · 2 人成团（发起人 + 1 个参团人），适合校园二手"同班一起收"
--  · 有效期 24 小时，到期未成团自动过期（定时任务 + 查询时懒惰过期双保险）
--  · 状态：1 拼单中 / 2 已成团 / 3 已过期
--  · 并发防护：Redisson 锁 lock:group:join:{groupId} + 唯一键 uk_group_user 兜底，
--    人满后绝不超员；不能参加自己的团
-- ====================================================================

USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. 建表
-- --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tb_group_buy` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '拼单id',
  `goods_id`       BIGINT        NOT NULL                COMMENT '商品id',
  `leader_user_id` BIGINT        NOT NULL                COMMENT '团长用户id',
  `group_price`    BIGINT        NOT NULL                COMMENT '拼单价（9折，单位元）',
  `required_num`   INT           NOT NULL DEFAULT 2      COMMENT '成团所需人数',
  `status`         TINYINT       NOT NULL DEFAULT 1      COMMENT '1拼单中 2已成团 3已过期',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '开团时间',
  `expire_time`    DATETIME      NOT NULL                COMMENT '成团截止时间（开团+24h）',
  PRIMARY KEY (`id`),
  KEY `idx_goods_status` (`goods_id`, `status`),
  KEY `idx_status_expire` (`status`, `expire_time`),
  KEY `idx_leader` (`leader_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='拼单';

CREATE TABLE IF NOT EXISTS `tb_group_member` (
  `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `group_id`    BIGINT   NOT NULL                COMMENT '拼单id',
  `user_id`     BIGINT   NOT NULL                COMMENT '参团用户id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '参团时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='拼单成员';

-- --------------------------------------------------------------------
--  2. 种子数据（显式 id + INSERT IGNORE 幂等）
--     商品：16=罗技鼠标189  17=索尼耳机899  24=星巴克保温杯99  31=民谣吉他280
--     用户：1=大四学长 2=大三学姐 4=小萌新 5=可爱多
--     拼单价 = 9 折：16→170  17→809  24→89  31→252
-- --------------------------------------------------------------------

-- 进行中：罗技鼠标（团长 用户2，还差 1 人）
INSERT IGNORE INTO `tb_group_buy`
(`id`,`goods_id`,`leader_user_id`,`group_price`,`required_num`,`status`,`create_time`,`expire_time`) VALUES
(1, 16, 2, 170, 2, 1, DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_ADD(NOW(), INTERVAL 21 HOUR)),
(2, 17, 5, 809, 2, 1, DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_ADD(NOW(), INTERVAL 16 HOUR)),
(3, 24, 4, 89,  2, 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_ADD(NOW(), INTERVAL 23 HOUR)),
-- 已成团示例：民谣吉他（用户5开团、用户4参团）
(4, 31, 5, 252, 2, 2, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT IGNORE INTO `tb_group_member` (`id`,`group_id`,`user_id`,`create_time`) VALUES
(1, 1, 2, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(2, 2, 5, DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(3, 3, 4, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(4, 4, 5, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(5, 4, 4, DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 2 HOUR);

-- --------------------------------------------------------------------
--  3. 验证
-- --------------------------------------------------------------------
SELECT g.id, g.goods_id, gd.name, g.group_price, g.required_num, g.status,
       (SELECT COUNT(*) FROM tb_group_member m WHERE m.group_id = g.id) AS joined
FROM tb_group_buy g JOIN tb_goods gd ON gd.id = g.goods_id
ORDER BY g.id;
