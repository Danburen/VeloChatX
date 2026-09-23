# 服务绑定改造方案（兼容双读「现在做」+ 配置迁移「计划」）

> 目标：在**不改用户现有配置文件**的前提下，让插件同时支持旧键与新键；
> 在启动与 `status` 指令中清晰提示「哪些服务器正常绑定 / 哪些未正常绑定」；
> 把「配置迁移工具」作为后续计划（config Flyway）。
>
> 关联：`docs/waterapi-command-tree-plan.md`；`.ai/PROJECT.md`。
> 生成日期：2026-09-22

---

## 0. 三个真相源（问题重述）

| # | 真相源 | 管什么 |
|---|---|---|
| T1 | Velocity `[servers]`（运行时注册） | 现实 |
| T2 | `server-display.display` | 显示名 |
| T3 | `channels.channel-list.*.servers` | 通信分组 |

**转发由 T3 + `channels.global` 决定；T2 只影响 `{Server}`/`{channel}` 显示。**

---

## 1. 旧 / 新键映射

| 维度 | 旧键 | 新键 |
|---|---|---|
| 显示 | `server-display.display.<id>` | `server.list.<id>.display` |
| 成员 | `channels.channel-list.<name>.servers[]` | `server.list.<id>.channels[]` |
| 代理显示 | `server-display.proxy` | `server.proxy`（过渡期同时读旧键，旧优先） |
| 显示开关 | `server-display.enable` | `server.display-enable`（过渡期同时读旧键，旧优先） |

## 2. 兼容优先级（运行时，只读，不写文件）

```
legacyDisplay    = server-display.display
legacyChannels   = channels.channel-list.*.servers
newServers       = server.list.*

legacyMembership = 任一 legacyChannels 非空
legacyMode       = legacyMembership || !legacyDisplay.isEmpty()

display(id)  = legacyDisplay[id]  ?? newServers[id].display ?? 原始名     // 逐项旧优先
channels(id) = legacyMembership ? 反推 legacyChannels : newServers[id].channels
```

- **display**：逐项旧优先 → 支持渐进迁移。
- **membership**：粗粒度——只要旧 `channel.servers` 存在，新 `server.channels` **整体忽略**（方向相反，避免诡异行为）。
- 新旧共存 → **WARN**「旧键优先，新键被忽略，建议迁移」。
- 都无 → 全部 `UNBOUND`，走默认（原始名 / 空 `{channel}`），不报错。

## 3. 绑定状态与数据模型

引入 `ServerRegistry`（读兼容）统一产出：

- `display(id)`、`channels(id)`、`isBound(id)`、`state(id)`、`issues()`、`canCommunicate(a,b)`。
- `BindingState`：`BOUND`（声明且已注册）/ `UNBOUND`（已注册未声明）/ `PHANTOM`（声明未注册）。
- 上层（显示/占位符/转发/诊断/status）**只认注册表**，不再各处拼 config（修 B6）。

## 4. 诊断分级（按 `global` 分档）

| Issue | 条件 | `global=true` | `global=false` |
|---|---|---|---|
| `CHANNEL_UNKNOWN_SERVER` | 频道引用了 Velocity 不存在的服 | INFO | **WARN** |
| `CHANNEL_EMPTY` | 频道校验后为空 | **WARN** | **WARN** |
| `SERVER_WITHOUT_CHANNEL` | 已注册后端不在任何频道 | — | **WARN** |
| `UNBOUND_SERVER` | 已注册但未声明 | INFO | INFO |
| `PHANTOM_SERVER` | 声明但 Velocity 没有 | INFO | INFO |

- `server-display.enable=false`（新键 `server.display-enable=false`）时**不报** UNBOUND（原始名是预期行为，修 B1）。
- `global=true` 时 `CHANNEL_UNKNOWN_SERVER` 降为 INFO（不丢消息，只坏 `{channel}`）。

## 5. 启动提醒与指令

**启动（`init()` → `reportBindingProblems()`）**
- 有 WARN：逐条 i18n 输出 + 末尾汇总「共 N 条需处理，运行 `/<cmd> status` 查看」。
- 只有 INFO：仅一行汇总（或 `debug` 才展开）。
- 旧模式：额外一行「检测到旧版绑定配置，建议迁移」。

**指令**
- `/<cmd> status`：保留汇总额，新增逐服状态表（`BOUND/UNBOUND/PHANTOM`）+ 频道状态。
- 覆盖新旧键两种模式（注册表同源）。

## 6. 配置迁移工具（计划，config Flyway）

现在**不做**，仅设计。复用现有底座：

| 现有 | 复用方式 |
|---|---|
| `MigrationManager` / `Migration` / `AbstractMigration`（DB） | 照搬模式做 **config 版** |
| `config-version` | 作为「已应用版本」 |
| `ConfigFileUpdater`（`bak/` 备份、写盘） | 复用备份/写盘机制 |
| `YamlConfigDiffer` / `ConfigDiff`（键级 diff） | 用于**预览 / 校验**，不用于变换 |
| `YamlConfigMerger` / `YamlTextIndex` | 注释保留的文本操作 |

设计：

```
domain/config/migration/
├── ConfigMigration          // { int version(); void migrate(ConfigData from, ConfigData to); }
├── ConfigMigrationManager   // 按 version 顺序执行未应用项；幂等
└── migrations/C2026xx__ServerListBinding.java
```

- 与 Flyway 一致：**迁移逻辑显式写在迁移类里**；`config-version` 充当 `schema_version`。
- 触发：手动 `/<cmd> config migration [--dry-run]`（不自动改配置），写前备份 `bak/`。
- **难点**：① 旧 `channel.servers` → 新 `server.channels` 是方向反转，不能靠键级 add/remove；② `channels.channel-list` 是 user-owned 动态集合，differ 会跳过；③ 结构重写要保留注释；④ `config/{en,zh}.yml` 两份都要迁。

## 7. 风险

| 风险 | 缓解 |
|---|---|
| 旧键优先导致「新键不生效」 | 共存即 WARN + 迁移工具 |
| membership 方向混用 | 粗粒度模式选择 |
| `global=true` 告警过吓人 | 按 global 分档 |
| `config update` 无法结构迁移 | 专门迁移代码 + 备份 + dry-run |

## 8. 测试计划

- 双读优先级：旧 display 覆盖新 display；旧 channel.servers 存在时新 server.channels 被忽略。
- 绑定状态：`BOUND/UNBOUND/PHANTOM` 判定。
- 分级矩阵：`global` true/false 下同一配置的等级不同。
- `server-display.enable=false` 不产生 UNBOUND。
- `canCommunicate(null, x)` 返回 `false`（fail-closed）。

---

## 9. 实现状态（2026-09-22）

已落地（运行时兼容层，**不修改用户配置**）：
- 新键 `server.list.<id>.{display,channels}`（`ServerConfigKey.SERVER_LIST`；`config/{en,zh}.yml` 模板已改为**带示例条目**的结构，与 `channel-list` 对称，不再用空 `{}`）。
- `ServerServiceImpl`：双读纯函数 `resolveDisplay`/`channelsOf`（原 `BindingRules` 下沉，2026-09-23）+ 并集 declared ids 内联于 `loadServers` + `BindingState(BOUND/UNBOUND/PHANTOM)` + `getDeclaredChannelNames` + `hasLegacyBindings`。
- `ChannelServiceImpl`：`legacyMembership` 包内纯函数（原 `BindingRules` 下沉）；成员来源旧优先（任一 channel 声明非空 `servers` 即旧模式），否则 server-centric；`canCommunicate(null,*)` 改 `false`（fail-closed）。
- `BindingDiagnostics`：新增 `Severity(INFO/WARNING)`，按 `channels.global` 分档；`server-display.enable=false` 时不报 UNBOUND（修 B1）+ 汇总行；旧键模式追加迁移提示。
- 启动与 `reload` 均重跑绑定检查（修 B4）。
- `/velochatx status`：逐服 `BOUND/UNBOUND/PHANTOM` 表 + 分级 issues。

未做（计划 / 后续）：
- **「未绑定默认不转发」**：未启用（仍为弱绑定；本次仅把 `null` 分支 fail-closed）。

已完成补充：
- **i18n**：绑定诊断与 `/velochatx status` 已走 `system-message/{en,zh}.yml`；`BindingDiagnostics.Issue` 结构化 + `Renderer` 本地化渲染。
- **config Flyway**：`ConfigMigration` / `ConfigMigrationManager`（WaterAPI）+ **整合进 `ConfigFileUpdater.migrate`**（复用 read/backup/write）+ `YamlConfigMerger.mergeDataDiff`（保注释文本补丁）+ `ServerBindingConfigMigration`（VeloChatX）+ `/velochatx config migration [--dry-run]`。apply 前备份到 `bak/`；写入为文本补丁，**注释保留**（被删块的前导注释随块删除）。
- **`server.list` item schema**（2026-09-22）：`KeyItemSchema` 注册 `optional("display")` + `optional("channels")`（均 optional，条目可为 `{}`）；进入动态白名单。
- **未绑定默认显示补 `[]`**（2026-09-22，方案 A）：`server-display.enable=true` 且该 id 未配置 display 时，`{server}` 输出 `[原始名]`（已带 `[]`/`【】` 则不重复包）；`enable=false` 保持裸名。`{channel}` 未绑定仍为空串。配置了 display 的条目原样输出（不强制加括号）。
- **`server.proxy` 双读**（2026-09-22）：新键 `server.proxy`（`ServerConfigKey.SERVER_PROXY`）；运行时经 `ConfigData.getResolvedLegacyValue`（原 `BindingRules.resolveProxyDisplay` 已删除）旧键 `server-display.proxy` 优先（raw 非 null），否则新键，否则默认。`{proxy}` 占位符与 `MsgCommandHandler.sendFromProxy` 经 `ServerServiceImpl.getProxyDisplay()` 同源。`/velochatx config migration` 将 `server-display.proxy` 迁到 `server.proxy` 并删除旧键。
- **`server.display-enable` 双读 + 迁移**（2026-09-23）：开关从 `server-display.enable` 迁到 `server.display-enable`（`ServerConfigKey.DISPLAY_ENABLED`）；运行时 `ServerConfigKey.isDisplayEnabled` 经 `getResolvedLegacyValue` 旧键优先；`ServerBindingConfigMigration` VERSION 升 **2.1.1**（已盖 2.1.0 的文件会重跑），迁移后若 `server-display` 节为空则整节删除；出厂 `config/{en,zh}.yml` 的 config-version 同步 2.1.1，模板不再含 `server-display` 节。
- **`BindingRules` 删除**（2026-09-23）：`resolveDisplay`/`channelsOf` → `ServerServiceImpl` 包内静态；`legacyMembership` → `ChannelServiceImpl` 包内静态；declared 并集内联 `loadServers`；`resolveProxyDisplay` 死代码随类删除。

测试：`ServerServiceImplBindingRulesTest`(4) / `ChannelServiceImplMembershipTest`(3) / `ServerBindingConfigMigrationTest`(5) / `ConfigMigrationManagerTest`(3) / `YamlConfigMergerDataDiffTest`(2) / `SystemMessageKeysTest`(1) / `ServerServiceImplDisplayTest`(+schema/default brackets) / `ConfigKeysTest`（覆盖 `server.proxy` / `server.display-enable` 模板）。

