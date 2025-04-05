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
- fix Global broadcast util when global broadcast enable which throw a null pointer exception
- fix the problem of local broadcast can't be able to read.