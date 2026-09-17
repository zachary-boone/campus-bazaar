# 校园小黑市交易平台

> 面向全校学生的二手物品交易平台：宿舍级定位发布（Redis GEO 附近检索）、关注学长学姐动态流
> （ZSet 推模式 Feed）、秒杀优惠券（Redisson 锁 + Lua 预扣 + RabbitMQ 异步下单）、每日签到（BitMap，连续签到满 7 天领运费券）。

## 技术栈

- **后端框架**：Spring Boot 2.3.12 / MyBatis-Plus 3.4.3 / JDK 8
- **存储**：MySQL 8（索引优化、事务隔离、乐观锁）、Redis（缓存、ZSet 点赞/关注流、Set 关注关系、BitMap 签到、GEO 附近检索、Lua 原子脚本）
- **中间件**：RabbitMQ 3.13（异步下单削峰、延迟队列、死信队列、发布确认、手动 ack）、Redisson 3.15（分布式锁）
- **Web 基础设施**：nginx 1.18（前端静态资源托管 + `/api` 反向代理）
- **前端**：Vue 2 + Element UI（静态页）
- **工程化**：Actuator + Micrometer 指标、TraceId 日志链路、JMeter 压测

## 项目结构

```
E:\project\campus-bazaar\
├── start.bat                     # 一键启动脚本（RabbitMQ/Redis/nginx/后端）
├── stop.bat                      # 一键停止脚本
├── README.md                     # 项目文档（GitHub 主页渲染）
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
│       └── target\campus-bazaar.jar
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

### 前端改动后必须部署 ⚠️

nginx 的 `root` 是相对自身安装目录的 `html/campus-bazaar`，所以**它托管的是部署副本，
不是你编辑的源码目录**。只改 `campus-bazaar\frontend` 下的文件，浏览器里不会生效
（表现为新页面 404，或链接仍走老逻辑，例如「每日签到」一直提示"功能即将上线"）。

| 角色 | 路径 |
|---|---|
| 源码（在 git 里，编辑这里） | `E:\project\campus-bazaar\frontend` |
| nginx 实际托管 | `E:\project\nginx-1.18.0\nginx-1.18.0\html\campus-bazaar` |

改完前端后双击 `scripts\frontend-deploy.bat` 同步（单条 xcopy，不会删除部署目录里已有的额外文件），
再在浏览器按 **Ctrl+F5** 硬刷新。排查时可直接 `curl http://127.0.0.1:8080/xxx.html` 确认是否 404。

## 数据库

- 库名：`campus_bazaar`（MySQL 8，用户名 root）
- 建库脚本：`src\main\resources\db\campus_bazaar.sql`
- 核心表：
  - `tb_goods` 商品（含 seller_id 卖家、status 状态 1在售/2已售/3下架/4交易中、price 售价、area 校区、address 楼栋）
  - `tb_goods_category` 商品分类（数码电子/图书教材/生活用品/服饰鞋包/运动健身/美妆个护/其他）
  - `tb_order` 订单（下单即原子预占商品防超卖，支付/取消/超时关单联动商品状态）
  - `tb_post` 出物/求购帖（post_type 1出物 2求购、price、location）
  - `tb_coupon` / `tb_seckill_coupon` / `tb_coupon_order` 运费券与秒杀券、券订单（0未支付/1已支付/2已核销/-1超时）
  - `tb_user` / `tb_user_info` / `tb_follow` / `tb_sign` 用户（含 role 角色）、关注、签到

## 主要接口

| 模块 | 接口 |
|---|---|
| 用户 | `/user/code` `/user/login` `/user/logout` `/user/me` `/user/info/{id}` `/user/card/{id}`（用户卡片：昵称/头像/签名 + 关注数/粉丝数 + 在售/已售数 + 是否已关注）`/user/sellers`（学长学姐专区：有在售商品的卖家列表） |
| 商品 | `/goods/{id}` `/goods/of/type?typeId=&sortBy=`（排序：new/priceAsc/priceDesc/comments/score）`/goods/of/name` `/goods/of/seller/{id}`（某卖家在售）`/goods/of/nearby`（GEO 半径）`/goods/map`（地图：中心点+半径内商品+校区统计）`/goods/geo/rebuild`（重建 GEO 索引）`/goods/category/list` |
| 帖子 | `/post/hot` `/post/of/me` `/post/of/user/{id}`（某用户发布）`/post/of/follow` `/post/likes/{id}` |
| 运费券 | `/coupon/list/{goodsId}` `/coupon/seckill` `/coupon-order/seckill/{id}` |
| 签到 | `/sign`（签到）`/sign/count`（连续天数）`/sign/records`（签到日历）`/sign/coupons`（我的签到运费券）`/sign/coupon`（领取满 7 天奖励） |
| 关注 | `/follow/{id}/{isFollow}`（关注/取关，幂等，返回最新计数）`/follow/counts/{id}`（关注数/粉丝数）`/follow/following/{id}`（TA 关注的人）`/follow/followers/{id}`（TA 的粉丝）`/follow/or/not/{id}` `/follow/common/{id}` |
| 消息 | `/messages/of/me?current=&type=`（我的消息，type：0全部 1系统 2交易 3互动）`/messages/unread/count`（未读数）`/messages/read/{id}`（单条已读，幂等+归属校验）`/messages/read/all`（全部已读，均需登录） |

## 技术亮点

- **双拦截器认证**：RefreshTokenInterceptor（全路径解析 token + 无感续期 + ThreadLocal 透传）+ LoginInterceptor（白名单外校验登录态），token 有效期 10 小时，活跃访问自动续期
- **Redisson 分布式锁**：替换手写 SETNX，用于商品缓存防击穿与秒杀场景（锁用户/券维度），解决事务锁失效
- **商品秒杀高并发链路**：Redis Lua 预扣商品库存（原子防超卖 + 一人一单）→ RabbitMQ 异步下单削峰 → 消费者 Redisson 锁 + 幂等表去重 + DB CAS 扣库存 + 事务建单，失败自动补偿库存
- **双层令牌桶限流**：Redis Lua 实现（@RateLimit 注解），单用户维度防连点器（秒杀 20/s、验证码 2/s）+ 全局维度兜底防全体流量洪峰（秒杀 200/s、验证码 50/s），先全局后单用户双层拦截
- **ZSet 点赞排行**：点赞/取消/是否点赞/点赞用户 TopN（ZSet + 时间戳排序）
- **验证码两级限流**：ZSet 滑动窗口，一级 60s 内 3 次 + 二级 1h 内 10 次
- **支付状态轮询**：RabbitMQ 延迟队列（TTL 5s/10s/20s/40s/80s 五级）+ 指数退避，未支付逐级重试、超时关单
- **关注流 Feed**：发帖实时推送到粉丝收件箱（ZSet 推模式），`/post/of/follow` 滚动分页（游标 + 同分 offset），大 V 可扩展拉模式
- **GEO 附近商品 + 校园地图**：发布/更新商品写入 Redis GEO 坐标（校区索引 + all 索引**双写**），`/goods/of/nearby` 半径检索按距离排序；`/goods/map` 一次返回中心点 + 半径内商品（含距离）+ 各校区在售数；GEO 索引带**启动自愈**（SQL 导入的种子数据不进索引 → 定时任务比对基数后按 DB 重建），支撑"宿舍级附近交易"与地图找货
- **缓存一致性**：逻辑过期 + Redisson 锁异步重建（读多写少热点），更新链路延迟双删保证最终一致；防穿透（空值）/防击穿（逻辑过期）/防雪崩（随机 TTL 思路）
- **BCrypt 密码加密**：替换裸 MD5（自带随机盐抗彩虹表），支持首次设置密码
- **库存对账任务**：定时以 DB 为基准校准 Redis 预扣库存（缺失初始化/负值修正/超扣回收），兜底最终一致性
- **MQ 消费幂等**：tb_mq_idempotent 幂等表（msg_id 唯一键），防止重复投递导致重复下单
- **接口防重复提交**：@Idempotent 注解 + Redis SETNX 防抖，下单/支付接口防连点
- **可观测**：Actuator 健康/指标端点 + Micrometer 自定义指标（秒杀请求/成功、限流拒绝、防重拒绝）+ TraceId 日志链路
- **MQ 可靠性**：发布确认（ConfirmCallback）+ 路由失败回调（mandatory）+ 消费者手动 ack / 失败重新入队
- **关注关系**：`tb_follow` 唯一键 + Service 幂等关注，关注数/粉丝数实时 COUNT（不维护冗余列）；关注/粉丝列表批量回填用户卡片（一次 IN 查询算出"我是否已关注"，避免 N+1）；Redis Set 存关注集合用于共同关注求交集，缓存缺失时从 DB **自愈重建**
- **BitMap 签到**：`SETBIT` 打点 / 逐天 `GETBIT` 读回整月（读取量与签到天数无关，且不受不同 Redis 实现的字节序差异影响）/ 回溯连续天数（支持跨月），连续签到满 7 天发签到运费券
- 登录注册全链路：验证码下发、自动注册、token 10 小时有效、interface 白名单

## 压测数据（JMeter 5.6.3，本机单机：Windows + Redis + MySQL 同机）

| 场景 | 并发 | 结果 |
|---|---|---|
| 秒杀接口（双层限流 + Redis 预扣 + 异步下单） | 300 | QPS 555/s，Avg 180ms，**0 超卖**（Redis=DB=499），**一人一单**（300 并发仅 1 单） |
| 商品详情（缓存读） | 300 | 0 错误，Avg 2766ms（含 DB 卖家信息回填） |
| 秒杀 1000 并发（仅单用户维度限流） | 1000 | 568 个请求被令牌桶 429 拦截 |
| 秒杀 1000 并发（单用户 + 全局双层限流） | 1000 | 177 个连接被拒，753 个请求被 429 拦截，**0 超卖** |

> 说明：Redisson / RabbitMQ 需本机安装对应服务（Redis 6379 已有；RabbitMQ 已装于 E:\tools，5672 需启动后秒杀异步下单与支付轮询才可完整运行）。
