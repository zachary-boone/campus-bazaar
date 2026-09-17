# 服务启停脚本

每个服务独立一个启动脚本 + 一个停止脚本，共 8 个。**双击即可运行。**

## 脚本清单

| 服务 | 启动 | 停止 | 端口 |
|------|------|------|------|
| **Redis** | `redis-start.bat` | `redis-stop.bat` | 6379 |
| **nginx** | `nginx-start.bat` | `nginx-stop.bat` | 8080 |
| **后端 (Spring Boot)** | `backend-start.bat` | `backend-stop.bat` | 8081 |
| **RabbitMQ** | `rabbitmq-start.bat` | `rabbitmq-stop.bat` | 5672 / 15672 |

## 安装路径

如果目录有变动，需要改脚本里对应的路径：

| 组件 | 路径 |
|------|------|
| Redis | `E:\redis-zb\redis-win-x64\` |
| nginx | `E:\project\nginx-1.18.0\nginx-1.18.0\` |
| 后端 jar | `E:\project\campus-bazaar\backend\target\campus-bazaar.jar` |
| RabbitMQ | `E:\tools\rabbitmq_server-3.13.7\sbin\` |
| Erlang | `E:\tools\erl-26.2.5\` |

---

## 推荐启动顺序

启动顺序很重要，因为 **Redisson 在后端启动阶段就会连 Redis，连不上后端会直接启动失败**（不是警告后继续）。

```
1. redis-start.bat        底层，必须最先启动
2. rabbitmq-start.bat     可选（不启动也能跑后端）
3. backend-start.bat      依赖 Redis
4. nginx-start.bat        代理前端 + /api
```

访问 `http://127.0.0.1:8080`

## 关闭顺序（和启动相反）

```
1. nginx-stop.bat
2. backend-stop.bat
3. rabbitmq-stop.bat
4. redis-stop.bat
```

---

## 脚本特性

- **幂等**：启动时先检查端口，已在运行则提示并退出，不会重复启动
- **轮询等待**：启动后按秒轮询端口，服务一就绪立即报告成功（不是死等固定秒数）
- **双层清理**：停止时先用官方优雅命令（`redis-cli shutdown` / `nginx -s quit` / `rabbitmqctl stop`），失败再按端口强杀
- **依赖预检**：`backend-start.bat` 会先检查 Redis 是否在跑，不在则提示"请先运行 redis-start.bat"

## 各脚本等待上限

| 脚本 | 最长等待 |
|------|---------|
| nginx-start | 10 秒 |
| redis-start | 15 秒 |
| backend-start | 40 秒 |
| rabbitmq-start | 90 秒 |

超过上限会报 `[FAIL]` 并提示去看对应日志。

---

## 排查

脚本报 `[FAIL]` 时，查看对应日志：

| 服务 | 日志位置 |
|------|---------|
| Redis | `E:\redis-zb\redis-win-x64\redis.log` |
| nginx | `E:\project\nginx-1.18.0\nginx-1.18.0\logs\error.log` |
| 后端 | `E:\project\campus-bazaar\backend\run.log` |
| RabbitMQ | `E:\tools\rabbitmq_server-3.13.7\data\log\rabbit@<计算机名>.log` |

### RabbitMQ 启动失败

- 确认 `E:\tools\erl-26.2.5\bin\erl.exe` 存在（RabbitMQ 依赖 Erlang）
- 脚本内已设置 `ERLANG_HOME` 等环境变量，无需手动配置
- 首次启动或数据目录损坏时可能较慢，可看 `data\log\` 下的日志

### 后端启动失败

常见原因按概率排序：

1. **Redis 没启动** → 报 `RedisConnectionException: Unable to connect to Redis server`
   → 先跑 `redis-start.bat`
2. **jar 不存在** → 需要先构建：
   ```
   cd /d E:\project\campus-bazaar\backend
   E:\tools\apache-maven-3.9.16\bin\mvn.cmd package -DskipTests
   ```
3. **MySQL 没启动** → 检查 3306 端口

### 关于 RabbitMQ 的启用

后端默认**不启用** RabbitMQ 消费者（避免本地没 RabbitMQ 时反复重连）。要启用秒杀异步下单 / 支付延迟队列：

```bat
set SPRING_RABBIT_LISTENER_AUTOSTARTUP=true
```

在 `backend-start.bat` 里加这一行（放在 `java` 命令之前），然后确保 RabbitMQ 已经跑起来。

---

## 开发提示

- **不要用任务管理器杀所有 java.exe**，会误杀 IDEA。后端 stop 脚本用窗口标题 `CampusBazaar*` 匹配，匹配不到才按端口杀。
- 脚本都是 **CRLF 行尾 + 纯 ASCII 内容**，避免 cmd 解析和中文编码问题。如果要改，注意保持这个格式（用记事本另存为 ANSI 编码，或用支持 CRLF 的编辑器）。
- 8 个脚本由 `E:\project\.workbuddy\scripts_gen\gen_service_bats.py` 生成，需要批量改动时改生成器再重新跑。
