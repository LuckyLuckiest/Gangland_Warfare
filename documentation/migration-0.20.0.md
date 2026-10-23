# Migrating a server to Gangland 0.20.0 (Street crime, waves 1 and 2)

[← Back to Documentation Index](./README.md)

Gangland 0.20.0 needs Keystone 1.15.3 (the `keystone.version` in the root `pom.xml`) and Bartizan 0.6.0 (`bartizan.version`).
Citizens 2.0.41 or newer stays soft: without it the cop, witness, overwatch, siren and tracker features are inert.
`gangland-api` is at `2.7` (additive since 0.19.0). Four runtime modules declare `Host_Api: 2.7`: `cops-n-crooks`,
`gangland-gadget`, `gangland-npc-shops` and `gangland-lootchest`. A 2.7 module refuses to load on a 0.19.x core
(`module.host.incompatible`), so update the core and all four module jars together. Upgrading from 0.19.x: nothing else is
needed beyond the steps below. Read [`migration-0.19.0.md`](./migration-0.19.0.md) first if you skipped it.

Every new key has a code default equal to its shipped value. Existing YAML files are not rewritten, so a key that is missing
from your file uses that default. To change a value, copy the key into your file.

## Part W1 - Street crime I

### 1. Hand edits on existing servers

- **Darkness on SWAT and Military.** The shipped `copsncrooks/cops.yml` sets `Ignore_Darkness: true` on the SWAT and Military
  tiers. A key missing from your file reads as `false`, so add `Ignore_Darkness: true` to those two tiers by hand if you
  want them to ignore the light factor (see `Cops.Sight` below).
- **Radio lines.** The new radio lines (`Overwatch_Spotted`, `Tracker_Tagged`, `Tracker_Ping`, `Caller_911`,
  `Suspect_Description`, the patrol lines) are in `copsncrooks/cop_radio_messages.yml` and `cop_radio_messages_es.yml`. A
  server with an old copy of those files falls back to the code defaults, so the new lines still speak. To see and edit them
  in the file, regenerate the two files (move the old copies aside first; known as T-138).
- **Wanted messages.** `copsncrooks/wanted_messages.yml` gains the `Patrol.*`, `Suspect.*`, `Tracker.*` and `Trespass` text. Same rule: missing
  keys use the default.
- **Restricted areas.** Trespass needs an admin region tagged `restricted`. Run `/glw cop setup mode restricted`, take the
  setup wand (`/glw cop setup wand`), mark the two corners (left click pos1, right click pos2) and run `save <name>`. No
  region, no trespass crime. See [`features/cops-n-crooks.md`](./features/cops-n-crooks.md#the-setup-wand).
- **Scanner and map items.** Admins give them with `/glw cop item police_scanner [player]` and `/glw cop item scanner_map [player]`.
  To sell them, add `copsncrooks:police_scanner` or `copsncrooks:scanner_map` to a trader or shop item list.

### 2. New keys

Defaults are the shipped values. "Missing keys use the defaults" applies to every row.

**Gadget** (`gadget/gadget_settings.yml`, Bartizan-owned gadget module)

| Key | Default | Meaning |
|---|---|---|
| `Hotwire.Enable` | `true` | `false` turns hotwiring off; only owners and gang mates can mount. |
| `Hotwire.Seconds` | `6` | Seconds the hotwire takes. |
| `Hotwire.Max_Distance` | `3.0` | Blocks from the car you can start from. |
| `Hotwire.Consume_Lockpick` | `true` | Use up the lockpick when the hotwire succeeds. |
| `Hotwire.Lockpick_Item` | `lockpick` | Unique item id that opens a car you may not use. |
| `Car_Events.Move_Interval_Ticks` | `4` | Ticks between `CarMoveEvent` throttle steps. |

**Trader** (`npc/trader_settings.yml`, npc-shops module)

| Key | Default | Meaning |
|---|---|---|
| `Hold_Up.Enable` | `true` | `false` turns store hold-ups off for every trader. |
| `Hold_Up.Range` | `6.0` | Blocks within which an aimed weapon starts a hold-up. |
| `Hold_Up.Aim_Angle` | `10.0` | Degrees off the trader's eye line that still counts as aiming. |
| `Hold_Up.Fill_Seconds` | `10` | Seconds the clerk needs to fill the bag before the take is paid. |
| `Hold_Up.Alarm_Seconds` | `10` | Second into the fill when the silent alarm trips (`Store_Robbery`). |
| `Hold_Up.Dislike_Mood` | `0.2` | Mood at or below which the alarm trips early. |
| `Hold_Up.Alarm_Seconds_Disliked` | `4` | Alarm second used instead when the trader dislikes you. |
| `Hold_Up.Take_Min` | `200.0` | Smallest take. |
| `Hold_Up.Take_Max` | `600.0` | Largest take. |
| `Hold_Up.Cooldown_Seconds` | `300` | A robbed trader refuses hold-ups for this long. |

**Lootchest** (`lootchests/loot_chest_settings.yml`)

| Key | Default | Meaning |
|---|---|---|
| `Cracking_Alarm.Enable` | `true` | Cracking a chest of a listed tier rings the alarm and commits `Safe_Cracking` (inert in 0.20.0: cracking is not enabled yet, LS-02). |
| `Cracking_Alarm.Tiers` | `[police_armory, military_vault]` | Loot table ids that ring the alarm. |
| `Cracking_Alarm.Sound` | `BLOCK_BELL_USE` | Sound played at the chest. |

**Civilians** (`npc/civilians.yml`, civilians module)

| Key | Default | Meaning |
|---|---|---|
| `Witness.Enable` | `true` | Civilians call in crimes they see. |
| `Witness.Range` | `24.0` | Blocks a civilian must be within to see a crime. |
| `Witness.Call_Seconds` | `8` | Seconds a 911 call lasts before it lands. |
| `Witness.Caller_Glow` | `true` | The caller glows while calling. |
| `Witness.Scare_Range` | `16.0` | A weapon aimed at a caller within this range stops the call. |
| `Witness.Scare_Angle` | `12.0` | Degrees off the caller's eye line that counts as aiming. |
| `Witness.Report_Assault` | `true` | Hitting a civilian publishes `Assault_Civilian`. |
| `Witness.Assault_Repeat_Seconds` | `10` | Minimum gap between `Assault_Civilian` events for one attacker and civilian. |
| `Witness_Calling` (`civilian_messages.yml`) | `&cA witness is calling 911! Stop them!` | Text shown to the caller. |

**Cops** (`copsncrooks/wanted.yml`, `copsncrooks/cops.yml`, cops-n-crooks module)

| File and key | Default | Meaning |
|---|---|---|
| `wanted.yml` `Wanted.Trespass.Enable` | `true` | Walking into a `restricted` region commits `Trespass_Restricted`. |
| `wanted.yml` `Wanted.Trespass.Repeat_Seconds` | `60` | Gap before the same trespass counts again. |
| `wanted.yml` `Wanted.Heat.Crimes.Evading_Police` | `100` | Heat a patrol-reported evasion adds. |
| `wanted.yml` `Wanted.Witnesses.Enable` | `true` | Car theft and assault on a civilian wait for a witness or a cop. |
| `wanted.yml` `Wanted.Witnesses.Crimes` | `[Car_Theft, Assault_Civilian]` | The crimes that are gated. Other crimes count at once, as before. |
| `cops.yml` `Cops.Disguise.Enable` | `true` | Descriptions, masks and outfit rules. |
| `cops.yml` `Cops.Disguise.Mask_Items` | `[CARVED_PUMPKIN, PLAYER_HEAD, ZOMBIE_HEAD, SKELETON_SKULL, CREEPER_HEAD]` | Head items that hide you. |
| `cops.yml` `Cops.Disguise.Mask_Wearables` | `[]` | Bartizan wearable keys that hide you. |
| `cops.yml` `Cops.Disguise.Police_Outfit` | `[police_helmet, police_vest]` | Wearables that make up the police outfit. |
| `cops.yml` `Cops.Disguise.Outfit_Max_Level` | `2` | Highest star level the outfit still passes at. |
| `cops.yml` `Cops.Disguise.Match_Needed` | `2` | Pieces of head, chest and vehicle that must still match for a cop to keep you (higher = easier to shake). |
| `cops.yml` `Cops.Patrol.Enable` | `true` | Beat cops walk near stations. |
| `cops.yml` `Cops.Patrol.Cops_Per_Station` | `2` | Beat cops per station. |
| `cops.yml` `Cops.Patrol.Activation_Radius` | `64` | Blocks from a station a player must be within before beats spawn. |
| `cops.yml` `Cops.Patrol.Despawn_Radius` | `96` | Beat cops despawn beyond this. |
| `cops.yml` `Cops.Patrol.Despawn_After_Seconds` | `30` | Idle time before a beat cop despawns. |
| `cops.yml` `Cops.Patrol.Beat_Radius` | `24` | Beat walk radius around the station. |
| `cops.yml` `Cops.Patrol.Pause_Seconds_Min` / `_Max` | `3` / `6` | Pause at each beat point. |
| `cops.yml` `Cops.Patrol.Tier` | `1` | Tier of beat cops. |
| `cops.yml` `Cops.Patrol.Report_Wanted` | `true` | Beat cops report wanted players to their squad. |
| `cops.yml` `Cops.Warning.Enable` / `Range` / `Seconds` / `Repeat_Seconds` | `true` / `12.0` / `5` / `30` | "Put that away!" warning for a non-wanted player holding a weapon. |
| `cops.yml` `Cops.Pull_Over.Enable` | `true` | Speeding drivers are pulled over. |
| `cops.yml` `Cops.Pull_Over.Range` / `Speed_Limit` | `24.0` / `12.0` | Range and speed (blocks per second) that trigger it. |
| `cops.yml` `Cops.Pull_Over.Comply_Seconds` / `Stop_Speed` / `Stop_Hold_Seconds` | `6` / `1.0` / `2` | Time to stop, speed that counts as stopped, and how long they stay stopped. |
| `cops.yml` `Cops.Pull_Over.Ticket_Amount` / `Repeat_Seconds` | `150.0` / `120` | Fine and gap before the same driver is pulled over again. |
| `cops.yml` `Cops.Tracker.Enable` | `true` | Tracker tags on vehicles. |
| `cops.yml` `Cops.Tracker.Min_Level` | `2` | Star level that starts tagging. |
| `cops.yml` `Cops.Tracker.Tag_Seconds` / `Tag_Range` | `4` / `48.0` | Seconds of sighting to tag, and the tagging range. |
| `cops.yml` `Cops.Tracker.Ping_Seconds` / `Expire_Seconds` | `6` / `150` | Ping interval and tag lifetime. |
| `cops.yml` `Cops.Scanner.Enable` | `true` | The Police Scanner item works. |
| `cops.yml` `Cops.Scanner.Range` | `160.0` | Blocks within which a squad's radio is heard with the scanner. |
| `cops.yml` `Cops.Scanner.Item.*` | Material `CLOCK`, name `&9Police Scanner` | The scanner item. |
| `cops.yml` `Cops.Siren.Enable` | `true` | Pursuing cops sound a siren during a chase. |
| `cops.yml` `Cops.Siren.Interval_Seconds` / `Min_Distance` | `5` / `24.0` | Siren rate and the closest a pursuing cop may be. |
| `cops.yml` `Cops.Siren.Sound` / `Volume` / `Pitch` | same sound as the 0.15 HUD siren / `3.0` / `1.0` | Siren sound. |
| `cops.yml` `Cops.Sight.Enable` | `true` | Light, crowd, sneak and shot factors on cop sight. |
| `cops.yml` `Cops.Sight.Dark_Max_Level` / `Dark_Factor` | `4` / `0.4` | Light level 0 to 4 and its range factor. |
| `cops.yml` `Cops.Sight.Dim_Max_Level` / `Dim_Factor` | `8` / `0.7` | Light level 5 to 8 and its range factor. |
| `cops.yml` `Cops.Sight.Crowd_Factor` / `Crowd_Min_Civilians` / `Crowd_Radius` | `0.6` / `3` / `4.0` | Crowd cover. |
| `cops.yml` `Cops.Sight.Crowd_Out_Of_Contact_Seconds` | `2` | Seconds out of contact before crowd cover applies. |
| `cops.yml` `Cops.Sight.Sneak_Factor` / `Min_Factor` | `0.7` / `0.25` | Sneaking factor, and the floor for all factors. |
| `cops.yml` `Cops.Sight.Shot_Reset_Seconds` | `5` | Firing, burning or glowing resets range to full for this long. |
| per tier `Ignore_Darkness` | `false` (SWAT and Military bundled `true`) | Tier ignores the light factor. |
| `cops.yml` `Cops.Overwatch.Enable` | `true` | Marksman overwatch perches. |
| `cops.yml` `Cops.Overwatch.Min_Level` / `Role` | `4` / `Marksman` | Star level and squad role that perch. |
| `cops.yml` `Cops.Overwatch.Ring_Min` / `Ring_Max` / `Columns` / `Min_Height` | `10.0` / `20.0` / `8` / `4` | Roof ring around the player. |
| `cops.yml` `Cops.Overwatch.Sight_Range` / `Max_Failures` | `48.0` / `3` | Perch sight range and failed approaches before giving up. |
| `cops.yml` `Cops.Overwatch.Reach_Seconds` / `Max_Seconds` | `20` / `120` | Time to reach the roof and total perch time. |
| `cops.yml` `Cops.Scanner_Map.Enable` | `true` | The Scanner Map item works. |
| `cops.yml` `Cops.Scanner_Map.Blocks_Per_Pixel` / `Refresh_Ticks` | `1` / `10` | Map scale and refresh rate. |
| `cops.yml` `Cops.Scanner_Map.Show_Hideouts` | `true` | Admin hideout regions show on the map. |
| `cops.yml` `Cops.Scanner_Map.Item.*` | name `&9Scanner Map` | The map item. |
| `cops.yml` `Cops.Radio.Cooldown_Ticks.Caller_911` | `100` | Radio gap for 911 calls. |
| `cops.yml` `Cops.Radio.Cooldown_Ticks.Overwatch_Spotted` | `200` | Radio gap for overwatch sightings. Listed in `Radio.Priority`. |

### 3. Behaviour changes to expect

- **Car theft and assault on a civilian need a witness or a cop.** A theft or an assault that no cop saw is not counted at once.
  A civilian within `Witness.Range` who saw it starts a 911 call. If the call lasts `Call_Seconds` it lands and the crime counts.
  Stop the caller in time by hitting them, or aim a weapon at them within `Scare_Range`, and the call is cancelled.
- **Assault on a civilian is published.** It was not published before; it is now gated by the witness rule above.
- **Trespass.** Walking into a `restricted` region commits `Trespass_Restricted` (300 heat). Players with
  `gangland.cop.trespass.bypass` are exempt.
- **Hold-ups and cracking.** Store hold-ups commit `Store_Robbery`; cracking a `police_armory` or `military_vault` chest
  commits `Safe_Cracking`.
- **npc-shops loads without Bartizan.** It compiles against `bartizan-api`, but store hold-ups need Bartizan's weapons and
  stay inert without it. The rest of the module is unchanged.

### 4. Commands and permissions

- `/glw cop item <police_scanner|scanner_map> [player]`. Permission `gangland.cop.item`. The item id has tab completion; the
  player argument completes online names. Console is allowed when a player is named.
- `gangland.cop.trespass.bypass` exempts a player from trespass. It is not declared in `plugin.yml`, so only operators have it
  until you grant it in your permissions plugin.
- `gangland.cars.bypass` is unchanged.

### 5. Known limits (wave 1)

- Stolen-car grants are kept in memory and on the vehicle. They reset on a restart, by design.
- Map hideouts are admin regions only. Turf and gang hideouts are not on the map yet.
- Tracker tags are stored on the vehicle itself, not in a player record.

## Part W2 - Street crime II: the hot-cash bag, fence and crew lookout

### 1. The bag

- Cash you pick up while wanted goes into your **bag**, not your wallet. Wanted is checked at pickup, so the drop from the
  kill that gave your first star goes into the bag too. Not wanted: the wallet, as before.
- The action bar shows `Bag: $X` while the bag holds money (`Wanted.Bag.Hud_Enable`).
- The bag ends with the chase:
  - **Get away** (the evasion clock or the safety-net timer takes the last star): the bag banks into your wallet, up to
    `Max_Banked_Per_Day` per server day. Anything above that converts at `Fence_Cut`.
  - **Any other clear** (sign, contact, handcuff bribe, admin, unknown): the bag converts at `Fence_Cut`. With the default
    `0.35` you get 65 percent.
  - **Busted**: the bag is seized at jail intake. The charge sheet prints `Bag seized: $X`.
  - **Wasted** (death, including going down) or **quitting** while the server runs: the bag spills as money items where you stood.
    If you are handcuffed or in transit at that moment, the bag is seized instead.
  - **Server stop, plugin disable or reload**: the bag is saved and is there on rejoin. Nothing spills.
- A spilled bill is hot: a player who picks it up and is not wanted gains one star and the bill goes into his bag.
- Each chase caps the bag at `Max_Per_Chase`. A full bag refuses the pickup, and the cash stays on the ground. A store take that
  does not fit is not paid.
- Cops drop no cash with the bundled settings (below).

| Key | Default | Meaning |
|---|---|---|
| `wanted.yml` `Wanted.Bag.Enable` | `true` | `false` sends cash to the wallet as before 0.20. |
| `Wanted.Bag.Fence_Cut` | `0.35` | Share the fence keeps. Must be above 0 and below 1; otherwise `0.35` is used and a config line is logged. |
| `Wanted.Bag.Max_Per_Chase` | `5000` | Most cash one chase can bag. `0` = no cap. |
| `Wanted.Bag.Max_Banked_Per_Day` | `15000` | Most bag cash a getaway banks per server day. `0` = no cap. |
| `Wanted.Bag.Spill_Stacks` | `4` | Money items a spilled bag drops (1 to 16). |
| `Wanted.Bag.Spill_Variation` | `medium` | `items/money.yml` variation the spilled bills use. |
| `Wanted.Bag.Hud_Enable` | `true` | Show `Bag: $X` on the action bar. |
| `Wanted.Bag.Hud_Interval_Ticks` | `40` | Action bar refresh. |
| `items/money.yml` `Money.Drop_Sources.COP.Enabled` | `false` | Cops carry no cash. `true` brings cop drops back. |
| `wanted_messages.yml` `Bag.*`, `Fence.*` | shipped text | Messages for the bag and the fence. |
| `Cops.Lookout.Enable` (`cops.yml`) | `true` | A gang mate who is not wanted passes on the police radio he hears. |
| `Lookout_Format` (root key of `cop_radio_messages.yml`) | `&8[Lookout %name%] &r%line%` | Format of a relayed line. The Spanish file uses `&8[Vigía %name%] &r%line%`. |

- **Money file.** `items/money.yml` is not version-replaced. A server that keeps its old `COP: Enabled: true` keeps cop drops.
  To match the new bundled setting, set `Money.Drop_Sources.COP.Enabled` to `false` by hand.
- **Phone contacts.** `inventory/phone_contacts.yml` is version-replaced, so the "Fence the bag" slot (slot 25) appears on upgrade.
- **Lookout radio text.** Regenerate `cop_radio_messages*.yml` to see `Lookout_Format` in the file. The code default works meanwhile.

**Database.** New cops table `hot_cash_bag`, created at first boot by the module's repository scan. No core table changes and no
column on the user table. Run no SQL.

### 2. Fence the bag

- `/glw contact fence` and the "Fence the bag" slot on the phone contacts page sell the bag for wallet cash, minus `Fence_Cut`.
  Stars are not touched, and the bag keeps filling afterwards.
- The gate is the same as the crooked contact: contacts enabled, you are wanted, no cop can see you, and the contacts cooldown is
  free. A successful fence starts the shared contacts cooldown.
- Refused while you are handcuffed, in transit or jailed ("Not while you are in cuffs."). An empty bag says so and starts no
  cooldown.
- Handcuff and jail bribes take from the bag first and charge the wallet only for the rest. The bag is debited only after the
  wallet part succeeds. What is left converts at `Fence_Cut` when the bribe clears your stars.
- Without cops-n-crooks, `/glw contact fence` says contacts are off and nothing else changes.

### 3. Crew lookout

- A gang mate who is not wanted and hears a police line about you passes it on. You get the line once, prefixed
  `[Lookout <name>]`, even when you are out of radio range.
- No relay when the mate is wanted, in no gang or another gang, is the player the squad hunts, or `Cops.Lookout.Enable` is
  `false`. Civilian gang shouts never relay. A line you heard yourself is not relayed again. Your radio gap still applies;
  priority lines bypass it exactly as for direct delivery.
- Without the gang module there is no relay.

### 4. Commands and permissions

- `/glw contact [stars|fence]`. The `fence` argument has tab completion only while cops-n-crooks is loaded. No new
  permission: `/glw contact fence` uses the permission `/glw contact` already uses.

### 5. Known limits (wave 2)

- Spilled bills drop whole money items, so cents are lost on a spill.
- The contacts cooldown is memory-only. A restart clears it.
- Restrained death or quit seizes the bag rather than spilling it.

## Developer notes (both waves)

- `gangland-api` is `VERSION "2.7"`. Wave 1 adds the five vehicle events in `org.luckyraven.gangland.events.vehicle`
  (`CarEnterEvent`, `CarExitEvent`, `CarMoveEvent`, `CarDamageEvent`, `CarTheftAttemptEvent`), six `Crimes` ids
  (`BRANDISH_NEAR_COP`, `CAR_THEFT`, `SAFE_CRACKING`, `STORE_ROBBERY`, `TRESPASS_RESTRICTED`, `EVADING_POLICE`) and
  `RadioVoice.overhears(Player, NpcSquad)` (default `false`). Wave 2 adds `data.economy.HotCash`, `data.economy.HotCashRouter`,
  `RadioVoice.relaysTo(Player, NpcSquad)` (default `false`) and, in the re-exported gangland-item, `MoneyDepositService.depositPickup`
  (default method). Nothing was removed, renamed or re-signed.
- Signatures the 0.21.0 release relies on are unchanged since the 0.20.0 base. They were checked against the 0.20.0 tip:
  `HeatLedger.record(CrimeCommittedEvent)`, `CrimeCommittedEvent(Player, String, Location, boolean, int)`,
  `PerimeterController.isActive(UUID)`, `CopManager.addAiTickHook`, `addCopAttackedHook`, `findCopByEntity`, `groupOf`,
  `CopNpc.holdPost` (inherited from keystone-npc `AbstractNpc`), `CopNpc.getGuardedPlayerId` and `getTierConfig` (Lombok on
  `CopNpc`), `CopRadio.sayFromLeader(group, key[, extra])` and `DetainmentService.isRestrained(Player)`.
- New seams added in 0.20.0 (additive): `CopPerception` (`rangeFor`, `sees`, `acquires`, `pursuitRange`, `addSightFactor`,
  `addLockOnFilter`), `CopManager.addCopResolver`, `CopRadio.setOverhearing`, `setExtraPlaceholders`, `setLookout`, and the
  `CopState` values `PATROL`, `WARNING` and `OVERWATCH`.
- Wave-2 setters, installed from `@Configuration` `@PostConstruct` blocks so the constructors stay as they were:
  `JailIntakeService.setBagSeizure`, `BribeService.setBagFunding(BagFunding)`, `PaperworkView.setBagLine`, `CopRadio.setLookout`.
- Shared YAML is append-only: one `# 0.20.0` block at the end of each parent block. `DEFAULT_LINES` in `CopRadioMessages` is a
  `Map.ofEntries`, so a duplicate key throws at class load.

## Degradation

| Absent | Effect |
|---|---|
| Citizens | No patrols, witnesses (gated crimes count at once), overwatch, sirens, tracker or hold-ups. The map shows no cops. |
| Bartizan | cops-n-crooks is skipped (`Plugins: [Bartizan]`), so there is no heat at all. npc-shops loads with hold-ups inert. |
| cops-n-crooks | Crimes are published with no listener. The bag and fence are off, and cash goes to the wallet. |
| gang module | No lookout relay. |
| npc-shops | No store take or hold-up. The rest is unchanged. |
| WorldGuard | Not used. |

## For the changelog

See [`v0.20.0/CHANGELOG.md`](./v0.20.0/CHANGELOG.md).
