# VeloChatX 重构收尾方案

> 记录重构当前的完成度、剩余工作与执行顺序。
> 生成日期：2026-09-18
> 关联文档：`docs/waterapi-supplement-plan.md`、`.ai/context/2026090*.md`

---

## 0. 目标

把 VeloChatX 从「`manager/*` 静态工具类 + 旧 WaterAPI」重构为：

- **WaterAPI**：跨平台基础设施（SPI + Kernel + 领域模型 + 数据层），Velocity / Bukkit 双端适配。
- **VeloChatX**：`domain/*` DDD 服务 + `ServiceFactory` 编排 + 接口驱动的命令/事件系统。

验收标准：`gradlew build` 通过；Velocity 端可正常启动并完成聊天/广播/Tab/命令/屏蔽全流程。

---

## 1. 当前状态总览

| 层面 | 状态 |
|---|---|
| WaterAPI SPI / Kernel / 领域模型 | 主体完成 |
| WaterAPI Velocity 适配 | 完成 |
| WaterAPI Bukkit 适配 | **未完成（编译失败）** |
| VeloChatX domain 服务 | 基本完成，存在功能回退与运行时缺陷 |
| VeloChatX 命令/事件 | 已迁移到 SPI |
| VeloChatX 旧代码清理 | **未完成（编译失败）** |
| 资源/配置（yml） | **en/zh 不同步，与代码 schema 不符** |
| 构建 | **失败** |

---

## 2. WaterAPI 收尾（第一阶段）— ✅ 已完成

> **Deprecated 处理约定（用户指定）**：凡标注 `@Deprecated` 的类/方法，**不再修补，直接移除**，改用新类/新接口。

### 2.1 删除废弃/损坏类

| 文件 | 原因 |
|---|---|
| `plugin/bukkit/command/BukkitCommand.java` | 引用已删除的 `enums.COLOR`、`BukkitPlugin.getPluginMessage`；由 SPI 适配器取代 |
| `plugin/bukkit/command/CommandHandler.java` | 与 SPI `CommandHandler` 重名冲突；由 SPI 取代 |
| `plugin/bukkit/command/CommandManager.java` | 旧命令树；由 `BukkitCommandRegistrar` 取代 |
| `plugin/bukkit/command/CommandNode.java` | 同上 |
| `plugin/velocity/util/MethodBase.java` | `@Deprecated`，引用已删除的 `WaterPlugin.getInstance/getConfigs/getPluginMessageOld` |
| `plugin/velocity/util/ScheduledManager.java` | `@Deprecated`，由 `SchedulerService` 取代 |
| `utils/DebugUtil.java` | `@Deprecated`，由 `LoggerService` 取代 |
| `utils/processor/MessageProcessor.java` | 引用已删除的 `BukkitPlugin.getPluginMessage`，无调用方 |
| `domain/shared/common/COLOR.java` | `@Deprecated`，由 `utils.ChatColor` / `WColor` 取代 |
| `domain/shared/common/TAGS.java` | `@Deprecated`，无引用 |
| `utils/Parser.java` | `@Deprecated`，无引用 |

### 2.2 新增 Bukkit SPI 适配器

对齐 Velocity 的 `domain/infra/velocity/adapter/*` 结构：

| 新增文件 | 实现接口 | 说明 |
|---|---|---|
| `domain/infra/bukkit/adapter/BukkitCommandSource.java` | `domain.spi.CommandSource` | 包装 `org.bukkit.command.CommandSender`；Player/Console 判定、locale、`MessageParser` 颜色解析、`toNative()` |
| `domain/infra/bukkit/adapter/BukkitCommandRegistrar.java` | `domain.spi.CommandRegistrar` | 优先绑定 `plugin.yml` 中声明的 `PluginCommand`；未声明时通过 `Server.getCommandMap()` 动态注册 |

### 2.3 修正插件基类与 Kernel

| 文件 | 改动 |
|---|---|
| `plugin/bukkit/BukkitPlugin.java` | 实现 `Plugin.disable()`；新增 `createCommandRegistrar()`；提供 `getPluginMessage(String[, Object...])` 实例方法（委托 Kernel `PluginMessageService`） |
| `domain/kernel/Kernel.java` | 修复 `disable()` 递归：回调 `plugin.disable()` **之前** 置 `isDisabled = true`；`plugin.getPluginName()` → `getPluginContext().getPluginName()` |
| `domain/kernel/services/meta/PluginUpdateMeta.java` | 移除 6 个 `@Deprecated` 方法；修复 `ofNilNew()` 构造参数 |
| `domain/shared/common/BatchActionResult.java` | 补 `@AllArgsConstructor`（`@Builder` 需要）；修复 success/fail 列表归类 |
| `domain/model/UPlayerRegistry.java` | 构造器补 `WProxyServer`，以构造 3 参 `UPlayer` |
| `domain/shared/config/ConfigData.java` | `getData()` 返回类型 `HashMap` → `Map`（与 Lombok `@Getter` 兼容） |
| `domain/infra/io/FileConfigProcess.java` | 移除 `DebugUtil` 调用 |

### 2.4 测试补强（`utils/diff` 与核心冒烟）

| 文件 | 改动 |
|---|---|
| `utils/diff/MyserDiffer.java` | 修复 Myers 回溯：`vHistory` 索引 off-by-one、`prevY` 用了当前 k 而非 `prevK`、REMOVED 取错数组、越界。6 个既有测试从 5 失败 → 全通过 |
| `domain/shared/config/FileConfigData.java` | `get()` 误把整个 `data` map 传给 `DataMapperUtil.toValue`（忽略取到的 `value`）；`getString()` 在 key 缺失时返回 null 而非默认值。均修复 |
| `domain/shared/common/BatchActionResult.java` | 补 `@Getter`（DTO 无访问器，无法被消费/测试） |
| `src/test/.../WaterApiCoreTest.java` | 新增 6 个冒烟测试：`BatchActionResult`、`PluginUpdateMeta`、`FileConfigData` 嵌套读取与缺省回退 |

### 2.5 深度审查与修复（2026-09-18）

对 WaterAPI 做了一轮系统审查，发现并修复以下问题：

| 级别 | 问题 | 修复 |
|---|---|---|
| Critical | `H2DataBase` 共享连接被 `BaseMapper`/`MigrationManager` 的 try-with-resources 关闭 → 首次操作后 DB 失效 | `getConnection()` 改为每次返回**新连接**；`isConnected()` 用探针；`close()` 置标志 |
| Critical | `MigrationManager.init()` 从未调用 → 首次 `migrate()` 查不存在的 `schema_version` | `migrate()` 内部先调 `init()`（幂等） |
| Critical | `MessageServiceImpl` base name 为 `"messages"`，实际资源是 `message/` → 抛 `FileNotFoundException` → `skipPlayerMessageLoad()` → `messageService=null` | 改用 `Constants.MESSAGE`（`"message"`） |
| Critical | `PluginMessageServiceImpl.getPluginMessage(MessageTemplate)` 在 `localeMsgs` 缺语言时 NPE（`Kernel.enable()` 启动即崩） | 回退到默认 `kernelMessages`；`load()` 去掉重复加载 |
| Critical | `Kernel.enable()` 在配置初始化**之前**调 `getBannerContext()` → `pluginConfigService` 为 null → NPE | banner 移到 `pluginConfigService` 之后 |
| Critical | `propertiesConfigLoader` 误用 `YamlConfigParser` → `default/*.properties` 找不到 | 改用 `PropertiesConfigParser` |
| High | `PlaceholderServiceImpl` 对整个模板 `toLowerCase()` → 破坏所有展示文本大小写 | 改为**大小写不敏感替换**，保留字面文本 |
| High | 动态占位符把**整条消息**当 `fullKey` 传给 resolver → 消息被毁 | 按 `pattern` 匹配占位符名并逐项替换 |
| Medium | `Kernel.enable()` 出错后继续初始化 | 关键步骤后加 `isDisabled` 守卫提前返回 |
| Medium | `BaseMapper.executeBatch` 不还原 autoCommit、失败不回滚 | 补 restore + rollback |
| Medium | `MessageServiceImpl` 在 DEFAULT 缺失时 NPE | 补 null 守卫 |
| Medium | `WaterPlugin.getDataFolder()` 与 `PluginContext.dataFolder` 两套来源 | 统一委托 `PluginContext` |
| Medium | `domain/config/{FileConfig,YamlConfiguration,PropertiesConfiguration}` 死代码 | 删除 |
| Low | `EventBus` 用 `ArrayList` 并发订阅/发布不安全 | 改 `CopyOnWriteArrayList` |

新增回归测试 `WaterApiRegressionTest`（6 项）：DB 连接复用、迁移首跑幂等、占位符大小写、动态占位符、未解析占位符保留、`%xxx%` 转换。

### 2.6 第二轮审查与修复（2026-09-18）

| 级别 | 问题 | 修复 |
|---|---|---|
| Critical | `FileConfigLoader.load(..., RESOURCE_ONLY)` **强制要求磁盘文件存在** → jar 内资源（`message/*.yml`、`system-message/*.yml`、`default/*.properties`）全部加载失败 | 三个 `load` 重载：`RESOURCE_ONLY` 跳过文件存在性检查 |
| Critical | `UServer.getPlayers()` 对不可变 List（`Stream.toList()`）调用 `clear()/addAll()` → 每次调用抛 `UnsupportedOperationException` | 构造器改为 `new ArrayList<>(...)` 可变副本 |
| High | `FileConfigLoader.loadResource` 资源缺失时 `.openStream()` NPE（非 IOException，调用方 catch 不到） | 资源为 null 时抛 `FileNotFoundException` |
| High | `UpdateServiceImpl.getConfigVersion` 守卫条件**写反**：对 `FILE_ONLY`/`RESOURCE_ONLY` 反而抛异常（调用方正是这两种）→ 更新检查恒失败 | 条件取反；资源缺失抛 `FileNotFoundException` |
| High | `PluginConfigServiceImpl` 启动时忽略 `config.yml` 的 `locale`，只用 JVM 语言 | 优先读配置 `locale` |
| Medium | `JavaLoggerServiceImpl.autoLogMessageWithColor` 在不支持 ANSI 时**打印两次** | 提前 `return` |
| Medium | `JarResourceAccessor.isResourceExists` 用了 `getClass().getClassLoader()` 而非注入的 classLoader | 改用注入的 `classLoader` |
| 内容 | `KernelMessage` 42 个 key 中 **23 个**在 `default/*.properties` 里不存在 → 内核日志显示 `PLUGIN_MESSAGE_MISSING<...>` | 待补（内容问题，不影响功能） |
| 低 | `VelocityColoredMessageSender.showTitle` 把配置值按 **tick**（`*50ms`）处理，而配置注释写的是**秒** | 待确认单位口径 |

新增回归测试 `WaterApiConfigModelTest`（3 项）：`UServer.getPlayers()` 刷新不抛异常、`RESOURCE_ONLY` 无需磁盘文件、资源缺失抛 `IOException`。

### 2.7 第三轮审查与修复（2026-09-18）

覆盖此前未审查的：权限层、数据库工具、插件元数据、Web、工具类、更新 diff。

| 级别 | 问题 | 修复 |
|---|---|---|
| High | `SafePermissionService`：`Class.forName("...LuckPermsProvider")` **必然成功**（API 是编译依赖），真正失败点在 `LuckPermsProvider.get()` 抛 `IllegalStateException`，未被捕获 → 未装 LuckPerms 时插件初始化崩 | 改为 `catch (Throwable)` 回退 NoOp |
| Medium | `BatchMapResult.success(Map, Map<ID,Exception>)` 传入 `new HashMap<>()` 而非 `exceptionMap` → **异常信息丢失** | 传入 `exceptionMap` |
| Medium | `PluginMetaLoader`：`plugin.yml` 缺失时 `getResourceAsStream` 返回 null → `new Yaml().load(null)` → NPE（未被 IOException 捕获） | 显式判空并抛 `FileNotFoundException`；清理死方法 `toStringSafe` |
| Low | `LuckPermsHock.hasLuckPerm()` 只捕获 `NoClassDefFoundError`，未捕获 `IllegalStateException` | 一并捕获 |

**未修复（内容/待确认）**：
- `KernelMessage` 42 key 中 23 个在 `default/*.properties` 缺失（内核日志显示 MISSING）。
- `VelocityColoredMessageSender.showTitle` 时间单位 tick vs 秒 口径不一致。

**发现的大片死代码（legacy 配置子系统，仅自引用）**：
`utils/ConfigReader`、`utils/ConfigPatcher`（空类）、`utils/Translate`、`utils/TextConverterUtil`、`utils/adapter/DataAdapter`(@Deprecated)、`utils/processor/StringProcess`、`domain/infra/io/{FileConfigProcess,FileConfiguration,MemoryProcess,FileConfigBase}`。
待确认后统一清理。

### 2.8 验收结果

```
gradlew :WaterAPI:build  → BUILD SUCCESSFUL

KernelLifecycleTest      tests=2 failures=0 errors=0
MyserDifferTest          tests=6 failures=0 errors=0
WaterApiCoreTest         tests=6 failures=0 errors=0
WaterApiRegressionTest   tests=6 failures=0 errors=0
WaterApiConfigModelTest  tests=3 failures=0 errors=0
                         ─────────────────────────────
                         23 tests, 0 failed
```


---

## 3. VeloChatX 收尾（第二阶段）

### 3.1 删除旧代码（编译阻断）

| 文件 | 说明 |
|---|---|
| `manager/`（8 类） | `BasicMethods` / `ChannelManager` / `ChatManager` / `LogManager` / `MessageManager` / `PlayerManager` / `TabListManager` / `TaskManager`，均引用已删除的 `org.Danburen.*` |
| `events/PlayerEvents.java`、`events/ServerEvents.java` | 引用 `me.Danburen.velochatx.*` |
| `mapper/MapperBase.java`、`mapper/FilePlayerMapper.java`、`mapper/PlayerMapperArchived.java` | 旧数据层 |
| `entity/Channel.java` | 与 `domain/channel/Channel` 重复 |
| `utils/BroadCastUtil.java`、`utils/SubServer.java`、`utils/TitleShow.java`、`utils/PlayerAttribution.java` | 与 domain 版重复 |
| `domain/player/PlayerConfigKey.java` | import 路径错误（`domain.infra.config.ConfigKey` 不存在）；若无用途则删除，否则改用 `domain.shared.config.ConfigKey` |

### 3.2 功能修复

| 位置 | 问题 | 修复 |
|---|---|---|
| `ChatServiceImpl` | `chat-format` 校验大小写不匹配（`{server}` vs `{Server}`），导致配置被忽略 | 校验改为大小写不敏感 |
| `ChatServiceImpl` / `VCXEventHandler` | `chat-format-model`（none/legacy/mini-message）未接入，聊天格式转换回退 | 在消息发送前按 model 转换 |
| `PlayerServiceImpl.storeOrUpdatePlayerBaseInfo` | 新玩家返回 `false`，首登判定反转 | 修正返回值语义 |
| `PlayerServiceImpl.getOrCreateAttrs` | `computeIfAbsent` 内再次 `put` 同一 key，CHM 递归更新风险 | 去掉函数内的 `put` |
| `ChannelServiceImpl.getChannelForServer` | 返回 `null` 导致 NPE | 返回 `Optional` 或调用处空值兜底（默认 global） |
| `ServerServiceImpl.loadServers` | `server-display.enable=false` 时不加载服务器 | 分离「服务器注册」与「显示名映射」 |
| `BroadcastServiceImpl.logToConsole` | 使用 `System.out.println` | 改用 `LoggerService` |
| `CommandHelper.getMessage(template, source, args...)` | 忽略 `args` | 使用 `PluginMessageService.getMessage(template, args)` |

### 3.3 资源/配置同步

| 文件 | 问题 | 修复 |
|---|---|---|
| `broadcast/en.yml` | 仍是旧 schema（`join-leave-broadcast`），与代码 `entry-broadcast.*` 及 `zh.yml` 不一致 | 重写为 `entry-broadcast` 新结构 |
| `system-message/en.yml` | 缺 `config-file-reload-error` | 补齐 |
| `system-message/*.yml` | `ignore/reject/remove-command-format` 文案已过时（命令已合并为 block/unblock） | 更新文案 |
| `config/en.yml` | 缺 `tab-list.refresh-ratio`；`mention-show-title` 走硬编码 key | 补配置项、收敛到 ConfigKey |
| `plugin.yml` | `main: me.Danburen.velochatx.VeloChatX`、version 2.0.8 过期 | 更新或删除（Velocity 走 `@Plugin` 注解） |

---

## 4. 执行顺序

1. ~~**WaterAPI 编译通过**（第 2 节）~~ ✅ 已完成
2. VeloChatX 旧代码清理 → 编译通过 ← **下一步**
3. 功能修复（第 3.2 节）
4. 资源/配置同步（第 3.3 节）
5. 整体 `gradlew build` + Velocity 实机启动验证

---

## 5. 风险与注意

- `Kernel.disable()` 递归修复会同时影响 Velocity/Bukkit 生命周期，需回归验证。
- `UPlayerRegistry` 构造器已改为 `(strategy, wProxyServer)`，VeloChatX 侧 `new UPlayerRegistry(strategy)` 需在第二阶段同步更新。
- 删除旧 `manager/*` 前确认无反射/字符串引用（已静态扫描，无）。
- Bukkit 端为「未来平台」，本次只保证**编译通过 + 接口对齐**，不做实机验证。
- `utils/diff/MyserDiffer` 回溯 bug 已修复并有测试覆盖；后续改动该算法请先跑 `MyserDifferTest`。

### 5.1 仍待处理（属 VeloChatX 第二阶段，非 WaterAPI 范围）

| # | 问题 | 修复位置 |
|---|---|---|
| H1 | 占位符 contributor 从未注册 → `{server}{player}{ping}...` 失效 | `ServiceFactory` 构造器内 `placeholderService.registerContributor(serverService/channelService/playerService)` |
| H2 | Kernel 监听器从未注册 → 配置 reload 不生效 | `ServiceFactory` 内 `kernel.registerListener(KernelConfigUpdateEvent.class, service)` |
| H5 | `VelocitySchedulerService` 的 plugin 属主传成了 `ServiceFactory` | 传入真正的插件实例 |

---

## 6. 第二阶段完成记录（2026-09-18）

### 6.1 旧代码清理（删除 18 个文件）

`manager/`（8）、`events/PlayerEvents`、`events/ServerEvents`、`mapper/{MapperBase,FilePlayerMapper,PlayerMapperArchived}`、`entity/Channel`、`utils/{BroadCastUtil,SubServer,TitleShow,PlayerAttribution}`。

### 6.2 接线修复（对应 5.1 的 H1/H2/H5）

| 项 | 实现 |
|---|---|
| H1 占位符 contributor | `ServiceFactory` 注册 `serverServiceImpl` / `channelServiceImpl` / `playerServiceImpl` |
| H2 Kernel 监听器 | `ServiceFactory` 注册 5 个 `KernelConfigUpdateEvent` 监听器（server/channel/chat/tablist/broadcast） |
| H5 调度器属主 | `ServiceFactory` 新增 `pluginInstance` 参数，`VeloChatX` 传入 `this` |

### 6.3 功能修复

| 位置 | 修复 |
|---|---|
| `VCXEventHandler` | 引用已删除的 `VeloChatXPlayerMessage` → 改用 `VeloChatXCustomMessage`；首登语义修正（`!storeOrUpdate`）；channel 空值保护；接入 `chat-format-model`（none/legacy/mini-message） |
| `ChatServiceImpl` | `chat-format` 校验改为大小写不敏感；新增 `getChatFormatModel()` |
| `PlayerServiceImpl` | `computeIfAbsent` 内不再 `put`（消除递归更新）；`storeOrUpdatePlayerBaseInfo` 复用 `getOrCreateAttrs` |
| `ChannelService(Impl)` | `getChannelForServer` 返回 `Optional<Channel>`；`BroadcastServiceImpl`/`VCXEventHandler` 同步 |
| `ChannelConfigKey` | `CHANNEL_LIST` 默认值由 `ArrayList` 改为 `HashMap`（否则配置 Map 读不回来） |
| `ServerServiceImpl` | 不再因 `server-display.enable=false` 而清空服务器注册；显示名缺省回退服务器名 |
| `BroadcastServiceImpl` | 注入 `LoggerService`（替换 `System.out`）；定时广播改为**按服务器发送一次**（原按在线玩家发送 → 重复 N 次） |
| `CommandHelper` | `getMessage(template, source, args...)` 不再忽略 `args` |

### 6.4 WaterAPI 侧配套修复

- `DataMapperUtil.toValue`：补 Map/List 处理（默认值为 `Map.of()`/`List.of()` 时按“种类”返回真实值，而非退回默认值）。

### 6.5 资源同步

| 文件 | 修复 |
|---|---|
| `broadcast/en.yml` | 重写为 `entry-broadcast` 新 schema（与 zh.yml 一致） |
| `system-message/en.yml` | 补 `config-file-reload-error` |
| `plugin.yml` | `main` → `org.nitrowater.velochatx.VeloChatX`，version → 2.1.0 |
| `config/en.yml` | 补 `tab-list.refresh-ratio` |

### 6.6 验收

```
gradlew clean build  → BUILD SUCCESSFUL
产物: VeloChatX-2.1.0.jar
WaterAPI 测试: 23 tests, 0 failed
en/zh 配置键: broadcast / system-message / config 全部一致
```

### 6.7 已知遗留（非阻断）

（已在第 7 节全部处理）

---

## 7. 第四轮：审查与收尾（2026-09-18）

### 7.1 本轮新发现的逻辑缺陷（已修复）

| 位置 | 问题 | 修复 |
|---|---|---|
| `TabListServiceImpl` | 用 `Component.text(...)` 发送 tab 头/尾/条目 → **`§` 颜色码原样显示**；且上下文缺 `serverName` → `{server}` 解析为空 | 改用 `MessageParser.parse(...)`；新增 `buildContext()` 带上当前服务器名 |
| `BroadcastServiceImpl.getPrefix` | 返回 `broadcasts.get(0)`（全局）→ **区服前缀永远被忽略**（文档要求 locale 覆盖 global） | 从后往前取第一个非空前缀 |
| `BroadcastServiceImpl.executeTimedBroadcast` | 按**在线玩家**遍历发送 → 同一服务器 N 人重复广播 N 次 | 改为按**服务器**发送一次 |
| `ControlCommandHandler.handleReload` | `reload broadcast/message` 只重载主配置 | 按参数分派：`all`/`config`/`broadcast`/`message` |
| `BroadcastServiceImpl` | 广播配置不可替换，无法重载 | 新增 `updateConfig(Config)`；`ServiceFactory.reloadBroadcast()` 重新读盘 |
| `VelocityColoredMessageSender.showTitle` | 时间按 tick（`×50ms`）处理，而配置/旧实现是**秒**（`Duration.ofSeconds`） | 改回 `Duration.ofSeconds` |

### 7.2 配置/资源对齐

| 项 | 处理 |
|---|---|
| `KernelMessage` ↔ `default/*.properties` | 补齐 `en.properties` 缺失的 23 个 key、`zh.properties` 缺失的 1 个 key → **en/zh 缺失均为 0** |
| 启动 banner 信息源 | `Kernel.getPluginInfo()` / `getBannerContext()` 改为读取 **`plugin.yml` → `PluginMeta`**（不再从 `config.yml` 的 `plugin:` 段读取）；`config/{en,zh}.yml` 的 `plugin:` 段已移除 |

### 7.3 新增回归测试

| 测试 | 覆盖 |
|---|---|
| `WaterApiResourceTest.mapAndListDefaultsReturnActualValues` | `DataMapperUtil` 对 `Map.of()`/`List.of()` 默认值返回真实值 |
| `WaterApiResourceTest.allKernelMessageKeysExistInDefaultProperties` | `KernelMessage` 每个 key 在 `default/en|zh.properties` 均存在 |
| `PluginMetaLoaderTest.loadsPluginMetaFromPluginYml` | banner 元数据来自 `plugin.yml`（name/version/author/description） |

### 7.4 最终验收

```
gradlew clean build  → BUILD SUCCESSFUL
产物: VeloChatX-2.1.0.jar

KernelLifecycleTest      tests=2 failures=0
MyserDifferTest          tests=6 failures=0
WaterApiCoreTest         tests=6 failures=0
WaterApiRegressionTest   tests=6 failures=0
WaterApiConfigModelTest  tests=3 failures=0
WaterApiResourceTest     tests=2 failures=0
PluginMetaLoaderTest     tests=1 failures=0
                         ────────────────────
                         26 tests, 0 failed

en/zh 一致性:
  - KernelMessage 缺失: en=0, zh=0
  - VeloChatXCustomMessage 缺失: en=0, zh=0
  - VeloChatXSystemMessage 缺失: en=0, zh=0
  - broadcast / system-message / config 顶层 key 完全一致
```

### 7.5 结论

- WaterAPI 与 VeloChatX 均编译通过，整体 `gradlew clean build` 成功并产出可部署 jar。
- 旧 `manager/events/mapper/entity/utils` legacy 代码已全部移除。
- 占位符 contributor、Kernel 监听器、调度器属主等跨层接线已完成。
- 尚需**实机启动验证**（Velocity 服务器加载 + 聊天/广播/Tab/命令/屏蔽全流程）。

---

## 8. 第五轮：完整逻辑审查（2026-09-18）

### 8.1 发现并修复的逻辑缺陷

| 级别 | 位置 | 问题 | 修复 |
|---|---|---|---|
| **Critical** | `PlayerMapper.insertPlayerRecord` | 仍向 `player_ban_list` 插入，而 `V002` 已 DROP 该表 → **首次进服必抛 SQLException** | 移除该插入（屏蔽关系已改为 `player_blocked` 关联表） |
| **High** | `MentionCommandHandler` | 要求 `args.length == 2`，但旧实现/帮助文本/消息模板都是 **1 个参数**（`/mention <player>`）→ 命令不可用 | 改为 `args.length != 1`，`targetName = args[0]` |
| **High** | `MentionCommandHandler` | `MENTION_TO`（发给发送者）用**发送者**上下文解析 `{Player}` → 显示成自己 | 改为用**被提及者**上下文；`MENTION_RECEIVE`/Title 用发送者上下文 |
| **High** | `TabListServiceImpl.processUpdates` / `queueUpdate` | 按 `playerUuid` 去重 → 同一玩家对**多个目标**的 ADD/REMOVE 只保留 1 条（如断线时只从一个玩家的 tab 里移除） | 去重键改为 `(type, player, target)`；ADD/REMOVE 抵消按 `(player, target)` 配对 |
| Medium | `ControlCommandHandler.handleBlockAction` | block/unblock 提示直接发模板 → `{Player}` 原样显示 | 用目标玩家上下文 `resolve()` |
| Medium | `ControlCommandHandler.handleBlockAction` | `checkNotSelf` 已发错误，又重复发一次 NO_SELF_ACTION | 去掉重复 |
| Medium | `MentionCommandHandler` / `MsgCommandHandler` | tab 补全仅在 `args.length == 0` 时给建议，输入首字母后无建议 | 改为 `args.length <= 1` 且按前缀过滤 |
| Medium | `VCXEventHandler.onDisconnect` / `PlayerEventManager` | 断线未清理 `PlayerService.attributions` 与 `UPlayerRegistry` → 长时间运行内存持续增长（旧代码有 `removePlayer`） | 新增 `PlayerService.removePlayer(UUID)`；断线时清理两处缓存 |
| Low | `PluginMessageServiceImpl.loadLocale` | `locale/<lang>` 覆盖缺失时抛 IOException → 每次切服刷警告 | 降级为 debug 日志，不抛异常 |

### 8.2 新增测试基础设施与测试

| 项 | 内容 |
|---|---|
| `build.gradle` | 新增 JUnit 5 依赖与 `useJUnitPlatform()`（此前 VeloChatX 无测试） |
| `PlayerPersistenceTest` | 端到端：跑 `V001`+`V002` 迁移 → `insertPlayerRecord`（守住 `player_ban_list` 修复）→ 改名/查询 → chat-offline 状态 → `player_blocked` 的 MERGE 幂等与增删查 |

### 8.3 验收

```
gradlew build → BUILD SUCCESSFUL

VeloChatX:  PlayerPersistenceTest      tests=1 failures=0
WaterAPI:   KernelLifecycleTest        tests=2 failures=0
            PluginMetaLoaderTest       tests=1 failures=0
            MyserDifferTest            tests=6 failures=0
            WaterApiCoreTest           tests=6 failures=0
            WaterApiRegressionTest     tests=6 failures=0
            WaterApiConfigModelTest    tests=3 failures=0
            WaterApiResourceTest       tests=2 failures=0
                                       ────────────────────
                                       27 tests, 0 failed
```

---

## 9. 第六轮：配置 / 功能 / 占位符 / 广播 专项审查（2026-09-18）

### 9.1 广播（Broadcast）

| 级别 | 问题 | 修复 |
|---|---|---|
| **High** | `broadcastPlayerJoin` 的 `CHANNEL_JOIN` 消息用 `previousChannelName` 作为广播范围 → **加入消息发到了「上一个」频道** | 改用 `currentChannelName`（leave 仍用 previous） |
| **High** | `resolveMessage()` 未带 `serverName` → 所有 entry 广播里的 `{server}`/`{channel}` **渲染为空** | `resolveMessage(template, player, serverName)`；join 用当前服、leave 用上一服 |
| **High** | `broadcastPlayerLeave` **从未被调用** → 离开上一服务器/频道时没有任何离开广播（旧实现有） | `VCXEventHandler.onJoinServer` 在 `prevServer != null` 时先调用它 |
| Medium | `entry-broadcast.proxy.scope` 在 `broadcast/{en,zh}.yml` 中缺失（`BroadcastConfigKey.PROXY_SCOPE` 存在） | 补齐 `scope: proxy` |

### 9.2 占位符（Placeholder）

| 级别 | 问题 | 修复 |
|---|---|---|
| **High** | 动态占位符把**原始大小写**的占位符名传给 resolver → `fullKey.replace("_channel_online","")` 对 `{Login_Channel_Online}` 失效 | `applyDynamic` 统一传小写 `fullKey` |
| 备注 | `PlaceholderContext.dynamicParams` 字段从未被 `PlaceholderServiceImpl` 读取（死字段） | 记录待清理（未删除，避免影响 API） |

已核对旧实现占位符集合：`{channel}{server}{proxy}{online}{total_online}{player}{prefix}{suffix}{group}`，新实现全部保留并新增 `{ping}`；旧的 `{<channel>_channel_online}` 属新特性（旧代码无），无回归。

### 9.3 模块配置（Config）

| 级别 | 问题 | 修复 |
|---|---|---|
| Medium | `PlayerConfigKey`（`player-storage.*`）为死代码：无引用、配置中也不存在 | 删除该枚举 |
| 增强 | 无机制保证 ConfigKey 与 yml 同步 | 新增 `ConfigKeysTest`：逐个校验 `Chat/Channel/Server/TabList/BroadcastConfigKey` 的 dotted path 在 `config/{en,zh}.yml`、`broadcast/{en,zh}.yml` 中存在（**该测试当轮即抓出 `entry-broadcast.proxy.scope` 缺失**） |

### 9.4 模块功能（Functionality）

| 级别 | 问题 | 修复 |
|---|---|---|
| Low | `PlayerAttribution.blockedPlayers` 为 `HashSet`，被聊天线程读取、命令线程写入 | 改为 `ConcurrentHashMap.newKeySet()` |

已核对：频道互通 `canCommunicate` 的实例比对依赖「server 先于 channel 重载」的监听器顺序（`ServiceFactory` 注册顺序保证），配置 reload 后 `Channel`/`BroadcastModel` 会拿到刷新后的 `UServer` 实例。

### 9.5 新增测试

| 测试 | 覆盖 |
|---|---|
| `ConfigKeysTest.allConfigKeysExistInLanguageTemplates` | 所有 ConfigKey ↔ en/zh yml 同步 |
| `WaterApiPlaceholderTest`（3 项） | contributor 注册、大小写不敏感静态占位符、字面文本保留、动态 pattern 占位符、args 仅作用于存在的占位符 |

### 9.6 验收

```
gradlew build → BUILD SUCCESSFUL

VeloChatX  ConfigKeysTest              tests=1 failures=0
           PlayerPersistenceTest       tests=1 failures=0
WaterAPI   KernelLifecycleTest         tests=2 failures=0
           PluginMetaLoaderTest        tests=1 failures=0
           MyserDifferTest             tests=6 failures=0
           WaterApiCoreTest            tests=6 failures=0
           WaterApiRegressionTest      tests=6 failures=0
           WaterApiConfigModelTest     tests=3 failures=0
           WaterApiResourceTest        tests=2 failures=0
           WaterApiPlaceholderTest     tests=3 failures=0
                                       ────────────────────
                                       31 tests, 0 failed
```

---

## 10. 会话纪要（2026-09-18）

> 详细日志：`.ai/context/20260918-225120.md`（普通日志）
> 本文档 §2–§9 为本次会话的完整修复台账。

### 10.1 会话主线

从「构建失败、新旧代码并存」到「WaterAPI + VeloChatX 全量编译通过、31 测试全绿、资源 en/zh 一致」。

### 10.2 六轮 WaterAPI 审查（累计 30+ 项修复）

| 轮次 | 主题 | 代表修复 |
|---|---|---|
| 1 | 编译解锁 + Bukkit SPI 适配 | 删 11 个废弃类；新增 `BukkitCommandSource`/`BukkitCommandRegistrar`；`Kernel.disable()` 递归守卫 |
| 2 | 配置管线 / 模型层 | `RESOURCE_ONLY` 不再要求磁盘文件；`UServer.getPlayers()` 不可变 List；`loadResource` NPE |
| 3 | 权限 / 元数据 / Web | `SafePermissionService` 捕获 `IllegalStateException`；`BatchMapResult` 丢异常；`PluginMetaLoader` NPE |
| 4 | 启动链路 + 收尾 | banner NPE；`PropertiesConfigParser`；TabList 颜色码；广播前缀优先级；`showTitle` 秒；KernelMessage key 对齐 |
| 5 | 完整逻辑审查 | `player_ban_list` 首次进服崩；`/mention` 回归；TabList 去重；断线缓存泄漏 |
| 6 | 配置/功能/占位符/广播专项 | 广播 join 发错频道 / 缺 serverName / leave 未调用；动态占位符大小写；`PlayerConfigKey` 死代码；`ConfigKeysTest` |

### 10.3 VeloChatX 第二阶段

- 删除 18 个 legacy 文件
- 完成跨层接线：占位符 contributor、Kernel 监听器、调度器属主、`UPlayerRegistry` 3 参
- 功能修复：`chat-format-model` 接入、首登语义、`Optional` channel、`computeIfAbsent` 递归、广播去重、`DataMapperUtil` Map/List

### 10.4 测试基线（31 个）

- VeloChatX：`PlayerPersistenceTest`、`ConfigKeysTest`
- WaterAPI：`KernelLifecycleTest`、`PluginMetaLoaderTest`、`MyserDifferTest`、`WaterApiCoreTest`、`WaterApiRegressionTest`、`WaterApiConfigModelTest`、`WaterApiResourceTest`、`WaterApiPlaceholderTest`

### 10.5 结论与后续

- `gradlew clean build` 成功，产出 `VeloChatX-2.1.0.jar`；en/zh 与所有消息/配置 key 一致。
- **唯一剩余：Velocity 实机启动验证**（加载 → 聊天/广播/Tab/命令/屏蔽全流程）。
- 可选静态自检：依赖打包（shadow）、`velocity-plugin.json`、类加载隔离。
- 待清理（非阻断）：`PlaceholderContext.dynamicParams` 死字段；WaterAPI 剩余 legacy 配置子系统（`ConfigReader`/`FileConfigProcess` 等）。





