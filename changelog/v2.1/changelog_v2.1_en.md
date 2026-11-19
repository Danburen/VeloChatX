VeloChatX 2.1.0 Changelog
Release date: 2026.11.19

A full rewrite: the kernel is now the standalone WaterAPI module, and the config, message, command and tab-list subsystems were rewritten.
The config schema is now config-version: 2.1.0 - please read the Upgrade Notes at the bottom before updating.

[Refactor]
- Re-layered the project by domain: chat / channel / broadcast / player / tablist / server / shared; services are wired by ServiceFactory and the old singleton Manager classes are gone
- Package renamed from me.waterwood.velochatx to org.nitrowater.velochatx
- Added the standalone WaterAPI kernel module: config, messages, command tree, placeholders, logging, update checks and database migrations
- Platform capabilities are behind SPI interfaces (PlayerActionService / TabListProvider / SchedulerService / PermissionService ...), leaving room for Bukkit / BungeeCord adapters
- Added 50+ unit test suites covering chat, channels, broadcasts, tab list, config migration and command completion

[Features]
- Config system
  1. Every file carries config-version; versions and structure are validated on startup, with hints for missing keys, outdated versions and pending migrations
  2. New /velochatx config diff | update | migration | reload
  3. config update only inserts missing keys, keeps comments and existing values, and keeps a rolling backup in bak/
  4. config migration migrates legacy config (server bindings, player entry broadcasts) and supports --dry-run
- Server bindings: new server.list.<id> structure with display name and channels; legacy keys are accepted and migrated; servers missing from Velocity are reported as WARNING and marked PHANTOM in /velochatx status
- Hierarchical entry broadcasts: new entry-broadcast fires at proxy > channel > backend levels with per-level enable / scope / join / leave messages, an overlay mode (hierarchical-overlay) and console logging
- Chat format models: chat-format-model supports none / legacy / mini-message
- Tab list rewrite
  1. Producer-consumer queue with per-player task coalescing to cut packet volume under frequent events
  2. Dynamic interval: interval * (1 + online / 20 * refresh-ratio); interval: 0 runs event-driven
  3. Proxy-wide entries are kept across backend switches and only removed when a player leaves the proxy; header/footer ({online} / {total_online} / {ping}) refreshes immediately on join/leave
  4. Entry format, header and footer all accept the full placeholder set
- Unified command tree and permissions: one node per subcommand (velochatx.player.<cmd> / velochatx.admin.<cmd>, still accepting velochatx.player / velochatx.admin); tab completion is permission-filtered
- Admin commands: /velochatx status, /velochatx placeholder <text> for debugging placeholders, /velochatx show-channel-info
- Localization: messages split into message/ (player-facing), system-message/ (system / console) and default/ (kernel) with language-chain fallback; player-locale: global | locale selects server or per-client language
- Player persistence: H2 database with versioned migrations (V001/V002) storing chat toggle, block list and last leave time; schema migrates automatically
- Update checker: GitHub Release lookup, auto download and release notes, plus config version validation
- Console logging: log-text.enable toggles output and log-text.convert renders colors as terminal ANSI; debug prints detailed flow

[Fixed]
- LuckPerms prefixes/suffixes using & color codes are now converted in chat, tab list and broadcasts
- Fixed the swapped parameters of MessageService.getMessage(key, lang) that returned MESSAGE_MISSING for explicit locales
- Fixed the player locale being cached as empty at login, which kept Chinese clients on English messages
- Fixed the ban-word console log reading the wrong locale file (PLUGIN_MESSAGE_MISSING) and rendering an empty {Player}
- Fixed the tab list losing a player's entry in other players' lists after a server switch, and stale entries after a proxy leave
- Fixed {total_online} and the tab header/footer staying stale for a long time after a player left
- Fixed /velochatx config migration not hinting config update when only keys were missing
- Fixed /velochatx placeholder only resolving a single argument; it now accepts a whole template
- Fixed legacy issues such as channels.global: false not applying and null pointers when a server could not be resolved

[Changes]
- Channel membership now lives in server.list.<id>.channels; channels.channel-list.<name>.servers is legacy and removed by config migration
- Player entry broadcasts moved from config.yml to entry-broadcast in broadcast.yml
- locale/*.properties is replaced by system-message/*.yml and message/*.yml
- Permission nodes are per-subcommand; the old group nodes still work
- Minimum runtime stays Java 17 (Velocity 3.x)

[Upgrade Notes]
1. Back up the whole plugins/VeloChatX/ directory
2. Replace the jar and start once, then run /velochatx config migration
3. Run /velochatx config update to add new keys, then /velochatx config reload
4. Move custom strings from the old locale/*.properties into system-message/<lang>.yml
5. The player database migrates automatically on startup

[Known Issues]
- chat-format-model: none can render oddly on some Fabric clients; prefer legacy
- MiniMessage mode depends on tag support and may not render fully on very old clients
