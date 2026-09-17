-- ====================================================================
--  校园小黑市 - 2026-09-13 补充商品 / 秒杀 / 帖子种子数据
--  适用：已建库环境增量执行（幂等：按 name+seller_id 判重后插入）
--  说明：原 campus_bazaar.sql 只有 14 件商品且集中在上架初期，
--        分类页/首页瀑布流数据偏少；此脚本为 7 个分类各补 2 件商品，
--        并新增 3 件「多库存 + 秒杀时间窗」商品用于秒杀演示。
-- ====================================================================

USE `campus_bazaar`;

-- --------------------------------------------------------------------
--  1. 商品补充：23 件（分类 1~7 各至少 2 件）
--     注：tb_goods 的 images / x / y 为 NOT NULL，必须显式赋值
-- --------------------------------------------------------------------
INSERT INTO `tb_goods`
  (`name`, `type_id`, `seller_id`, `status`, `images`, `area`, `address`, `x`, `y`,
   `price`, `sold`, `comments`, `score`, `trade_time`, `stock`)
SELECT * FROM (
  -- ===== 数码电子 (type 1) =====
  SELECT '罗技 MX Master 3S 无线鼠标'      AS n, 1 AS t, 1 AS s, 1 AS st, '/imgs/goods/2.svg'  AS img, '东校区' AS area, '宿舍楼2栋 311' AS addr, 120.149192 AS x, 30.316078 AS y, 189 AS p,  12 AS sold, 24 AS cmt, 46 AS score, '18:00-22:00' AS tt, 1 AS stock
  UNION ALL SELECT '索尼 WH-1000XM4 降噪耳机', 1, 2, 1, '/imgs/goods/1.svg', '北校区', '宿舍楼7栋 205', 120.148603, 30.318618,  899,  5, 18, 48, '12:00-13:30', 1
  UNION ALL SELECT 'Apple AirPods Pro 2 全新未拆', 1, 1, 1, '/imgs/goods/3.svg', '南校区', '紫荆宿舍楼 519', 120.149093, 30.324666, 1299, 36, 58, 50, '全天', 50
  -- ===== 图书教材 (type 2) =====
  UNION ALL SELECT '数据结构与算法分析(Java版)', 2, 2, 1, '/imgs/goods/4.svg', '北校区', '实验楼A 302', 120.151954, 30.324970,  32,  9, 11, 47, '全天', 1
  UNION ALL SELECT '雅思真题 4-18 全套 9成新', 2, 1, 1, '/imgs/goods/5.svg', '南校区', '图书馆2层', 120.146659, 30.312742, 120,  4,  9, 45, '09:00-21:00', 1
  UNION ALL SELECT '考研英语黄皮书 真题全套', 2, 2, 1, '/imgs/goods/6.svg', '东校区', '自习室B 201', 120.151505, 30.333422,  89, 21, 33, 46, '全天', 30
  -- ===== 生活用品 (type 3) =====
  UNION ALL SELECT '小米电水壶 1.5L 恒温款', 3, 1, 1, '/imgs/goods/7.svg', '东校区', '宿舍楼6栋 412', 120.157780, 30.310633,  65,  7, 12, 46, '随时', 1
  UNION ALL SELECT '宿舍折叠书桌 懒人桌', 3, 2, 1, '/imgs/goods/8.svg', '北校区', '宿舍楼7栋 108', 120.148603, 30.318618,  55,  3,  6, 44, '随时', 1
  UNION ALL SELECT '星巴克保温杯 350ml 代购全新', 3, 1, 1, '/imgs/goods/9.svg', '南校区', '快递驿站', 120.149192, 30.316078,  99, 15, 20, 47, '全天', 20
  -- ===== 服饰鞋包 (type 4) =====
  UNION ALL SELECT '北面冲锋衣 M码 三合一', 4, 2, 1, '/imgs/goods/10.svg', '南校区', '南区1栋 220', 120.124691, 30.336819, 320,  2,  8, 49, '晚上9点后', 1
  UNION ALL SELECT '匡威帆布鞋 39码 高帮', 4, 1, 1, '/imgs/goods/11.svg', '东校区', '体育馆旁', 120.150526, 30.325231, 130,  1,  5, 45, '晚上8点后', 1
  -- ===== 运动健身 (type 5) =====
  UNION ALL SELECT '李宁羽毛球拍 双拍装 送球', 5, 2, 1, '/imgs/goods/12.svg', '东校区', '体育馆器材室', 120.150598, 30.325251, 160,  6, 10, 47, '16:00-21:00', 1
  UNION ALL SELECT '阿迪达斯健身手套 全新', 5, 1, 1, '/imgs/goods/13.svg', '北校区', '操场看台', 120.149093, 30.324666,  40,  2,  3, 44, '清晨/晚间', 1
  -- ===== 美妆个护 (type 6) =====
  UNION ALL SELECT '兰蔻小黑瓶精华 50ml 全新', 6, 2, 1, '/imgs/goods/14.svg', '南校区', '澡堂门口', 120.158530, 30.310002, 380,  8, 16, 48, '全天', 1
  UNION ALL SELECT '飞利浦电动牙刷 HX6730', 6, 1, 1, '/imgs/goods/1.svg', '北校区', '快递柜旁', 120.149830, 30.312110, 150,  5,  7, 46, '随时', 1
  -- ===== 其他 (type 7) =====
  UNION ALL SELECT '民谣吉他 41寸 带琴包', 7, 2, 1, '/imgs/goods/2.svg', '南校区', '社团活动室', 120.130453, 30.327655, 280,  3,  9, 49, '全天', 1
  UNION ALL SELECT '宿舍小冰箱 50L 可冷冻', 7, 1, 1, '/imgs/goods/3.svg', '东校区', '宿舍楼9栋 101', 120.128958, 30.337252, 220,  4, 11, 47, '全天', 1
) AS new_goods
WHERE NOT EXISTS (
  SELECT 1 FROM `tb_goods` g WHERE g.`name` = new_goods.n AND g.`seller_id` = new_goods.s
);

-- --------------------------------------------------------------------
--  2. 秒杀商品：为多库存商品开启秒杀时间窗（现在 -1 天 ~ +30 天）
-- --------------------------------------------------------------------
UPDATE `tb_goods`
SET `seckill_begin` = DATE_SUB(NOW(), INTERVAL 1 DAY),
    `seckill_end`   = DATE_ADD(NOW(), INTERVAL 30 DAY)
WHERE `stock` > 1
  AND `status` = 1
  AND `seckill_begin` IS NULL;

-- --------------------------------------------------------------------
--  3. 帖子补充：3 条（校园动态流不再单调）
-- --------------------------------------------------------------------
-- 注：tb_post 实表无 icon / name 列（发帖人昵称头像由查询时 join tb_user 回填）
INSERT INTO `tb_post`
  (`goods_id`, `user_id`, `post_type`, `title`, `images`, `content`, `price`, `location`, `liked`, `comments`)
SELECT * FROM (
  SELECT 18 AS gid, 2 AS uid, 1 AS pt,
         '搬宿舍清仓 | 数码配件一箩筐' AS title, '/imgs/post-study.svg' AS img,
         '毕业季搬家,鼠标键盘耳机全部打包出。<br>都是自己用过的,功能完好,可当面验货。<br><br>紫荆宿舍楼下自提,打包更划算。' AS content,
         52000 AS price, '紫荆宿舍楼' AS loc, 1 AS liked, 0 AS cmt
  UNION ALL SELECT 21, 1, 2,
         '求购 | 考研数学复习全书 有无笔记都行', '/imgs/post-study.svg',
         '准备二战,求一本数学复习全书。<br>有笔记最好,没有也行,价格好商量。<br><br>图书馆当面交易,我请一杯奶茶。',
         3000, '图书馆2层', 0, 1
  UNION ALL SELECT 23, 5, 4,
         '分享 | 宿舍改造小成果,总花费不到 300', '/imgs/post-dorm.svg',
         '利用周末把宿舍桌面彻底改造了一遍:<br>收纳盒 + 折叠桌 + 台灯 + 挂钩,一共 268 元。<br><br>需要的同学可以留言,我把链接整理给你们。',
         26800, '西区5栋', 3, 0
) AS new_posts
WHERE NOT EXISTS (
  SELECT 1 FROM `tb_post` p WHERE p.`title` = new_posts.title
);

-- --------------------------------------------------------------------
--  4. 结果核对
-- --------------------------------------------------------------------
SELECT t.`id` AS type_id, t.`name` AS category, COUNT(g.`id`) AS goods_count
FROM `tb_goods_category` t
LEFT JOIN `tb_goods` g ON g.`type_id` = t.`id` AND g.`status` = 1
GROUP BY t.`id`, t.`name`
ORDER BY t.`id`;

SELECT COUNT(*) AS total_goods,
       SUM(CASE WHEN `stock` > 1 THEN 1 ELSE 0 END) AS multi_stock_goods,
       SUM(CASE WHEN `seckill_end` IS NOT NULL THEN 1 ELSE 0 END) AS seckill_goods
FROM `tb_goods` WHERE `status` = 1;
