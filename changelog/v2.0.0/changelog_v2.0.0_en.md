## Changelog - v2.0.0
### New Features
- Added **channel** functionality for managing cross-server chat groups
    1. Servers can be assigned to different channels based on categories
    2. Channels can be configured for chat interoperability or isolation
- Added database support for saving player information to the database
- Added **scheduled broadcast** feature to send broadcasts to specific servers at set intervals
- Added welcome message broadcast when a player enters a proxy server
- Player cross-server chat block will now be permanently saved
- Updated automatic message fetching and reading

### Bug Fixes
- Simplified some methods to improve reading efficiency
- Removed and simplified unnecessary methods
- Improved the fault tolerance of configuration files

### Changes
- Broadcast functionality for player joining and leaving servers has been moved to `broadcast.yml`
- Proxy display settings will now be independent of other servers

### Known Issues
- None