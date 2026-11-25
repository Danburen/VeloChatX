# VeloChatX ↔ nitrowater 远程监控/控制方案（定稿）

> 方案：**30s 心跳 + SSE 命令长连接（常驻） + 5s 状态上报（仅 viewers>0）**
> 约束：VeloChatX（下称 V，Velocity 插件）不开入站端口；nitrowater（下称 N）除认证/注册元数据外不存业务数据；
> 数据主场在 V；100+ 实例；可用空闲内存 ~1GB；带宽 2Mbps。
> 身份：**一把用户级 api-key + 每台 V 自生成 UUID**，实例身份 = `(key_id, instanceId)` 二元组，每 key 最多 3 台。
> 本文只做设计，不含实现代码。

---

## 0. 结论速览

| 维度 | 结论 |
|---|---|
| 连接方向 | 全部由 V 主动发起（心跳 / 5s 状态 POST / SSE GET / 结果 POST） |
| V 是否开端口 | **否** |
| N 侧常驻连接 | 每个已注册实例 1 条命令 SSE（常驻）+ 浏览器在线时的 UI SSE |
| 浏览器不在线时 | V 保留心跳 + SSE；**停 5s 状态上报**（省带宽），心跳与 SSE 不停 |
| 命令延迟 | SSE 常驻，命令近实时；只有实例离线（无 SSE）时才 `503 OFFLINE` |
| 认证 | N 签发**用户级** api-key → `vcx remote set` → V 的 H2 → 每次请求 Bearer；N 验 key 定用户，实例由 UUID 区分 |
| 实例上限 | 每把 key 最多 **3 台** V（N 侧常量）；满额再接入 → `403 instance_limit_exceeded` |
| 克隆/冲突 | 后到者收到 `409 instance_conflict` → **自动生成新 UUID 并回写 H2 重连**（先到者不受影响） |
| 带宽预算 | 全 ACTIVE 100 实例约 300–450 kbps（payload ≤2KB + gzip），2Mbps 够用 |

---

## 1. 整体拓扑

```
浏览器                  nitrowater(N)                    VeloChatX(V)
   │ SSE /api/ui/stream     │                                │
   │◀───────────────────────│                                │
   │ POST /api/ui/commands  │                                │
   │───────────────────────▶│                                │
   │                        │  30s POST /api/public/instance/heartbeat  │
   │                        │◀───────────────────────────────│
   │                        │  resp: viewers / interval / displayName  │
   │                        │                                │
   │                        │  5s  POST /api/public/instance/status     │
   │                        │◀───────────────────────────────│ (仅 viewers>0)
   │                        │                                │
   │                        │  SSE GET /api/public/instance/commands/stream（常驻）
   │                        │◀───────────────────────────────│
   │                        │  event: command ──────────────▶│
   │                        │  POST .../commands/{id}/result │
   │                        │◀───────────────────────────────│
   │  status / result       │                                │
   │◀─── UI SSE fan-out ────│                                │
```

**要点**：V 的所有出站请求都打到 N，V 侧零监听端口；N 不主动连 V。
命令 SSE **常驻**（不随 viewers 开关），保证 AI/UI 随时能对任意实例下发命令，也是克隆自动纠正的触发通道。

---

## 2. V 端状态机（核心）

```
                     ┌──────────────────────────────┐
     启动 / 无观众    │             IDLE             │
    ────────────────▶│  30s heartbeat + SSE 常驻     │
                     │  无 5s status                 │
                     └──────────────┬───────────────┘
                                    │ heartbeat resp: viewers > 0
                                    ▼
                     ┌──────────────────────────────┐
                     │            ACTIVE             │
                     │  30s heartbeat 继续           │
                     │  SSE 常驻 继续                │
                     │  + 5s status POST             │
                     └──────────────┬───────────────┘
                                    │ heartbeat resp: viewers == 0
                                    ▼
                            回到 IDLE（只停 5s status，心跳与 SSE 不停）
```

**红线**：

- **30s 心跳在任何状态下都不能停**——它是 V 感知浏览器上线的唯一通道。停了，浏览器上线后 V 永远不知道（V 不开端口，N 无法主动叫它）。
- **SSE 在任何状态下都不能因为 viewers=0 而停**——AI 工具随时可能下命令，且克隆冲突要靠 SSE 建连时的 409 来发现。SSE 只承载低频 ping + 命令，常驻成本极低（见 §7）。

`viewers` 只做一件事：**控制 5s 状态上报的开关**。这样既省了 5s 上报的带宽（300–450 kbps 的来源），又保留了随时可达的命令通道。

- `viewers` 是 N 的内存计数（浏览器 SSE 会话数），不落库。
- 从 IDLE 切到 ACTIVE 的最坏延迟 = 一个心跳周期（30s）；面板先显示加载中，可接受。
- 上报间隔由 N 在心跳响应里下发 `reportIntervalMs`（5s / 30s），V 不自作主张，方便 N 按带宽动态调节。

---

## 3. 接口协议

### 3.0 通用

- Base URL：`remote.endpoint`，如 `https://n.example.com/api/public/instance`
  - nitrowater 的 BFF 只允许 `/api/**` 作为数据命名空间（`/api/public/**` 为有意匿名前缀），因此不能用顶层 `/v1/agent/**`；详见 `nitrowater/docs/velochatx-remote-integration.md`。
- TLS **强制**；证书校验开启（`remote.tls.verify: true`，仅调试可关）
- 认证头：`Authorization: Bearer <api-key>`（BFF 专用代理逐字转发，通用代理会剥掉该头）
- 所有请求/响应 JSON，UTF-8；**所有 JSON 响应统一包一层 `ApiResponse<T>`**（`{success, code, message, data}`），下文中响应示例均指其中的 `data` 部分，V 侧必须取 `.data`；错误读 `.code`，HTTP 状态只作基础设施语义
- SSE 帧不包裹（协议层例外），仍是裸 JSON `data:`
- `Content-Encoding: gzip` 用于 status 响应体超 512B 时
- 通用字段：
  - `instanceId`：**V 首次启动自己生成的 UUID**（见 §4.1）；它是 `(key_id, instanceId)` 二元组的一半，key 定用户、UUID 定机器
  - `bootId`：本次进程 UUID（区分重启，仅用于管理页展示/排错，**不参与自动纠正判断**）
  - `seq`：单调递增序号（V 侧生成，用于去重和乱序识别）

### 3.1 心跳（30s，常驻）

```
POST /api/public/instance/heartbeat
{
  "v": 1, "instanceId": "<V 生成的 UUID>", "bootId": "...", "seq": 1234,
  "startTime": 1763900000000,
  "pluginVersion": "2.1.0", "proxyVersion": "3.4.0"
}

200
{
  "v": 1,
  "instanceId": "...",
  "displayName": "instance-1Im3eA",  // N 首绑生成、可在 NW 改；V 缓存展示
  "serverTime": 1763900030000,
  "viewers": 0,
  "reportIntervalMs": 30000,      // viewers=0 → 30000；>0 → 5000
  "instanceState": "IDLE"         // N 侧视图，V 用它发现状态漂移
}
```

- 请求体 ~300B，响应 ~250B，含 HTTP 头约 1.5KB/次。
- 失败：指数退避 1s→2s→4s→…→30s + 随机抖动（jitter）。
- 心跳不携带业务数据；N 只更新内存 `lastSeen` 和注册表/凭据里的 auth 类元数据（允许）。
- 心跳首次会把 `(key_id, instanceId)` 注册进 N 的注册表：
  - 该 key 已登记数 < 3 → 登记成功，N 生成 `displayName` 并回显；
  - 已是登记的 instanceId → 只更新 `lastSeen/bootId`，回显当前 `displayName`；
  - 新 instanceId 但该 key 已满 3 台 → `403 instance_limit_exceeded`，V 停止自动纠正并告警。

### 3.2 状态上报（5s，仅 viewers>0）

```
POST /api/public/instance/status
{
  "v": 1, "instanceId": "...", "bootId": "...", "seq": 1235,
  "status": {
    "onlinePlayers": 23,
    "maxPlayers": 100,
    "servers": { "lobby": "BOUND", "server1": "BOUND", "ghost": "PHANTOM" },
    "channels": ["login", "games"],
    "broadcastMessages": 12,
    "bindingIssues": 2
  },
  "recentEvents": [
    { "ts": 1763900031000, "type": "PLAYER_JOIN", "player": "Steve", "server": "lobby" }
  ],
  "lastCommandResult": { "id": "cmd-9", "status": "OK" }
}

200 { "v": 1, "ackSeq": 1235 }
```

**payload 硬约束**：

| 项 | 上限 |
|---|---|
| 原始 JSON（gzip 前） | 2 KB（可配置 `remote.payload-limit`） |
| `recentEvents` 条数 | 10 |
| 完整玩家列表 | 不上传；需要时走命令按需查询 |
| 完整聊天记录 | 不上传；只报计数 + 最近 N 条 |

V 侧超限处理：丢弃最旧事件 → 仍超限则只发 `status` 摘要。

数据来源：`StatusSnapshot.collect(serverService, channelService, broadcastService)`（已有）+ `WProxyServer.getAllOnlinePlayers().size()` + 事件环形缓冲。

### 3.3 命令下发 SSE（常驻）

```
GET /api/public/instance/commands/stream
Authorization: Bearer <api-key>
（重连时带 `Last-Event-ID: 42`；N 从 key + instanceId 定位该实例的会话）

: ping                                   ← 每 20s 心跳注释，防 NAT/nginx 掐断

id: 42
event: command
data: {"id":"cmd-42","method":"broadcast","params":{"message":"...","scope":"GLOBAL"},"issuedAt":1763900040000}

id: 43
event: command
data: {"id":"cmd-43","method":"status","params":{},"issuedAt":1763900045000}
```

- **不能用浏览器原生 `EventSource`**（不能设自定义 Header）。V 端用 OkHttp `RealEventSource` 或 JDK `HttpURLConnection` 流读取（专用线程）。
- `Last-Event-ID` 语义：V 重连时带上次收到的 `id`，N 只重放该 id 之后的命令（N 内存短 TTL 缓冲，不落库）。
- 收到命令 → 查 `RpcRegistry` 白名单 → 执行 → POST 结果：

```
POST /api/public/instance/commands/{id}/result
{ "id":"cmd-42", "status":"OK"|"ERROR"|"TIMEOUT",
  "result": {...}, "error": "...", "finishedAt": 1763900041200 }
200 { "v": 1, "ack": true }
```

- 命令执行超时 10s；超时回 `TIMEOUT`。
- N 侧若该实例无 SSE 连接（真离线）→ 命令接口直接 `503 OFFLINE`，**不排队、不落库**（符合约束）。
- V 不验证命令来源：SSE 走与心跳同源的 TLS 连接 + V 已用 api-key 认证，N 即 peer。无需额外实例令牌。

#### 3.3.1 建连冲突与自动纠正（克隆场景）

两台 V 用了同一把 key、同一份 H2（典型是运维复制了数据目录），会带同一个 `(key, instanceId)` 建 SSE：

1. N 侧**先到者保留 SSE**，后到者在流打开前收到 `409 instance_conflict`（JSON `ApiResponse`，见 N 文档 §2.5）。
   - N 内部要先判断旧连接是否真的还活着：若只是旧进程断线残留、map 未清理，直接替换，**不返回 409**（避免正常重启被误判为克隆）。
2. **只有收到 409 的一方**执行自动纠正：
   - `UUID.randomUUID()` 生成新 `instanceId`；
   - **写回 H2 `remote_credential.instance_id`**（覆盖旧值），api-key 列不动；
   - 立即重新心跳 + 重建 SSE，用新 id 注册（N 生成新 `displayName`）。
3. 先到者不受影响，一步收敛成两台独立实例；NW 页面上用户可改显示名。

护栏（防止失控）：

| 护栏 | 做法 |
|---|---|
| 区分 409 / 403 | 409 → 换 UUID 重连；`403 instance_limit_exceeded` → **立即停止纠正并告警**（满 3 台后不能无限生成 id） |
| 重试上限 | 连续 3 次 409 → 退避 + 告警，不再自动换 id |
| 槽位计数 | 按注册表全部记录计（含离线），靠 NW「移除实例」释放，不按离线时长自动回收 |
| 轮换保留 id | `vcx remote set <newkey>` 只更新 key 相关列，`instance_id` / `display_name` 必须保留 |
| H2 写入失败 | 沿用旧 id 退避重试，**绝不能"内存换了但没落盘"**（重启会身份回退） |
| bootId 不参与 | bootId 只用于展示/排错，**不要拿它做自动换 id 的判断**（正常重启也会变，会误伤） |

### 3.4 浏览器侧（N 提供，本项目不实现）

- `GET /api/ui/stream`（浏览器 `EventSource`，Cookie 会话）：N 把对应实例的 status / 命令结果 fan-out 给在线会话；不在线就没有连接、零成本。
- `POST /api/ui/commands` → N 校验用户 → 按 `key_id + instanceId` 找到实例的 SSE → 发命令 → 返回 `202 {id}`；结果通过 UI SSE 推送。
- N 内存维护 `instanceId → Set<browserSession>`，`viewers` 即该集合大小。
- 管理页：`POST /api/admin/instance-credentials/{id}/instances/{instanceId}/rename` 改显示名；`/remove` 释放槽位（见 N 文档 §1.3）。

### 3.5 错误码

nitrowater 的 JSON 错误体是 `ApiResponse`：`success=false`、`code` 为下表字符串、HTTP 状态作基础设施语义。

| HTTP | `code` | 含义 |
|---|---|---|
| 400 | `instance_bad_protocol` | 协议版本/字段非法 |
| 401 | `instance_unauthorized` | api-key 缺失、格式错误、已吊销 |
| 403 | `instance_forbidden` | scope 不足（如只有 `telemetry:write` 却发命令） |
| 403 | `instance_limit_exceeded` | 该 key 已登记 3 台实例；V 停止自动纠正并告警 |
| 409 | `instance_conflict` | 同一 `(key, instanceId)` 已有活 SSE；V 收到后自动生成新 UUID 重连 |
| 429 | `instance_rate_limited` | 限流（命令频率 / 上报频率超限），响应携带新的 `reportIntervalMs` |
| 503 | `instance_offline` | 实例离线（无 SSE 连接） |
| 200 | — | 命令执行结果在结果回传的 `status:"OK"/"ERROR"/"TIMEOUT"` 中 |

---

## 4. 身份与认证

### 4.1 identity（每台 V 的 UUID）

**权威身份 = `(key_id, instanceId)` 二元组**：key 由 N 签发、决定"属于哪个用户"；instanceId 由 V 自己生成、决定"是这个用户的哪台机器"。

- V **首次启动**执行 `UUID.randomUUID()`，写入 H2 `remote_credential.instance_id`（明文，与 api-key 同表、同一套访问层；UUID 不是凭证，不需要加密）。
- 之后每次启动都从 H2 读同一个 UUID → 重启身份稳定，不漂移。
- V **不配置** `remote.instance-id`，也不读写任何 `identity.json` 文件——本地身份源只有 H2 一处。
- 首次心跳把 `(key, instanceId)` 注册到 N；N 生成可读显示名 `instance-xxxxxx` 并在心跳响应里回显，V 把 `displayName` 也缓存进 H2，供 `vcx remote status` 展示。用户可在 NW 页面改名，下次心跳回显新名字。
- `bootId` 每次进程启动新生成，单独字段，**只用于展示/排错，不参与身份与自动纠正判断**。

**为什么不用 N 在签发时把 id 焊进 key**：本方案里一把 key 会被同一用户的多台 V 共用（最多 3 台），一把 key 无法区分具体机器。所以 key 只能定用户，机器身份必须由每台 V 自带，且用 `(key_id, instanceId)` 联合定位。

**克隆自动纠正**见 §3.3.1：以 SSE 409 为唯一触发，只有后到者换 UUID 并回写 H2，先到者不变。

### 4.2 api-key 流程

1. 用户在 NW 管理页填备注后点击「生成实例凭据」→ N 签发一把**用户级** api-key（带 `kid`、`scopes`、`owner_uid`，**不绑定任何实例 id**），明文**只展示一次**，服务端只存 hash。
2. 运维把**同一把 key** 贴到最多 3 台 V，各执行 `vcx remote set <api-key>`：
   - 校验前缀/长度（如 `nw_live_` + 43 字符）
   - 本地 AES 加密（密钥文件 `remote/.localkey`，权限 600）→ 写入 H2
   - 旧 key 标记 `ROTATED`，保留一个宽限期
   - **保留**已有的 `instance_id` / `display_name` 列（key 轮换不能改身份）
   - 触发心跳线程立即重连
3. `VCX_REMOTE_API_KEY` 环境变量存在时优先，**不落盘**。
4. 日志 / `/vcx remote status` 只显示 `kid` + last4 + `displayName` + `instanceId`；命令不回显、不进 tab 补全、不写日志。
5. scope：`telemetry:write`（上报）、`rpc:execute`（命令），按 key 授权；同一把 key 下 3 台机器共享 scope。
6. **V 端不做认证**：只做被动持有者（加密存储 → 原样附带 → 401 停连告警 → 脱敏展示），不校验 key 格式/scope、不签发轮换、不管用户角色；认证全在 N 的 `InstanceApiKeyFilter`。

> **实例唯一性由 N 保证**：key hash 全局唯一；`(key_id, instance_id)` 在注册表唯一（DB `UNIQUE`）；每 key 限 3 台（N 常量）；每 `(key, instance)` 一条活 SSE（后到者 409 → V 自动纠正）；`bootId` 在管理页展示供排错。详见 `nitrowater/docs/velochatx-remote-integration.md` §1.3 / §2.3。

> 说明：本地 api-key 加密是混淆——能读数据目录的人通常也能读 `.localkey`。真正的安全来自文件权限、TLS、scope 限制、可轮换和"N 不存业务数据"。UUID 不需要加密（它不是凭证）。

### 4.3 H2 表（新增 migration `V003__CreateRemoteCredentialTable`）

```sql
CREATE TABLE IF NOT EXISTS remote_credential (
  id INTEGER PRIMARY KEY,              -- 单例行 = 1
  key_id VARCHAR(64) NOT NULL,
  key_last4 VARCHAR(8) NOT NULL,
  key_hash CHAR(64) NOT NULL,          -- SHA-256，仅展示/去重
  secret_enc VARBINARY(512) NOT NULL,  -- 加密后的 api-key
  instance_id VARCHAR(128) NOT NULL,   -- V 首次启动生成的 UUID；409 自动纠正时覆盖
  display_name VARCHAR(64),            -- N 首绑下发、可在 NW 改；仅展示
  scopes VARCHAR(256),
  status VARCHAR(16) NOT NULL,         -- ACTIVE / ROTATED / REVOKED
  created_at BIGINT NOT NULL,
  rotated_at BIGINT
);
CREATE TABLE IF NOT EXISTS remote_event_queue (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payload CLOB NOT NULL,               -- 离线时暂存的 status/event，恢复后补发
  created_at BIGINT NOT NULL
);
```

`remote_event_queue`：V 离线/上报失败时暂存，恢复后按序补发，条数上限（如 200）防止无限增长。这是 V 本地数据，不违反"N 不存业务数据"。

---

## 5. 项目内落点

### 5.1 新增包 `domain/remote/`

| 类 | 职责 |
|---|---|
| `RemoteConfigKey` | `ConfigKey` 枚举：`remote.enable / endpoint / heartbeat-interval(30000) / report-interval(5000) / sse-ping(20000) / payload-limit(2048) / tls.verify / queue-limit(200)`（**无 `remote.instance-id`**） |
| `RemoteIdentity` | H2 单例行读写：首次生成 UUID、重启读取、409 时重生成并回写、缓存 N 下发的 `displayName`；不参与认证 |
| `RemoteCredentialRepository` | `BaseMapper<RemoteCredential,Integer>`，H2 单例行读写 |
| `RemoteCredentialService` | 设置/轮换/读取/环境变量覆盖/脱敏展示；轮换时保留 `instance_id` / `display_name` |
| `RemoteClient` | 核心：心跳、5s status 门控、SSE 常驻读取、结果回传、409 自动纠正、重连退避 |
| `RemoteTransport` | 接口：`heartbeat/status/stream/result`，便于测试 fake |
| `JdkRemoteTransport` | 基于 `HttpURLConnection` + Gson 的实现（零新依赖；若引入 OkHttp 更简单） |
| `SseReader` | SSE 流解析（`: ping` / `id` / `event` / `data`，UTF-8，超时重连） |
| `RpcRegistry` | 命令白名单：method → handler / scope / 限流 / 超时 |
| `RemoteEventHandler` | 接 `VCXEventHandler` / `PlayerService` 的事件，写入最近事件环形缓冲 |
| `RemoteEventQueue` | H2 outbox，按序补发 |
| `V003__CreateRemoteCredentialTable` | DB migration |
| `RemoteCommandHandler` | `vcx remote set/status/connect/disconnect` 命令节点 |

### 5.2 现有类改动

| 文件 | 改动 |
|---|---|
| `ServiceFactory` | 创建 `RemoteCredentialRepository`、`RemoteIdentity`、`RemoteClient`；`startSchedulers()` 里启动；`stopSchedulers()/close()` 里停止；reload hook 中 endpoint 变化时重连 |
| `VeloChatX` | `registerCommands()` 中把 `remote` 子树挂进 `ControlCommandHandler.buildRoot()`（沿用 `CommandNode` 树） |
| `config/{en,zh}.yml` | 新增 `remote:` 段 + 注释；`config-version` 升级；schema 注册 |
| `system-message/{en,zh}.yml` | 新增 remote 命令回显/诊断消息（沿用 i18n 结构化 key） |
| `VCXEventHandler` | 在 join/leave/chat 事件处调用 `RemoteEventHandler.publish()`（异步、非阻塞） |
| `mapper/migration` 注册 | `MigrationManager` list 加入 `V003` |

---

## 6. 线程与生命周期

- `RemoteClient` 使用**专用单线程池**执行心跳/状态 POST（短请求，完成即释放连接）。
- SSE 读取使用**另一个专用线程**（阻塞在 `readLine()`），关闭时 `connection.disconnect()` + 线程中断。
- 命令执行若触碰 Velocity API（广播、踢人），通过 `proxyServer.getScheduler().execute()` 回主线程；结果 POST 回到远程线程池。
- **禁止在 Velocity 事件线程做网络 I/O**；`RemoteEventHandler.publish()` 只往内存环形缓冲写。
- `ProxyShutdownEvent` → `close()` 顺序：停 SSE → 停心跳/状态 → flush outbox（best effort）→ 关数据库。

---

## 7. 资源预算（100+ 实例）

| 项 | 计算 | 结果 |
|---|---|---|
| N 侧常驻连接 | 100 条 SSE（常驻）× 10–50KB | 1–5 MB（1GB 内存绰绰有余） |
| 心跳 | 100 ÷ 30 × 1.5KB | ~40 kbps |
| 5s 状态（1KB payload + gzip） | 100 ÷ 5 × 1.5KB | ~240 kbps |
| 5s 状态（2KB payload） | 100 ÷ 5 × 2.5KB | ~400 kbps |
| SSE ping + 命令 | 低频（20s 一次 ping 注释） | ~10 kbps |
| **合计** | payload ≤2KB、全 ACTIVE | **300–450 kbps**，2Mbps 留有一半余量 |

SSE 常驻不增加可观带宽（只多低频 ping 注释）；省下的 5s status 在 viewers=0 时不发，是主要节流手段。

---

## 8. 异常与降级

| 场景 | 行为 |
|---|---|
| N 不可达 | 心跳退避重试；状态写本地 outbox（上限 200 条，满则丢最旧）；**不影响聊天/广播主链路** |
| SSE 断开 | 1s→30s 退避 + jitter 重连；带 `Last-Event-ID` 补投；不因 viewers=0 而主动关 |
| 建 SSE 收到 409 | 自动生成新 UUID → 回写 H2 → 重连；连续 3 次 409 则退避 + 告警 |
| 注册收到 403 `instance_limit_exceeded` | 停止自动纠正、停止重连，日志告警"该 key 已满 3 台，请在 NW 移除实例或换 key" |
| 浏览器下线 | 下个心跳拿到 `viewers=0` → 停 5s status；**心跳与 SSE 保持** |
| 命令方法不在白名单 | 返回 `403` + 记录告警，不执行 |
| 命令执行超时 10s | 回 `TIMEOUT`，N 侧展示明确状态 |
| api-key 吊销 | 下次请求 `401` → V 停止重连并日志告警；`vcx remote status` 显示 `REVOKED` |
| 上报频率超限 | N 响应 `429` + 新的 `reportIntervalMs`，V 自动降频 |
| H2 写入 UUID 失败 | 沿用旧 UUID 退避重试，不改变内存身份 |

---

## 9. 安全清单

- TLS 强制；`remote.tls.verify` 默认 true。
- api-key 不入 config.yml、不入 jar、不入日志、不入 tab 补全；文档只写占位符。
- 请求体大小限制（64KB）；命令 method 白名单 + 频率限制 + 审计日志（不含 key）。
- 不暴露任意 Java 方法调用 / 不反序列化不可信对象；命令参数用 Gson 映射到固定 DTO。
- 身份 = `(key_id, instanceId)`：key 定用户、UUID 定机器；N 做唯一性与 3 台上限校验，V 无法冒充他人实例。
- 本地 `.localkey` 与 `data.db` 权限收紧（600）；UUID 明文存储即可（非凭证）。

---

## 10. 测试计划

| 测试 | 内容 |
|---|---|
| `RemoteIdentityTest` | H2 首次生成 UUID / 重启身份稳定 / 409 后重生成并回写 / 轮换 key 保留 instance_id / bootId 每次变化 |
| `RemoteStateMachineTest` | viewers 0→1→0 切换只影响 5s status；心跳与 SSE 全程不停 |
| `RemoteClientTest`（fake transport + fake clock） | 心跳/状态/重连退避/payload 超限降级/outbox 补发 |
| `SseReaderTest` | `: ping`、`id`/`event`/`data` 解析、UTF-8、断流重连、Last-Event-ID |
| `RemoteConflictTest`（fake N 返回 409/403） | 409 → 换 UUID 回写 H2 重连；连续 3 次 409 退避告警；403 停止纠正告警 |
| `RpcRegistryTest` | 白名单拒绝、scope 校验、限流、超时 |
| `RemoteCredentialTest` | 环境变量覆盖、轮换保留身份、脱敏、V003 migration 幂等 |
| 集成（JDK HttpServer 假 N） | 完整首绑注册 → viewers 切换 → 收命令 → 回结果 → 克隆 409 自动纠正 → 满 3 台 403 |

---

## 11. 实施顺序

1. **Phase 0**：协议文档定稿（本文）+ N 侧接口约定；确认 2Mbps 与命令延迟。
2. **Phase 1**：配置 `remote:` 段 + `vcx remote set` + H2 V003（含 `instance_id` / `display_name`）+ 脱敏（不含网络）。
3. **Phase 2**：`JdkRemoteTransport` + 心跳首绑注册 + 5s status（门控）+ outbox（fake N 测试）。
4. **Phase 3**：SSE 常驻 + 命令接收 + `RpcRegistry` + 结果回传（先用 `status`/`broadcast` 两个 method）+ 409 自动纠正。
5. **Phase 4**：接入真实事件（join/leave/chat）+ 带宽实测 + gzip + 动态间隔。
6. **Phase 5（后期）**：Paper companion 插件（plugin message 通道）+ 持续监控/告警（N 侧规则引擎，不依赖浏览器）。
