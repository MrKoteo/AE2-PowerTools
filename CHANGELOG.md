# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html

## [1.1.0] - 2026-01-17
### Added
Network Health Scanner:
- Detects channel chokepoints where demand exceeds cable capacity
- Identifies devices missing channels (requiring but not receiving a channel)
- Per-direction breakdown showing channel flow at intersections
- New tabs in the scanner GUI for chokepoints and missing channel devices


## [1.0.0] - 2026-01-16
### Added
Network Health Scanner:
- Detects cable loops in AE2 networks
- Identifies network components in non-chunkloaded areas
- Visual overlay with directional arrows
- Interactive GUI with dimension-grouped results
- Tabs for loops vs non-chunkloaded areas

Priority Tuner:
- Apply stored priority to multiple blocks
- Visual highlight feedback on application
- Automatic application on blocks placed when in off-hand
