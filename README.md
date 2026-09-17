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

## 改造记录

- **2026-09-17 修复地图"点击商品后变成另一个"**：根因是**标签和它自己的圆点是分离的，而且标签不接收点击**。标签画在圆点上方 13~26 单位处，密集处两个圆点可能只隔二十几单位 → **A 的名称标签恰好压在 B 的圆点上**；而标签是 `pointer-events:none`，点击穿透到下面的 B 圆点 → 选中的是 B。修法：
  1. **标签本身可点击**，`@click.stop="select(l.goods)"` 直接绑到该标签对应的商品（`labels` computed 里把原始 goods 一并带出），不再靠"谁在下面"来猜
  2. 标签与圆点之间加**一条引线**，明确"这个名字属于哪个点"
  3. 空白处点击改为**取离点击点最近的商品**（16 单位内自己算距离，而不是依赖 SVG 绘制顺序）——因为密集重叠时最上层往往是"已选中"的那个，只靠命中顺序会出现"点了没反应/选中邻居"；同时用 `dragged` 标记避免拖地图后误选
  4. 圆点热区保持与可见圆等大（不做隐形放大热区，否则大热区会盖住邻居的圆点，又是同一类 bug）
- **2026-09-17 修复三个前端 bug：商品排序 / 底部「地图」点不动 / 地图标签重叠**
  1. **排序不生效**：`/goods/of/type` 完全忽略前端传的 `sortBy`，且 UI 里没有「最新」。后端补上排序白名单（`new` 最新 / `priceAsc` `priceDesc` 价格 / `comments` 人气 / `score` 评分 / 空=综合；**列名只在 switch 里映射，不接受外部拼串**，非法值回落综合），前端加「最新」并让「价格」支持升↔降切换（带 ↑↓ 图标）
  2. **底部导航「地图」点不动**：发布按钮 `<img class="add-btn" src="/imgs/add.png">` 的 **`.add-btn` 在 CSS 里根本没有定义**（真正定义好的是 `.foot-add`），27KB 的大图无尺寸限制，从中间那个 20% 宽的格子**溢出盖住了左右两侧的「地图」「消息」**，点击被图片吃掉 → 改成用 CSS 画的 `.foot-add`（不再依赖图片，顺带解决 `add.png` 只存在于部署目录、源码里缺失会导致同步后 404 的问题），并补 `foot-box img` 的尺寸兜底
  3. **地图商品名重叠看不清**：原来给每个标记都画名称文字。改为**标签防重叠**——按 y 顺序贪心摆放，与已放标签矩形相交就跳过；选中项优先占位且永远显示（红底白字），其余白底胶囊；名称截断到 5 字；缩放/拖动时自动重算，放大后能显示更多。默认半径由 3km 收成 **1km**（校园商品实际集中在 ±500m 内，3km 视角下只能放下 2 个标签，1km 下 15 件能清晰显示 9 个）
- **2026-09-17 修复 `/goods/of/type` 报 `Required Integer parameter 'typeId' is not present`**：日志里一直在报这个错，实际后果是**首页/分类页/发帖页/商品详情页的"相似商品"列表全空**（异常被 `GlobalExceptionHandler` 兜成业务失败，HTTP 仍是 200，前端 `.then` 从不执行，所以只是"列表空"而不报错）。两个问题叠加：
  1. **前端 `paramsSerializer` 把 `0` 当空值丢了**——原来的实现是 `if (params[k]) p += "&" + k + "=" + params[k]`，而 `0`/`false` 都是 falsy，于是 `typeId: 0`（表示"全部分类"）根本没发出去。改为只丢弃 `undefined / null / ''`，并补上 `encodeURIComponent`（自定义 paramsSerializer 后 axios 不再自动编码，`area=东校区` 这类中文要靠它）
  2. **后端 `typeId` 声明成必填，但实现是"typeId > 0 才加分类条件"**（意图本就是可选）——改成 `required = false`
  另外给 `index.html` / `shop-detail.html` 的调用补上 `.catch`，避免以后接口挂了又变成"静默空列表"
- **2026-09-17 校园地图（Redis GEO）+ 定位**：底部导航「地图」原来 `toPage()` 里没处理 2/3，点了没反应；首页「📍 楼层定位」的 128/86/52 是写死的。本次用 **Redis GEO** 做出真正的地图：
  - 修掉两个 GEO 的坑：① `addGoodsGeo` 只写校区索引不写 `goods:geo:all`，而按 "all" 查的请求（不带 area）永远为空——改为**双写**；② **GEO 索引一直是空的**（索引只在"经接口发布/编辑商品"时写入，SQL 导入的种子数据不会进索引）——新增 `GeoIndexTask` 启动 5s 后自愈、之后每 10 分钟比对一次（只在"索引基数 ≠ DB 在售数"时才全量重建，平时就一次 ZCARD）
  - 新增 `GET /goods/map`：一次返回**中心点 + 半径内商品（含 distance）+ 各校区在售数**；不传坐标时用**在售商品坐标中位数**当校园中心（用中位数而非平均值——种子里有 1 件坐标在 600km 外的脏数据，平均值会被带偏）
  - `/goods/of/nearby` 的检索逻辑抽成 `geoRadiusGoods()` 与地图共用；新增 `POST /goods/geo/rebuild` 手动重建索引
  - 新增 `frontend/map.html`：**自包含 SVG 校园地图**（不依赖任何在线瓦片/API key，离线可用）——商品标记按真实经纬度等距圆柱投影（经度按 cos(纬度) 压缩）落点、距离圈、校园示意图缩略建筑；支持**拖动平移 / 滚轮与按钮缩放（0.4x~12x）/ 一键回中心**；点标记弹出商品卡（名称/价格/卖家/楼栋/距离）→「去购买」跳商品详情页；半径切换 500m/1km/3km/10km、校区筛选、`navigator.geolocation` 定位（不在校园范围内会提示并可切回校园视角）
  - `footer.js` 修好地图/消息跳转（未实现的消息改为明确提示，不再"点了没反应"）；首页「楼层定位」改为真实校区与在售数（来自 GEO 索引）并新增「查看地图 ›」入口
- **2026-09-17 修复"弹窗不消失、一直悬浮"**：症状是点按钮弹出的提示等一会也不消失。根因有两类：
  1. **用错了组件**——把"告知结果"的场景写成了 `$alert` / 原生 `alert()`，这类模态框本来就是**等用户点击才关闭**的，用户等它自动消失自然等不到；
  2. **Element MessageBox 是"单例栈 + 共享 `.v-modal`"**——秒杀轮询期间可能再弹一个支付确认框（`submitting` 提前置回 false 导致），叠框后会留下关不掉的弹窗和挡住整页的遮罩。
  修法：
  - `common.js` 新增 `util.closeAllPopups()`（收口 `$msgbox.close` / `$message.closeAll` / `$notify.closeAll` + 兜底摘掉残留 `.v-modal`）、`util.toast()`（自动消失的轻提示，带 grouping 不堆叠）、`util.notify()`（自动消失 + 可点击跳转）
  - **全局兜底补丁**：在 `common.js` 里给 `Vue.prototype.$message` / `$notify` 包一层——每次提示先清掉旧的（不再堆叠）、补默认 `duration`、开启 `grouping` 与关闭按钮，并把 **`duration: 0`（Element 约定为"常驻不关"）强制兜成 3 秒/4.5 秒**。老代码不用改，从此不存在"永不消失"的轻提示
  - 纯提示场景全部由 `$alert` 改为自动消失的通知：登录页验证码/协议、商品详情页的"支付完成""抢购已受理"（保留"点击查看订单"入口，用 Notification 的 `onClick`）
  - `askPay` 增加 `payDialogOpen` 互斥，杜绝轮询期间叠框；`buyNow` / 下单确认 / 取消订单 / 确认收货 / 退出登录的 `$confirm` 统一加 `closeOnClickModal` + `closeOnPressEscape`（点遮罩或按 Esc 也能关），并在弹框前先 `closeAllPopups()`
  - 顺手修掉 axios 响应拦截器的真 bug：超时/断网时 `error.response` 为 undefined，原来直接取 `.status` 会抛 TypeError（把"请求超时"报成"服务器异常"），并区分出 `ECONNABORTED` 的提示
- **2026-09-17 学长学姐专区 + 他人主页真实化**：首页「学长学姐」此前只弹"即将上线"，他人主页的商品/帖子/统计也都是拿全站数据或写死值凑的（`/goods/of/type` 全站商品、`/post/hot` 全站热帖、写死的 38/23）。本次做出真正的卖家闭环：
  - 新增 `/user/sellers`：**有在售商品的卖家列表**（一条 `GROUP BY seller_id` 聚合出各卖家在售/已售数量，再批量回填用户资料、签名、粉丝数，全程 3 次查询无 N+1），按在售数倒序，页码分页
  - 新增 `/goods/of/seller/{id}`：某卖家在售商品（只含 `status=1`，已售/下架不展示，与商品状态语义 1在售/2已售/3下架/4交易中 对齐）
  - 新增 `/post/of/user/{id}`：某人发布的帖子（此前主页用的是 `/post/hot`，看到的其实是全站热帖）
  - `/user/card/{id}` 补充 `goodsCount`/`soldCount`（用状态计数，不用 `SUM(sold)`——种子里 `sold` 有明显异常值）
  - 新增 `frontend/senior-list.html`（学长学姐专区：卖家卡片 + 在售/已售/粉丝、在售最多标记、点进主页）
  - `other-info.html` 改为真实数据：`/user/card/{id}` 拿资料与计数、`/goods/of/seller/{id}` 拿 TA 的在售商品（图下沿显示商品名，点击进 `shop-detail.html` 可直接下单）、`/post/of/user/{id}` 拿 TA 的帖子；空态补"TA 目前没有在售商品"
  - `index.html` 「学长学姐」跳 `/senior-list.html`
- **2026-09-17 打通关注闭环（个人中心计数 + 关注/粉丝列表）**：此前前端**从未调用过关注接口**——`other-info.html` 的「关注 TA」只翻转本地变量、统计是写死的 38/23，`info.html` 的粉丝/关注也一直是 0，首页「学长学姐」只弹"即将上线"。本次补齐：
  - 后端新增 `/follow/counts/{id}`、`/follow/following/{id}`、`/follow/followers/{id}`（列表返回 `UserCardDTO`，含昵称/头像/签名/我是否已关注）与 `/user/card/{id}`；`follow()` 改为**幂等**（重复关注不报错、不产生重复行），并校验"不能关注自己""目标用户必须存在"，直接回传最新计数省一次请求
  - 计数以 `tb_follow` **实时 COUNT** 为准，不再维护冗余列（`tb_user_info.fans/followee` 是历史遗留、一直没人维护，故不作为数据源，避免双写漂移）
  - 新增 `db/upgrade-0917-follow.sql`：给 `tb_follow` 补 `(user_id, follow_user_id)` 唯一键（此前只有主键，"连点关注"会插入多行把计数算多）+ 清理指向已删除用户的孤儿关注
  - 「共同关注」的 Redis Set 增加**自愈**：Set 为空时从 DB 重建，解决 Set 与 DB 不一致导致共同关注恒为空的问题
  - 前端新增 `frontend/follow-list.html`（关注/粉丝双 Tab、可关注/取关/互相关注、点头像进他人主页）；`info.html` 统计接真实计数且可点击进入列表；`other-info.html` 改为读 `/user/card/{id}` 真实资料并真正调用关注接口（去掉写死的 mock 昵称与假计数）；首页「学长学姐」跳转关注列表
- **2026-09-17 每日签到（BitMap）+ 签到运费券**：签到记录以 Redis Bitmap 为主存储（`sign:{userId}:{yyyyMM}`，bit offset = 当月第几天 - 1，整月仅约 4 字节），`tb_sign` 唯一键 `uk_user_date` 落库兜底；连续天数统计支持**跨月回溯**（本月一直连续到 1 号时继续读上月，最多读 2 个月）；**连续签到每满 7 天**获得 1 张运费券领取资格（`sign:week:earned:` 累计 / `sign:week:claimed:` 已领，领取用 Redis INCR 原子占位防超领，落库失败自动归还额度），领取后写入 `tb_coupon_order`（status 1 已领取）；新增签到页 `frontend/sign.html`（签到日历、7 天进度条、运费券领取与列表）、`info.html` 与首页 hero 入口角标；升级脚本 `db/upgrade-0917-sign-coupon.sql`（除建表/券模板外，还补齐老 hmdp 版 `tb_sign` 缺失的 `create_time` 列与 `uk_user_date` 唯一键，否则签到 INSERT 会 500）
- **2026-09-13 前端购买/秒杀修复**：修复 `tb_goods` 缺 `stock`/`seckill_begin`/`seckill_end` 列导致 `/goods/of/type` 抛 `Unknown column`（分类页与首页商品列表为空，升级脚本 `db/upgrade-0913-seckill-goods.sql`）；商品详情页新增「立即购买」（`POST /order` 同步下单，状态联动置灰）与「立即秒杀」按钮，秒杀成功后轮询订单并引导支付；「我的 - 我购买的」接入 `/order/my/` 真实订单（去支付/取消/确认收货）；补 17 件商品 + 3 件秒杀商品 + 3 条帖子（`db/seed-0913-more-goods.sql`）；MQ 消费者默认开启（`auto-startup: true`，并修正环境变量名 `SPRING_RABBITMQ_LISTENER_AUTOSTARTUP`）
- **2026-09-13 秒杀迁移到商品**：秒杀对象由"优惠券"改为"商品"。`tb_goods` 新增 `stock`（库存，默认 1）、`seckill_begin`/`seckill_end`（秒杀时间窗，可空）；新增 `POST /goods/seckill/{id}` 接口、`goods.seckill.exchange` 交换机与 `goods.seckill.order.queue` 队列、`GoodsSeckillConsumer` 消费者，异步建 `tb_order` 订单；Redis key 为 `seckill:goods:stock:{id}` / `seckill:goods:user:{id}`；库存对账任务同步覆盖商品。原券秒杀链路**保留未删**，前端已不再调用。升级脚本：`db/upgrade-0913-seckill-goods.sql`
- 包名 `com.campus.bazaar`，工程名 `campus-bazaar`（项目根目录 `E:\project\campus-bazaar\`）
- 数据库 `campus_bazaar`，表名 / 字段全面校园化（13 张表，含 `tb_order` 订单表、`tb_mq_idempotent` 幂等表）
- 接口路径：`/shop`→`/goods`、`/shop-type`→`/goods/category`、`/blog`→`/post`、`/voucher`→`/coupon`、`/voucher-order`→`/coupon-order`
- Redis Key：`cache:shop:`→`cache:goods:` 等
- 前端：目录 `html/campus-bazaar`，主题色改为校园绿
- MySQL 驱动 8.0.33、Lombok 1.18.30（适配 MySQL 8 / JDK 17）

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
