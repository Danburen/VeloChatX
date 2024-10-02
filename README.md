# VeloChatX
### Introduction
  a plugin that Velocity server distribute players' messages to its sub servers.
### plugin features
* When player join/leave server,broadcast the join/leave message to previous server and the present server.
* Automatically parse chat color logging out in proxy server.
* Support message customization
* Use [luckperms](https://github.com/LuckPerms/LuckPerms/tree/master?tab=readme-ov-file) to get player data.
* i18n support, support language en_US, zh_CN
* auto fix the language for the player join the game

### Programe build
  USE MY OWN API [WaterAPI](https://github.com/Danburen/WaterAPI/)
  USE JITPACK
### Build
***Requirement*** 
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
### Stats
![Bstats](https://bstats.org/signatures/velocity/VeloChatX.svg)

