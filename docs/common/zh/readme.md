# VeloChatX

![icon_middle_2](https://github.com/user-attachments/assets/2ce8ffa3-19b9-4905-aa50-5bd1a4386ad3)


### 🧐 插件简介
VeloChatX是一款专为Minecraft Velocity服务器设计的现代聊天解决方案，专注于在您的代理网络中提供快速、简洁且可靠的实时通信。

采用轻量级且可扩展的架构构建，易于维护、定制，并可随着服务器社区的扩大而扩展。

无论您是想要一个可靠且即开即用的聊天体验，还是想要一个支持高级功能的灵活基础，VeloChatX都能满足您的需求。
### 🤠 插件特性
* 完全自定义聊天格式 & 完全自定义消息输出
* mention标题提及/玩家跨服私聊
* 玩家跨服聊天屏蔽
* 国际化支持，根据玩家的所属地切换对其输出的语言。
* 支持服务端对玩家聊天
* 支持玩家屏蔽其他玩家跨服发言 & 支持跨服屏蔽违禁词语
* 玩家加入/离开子服务器消息广播 & 玩家加入/离开代理服务器消息广播
* 支持用户自定义TabList玩家列表的自定义显示
* 自动更新和下载插件新版本
* 支持定时广播
* 支持分区聊天
* 数据库存储玩家数据

> [!NOTE]
> See version changes:
> 
> Change Log: [简体中文](https://github.com/Danburen/VeloChatX/tree/main/docs/zh/changelog) / [English](https://github.com/Danburen/VeloChatX/tree/main/docs/en/changelog)
> 
> To Upgrade : [简体中文](https://github.com/Danburen/VeloChatX/tree/main/docs/en/upgrade) / [English](https://github.com/Danburen/VeloChatX/tree/main/docs/zh/upgrade)

### 👉 如何使用本插件
* 从 [Release](https://github.com/Danburen/VeloChatX/releases) 中下载最新的VeloChatX发行版
* 把t ```VeloChatX``` 文件放到你的 ```Velocity``` 插件文件夹下
* **启动** 服务器，插件会自动生成配置文件
* **关闭** 服务器并按照需求配置配置文件
* **重启** 重启服务器.

### 🧱 如何构建
* Java 17 or newer.
* [JitPack](https://www.jitpack.io/#Danburen/WaterAPI)
```Gradle:
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenCentral()
		maven { url 'https://www.jitpack.io' }
	}
}
```
dependency:
```Gradle:
dependencies {
	implementation 'com.github.Danburen:WaterAPI:-SHAPSHOT'
}
```
### 📊 统计数据
![Bstats](https://bstats.org/signatures/velocity/VeloChatX.svg)

### ❤️ 支持我们
[![](https://pic1.afdiancdn.com/static/img/welcome/button-sponsorme.png)](https://afdian.com/a/WaterWood/plan)
