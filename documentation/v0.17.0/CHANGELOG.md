# 0.17.0 Changelog

[← Back to Documentation Index](../README.md)

---

## 📦 Requires

- **Keystone 1.15.3** (0.16.1 shipped on 1.15.1; 1.15.2 adds WAL and busy timeouts to Keystone's SQLite pools, which fixes SQLITE_BUSY on boot; a player whose data still cannot load is kicked to rejoin instead of being saved over; 1.15.3 also flushes the database journal on a clean stop, so a stopped server's .db file is complete). Gangland refuses to start on an older Keystone.
- **Back up the database before the first boot.** 1.15.2 converts the SQLite database to WAL. Stop the server, back up `.db`, `.db-wal` and `.db-shm` together (expect the two extra files while the server runs). WAL needs a local filesystem, not SMB or NFS. See [the 0.17.0 migration notes](../migration-0.17.0.md).
- **Update the core jar and the gang, turf and mail module jars together.** Mixing versions is not supported; mail uses gang's new `GangJoinEvent`.
- **Bartizan 0.6.0** is the version Gangland builds against. Only cops-n-crooks needs Bartizan (`Plugins: [Bartizan]`); without it cops-n-crooks is skipped. Civilians, gadget and turf load without it, and weapon item references resolve to nothing.
- **Menus refresh on the first boot.** Shipped `inventory/*.yml` menus with no `Config_Version` are replaced from the jar. Your old copy is kept as `inventory/<name>-old.yml`; copy your changes back by hand. Your own menus are not touched.
- **Read [the 0.17.0 migration notes](../migration-0.17.0.md) before you start the server.** Every existing gang becomes invite-only, and a few one-time migrations run on the first boot.

## ✨ New

- **Turf map** — `/glw map` prints a chat grid of the turfs around you, with each gang's glyph and a legend. `/glw map text` gives one clickable line per turf (also the Bedrock form), `/glw map legend` the key, and `/glw map auto` re-sends the map when it changes. Auto is off by default.
- **Turf HUD and placeholders** — The turf you stand in shows one action-bar line. `/glw map hud on` adds the nearest turf. Six `turf-` placeholders feed scoreboards. The presence boss bar is gone.
- **One reason everywhere** — The map, HUD, turf card and enter message give the same reason: `attackable`, `shielded: ...`, `under attack by X, N%`, or `join a gang to capture`.
- **Owners-online capture window** — A turf can be attacked while one of its owners is online, and for ten minutes after the last one leaves. After an owner logs in, the turf is shielded for a two-minute wake-up. Only members who joined their gang 24 hours ago keep its turfs open.
- **One capture bar** — Every capture uses one bar. The bigger crew inside starts it, attackers count after 15 seconds inside, and guards hold the bar. A capture that stops moving ends as stalled after 10 minutes. The bar shows attackers, defenders and time left.
- **Alerts anywhere** — Owners hear when a turf is attacked (with the attacker count and side), when half their guards are down, at the 75% last stand, and when a capture is held, lost, taken or stalled. Allies hear the start.
- **Income and gang levels** — A turf pays only while its gang had a member online in the last 24 hours. Payouts give an action-bar line. Turf income grants gang XP, and gang levels raise the turf cap. `/glw turf` shows your cap, how many turfs pay, and the last three events.
- **Quartermaster prices** — Guards cost `Garrison.Cost_Per_Guard` (1000), times any `garrison_discount` boost. A turf holds up to 12 guards in reserve. Every purchase re-checks membership, rank and ownership. Allies can look but only the owning gang buys.
- **Gang bounty** — `/glw gang bounty <gang> <amount>` puts money from your balance on another gang. The gang that captures one of its turfs collects the bounty.
- **Gang chat, join and recruiting** — `/glw gang chat <message>` (or `/glw gang chat` to toggle) talks to your gang. `/glw gang join <gang>` joins an open gang. `/glw gang recruiting open|invite` sets who may join.
- **Gang progression** — Gangs earn XP from turf income and level up to 20. Disbanding with no contributions splits the bank equally.
- **Turf rules page** — `/glw turf rules` prints the server's live turf numbers in plain sentences.
- **Gang onboarding and lists** — `/glw gang` with no gang shows the onboarding screen. `/glw gang list [page]` lists every gang and `/glw gang info [name|id]` shows a public card (never the bank balance).
- **Gang decline and offline invites** — `/glw gang decline [gang]` declines an invite. Invites to offline players expire after 72 hours (`Invite_Offline_Expiry_Hours`).
- **Turf admin** — One permission, `gangland.turf.admin`, covers every turf admin verb. `/glw turf rename <name>` is new, and `/glw turf list` is sorted by distance and paged. New turfs snap to whole chunks, and each side must be 16 to 512 blocks.
- **Fresh-install ranks** — A new server gets the `member`, `officer` and `owner` ranks with preset permissions. Existing rank trees are not changed.
- **Offline cache kept after autosave** — Offline players stay in memory after an autosave, so a gang disband pays them and saves their share. An entry nobody has touched for `Database.Auto_Save.Offline_Cache_Minutes` (default 30, minimum 1) is dropped after a save.

## 🔧 Changed

- **gangland-api 2.4** — adds `Messages.USER_DATA_LOAD_FAILED`; modules built for 2.0-2.3 load unchanged.
- **Invite-only by default (D7)** — Every existing gang is invite-only after the upgrade. Run `/glw gang recruiting open` to let players join with `/glw gang join`.
- **Contribution reset (D15)** — Stored gang contributions reset to 0 once, on the first 0.17.0 boot. Contributions used to mix bank share and turf points.
- **Turf contribution removed** — The old turf contribution feature is gone.
- **Capture window (D1)** — Turfs can be attacked while their owners are online. Set `Capture.Window: OWNERS_OFFLINE` in `turf/turf_rules.yml` for the old rule.
- **Income defaults (D9)** — New turfs pay `Default_Income_Amount` (250) every `Income_Interval_Minutes` (10). Existing turfs keep their own amount; change one with `/glw turf income <amount>`. The multiplier is capped at 2.0 (`Income.Max_Multiplier`, `turf/turf_rules.yml`), and the gang's turf cap applies.
- **Inactivity release (D1)** — Turfs of gangs with no activity for `Capture.Inactivity_Auto_Release_Days` (default 4) are released. Server downtime counts.
- **Menus follow the plugin version** — From 0.17.0 on, shipped menus are refreshed on every version change, like `settings.yml`.
- **Level-up timing for developers** — `LevelUpEvent` fires after the level is raised. A cancel undoes that level and stops. Listeners read the level reached with `getLevel().getLevelValue()`. No XP is collected at the max level.
- **Gang command names** — `/glw gang name` is now `/glw gang rename`. `/glw gang list` lists all gangs; use `/glw gang members` for your own gang.
- **Player permissions** — `gangland.command.main` and the player verbs default to true. Owner-only gang verbs stay OP for now.
- **Turf admin errors** — Non-admins get `Only turf admins can do that.` for admin verbs they hold the node for.
- **Quartermaster wording** — `Quartermaster.Reserve_Full` now reads "already has %max% guards (stock and on duty)". Old files keep the old text until edited.

## 🐛 Fixed

- **Migrations** — A failed database read no longer marks the one-time migrations as done. `gang_migration.done_at` is the real run time.
- **Turf-upgrade grant** — The first 0.17.0 boot grants `gangland.turf.upgrade` once to every rank holding `gangland.gang.withdraw`. Removing it later sticks.
- **Level-ups** — Level-up lines name the level reached. Cancelled level-ups no longer eat XP.
- **Gang bounty** — Refused for your own gang even on a stale lookup. A claim that would push the capturing gang's bank over `Account.Maximum_Balance` does not happen; the bounty stays and the gang gets a `Bounty.Bank_Full` line.
- **Gang join** — `/glw gang join` cancels the joiner's other pending invites, like `/glw gang accept`.
- **Gang chat** — A player who left his gang while in gang chat mode has his next line sent to public chat.
- **Captures** — Stepping out of a contested turf and back keeps the contest. It ends abandoned only after the attacking gang has had nobody inside for `Capture.Abandon_Grace_Seconds` (default 15).
- **Guards** — Only guards that die count as down. A `/glw reload` extends guards already deployed. A turf that changes owner loses its garrison stock and the old owner's guards are recalled.
- **Repeated lines** — Held, failed and stalled lines and the digest are quiet for 15 minutes while the same outcome repeats.
- **New owner** — A turf that changes hands reads `attackable` at once while one of its members is online.
- **Quartermaster** — `garrison_discount` is clamped to 10 to 100% of the guard price, with a minimum price of 1.
- **Database** — A row with a bad uuid is skipped with a warning instead of stopping the load. Waypoint renumbering works with MySQL `BIGINT` ids.
- **Turf ownership** — Gang disband frees its turfs and cancels any capture. Admin `setowner` and `delete` cancel a running capture. Turf ids are never reused, including across restarts.
- **Alliances and invites** — Alliances count for both gangs. Accepting an invite cancels the other invites. Gang transfer and leave confirmations lapse after 30 seconds.
- **Numeric columns** — Numeric database columns read through `Number`, so a MySQL server returning a wider type or NULL no longer fails the load.
- **Menus** — The gang info menu opens only for players in a gang (`Open.Condition`). Gangless players get the onboarding screen.

---

[← Back to Documentation Index](../README.md)
