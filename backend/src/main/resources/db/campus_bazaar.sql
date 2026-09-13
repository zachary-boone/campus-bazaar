-- ====================================================================
--  校园小黑市交易平台 - 数据库初始化脚本
--  数据库: campus_bazaar (MySQL 8.x)
--  说明:   12 张表 + 14 件示例商品 + 4 条示例帖子 + 7 个商品分类
-- ====================================================================

DROP DATABASE IF EXISTS `campus_bazaar`;
CREATE DATABASE `campus_bazaar` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. 用户表 tb_user
-- --------------------------------------------------------------------
CREATE TABLE `tb_user` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `phone`       VARCHAR(11)  NOT NULL                COMMENT '手机号(校园账号)',
  `password`    VARCHAR(128) DEFAULT NULL             COMMENT '密码(可空,验证码登录无密码)',
  `nick_name`   VARCHAR(32)  DEFAULT NULL             COMMENT '昵称',
  `icon`        VARCHAR(255) DEFAULT NULL             COMMENT '头像路径',
  `role`        TINYINT      DEFAULT 0                COMMENT '角色 0普通用户 1管理员',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园用户表';

-- --------------------------------------------------------------------
--  2. 用户扩展信息表
-- --------------------------------------------------------------------
CREATE TABLE `tb_user_info` (
  `user_id`     BIGINT       NOT NULL                COMMENT '用户ID',
  `city`        VARCHAR(64)  DEFAULT NULL             COMMENT '城市',
  `introduce`   VARCHAR(255) DEFAULT NULL             COMMENT '个人介绍',
  `fans`        INT          DEFAULT 0                COMMENT '粉丝数',
  `followee`    INT          DEFAULT 0                COMMENT '关注数',
  `gender`      TINYINT      DEFAULT 0                COMMENT '性别 0保密 1男 2女',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户扩展信息';

-- --------------------------------------------------------------------
--  3. 商品分类表
-- --------------------------------------------------------------------
CREATE TABLE `tb_goods_category` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(32)  NOT NULL                COMMENT '分类名',
  `icon`        VARCHAR(255) DEFAULT NULL             COMMENT '图标路径',
  `sort`        INT          DEFAULT 0                COMMENT '排序',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类(校园 7 大类)';

-- --------------------------------------------------------------------
--  4. 商品表
-- --------------------------------------------------------------------
CREATE TABLE `tb_goods` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name`        VARCHAR(128) NOT NULL                COMMENT '商品名称',
  `type_id`     BIGINT       NOT NULL                COMMENT '分类ID',
  `seller_id`   BIGINT       DEFAULT NULL             COMMENT '卖家ID',
  `status`      TINYINT      DEFAULT 1                COMMENT '状态 1在售 2已售 3下架 4交易中(下单锁定)',
  `images`      TEXT         DEFAULT NULL             COMMENT '商品图(逗号分隔)',
  `area`        VARCHAR(64)  DEFAULT NULL             COMMENT '校区/区域',
  `address`     VARCHAR(128) DEFAULT NULL             COMMENT '楼栋定位',
  `x`           DOUBLE       DEFAULT NULL             COMMENT '经度',
  `y`           DOUBLE       DEFAULT NULL             COMMENT '纬度',
  `price`       BIGINT       NOT NULL                COMMENT '售价(元)',
  `sold`        INT          DEFAULT 0                COMMENT '已售出',
  `comments`    INT          DEFAULT 0                COMMENT '想要人数',
  `score`       INT          DEFAULT 50               COMMENT '评分(1-50)',
  `trade_time`  VARCHAR(64)  DEFAULT '18:00-22:00'    COMMENT '可面交时间',
  `stock`       INT          DEFAULT 1                COMMENT '库存(秒杀扣减，0为已抢光)',
  `seckill_begin` DATETIME   DEFAULT NULL             COMMENT '秒杀开始时间(为空则不限制)',
  `seckill_end`   DATETIME   DEFAULT NULL             COMMENT '秒杀结束时间(为空则不限制)',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type_id`   (`type_id`),
  KEY `idx_seller_id` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- --------------------------------------------------------------------
--  5. 运费券表
-- --------------------------------------------------------------------
CREATE TABLE `tb_coupon` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `goods_id`      BIGINT       DEFAULT NULL             COMMENT '关联商品',
  `title`         VARCHAR(64)  NOT NULL                COMMENT '券标题',
  `sub_title`     VARCHAR(128) DEFAULT NULL             COMMENT '副标题',
  `rules`         TEXT         DEFAULT NULL             COMMENT '使用规则',
  `pay_value`     BIGINT       NOT NULL                COMMENT '支付金额(元)',
  `actual_value`  BIGINT       DEFAULT 0                COMMENT '抵扣价值(元)',
  `type`          TINYINT      DEFAULT 0                COMMENT '0普通 1秒杀',
  `status`        TINYINT      DEFAULT 1                COMMENT '1未审核 2已审核 99已过期',
  `stock`         INT          DEFAULT 0                COMMENT '库存',
  `begin_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '结束时间',
  `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费券表';

-- --------------------------------------------------------------------
--  6. 秒杀运费券表
-- --------------------------------------------------------------------
CREATE TABLE `tb_seckill_coupon` (
  `id`          BIGINT       NOT NULL                COMMENT '关联 tb_coupon.id',
  `stock`       INT          DEFAULT 0                COMMENT '秒杀库存',
  `begin_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '开始时间',
  `end_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '结束时间',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀运费券库存';

-- --------------------------------------------------------------------
--  7. 券订单表
-- --------------------------------------------------------------------
CREATE TABLE `tb_coupon_order` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL                COMMENT '用户ID',
  `order_id`    BIGINT       DEFAULT NULL             COMMENT '订单ID(预留,关联主订单)',
  `coupon_id`   BIGINT       NOT NULL                COMMENT '券ID',
  `status`      TINYINT      DEFAULT 0                COMMENT '0未支付 1已支付 2已核销 -1超时',
  `pay_type`    TINYINT      DEFAULT 0                COMMENT '支付方式 0未支付 1余额 2支付宝 3微信',
  `pay_time`    DATETIME     DEFAULT NULL             COMMENT '支付时间',
  `use_time`    DATETIME     DEFAULT NULL             COMMENT '核销时间',
  `refund_time` DATETIME     DEFAULT NULL             COMMENT '退款时间',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id`   (`user_id`),
  KEY `idx_coupon_id` (`coupon_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运费券订单';

-- --------------------------------------------------------------------
--  8. 订单表
-- --------------------------------------------------------------------
CREATE TABLE `tb_order` (
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
--  9. 出物/求购帖表
-- --------------------------------------------------------------------
CREATE TABLE `tb_post` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `goods_id`    BIGINT       DEFAULT NULL             COMMENT '关联商品ID',
  `user_id`     BIGINT       NOT NULL                COMMENT '发贴人ID',
  `icon`        VARCHAR(255) DEFAULT NULL             COMMENT '头像快照',
  `name`        VARCHAR(64)  DEFAULT NULL             COMMENT '昵称快照',
  `post_type`   TINYINT      DEFAULT 1                COMMENT '1出物 2求购 3拼单 4分享',
  `title`       VARCHAR(128) NOT NULL                COMMENT '帖子标题',
  `images`      TEXT         DEFAULT NULL             COMMENT '图片列表',
  `content`     TEXT         DEFAULT NULL             COMMENT '帖子正文(可含HTML)',
  `price`       BIGINT       DEFAULT NULL             COMMENT '价格(分)',
  `location`    VARCHAR(64)  DEFAULT NULL             COMMENT '交易楼栋',
  `liked`       INT          DEFAULT 0                COMMENT '喜欢数',
  `comments`    INT          DEFAULT 0                COMMENT '评论数',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id`  (`user_id`),
  KEY `idx_goods_id` (`goods_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出物/求购帖';

-- --------------------------------------------------------------------
--  10. 帖子评论表
-- --------------------------------------------------------------------
CREATE TABLE `tb_post_comments` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL                COMMENT '评论人',
  `post_id`     BIGINT       NOT NULL                COMMENT '帖子ID',
  `content`     VARCHAR(512) NOT NULL                COMMENT '评论内容',
  `liked`       INT          DEFAULT 0                COMMENT '点赞数',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子评论';

-- --------------------------------------------------------------------
--  11. 关注关系表
-- --------------------------------------------------------------------
CREATE TABLE `tb_follow` (
  `user_id`         BIGINT NOT NULL COMMENT '关注人',
  `follow_user_id`  BIGINT NOT NULL COMMENT '被关注人',
  `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_id`, `follow_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系';

-- --------------------------------------------------------------------
--  12. 签到表
-- --------------------------------------------------------------------
CREATE TABLE `tb_sign` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT NOT NULL                COMMENT '用户',
  `year`        INT    NOT NULL                COMMENT '年',
  `month`       INT    NOT NULL                COMMENT '月',
  `date`        DATE   NOT NULL                COMMENT '签到日期',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日签到';


-- ====================================================================
--  种子数据
-- ====================================================================

-- 5 个示例用户(对应商品卖家 / 关注时使用)
INSERT INTO `tb_user` (id, phone, nick_name, icon) VALUES
  (1,   '13686869696', '大四学长',   '/imgs/icons/default-icon.svg'),
  (2,   '13838411438', '大三学姐',   '/imgs/icons/default-icon.svg'),
  (4,   '13456789011', '小萌新',     '/imgs/icons/default-icon.svg'),
  (5,   '13456789001', '可爱多',     '/imgs/icons/default-icon.svg'),
  (100, '13800138000', '校园体验号', '/imgs/icons/default-icon.svg');

-- 7 个商品分类
INSERT INTO `tb_goods_category` (name, icon, sort) VALUES
  ('数码电子', '/types/digital.svg', 1),
  ('图书教材', '/types/book.svg',    2),
  ('生活用品', '/types/life.svg',    3),
  ('服饰鞋包', '/types/clothes.svg', 4),
  ('运动健身', '/types/sport.svg',   5),
  ('美妆个护', '/types/beauty.svg', 6),
  ('其他',    '/types/other.svg',   7);

-- 14 件示例商品
INSERT INTO `tb_goods` (name, type_id, seller_id, status, images, area, address, price, sold, comments, score, trade_time) VALUES
  ('九成新 iPad Air 5 64G',        1, 1, 1, '/imgs/goods/1.svg',  '南校区', '宿舍楼2栋', 2380, 3, 12, 47, '18:00-22:00'),
  ('二手小米手环8 NFC版',          1, 2, 1, '/imgs/goods/2.svg',  '北校区', '宿舍楼5栋',  169, 1,  3, 45, '12:00-13:30'),
  ('考研数学复习全书(2026)',       2, 1, 1, '/imgs/goods/3.svg',  '南校区', '图书馆2层',   25, 0,  8, 49, '全天'),
  ('算法导论 第三版',                2, 2, 1, '/imgs/goods/4.svg',  '北校区', '实验室A楼',   58, 2, 14, 50, '全天'),
  ('宿舍小台灯 护眼USB充电',        3, 1, 1, '/imgs/goods/5.svg',  '南校区', '紫荆3栋',     35, 0,  5, 46, '随时'),
  ('桌面收纳盒 三层可叠',            3, 2, 1, '/imgs/goods/6.svg',  '北校区', '1栋',         28, 1,  4, 44, '随时'),
  ('优衣库外套 M码 只穿过两次',      4, 1, 1, '/imgs/goods/7.svg',  '南校区', '南区1栋',     99, 0,  2, 48, '晚上9点后'),
  ('篮球鞋 43码 高帮',              4, 2, 1, '/imgs/goods/8.svg',  '北校区', '体育馆旁',   180, 0,  6, 47, '晚上8点后'),
  ('迪卡侬瑜伽垫 加厚',              5, 1, 1, '/imgs/goods/9.svg',  '南校区', '操场看台',    45, 0,  3, 46, '清晨/晚间'),
  ('跳绳 钢丝竞速款',                5, 2, 1, '/imgs/goods/10.svg', '北校区', '操场',        18, 1,  2, 43, '全天'),
  ('未拆封洗面奶 氨基酸',            6, 1, 1, '/imgs/goods/11.svg', '南校区', '澡堂门口',    55, 0,  1, 45, '全天'),
  ('防晒霜 SPF50 全新',              6, 2, 1, '/imgs/goods/12.svg', '北校区', '快递柜旁',    68, 0,  4, 48, '随时'),
  ('毕业出闲置 打包价',              7, 1, 1, '/imgs/goods/13.svg', '南校区', '毕业清仓',   200, 0,  9, 50, '全天'),
  ('毕业清仓 电脑支架',              7, 2, 1, '/imgs/goods/14.svg', '北校区', '1栋宿舍',    40, 0,  2, 47, '全天');

-- 4 条示例帖子(出物/求购/拼单)
INSERT INTO `tb_post` (goods_id, user_id, icon, name, post_type, title, images, content, price, location, liked, comments) VALUES
  (4, 2, '/imgs/icons/default-icon.svg', '大三学姐', 2,
   '算法导论第三版 | 考研上岸书籍转让', '/imgs/post-study.svg',
   '准备换方向考公,这套书用不上了。<br>正版,封面几乎全新,每章都有我的笔记重点。<br><br>同院系同学优先,送电子版配套习题答案。',
   12000, '南区3号楼', 1, 2),
  (1, 2, '/imgs/icons/default-icon.svg', '大三学姐', 1,
   '毕业出闲置 | iPad Air 5 便宜出', '/imgs/post-study.svg',
   '下学期要实习准备换Pro,决定把这台Air 5出了。<br>九成新,电池循环78次,配原装壳膜。<br><br>1号楼自提优先,外校可走小红书同城。',
   238000, '紫荆宿舍楼', 1, 0),
  (10, 1, '/imgs/icons/default-icon.svg', '大四学长', 1,
   '学弟学妹看过来 | 跳绳瑜伽垫打包出', '/imgs/post-sport.svg',
   '大四整理宿舍,体育器材打包出。<br>跳绳用了2个月、瑜伽垫用了1学期,都保养得很好。<br><br>一起收 ¥55,比单买便宜不少。',
   13000, '西区 5 栋', 1, 0);

-- --------------------------------------------------------------------
--  MQ 消费幂等表（秒杀异步建单去重，msg_id 唯一键）
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
--  秒杀演示数据：给商品 1 设置 100 件库存并开启秒杀时间窗
--  （未显式设置的商品 stock 默认为 1，即传统"一物一件"商品）
-- --------------------------------------------------------------------
UPDATE `tb_goods`
SET `stock` = 100,
    `seckill_begin` = DATE_SUB(NOW(), INTERVAL 1 DAY),
    `seckill_end`   = DATE_ADD(NOW(), INTERVAL 30 DAY)
WHERE `id` = 1;
