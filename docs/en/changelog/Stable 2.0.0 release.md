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