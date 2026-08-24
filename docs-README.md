# 校园小黑市交易平台

> 面向全校学生的二手物品交易平台，支持宿舍级定位发布、拼单包邮、每日签到领运费券，
> 可关注学长学姐、查看实时在售与求购榜单。

## 技术栈

- **后端**：Spring Boot 2.3.12 / MyBatis-Plus 3.4.3 / MySQL 8 / Redis
- **前端**：Vue 2 + Element UI（静态页，由 nginx 托管）
- **中间件**：nginx（静态资源 + `/api` 反向代理）

## 项目结构

```
E:\project\campus-bazaar\
├── start.bat                     # 一键启动脚本
├── stop.bat                      # 一键停止脚本
├── docs-README.md                # 本文档
├── docs-改造方案.md               # 整体改造方案（保留备查）
├── backend\                      # 后端工程（Spring Boot）
│   ├── pom.xml                   # groupId: com.campus.bazaar
│   ├── .idea\
│   └── src\main\
│       ├── java\com\campus\bazaar\
│       │   ├── CampusBazaarApplication.java
│       │   ├── controller\       # Goods / Post / Coupon / User / Follow ...
│       │   ├── service\          # 业务层（缓存、登录、点赞）
│       │   ├── mapper\           # MyBatis-Plus Mapper
│       │   ├── entity\           # Goods / Post / Coupon / User ...
│       │   ├── dto\  utils\  config\
│       │   └── resources\db\campus_bazaar.sql   # 建库脚本
│       └── target\campus-bazaar-0.0.1-SNAPSHOT.jar
└── nginx-1.18.0\                # 独立目录，托管前端
    └── nginx-1.18.0\
        ├── conf\nginx.conf       # 监听 8080，/api 代理到 8081
        └── html\campus-bazaar\   # 前端页面（10 个 HTML + SVG 资源）
```

> 此外 `E:\redis-zb\redis-win-x64\` 提供 Redis 服务（Windows 版）

## 快速启动

### 方式一：一键脚本（推荐）

双击 `E:\project\campus-bazaar\start.bat`，依次启动 Redis → nginx → 后端。
停止时双击 `stop.bat`。

### 方式二：手动启动

```bash
# 1. Redis（E:\redis-zb\redis-win-x64）
redis-server.exe redis.windows.conf

# 2. nginx（E:\project\nginx-1.18.0\nginx-1.18.0）
nginx.exe -p E:\project\nginx-1.18.0\nginx-1.18.0\ -c conf\nginx.conf

# 3. 后端（E:\project\campus-bazaar\backend）
java -jar target\campus-bazaar-0.0.1-SNAPSHOT.jar --server.port=8081
```

### 访问地址

| 地址 | 说明 |
|---|---|
| http://127.0.0.1:8080 | 前端页面（校园小黑市） |
| http://127.0.0.1:8081 | 后端 API |

> 注意：nginx 所在路径不能包含中文，否则无法启动。

## 数据库

- 库名：`campus_bazaar`（MySQL 8，用户名 root）
- 建库脚本：`src\main\resources\db\campus_bazaar.sql`
- 核心表：
  - `tb_goods` 商品（含 seller_id 卖家、status 状态、price 售价、area 校区、address 楼栋）
  - `tb_goods_category` 商品分类（数码电子/图书教材/生活用品/服饰鞋包/运动健身/美妆个护/其他）
  - `tb_post` 出物/求购帖（post_type 1出物 2求购、price、location）
  - `tb_coupon` / `tb_seckill_coupon` / `tb_coupon_order` 运费券与秒杀券、券订单
  - `tb_user` / `tb_user_info` / `tb_follow` / `tb_sign` 用户、关注、签到

## 主要接口

| 模块 | 接口 |
|---|---|
| 用户 | `/user/code` `/user/login` `/user/logout` `/user/me` `/user/info/{id}` |
| 商品 | `/goods/{id}` `/goods/of/type` `/goods/of/name` `/goods/category/list` |
| 帖子 | `/post/hot` `/post/of/me` `/post/of/follow` `/post/likes/{id}` |
| 运费券 | `/coupon/list/{goodsId}` `/coupon/seckill` `/coupon-order/seckill/{id}` |
| 关注 | `/follow/or/not/{id}` `/follow/common/{id}` |

## 改造记录

- 包名 `com.campus.bazaar`，工程名 `campus-bazaar`（项目根目录 `E:\project\campus-bazaar\`）
- 数据库 `campus_bazaar`，表名 / 字段全面校园化（11 张表）
- 接口路径：`/shop`→`/goods`、`/shop-type`→`/goods/category`、`/blog`→`/post`、`/voucher`→`/coupon`、`/voucher-order`→`/coupon-order`
- Redis Key：`cache:shop:`→`cache:goods:` 等
- 前端：目录 `html/campus-bazaar`，主题色改为校园绿
- MySQL 驱动 8.0.33、Lombok 1.18.30（适配 MySQL 8 / JDK 17）

## 技术亮点

- **双拦截器认证**：RefreshTokenInterceptor（全路径解析 token + 无感续期 + ThreadLocal 透传）+ LoginInterceptor（白名单外校验登录态），token 有效期 10 小时，活跃访问自动续期
- **Redisson 分布式锁**：替换手写 SETNX，用于商品缓存防击穿与秒杀场景（锁用户/券维度），解决事务锁失效
- **秒杀高并发链路**：Redis Lua 预扣库存（原子防超卖 + 一人一单）→ RabbitMQ 异步下单削峰 → 消费者 Redisson 锁 + 事务落库，失败自动补偿库存
- **令牌桶限流**：Redis Lua 分布式令牌桶（@RateLimit 注解），秒杀/验证码/点赞接口级限流保护
- **ZSet 点赞排行**：点赞/取消/是否点赞/点赞用户 TopN（ZSet + 时间戳排序）
- **验证码两级限流**：ZSet 滑动窗口，一级 60s 内 3 次 + 二级 1h 内 10 次
- **支付状态轮询**：RabbitMQ 延迟队列（TTL 5s/10s/20s/40s/80s 五级）+ 指数退避，未支付逐级重试、超时关单
- Set 关注关系（含共同关注）、BitMap 签到（连续天数统计）
- 登录注册全链路：验证码下发、自动注册、token 10 小时有效、interface 白名单

> 说明：Redisson / RabbitMQ 需本机安装对应服务（Redis 6379 已有；RabbitMQ 5672 需启动后秒杀异步下单与支付轮询才可完整运行）。
