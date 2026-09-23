# WaterAPI 命令树 SPI 方案（权限「走 A」）

> 目标：让插件用**与平台无关的命令树**声明命令结构，由各平台适配器渲染成原生命令树
> （Velocity → Brigadier），从而获得**按权限过滤的客户端命令可见性/补全**。
>
> 关联：`docs/server-binding-plan.md`；`.ai/PROJECT.md`；`.ai/context/20260922-130622.md`。
> 生成日期：2026-09-22

---

## 0. 背景与决策

### 现状
- `CommandHandler.execute(CommandSource, String[])` + `CommandRegistrar.register(alias, handler, permission, aliases)`：**纯字符串派发**。
- `VelocityCommandRegistrar` 包成 `SimpleCommand`，`permission==null` → `hasPermission` 恒 true。
- VeloChatX 三个命令全部以 `null` 注册，权限在 handler 内判。
- 结果：`/vcx` 对所有人生成，子命令的**客户端可见性**没有权限过滤（仅 `tabComplete` 里手工过滤）。

### 决策
采用**路径 A**：在 WaterAPI 新增平台无关的命令树模型与注册方法；Velocity 适配为 `BrigadierCommand`。
**形态 B**：树的叶子仍复用现有 `CommandHandler`，业务逻辑零改动。

### 已确认的取舍
- Brigadier 只解决「不显示」，**没有「拒绝回消息」**：无权限玩家手打被隐藏子命令，仍可能落到子服。
- 这是 **WaterAPI 框架级新增**（子模块 + 主仓库两段提交）。
- 先做 Velocity；Bukkit 降级。

---

## 1. 平台无关命令树模型

新增 `org.nitrowater.waterapi.domain.spi.CommandNode`：

```java
public final class CommandNode {
    String name;                 // literal，如 "config" / "status" / "on"
    String permission;           // null = 不限制（requires 恒 true）
    boolean playerOnly;          // 影响 help 展示与执行前校验
    CommandHandler handler;      // 叶子非空；分支为 null
    List<CommandNode> children;  // 子 literal
}
```

- 提供 builder / 静态工厂（`literal(...)` / `branch(...)`）。
- 不可变，`children` 用 `List.copyOf`。
- **不引入 Predicate**：用「权限字符串 + playerOnly」表达 `requires`，保持跨平台与可测。

## 2. SPI 扩展

`CommandRegistrar` 新增（旧方法保留，不破坏其它插件）：

```java
void registerTree(String rootName, List<String> aliases, CommandNode root, String rootPermission);
```

## 3. Velocity 适配

`VelocityCommandRegistrar.registerTree`：

```java
LiteralCommandNode<com.velocitypowered.api.command.CommandSource> build(CommandNode n) {
    var b = LiteralArgumentBuilder.<...>literal(n.name());
    if (n.permission() != null)
        b.requires(src -> src.hasPermission(n.permission()));   // 客户端命令树按此裁剪
    for (var child : n.children()) b.then(build(child));
    if (n.handler() != null)
        b.executes(ctx -> { n.handler().execute(wrap(ctx.getSource()), args(ctx)); return 1; });
    return b.build();
}
commandManager.register(meta, new BrigadierCommand(build(root)));
```

- 叶子执行复用 `CommandHandler`，把 Brigadier `CommandContext` 转成 SPI `CommandSource` + `String[]`。
- 根节点权限由 `rootPermission` 决定（`null` = 恒可见）。

## 4. Bukkit 降级

- Paper 1.20+：可使用 `LifecycleEventManager` + Brigadier，与 Velocity 共用同一树模型。
- 纯 Bukkit：降级为「根命令 + `tabComplete` 按权限过滤子节点」，仅失去客户端侧树可见性。
- 在 Bukkit 适配器内做能力探测，SPI 不变。

## 5. VeloChatX 接入

- 新增 `VeloChatXPermissions`：`ADMIN` / `PLAYER` 常量（消除 P2）。
- `ControlCommandHandler` 的 `record SubCommand(name, permission, playerOnly)` 升级为 `CommandNode` 树；
  help 展示、tab 过滤、`requires` **三处共用同一棵树**（消除 P2/P3/P6）。
- `MentionCommandHandler` / `MsgCommandHandler` 作为叶子节点接入；删除 `getPermission()` 死代码（P1）。
- `admin` 是否蕴含 `player`：在 `requires` 里显式写 `admin || player`。

## 6. 兼容与取舍

- 三命令不再传 `null` 平台权限，而是走树；无需改 handler 内部判权（保留为纵深防御）。
- **无权限手打 → 无友好提示**（Velocity 硬限制），需在文档/帮助里说明。
- `CommandManager.executeGeneric`（`config`/`reload`）改为树节点，`executes` 内委托。

## 7. 测试计划

- 命令树 → `requires` 映射：给定权限的 source，可见节点集合正确。
- 无权限节点不出现；`playerOnly` 对控制台隐藏。
- 叶子执行仍拿到正确的 `String[]`。

---

## 8. 实现状态（2026-09-22）

已落地：
- `domain/spi/CommandNode`（平台无关树节点 + builder）。
- `CommandRegistrar.registerTree(...)`（`default` 抛 `UnsupportedOperationException`，向后兼容）。
- `VelocityCommandRegistrar.registerTree` → `BrigadierCommand`；节点权限 → `requires(...)`；叶子参数由原始输入截取（子命令在 `args[0]`，与字符串派发一致）。
- `BukkitCommandRegistrar.registerTree` 降级为 `TreeDispatcher`（按首参派发 + 权限/playerOnly 过滤 + tab 过滤）。
- `CommandManager.registerTree(...)` 委托。
- VeloChatX：`VeloChatXPermissions` 常量；`ControlCommandHandler.buildTree()`（help / tab / `requires` 共用同一子命令表）；`VeloChatX.registerCommands()` 主命令走树，`mention`/`vcmsg` 以真实权限注册（按权限隐藏）；`tabComplete` 补权限校验（修 P3）；删除 `getPermission()` 死代码（修 P1）。

取舍 / 后续：
- 无权限玩家手打被隐藏节点 → 无提示（Velocity 硬限制，Brigadier 亦然）。
- 尚未支持参数节点（argument node），因此 `mention`/`vcmsg` 的玩家名补全仍依赖 `SimpleCommand.suggest`。

