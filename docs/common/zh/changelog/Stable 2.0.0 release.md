## 更新日志 - v2.0.0
### 新特性
- 新增 **channel(频道)** 功能，用于管理分组跨服聊天
  1. 服务器可以按照不同类别分配到不同的频道当中
  2. 可以设置不同频道聊天互通相互隔离
- 新增数据库支持，玩家信息保存至数据库当中
- 新增 **定时广播** 功能，定时向指定服务器发送广播功能
- 新增玩家进入代理服的时候广播欢迎消息
- 玩家的跨服聊天屏蔽将永久保存
- 更新消息的自动获取和读取

### 修复
- 简化了一些方法，提高了读取效率
- 删除和简化了某些不必要的方法
- 提高了配置文件的容错率

### 改动
- 玩家加入和离开服务器的广播功能已移至 broadcast.yml当中
- proxy 的展示设置将独立于其他服务器

### 已知问题
- 暂无

## [2.0.1] - [2025.4.5]
### 修复
- 修复全局广播在不启用的时候抛出的空指针异常
- 修复局部广播无法读取的问题
- 修复日志无法被正常获取

### 改动
- 调整了更新日志结构

## [2.0.2] - [2025.4.21]
### 修复
- 修复了玩家进出服务器偶尔会导致的玩家Tab列表出现重复UUID导致异常的问题

### 新特性
- 添加了新版本下载时候的简单校验功能

## [2.0.3] - [2025.5.11]
### 新特性
- 添加了广播和跨服聊天的启用/禁用开关

### 改动
- 移除了 broadcast 中的启用禁用开关
- config.yml 新增启用跨服聊天以及广播启用/禁用开关
- 现在广播的启用/禁用 会影响玩家进出服广播的启用/禁用

## [2.0.4] - [2025.6.24]
### 新特性
- 添加了频道的人数占位符{xxx_channel_online}
- 添加了指定服务器的人数占位符{xxx_server_online}

### 改动
- 使用了最新的LP API 和 Velocity API
- 现在英文使用占位符的时候不会强制转化为小写

## [2.0.5] - [2025.10.26]
### 修复
- 修复了当玩家的消息设置为全局的时候，键值对缺失导致的空指针异常的问题。
- 修复了识别不出服务器时候的空指针异常问题。
### 改动
- 现在默认是开启全服聊天。

## [2.0.6] - [2026.2.27]
### 修复
- 修复了一些空指针问题导致了插件内部读取紊乱
- 修复了1.0版本旧版配置迁移至2.0以后遗漏的配置读取异常而导致默认值被错误的配置的问题

## [2.0.7] - [2026.3.21]
### 改动
- 降低插件的Java JDK 编译版本由22至17

## [2.0.8] - [2026.4.15]
### 修复
- 修复了玩家消息转发的适合对于部分客户端颜色不转义的问题。
### 新特性
- 用户可以通过更改配置文件当中的 'chat-format-model' 来选择不同的转换模型。

## [2.1.0] - [2026.11.19]
> 完整重构版本：内核拆分为独立 WaterAPI 模块，配置、消息、命令、Tab 列表等子系统重写；配置结构升级至 `config-version: 2.1.0`。
### 重构
- 按领域重新分层（chat / channel / broadcast / player / tablist / server / shared），由 ServiceFactory 统一装配
- 包名由 `me.waterwood.velochatx` 迁移至 `org.nitrowater.velochatx`
- 新增独立内核模块 WaterAPI（配置、消息、命令树、占位符、日志、更新检查、数据库迁移）
- 补充 50+ 组单元测试

### 新特性
- 配置系统：`config-version` 校验、`/velochatx config diff|update|migration|reload`、缺失键补齐（保留注释并滚动备份到 bak/）、旧配置迁移（支持 `--dry-run`）
- 服务器绑定：新增 `server.list.<id>`（显示名 + 频道），兼容旧键并自动迁移，未注册服务器标记 PHANTOM
- 层级进出广播：`entry-broadcast` 按 proxy > channel > backend 触发，可配置 scope、消息与终端日志
- 聊天格式模型：`chat-format-model` 支持 none / legacy / mini-message
- Tab 列表重写：队列合并、动态刷新间隔（`interval * (1 + 在线人数/20 * refresh-ratio)`）、跨子服保留条目、进出服即时刷新 header/footer
- 统一命令树与权限模型：按子命令授权（`velochatx.player.<cmd>` / `velochatx.admin.<cmd>`），Tab 补全按权限过滤
- 新增 `/velochatx status`、`/velochatx placeholder <文本>`、`/velochatx show-channel-info`
- 多语言消息分层（message / system-message / default），支持 `player-locale: global|locale`
- 玩家数据持久化：H2 数据库 + V001/V002 自动迁移
- 自动更新与终端日志颜色转换

### 修复
- 修复 LuckPerms 前后缀 `&` 颜色代码在聊天、Tab、广播中不生效
- 修复 `getMessage(key, lang)` 参数顺序错误导致的 `MESSAGE_MISSING`
- 修复玩家语言在登录时被缓存为空，导致中文客户端显示英文
- 修复违禁词控制台日志 `PLUGIN_MESSAGE_MISSING` 与 `{Player}` 为空
- 修复切换子服后 Tab 条目丢失、离开代理后条目残留
- 修复玩家离开后 `{total_online}` 与 Tab header/footer 长时间不刷新
- 修复 `config migration` 无迁移项但缺键时不提示 `config update`
- 修复 `/velochatx placeholder` 只解析单个参数

### 改动
- 频道成员关系统一至 `server.list.<id>.channels`，`channels.channel-list.<name>.servers` 转为旧格式
- 玩家进出广播迁移至 `broadcast.yml` 的 `entry-broadcast`
- `locale/*.properties` 由 `system-message/*.yml` 与 `message/*.yml` 取代

### 升级须知
1. 备份 `plugins/VeloChatX/`
2. 启动后执行 `/velochatx config migration`
3. 执行 `/velochatx config update` 后 `/velochatx config reload`
4. 旧 `locale/*.properties` 自定义内容迁移到 `system-message/<语言>.yml`

### 已知问题
- `chat-format-model: none` 对部分 Fabric 客户端可能显示异常，建议使用 `legacy`
- MiniMessage 模式依赖客户端标签支持