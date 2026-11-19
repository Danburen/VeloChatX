# VeloChatX 2.1.0 手工测试指南

面向发版前的人工验收。自动化测试（143 个用例）覆盖逻辑层，本指南覆盖真实代理环境下的行为与观感。

## 0. 部署前置检查

**不要直接部署 `build/libs/VeloChatX-2.1.0.jar`。** 该 jar 是"瘦包"，不含 WaterAPI 与 H2，启动会 `NoClassDefFoundError`。

可用的胖包（含 WaterAPI + H2 + 资源文件）由 IntelliJ Artifact 生成：

```
Build → Build Artifacts... → VeloChatX → Rebuild
输出：out/artifacts/VeloChatX/VeloChatX.jar
```

> 建议后续在 `build.gradle` 增加 `fatJar` 任务，使发版产物可复现、不依赖 IDE。

## 1. 测试环境

| 项    | 要求                                                                                                |
| ---- | ------------------------------------------------------------------------------------------------- |
| 代理   | Velocity 3.3+ / Java 17                                                                           |
| 后端   | 2~3 个 Paper 1.20+                                                                                 |
| 权限   | LuckPerms（Velocity 端，Velocity 无 OP）                                                               |
| 客户端  | 至少 2 个账号，验证频道隔离建议 3 个                                                                             |
| 生成文件 | `plugins/velochatx/` 下的 `config.yml`、`message.yml`、`system-message.yml`、`broadcast.yml`、`data.db` |

### 配置对齐

1. `velocity.toml` 的 `[servers]` id 必须与 `config.yml` → `server.list` 的键**大小写完全一致**。
2. 频道成员写在 `server.list.<id>.channels`；旧写法 `channels.channel-list.<频道>.servers` 仍兼容，混用时旧写法整体优先。
3. 验证频道隔离必须设 `channels.global: false`（默认 `true` 为全服互通）。
4. 测试期建议：`debug: true`、`broadcast.interval: 15`、`ban-words.words` 加一个测试词（如 `zzbanzz`）。
5. 验证"同频道切后端"的后端级广播，需要在同一频道内放两个后端。

## 2. 冒烟清单

### A. 启动与诊断

| #   | 操作                                                      | 预期                                                       |
| --- | ------------------------------------------------------- | -------------------------------------------------------- |
| A1  | 启动代理                                                    | banner、配置加载摘要正常；无绑定类 WARNING；无异常栈                        |
| A2  | `/vcx status`                                           | 声明/已注册/在线交集/离线数、频道数、广播数；BINDING 段均为 `BOUND`              |
| A3  | `/vcx show-channel-info`                                | 每个频道显示名称、显示名、在线人数                                        |
| A4  | `/vcx help`                                             | 玩家只看到有权限的命令；管理员多出 status/config/reload/show-channel-info |
| A5  | `/vcx placeholder "{server} {channel} {player} {ping}"` | 各占位符解析正确                                                 |

### B. 聊天

> 机制说明：插件**不取消** Velocity 聊天事件。发送者所在后端仍显示原句，插件只把格式化副本发给**其它后端**的玩家。跨服验证必须看另一台后端的玩家。

| #   | 操作                                                | 预期                                  |
| --- | ------------------------------------------------- | ----------------------------------- |
| B1  | 同服两人聊天                                            | 本地正常显示且不重复                          |
| B2  | A 在 server1 说话，B 在 server2                        | B 收到 `chat-format` 格式的跨服消息          |
| B3  | `channels.global: false`，A/B 在不同频道                | B 收不到                               |
| B4  | `chat-format-model` 依次设 `legacy` / `mini-message` | 颜色码与 MiniMessage 标签正确渲染             |
| B5  | 发送违禁词                                             | 发送者收到提示，跨服副本不发出（见第 4 节注意事项）         |
| B6  | `log-text.enable` / `convert` 切换                  | 终端有输出；`convert: true` 时显示颜色而非 `§` 码 |

### C. 开关 / 屏蔽 / 私聊 / 提及

| #   | 操作                                           | 预期                                     |
| --- | -------------------------------------------- | -------------------------------------- |
| C1  | `/vcx off` 后跨服说话                             | 其它后端收不到；本地聊天正常；重新登录后仍为 off             |
| C2  | `/vcx on`                                    | 恢复转发                                   |
| C3  | `/vcx block B` 后跨服说话；B `/mention A`          | B 静默收不到；A 的提及对 B 静默但 A 仍显示"已发送"        |
| C4  | `/vcx list` → `/vcx unblock B` → `/vcx list` | 先列出 B，解封后提示空列表；重启后状态保持                 |
| C5  | `/mention B`                                 | 双方提示 + B 屏幕标题 + 音效                     |
| C6  | `/vcmsg B 你好`                                | 双方各收到一条；控制台发送时接收方看到 `server.proxy` 显示名 |
| C7  | 对自己 mention/msg/block                        | 明确错误提示                                 |

### D. 加入 / 离开 / 欢迎

| #   | 操作                             | 预期                                                                              |
| --- | ------------------------------ | ------------------------------------------------------------------------------- |
| D1  | 首次登录                           | 收到 `welcome-broadcast.message`；`only-first-join: true` 时第二次不再收到                 |
| D2  | 登录                             | 全服收到 `entry-broadcast.proxy.join-message`                                       |
| D3  | 在同一频道的两个后端间切换                  | 目标后端收到 backend join，原后端收到 leave（`scope: server`）                                |
| D4  | 在不同频道间切换                       | 旧频道收到 channel leave，新频道收到 channel join；`hierarchical-overlay: true` 时不再额外发后端级消息 |
| D5  | 退出游戏                           | 全服 proxy leave；Tab 移除该玩家                                                        |
| D6  | 进入带 `welcome.enable: true` 的频道 | 仅本人收到频道欢迎语                                                                      |

### E. 定时广播

| #   | 操作                            | 预期                                        |
| --- | ----------------------------- | ----------------------------------------- |
| E1  | 等待 `broadcast.interval`       | `global.message-list` 全服轮播，`random` 控制顺序  |
| E2  | locale 广播                     | 仅发给 `channel-list` / `server-list` 命中的服务器 |
| E3  | 修改 `interval` 后 `/vcx reload` | 新间隔生效，无需重启                                |

### F. Tab 列表

| #   | 操作               | 预期                                                           |
| --- | ---------------- | ------------------------------------------------------------ |
| F1  | 查看 header/footer | `{proxy}`、`{server}`、`{online}`、`{total_online}`、`{ping}` 正确 |
| F2  | 查看每行条目           | `tab-list.format` 的服务器、前后缀、名字正确                              |
| F3  | 增减在线人数           | 刷新间隔随人数按 `refresh-ratio` 变化；`interval: 0` 不刷新                |
| F4  | 跨服切换 / 退出        | 条目服务器前缀更新，退出后条目消失                                            |

### G. 配置 / 迁移 / 重载

| #   | 操作                                | 预期                                               |
| --- | --------------------------------- | ------------------------------------------------ |
| G1  | `/vcx config diff`                | 列出相对内置默认的新增/删除/变更键及动态集合结构违规                      |
| G2  | `/vcx config update`              | 补齐缺失键且保留注释；无变化时提示无需更新                            |
| G3  | `/vcx config migration --dry-run` | 预览旧键迁移，不写盘                                       |
| G4  | `/vcx config migration`           | 应用迁移、备份到 `bak/`、自动 reload，旧键消失                   |
| G5  | `/vcx reload`                     | 全部配置与定时任务重载，无异常                                  |
| G6  | `server.list` 写入不存在的 id           | 启动 WARNING；`/vcx status` 显示 `PHANTOM` 并点名，不崩溃    |
| G7  | 删除 `config.yml` 重启                | 按 `locale` 重新生成并正常启动 |

### H. 权限

| #   | 操作                   | 预期                                                    |
| --- | -------------------- | ----------------------------------------------------- |
| H1  | 无权限玩家 `/vcx status`  | `NO_PERMISSION`（不是"未知命令"）                             |
| H2  | 仅 `velochatx.player` | on/off/block/unblock/list/mention/vcmsg 可用，admin 命令拒绝 |
| H3  | 控制台 `/vcx on`        | `ONLY_IN_GAME`                                        |
| H4  | `velochatx.admin`    | 玩家命令与管理命令均可用                                          |

## 3. 命令速查

| 命令                                                                    | 权限         | 说明             |
| --------------------------------------------------------------------- | ---------- | -------------- |
| `/vcx help`                                                           | 所有人        | 显示当前可用的子命令     |
| `/vcx status`                                                         | admin      | 服务器/频道/广播/绑定诊断 |
| `/vcx show-channel-info`                                              | admin      | 频道列表与在线人数      |
| `/vcx placeholder <文本>`                                               | admin（限玩家） | 解析占位符          |
| `/vcx config <reload\|update\|diff\|migration> [名称\|all] [--dry-run]` | admin      | 配置文件管理         |
| `/vcx reload`                                                         | admin      | 重载全部配置与任务      |
| `/vcx on` / `/vcx off`                                                | player     | 开关跨服聊天转发       |
| `/vcx block <玩家>` / `/vcx unblock <玩家>`                               | player     | 屏蔽/解除屏蔽        |
| `/vcx list [block]`                                                   | player     | 查看屏蔽列表         |
| `/mention <玩家>`（`/men`、`/at`）                                         | player     | 提及，带标题与音效      |
| `/vcmsg <玩家> <消息>`（`/vctell`）                                         | player     | 私聊             |

权限节点：`velochatx.admin` / `velochatx.player`，以及 `velochatx.admin.<cmd>`、`velochatx.player.<cmd>` 细粒度节点。

## 4. 已知观察点

1. **瘦 jar 问题**：`build/libs` 产物不可直接部署，发版前必须确认使用胖包。
2. **违禁词仅拦跨服副本**：`VCXEventHandler.onChat` 命中违禁词后只提示发送者并返回，未取消聊天事件，本地后端仍显示原句。若产品预期为全服拦截，需在发版前修复。
3. **`/vcx off` 同理**：仅停止跨服转发，本地聊天不受影响。

## 5. 自动化测试对照

| 手工场景        | 测试类                                                                   |
| ----------- | --------------------------------------------------------------------- |
| B1/B2/B4/B5 | `ChatServiceImplTest`、`VCXEventHandlerTest`                           |
| B3/A3       | `ChannelServiceImplTest`                                              |
| A2          | `StatusSnapshotTest`、`BindingDiagnosticsTest`、`ServerServiceImplTest` |
| C1/C3 持久化   | `PlayerServiceImplTest`                                               |
| C5/C6/C7    | `CommandHelperTest`、`VCXEventHandlerTest`                             |
| D2~D5/E     | `BroadcastServiceImplTest`、`VCXEventHandlerTest`                      |
| F           | `TabListServiceImplTest`                                              |
| G3/G4       | `ConfigMigrationServiceIntegrationTest`                               |
| 文案键一致性      | `MessageTemplateConsistencyTest`                                      |
| 版本号一致性      | `PluginMetaTest`                                                      |

自动化无法覆盖、必须手工确认：真实事件时序、客户端渲染效果、LuckPerms 前后缀、跨后端延迟、Tab 观感、屏蔽的静默体验。

## 6. 运行自动化测试

```powershell
$env:GRADLE_USER_HOME='D:\Project\Java\tool\.gradle'
.\gradlew.bat test --offline -q
.\gradlew.bat build --offline -q
```

当前基线：24 个测试类，143 个用例，0 失败。
