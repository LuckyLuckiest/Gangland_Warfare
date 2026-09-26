# Plaque system map — 2026-09-26 (added as the docket's fifth project, test-server wave 1)

| Code | Slug | Name | Modules / packages | Files | Hubs (god nodes / entry points) | Risk hotspots |
|---|---|---|---|---|---|---|
| BD | board | Scoreboard rendering | org/luckyraven/plaque/board/**, board/configuration/**, board/driver/**, board/part/**, listener/**, placeholder/** | 11 | Board, BoardManager, BoardAddon, DriverHandler, DriverV3, PapiText | FastBoard driver line resolution, placeholder-provider timing, config parsing of Board.Title/Board.Rows, join/quit listener lifecycle |
| BS | bootstrap | Plugin bootstrap & config | Plaque.java, bootstrap/**, config/**, command/ReloadCommand.java | 6 | Plaque (JavaPlugin.onEnable/onDisable), PlaqueContext, KernelConfig, BoardConfig | soft-dependency detection (PlaceholderAPI, ViaVersion), bean wiring order, reload command |
