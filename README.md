# DeloChatX
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
  use my own [api](https://github.com/Danburen/DeloChatX/tree/main/src/main/java/me/waterwood) to adapted to Velocity
  since Velocity still in the developing.
  
  Using Snake Yaml to load config files.
  Using properties load plugin message file.
  
  see more in [me.waterwood.*](https://github.com/Danburen/DeloChatX/tree/main/src/main/java/me/waterwood)

### Build
* Java 17 JDK or newer.

### Stats
![Bstats](https://bstats.org/signatures/velocity/VeloChatX.svg)
