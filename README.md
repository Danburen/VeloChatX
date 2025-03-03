# VeloChatX 
![icon_middle_2](https://github.com/user-attachments/assets/2ce8ffa3-19b9-4905-aa50-5bd1a4386ad3)
## [zh](https://github.com/Danburen/VeloChatX/tree/main/docs/zh)
### 🧐 Introduction
  a plugin that Velocity server distribute players' messages to its sub servers.
  It can also automatically broadcasting message from player and timed broadcast
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
### 📊 Stats
![Bstats](https://bstats.org/signatures/velocity/VeloChatX.svg)

### ❤️ Support Us
[![](https://pic1.afdiancdn.com/static/img/welcome/button-sponsorme.png)](https://afdian.com/a/WaterWood/plan)
