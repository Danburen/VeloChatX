# VeloChatX [Docs](https://danburen.github.io/VeloChatX-Docs/)
<img src="https://danburen.github.io/VeloChatX-Docs/favicon.svg" width="480" height="480" alt="VeloChatX">

![GitHub Release](https://img.shields.io/github/v/release/Danburen/VeloChatX?label=Latest%20Release)
![GitHub License](https://img.shields.io/github/license/danburen/VeloChatX?label=License)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/danburen/VeloChatX/total)
![bStats Servers](https://img.shields.io/bstats/servers/23273)

## [zh](https://github.com/Danburen/VeloChatX/tree/main/docs/zh)
### 🧐 Introduction
VeloChatX is a modern chat solution designed for Minecraft Velocity servers, focused on delivering fast, clean, and reliable real-time communication across your proxy network.
  
It is built with a lightweight and extensible architecture, making it easy to maintain, customize, and scale as your server community grows.
  
Whether you want a solid out-of-the-box chat experience or a flexible foundation for advanced features, VeloChatX is designed to support both.
### 🤠 plugin features
* When player join/leave server,broadcast the join/leave message to previous server and the present server.
* Automatically parse chat color logging out in proxy server.
* Support message customization
* Use [luckperms](https://github.com/LuckPerms/LuckPerms/tree/master?tab=readme-ov-file) to get player data.
* i18n support, support language en_US, zh_CN
* auto fix the language for the player join the game
* auto update
* ban words filter
* timed broadcast support
* persist data with database
* tab list replace
* flexable inner server placeholder

> [!NOTE]
> See version changes:
> 
> 📖 Change Log: [简体中文](https://github.com/Danburen/VeloChatX/tree/main/docs/zh/changelog) / [English](https://github.com/Danburen/VeloChatX/tree/main/docs/en/changelog)
> 
> 🔧 To Upgrade : [简体中文](https://github.com/Danburen/VeloChatX/tree/main/docs/en/upgrade) / [English](https://github.com/Danburen/VeloChatX/tree/main/docs/zh/upgrade)

### 👉 Getting Start
* Download the plugin from latest [Release](https://github.com/Danburen/VeloChatX/releases)
* Put ```VeloChatX``` file to your ```Velocity``` plugin file
* **Start** the server and the plugin will generate config files for you
* **Stop** the server and configurate the configs file
* **Restart** the server and enjoy.

### 🧱 How to Build
1. clone the repository from remote
```bash:
git clone https://github.com/Danburen/VeloChatX
```
2. add dependency of WaterAPI with jitpack
```Gradle:
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenCentral()
		maven { url 'https://www.jitpack.io' }
	}
}
```

```Gradle:
dependencies {
	implementation 'com.github.Danburen:WaterAPI:-SHAPSHOT'
}
```
### 📊 Stats
![Bstats](https://bstats.org/signatures/velocity/VeloChatX.svg)

### ❤️ Support Us
[![](https://pic1.afdiancdn.com/static/img/welcome/button-sponsorme.png)](https://afdian.com/a/WaterWood/plan)
