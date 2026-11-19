VeloChatX 2.1.0 更新日志
发布日期：2026.11.19

这是一次完整的重构版本：内核拆分为独立的 WaterAPI 模块，配置、消息、命令、Tab 列表等子系统全部重写。
配置结构已升级到 config-version: 2.1.0，升级前请先阅读文末「升级须知」。

【重构】
- 项目按领域重新分层：chat / channel / broadcast / player / tablist / server / shared，由 ServiceFactory 统一装配，移除旧的 Manager 单例写法
- 包名由 me.waterwood.velochatx 迁移至 org.nitrowater.velochatx
- 新增独立内核模块 WaterAPI：配置、消息、命令树、占位符、日志、更新检查、数据库迁移等通用能力下沉
- 平台能力通过 SPI 抽象（PlayerActionService / TabListProvider / SchedulerService / PermissionService 等），为 Bukkit / BungeeCord 适配预留接口
- 补充 50+ 组单元测试，覆盖聊天、频道、广播、Tab 列表、配置迁移、命令补全等核心链路

【新特性】
- 配置系统
  1. 配置文件带 config-version，启动时校验版本与结构，缺失键、版本滞后、待迁移项会给出提示
  2. 新增 /velochatx config diff | update | migration | reload
  3. config update 只补齐缺失键，保留注释与已有值，写入前自动滚动备份到 bak/
  4. config migration 支持旧版配置迁移（服务器绑定、玩家进出广播），支持 --dry-run 预览
- 服务器绑定：新增 server.list.<id> 结构，可配置显示名与所属频道；兼容旧键并自动迁移；未在 Velocity 注册的服务器会 WARNING 并在 /velochatx status 标记为 PHANTOM
- 层级进出广播：新增 entry-broadcast，按 proxy > channel > backend 三级触发，可分别设置 enable / scope / 进出消息，支持层级覆盖（hierarchical-overlay）与终端日志开关
- 聊天格式模型：chat-format-model 支持 none / legacy / mini-message 三种转换模式
- Tab 列表重写
  1. 生产者-消费者队列 + 同玩家任务合并，大幅减少高频事件下的数据包
  2. 动态刷新间隔：interval * (1 + 在线人数 / 20 * refresh-ratio)；interval: 0 为事件驱动模式
  3. 跨子服时保留代理内全部玩家条目，仅在离开代理服时移除；进出服即时刷新 header/footer（{online} / {total_online} / {ping}）
  4. 条目格式、header、footer 均支持完整占位符
- 统一命令树与权限模型：每个子命令独立权限节点（velochatx.player.<cmd> / velochatx.admin.<cmd>，同时兼容 velochatx.player / velochatx.admin），Tab 补全按权限过滤
- 管理命令：新增 /velochatx status 运行状态、/velochatx placeholder <文本> 占位符调试、/velochatx show-channel-info 频道信息
- 多语言消息系统：拆分为 message/（玩家可见）、system-message/（系统/控制台）、default/（内核），支持语言链回退；player-locale: global | locale 可选择按服务器或按玩家客户端语言显示
- 玩家数据持久化：H2 数据库 + 版本化迁移（V001/V002），保存聊天开关、屏蔽列表、最后离开时间；数据库结构随版本自动迁移
- 自动更新：GitHub Release 检查、自动下载、更新说明展示，配置文件版本可一并校验
- 终端日志：log-text.enable 控制输出，log-text.convert 可将颜色转换为终端 ANSI 效果；debug 模式输出详细流程

【修复】
- 修复 LuckPerms 前缀/后缀使用 & 颜色代码时，聊天、Tab、广播中颜色不生效的问题
- 修复 MessageService.getMessage(key, lang) 参数顺序错误，导致指定语言查询返回 MESSAGE_MISSING
- 修复玩家语言在登录时被缓存为空，导致中文客户端仍显示英文消息的问题
- 修复违禁词控制台日志使用错误的语言文件（PLUGIN_MESSAGE_MISSING）以及 {Player} 为空的问题
- 修复切换子服后其他玩家的 Tab 列表丢失该玩家条目、离开代理后条目残留的问题
- 修复玩家离开代理后 {total_online} 与 Tab header/footer 长时间不刷新的问题
- 修复 /velochatx config migration 无迁移项但仍有缺失键时，不提示 config update 的问题
- 修复 /velochatx placeholder 只能解析单个参数的问题，现支持整段模板
- 修复旧版 channels.global: false 不生效、服务器无法识别时的空指针等历史问题

【改动】
- 频道成员关系统一写在 server.list.<id>.channels；channels.channel-list.<name>.servers 转为旧格式，config migration 会移除
- 玩家进出广播由 config.yml 迁移到 broadcast.yml 的 entry-broadcast
- 原 locale/*.properties 由 system-message/*.yml 与 message/*.yml 取代
- 权限节点细化为按子命令授权，旧的组权限节点继续可用
- 最低运行环境保持 Java 17（Velocity 3.x）

【升级须知】
1. 备份 plugins/VeloChatX/ 整个目录
2. 替换 jar 后启动一次，按提示执行 /velochatx config migration 迁移旧配置
3. 再执行 /velochatx config update 补齐新键，最后 /velochatx config reload
4. 旧版 locale/*.properties 中的自定义内容需迁移到 system-message/<语言>.yml
5. 玩家数据库会在启动时自动迁移，无需手动处理

【已知问题】
- chat-format-model: none 对部分 Fabric 客户端可能显示异常，建议使用 legacy
- MiniMessage 模式依赖标签支持，过旧的客户端可能无法完整渲染
