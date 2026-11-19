# CHANGELOG-V-2.0.0

## [2.0.0] - [2025.3.1]
### Features
- Added **channel** functionality for managing cross-server chat groups
  1. Servers can be assigned to different channels based on categories
  2. Channels can be configured for chat interoperability or isolation
- Added database support for saving player information to the database
- Added **scheduled broadcast** feature to send broadcasts to specific servers at set intervals
- Added welcome message broadcast when a player enters a proxy server
- Player cross-server chat block will now be permanently saved
- Updated automatic message fetching and reading

### Fixed
- Simplified some methods to improve reading efficiency
- Removed and simplified unnecessary methods
- Improved the fault tolerance of configuration files

### Changes
- Broadcast functionality for player joining and leaving servers has been moved to `broadcast.yml`
- Proxy display settings will now be independent of other servers

## [2.0.1] - [2025.4.5]
### Fixed
- Fix Global broadcast util when global broadcast enable which throw a null pointer exception
- Fix the problem of local broadcast can't be able to read.
- Fix ChangLogs cannot be obtained normally

### Changes
- Adjusted the structure of the log

## [2.0.2] - [2025.4.21]
### Fixed
- Fix player frequent join/left throw out an error of tab list uuid repeat error

### Features
- add new version download file check

## [2.0.3] - [2025.5.11]
### Features
- Add switches for broadcasting and cross server chat
### Changes
- Removed the  switch in the broadcast
- Add switch for cross server chat and broadcast in config. yml
- The activation/deactivation of broadcasting now will affect the player inbound and outbound broadcasting

## [2.0.4] - [2025.6.24]
### Features
- Added placeholder for channel player counts on the specified channel {xxx_channel_online}
- Added placeholder for player counts on the specified server {xxx_server_online}
### Changes
- Used the latest LP API and Velocity API
- when using placeholders in English, they are not forcibly converted to lowercase

## [2.0.5] - [2025.10.26]
### Fixed
- Fixed the null pointer exception caused by missing key value pairs when the player's message is set to global.
- Fixed the null pointer exception problem when the server cannot be recognized
### Changes
- Now the default is to enable full-service chat.

## [2.0.6] -[2025.10.27]
### Fixed
- Fixed some null pointer problems that caused internal read disorder in the plug-in
- Fixed the problem that the default value was incorrectly configured due to the missing configuration.

## [2.0.7] - [2026.3.21]
### Changes
- We downgrade the java jdk compilation version from 22 to 17

## [2.0.8] - [2026.4.15]
### Fixed
- Fixed the problem that the player's message forwarding is not escaped for some clients.
### Features
- Users can choose different conversion models by changing the 'chat-format-model' in the configuration file.

## [2.1.0] - [2026.11.19]
> Full rewrite: the kernel is now the standalone WaterAPI module; config, messages, commands and the tab list were rewritten. The config schema is now `config-version: 2.1.0`.
### Refactor
- Re-layered by domain (chat / channel / broadcast / player / tablist / server / shared) wired by ServiceFactory
- Package renamed from `me.waterwood.velochatx` to `org.nitrowater.velochatx`
- Added the standalone WaterAPI kernel (config, messages, command tree, placeholders, logging, update checks, DB migrations)
- Added 50+ unit test suites

### Features
- Config system: `config-version` validation, `/velochatx config diff|update|migration|reload`, missing-key insertion (comments kept, `bak/` backup), legacy config migration with `--dry-run`
- Server bindings: `server.list.<id>` (display name + channels), legacy keys accepted and migrated, unregistered servers marked PHANTOM
- Hierarchical entry broadcasts: `entry-broadcast` at proxy > channel > backend with per-level scope, messages and console logging
- Chat format models: `chat-format-model` supports none / legacy / mini-message
- Tab list rewrite: queue coalescing, dynamic interval (`interval * (1 + online/20 * refresh-ratio)`), proxy-wide entries kept across server switches, immediate header/footer refresh on join/leave
- Unified command tree and permissions: per-subcommand nodes (`velochatx.player.<cmd>` / `velochatx.admin.<cmd>`), permission-filtered tab completion
- New `/velochatx status`, `/velochatx placeholder <text>`, `/velochatx show-channel-info`
- Layered localization (message / system-message / default) with `player-locale: global|locale`
- Player persistence: H2 database with automatic V001/V002 migrations
- Update checker and colored console logging

### Fixed
- LuckPerms `&` color codes in prefixes/suffixes now apply in chat, tab list and broadcasts
- Fixed swapped `getMessage(key, lang)` parameters that returned `MESSAGE_MISSING`
- Fixed the player locale cached as empty at login (Chinese clients saw English)
- Fixed the ban-word console log `PLUGIN_MESSAGE_MISSING` and empty `{Player}`
- Fixed tab entries lost after a server switch and stale entries after a proxy leave
- Fixed `{total_online}` and the tab header/footer staying stale after a player left
- Fixed `config migration` not hinting `config update` when only keys were missing
- Fixed `/velochatx placeholder` resolving only a single argument

### Changes
- Channel membership now lives in `server.list.<id>.channels`; `channels.channel-list.<name>.servers` is legacy
- Player entry broadcasts moved to `entry-broadcast` in `broadcast.yml`
- `locale/*.properties` replaced by `system-message/*.yml` and `message/*.yml`

### Upgrade Notes
1. Back up `plugins/VeloChatX/`
2. Start once and run `/velochatx config migration`
3. Run `/velochatx config update`, then `/velochatx config reload`
4. Move custom strings from `locale/*.properties` to `system-message/<lang>.yml`

### Known Issues
- `chat-format-model: none` can render oddly on some Fabric clients; prefer `legacy`
- MiniMessage mode depends on client tag support