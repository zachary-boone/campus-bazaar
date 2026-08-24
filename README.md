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

双击 `E:\project\campus-bazaar\start.bat`，依次启动 **RabbitMQ → Redis → nginx → 后端**（等待 RabbitMQ 5672 就绪后启动后端）。
停止时双击 `stop.bat`（后端 → nginx → Redis → RabbitMQ 优雅停止）。

### 方式二：手动启动

```bash
# 1. RabbitMQ（E:\tools\rabbitmq_server-3.13.7，依赖 E:\tools\erl-26.2.5）
set ERLANG_HOME=E:\tools\erl-26.2.5
set RABBITMQ_BASE=E:\tools\rabbitmq_server-3.13.7\data
set PATH=E:\tools\erl-26.2.5\bin;%PATH%
cd E:\tools\rabbitmq_server-3.13.7\sbin && rabbitmq-server.bat

# 2. Redis（E:\redis-zb\redis-win-x64）
redis-server.exe redis.windows.conf --stop-writes-on-bgsave-error no

# 3. nginx（E:\project\nginx-1.18.0\nginx-1.18.0）
nginx.exe -p E:\project\nginx-1.18.0\nginx-1.18.0\ -c conf\nginx.conf

# 4. 后端（E:\project\campus-bazaar\backend）
java -jar target\campus-bazaar.jar --server.port=8081
```

### 访问地址

| 地址 | 说明 |
|---|---|
| http://127.0.0.1:8080 | 前端页面（校园小黑市） |
| http://127.0.0.1:8081 | 后端 API |
| http://127.0.0.1:15672 | RabbitMQ 管理台（guest/guest） |

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
- **关注流 Feed**：发帖实时推送到粉丝收件箱（ZSet 推模式），`/post/of/follow` 滚动分页（游标 + 同分 offset），大 V 可扩展拉模式
- **GEO 附近商品**：发布/更新商品写入 Redis GEO 坐标，`/goods/of/nearby` 半径检索按距离排序，支撑"宿舍级附近交易"
- **缓存一致性**：逻辑过期 + Redisson 锁异步重建（读多写少热点），更新链路延迟双删保证最终一致；防穿透（空值）/防击穿（逻辑过期）/防雪崩（随机 TTL 思路）
- **BCrypt 密码加密**：替换裸 MD5（自带随机盐抗彩虹表），支持首次设置密码
- **库存对账任务**：定时以 DB 为基准校准 Redis 预扣库存（缺失初始化/负值修正/超扣回收），兜底最终一致性
- **MQ 消费幂等**：tb_mq_idempotent 幂等表（msg_id 唯一键），防止重复投递导致重复下单
- **接口防重复提交**：@Idempotent 注解 + Redis SETNX 防抖，下单/支付接口防连点
- **可观测**：Actuator 健康/指标端点 + Micrometer 自定义指标（秒杀请求/成功、限流拒绝、防重拒绝）+ TraceId 日志链路
- **MQ 可靠性**：发布确认（ConfirmCallback）+ 路由失败回调（mandatory）+ 消费者手动 ack / 失败重新入队
- Set 关注关系（含共同关注）、BitMap 签到（连续天数统计）
- 登录注册全链路：验证码下发、自动注册、token 10 小时有效、interface 白名单

## 压测数据（JMeter 5.6.3，本机单机：Windows + Redis + MySQL 同机）

| 场景 | 并发 | 结果 |
|---|---|---|
| 秒杀接口（Redis 预扣 + 异步下单） | 300 | QPS 285/s，Avg 633ms，**0 超卖**（Redis=DB=499），**一人一单严格生效**（300 并发仅 1 单） |
| 商品详情（缓存读） | 300 | 0 错误，Avg 2766ms（含 DB 卖家信息回填） |
| 秒杀限流保护 | 1000 | 568 个请求被令牌桶 429 拦截，未打穿业务层 |

> 说明：Redisson / RabbitMQ 需本机安装对应服务（Redis 6379 已有；RabbitMQ 已装于 E:\tools，5672 需启动后秒杀异步下单与支付轮询才可完整运行）。
