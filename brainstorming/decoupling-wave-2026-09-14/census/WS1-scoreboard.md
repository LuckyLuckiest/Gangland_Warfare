# WS1 Census: Scoreboard becomes its own plugin

## 1. Files

### gangland-ui/scoreboard-api (9 main files + 2 test files, 839 LoC total)

| File | Lines | Role |
|------|-------|------|
| `Scoreboard.java` | 48 | Core scoreboard container: wraps a driver and renders lines |
| `configuration/ScoreboardAddon.java` | 85 | Config DTO for scoreboard line definitions (interval, rendering) |
| `driver/DriverHandler.java` | 121 | Interface: driver contract for version-specific scoreboard update algorithms |
| `driver/version/DriverV1.java` | 97 | Clustering algorithm: updates similar line intervals each tick |
| `driver/version/DriverV2.java` | 80 | Built-in library variant of V1 algorithm |
| `driver/version/DriverV3.java` | 122 | Interactive driver: disables main scoreboard, uses alternative (ViaVersion-aware) |
| `part/Line.java` | 107 | Represents one rendered scoreboard line with placeholder resolution |
| `part/StaticLine.java` | 22 | Static (non-placeholder) line variant |
| `ScoreboardTest.java` | 60 | Tests schedule synchronously |
| `LineTest.java` | 97 | Tests line rendering and placeholder resolution |

### Gangland impl (4 files, ~170 LoC, all in gangland-impl)

| File | Lines | Role |
|------|-------|------|
| `gangland-impl/src/main/java/org/luckyraven/gangland/scoreboard/ScoreboardManager.java` | 70 | Bean: driver instantiation, version detection, Player→DriverHandler mapping |
| `gangland-impl/src/main/java/org/luckyraven/gangland/bootstrap/ScoreboardLifecycleService.java` | 58 | BeanPostInitialize: creates scoreboards for all online players at startup |
| `gangland-impl/src/main/java/org/luckyraven/gangland/listener/player/PlayerScoreboardListener.java` | 41 | ListenerHandler: creates scoreboard when player joins (UserDataInitEvent) |
| `gangland-impl/src/main/java/org/luckyraven/gangland/bootstrap/ReloadPlugin.java` | calls scoreboardReload() | Reload hook for `/glw reload scoreboard` |

### Configuration Files

| File | Lines | Summary |
|------|-------|---------|
| `gangland-impl/src/main/resources/scoreboard.yml` | 108 | Board title animation (26 frame intro), Rows 1-15 with dynamic content (Interval, Lines with %gangland_*% placeholders) |
| `gangland-impl/src/main/resources/settings.yml` → `Scoreboard:` | 14 | Enable flag, Driver choice (V1/V2/V3, default V3) |

### Domain (1 file)

| File | Lines | Role |
|------|-------|------|
| `gangland-infra/gangland-domain/src/main/java/.../gang/user/User.java` | Has Scoreboard field | User record holds a `Scoreboard scoreboard` field (allows per-player scoreboard state) |

---

## 2. Inbound Dependencies

### Import counts
- **15 files** across the codebase import from `org.luckyraven.gangland.scoreboard.*`
- Main inbound: impl (4), domain (1), api (2 internal)

### Detailed inbound callers (from graphify affected "Scoreboard")

| Caller | File | What it uses |
|--------|------|-------------|
| **gangland-impl** | FileConfig.java:L122 | @Bean `scoreboardManager()` produces ScoreboardManager |
| | SchedulingConfig.java:L59 | @Bean `scoreboardLifecycleService()` uses ScoreboardManager, UserManager |
| | KernelConfig.java | FileHandler loads `scoreboard.yml` |
| | ScoreboardLifecycleService.java:L28,31,49 | Calls scoreboardManager.getDriverHandler(), creates Scoreboard, calls start() |
| | PlayerScoreboardListener.java:L20,22,34,35 | @ListenerHandler on UserDataInitEvent; calls scoreboardManager.getDriverHandler() |
| | ReloadPlugin.java:L81 | Calls scoreboardReload() on /glw reload scoreboard |
| | Gangland.java:L104,135 | bStats chart `scoreboard_driver` (records driver choice) |
| | RemoveAccountListener.java:L83,84,85 | On logout: calls user.getScoreboard().end(), sets to null |
| **gangland-domain** | User.java:L26,53 | Imports Scoreboard; field stores player's active scoreboard |
| **Feature modules** | MoneyAspect.java (api) | Imports Scoreboard (for sign aspect) |
| | GanglandDetainmentEconomyContract.java (cops-n-crooks) | Imports Scoreboard |
| | TurfFriendlyFireListener.java (cops-n-crooks) | Imports Scoreboard |

### User.getScoreboard() / .setScoreboard() callers
- **ScoreboardLifecycleService**: setScoreboard() → user.setScoreboard(scoreboard)
- **PlayerScoreboardListener**: setScoreboard() and getScoreboard().start()
- **RemoveAccountListener**: getScoreboard().end(), setScoreboard(null)
- **Tests**: ScoreboardTest.java uses the scoreboard contract

---

## 3. Outbound Dependencies

### scoreboard-api imports (all classes)

| What | From | Used For |
|------|------|----------|
| **Bukkit** | `org.bukkit.*` | Player, events, logging |
| **Keystone** | `org.luckyraven.keystone.util.Placeholder` | Abstract placeholder resolution contract |
| **Keystone** | `org.luckyraven.keystone.util.ReflectionUtil` | Reflection-based driver discovery in ScoreboardManager static block |
| **FastBoard** | `fr.mrmicky.fastboard.*` (v1.3.1 via pom) | Scoreboard display/update backend |
| **ViaVersion** | `com.viaversion.viaversion.api.ViaAPI` | Used by DriverV3 for multi-version compatibility checks (client-version detection) |

### Gangland impl imports

| What | From | Used For |
|------|------|----------|
| **Settings** | gangland-api | `Settings.isScoreboardEnabled()`, `Settings.getScoreboardDriver()` |
| **PlaceholderService** | gangland-impl core | PlaceholderService autowired/injected to resolve %gangland_*% in line text |
| **UserManager** | domain | User lookup and iteration (getUsers().values()) |
| **FileManager** | implicitly via KernelConfig | Loads scoreboard.yml defaults |
| **ChatUtil** | Not directly; Keystone Placeholder handles colors |  |

### Maven coordinates

| Dependency | Group | Artifact | Version | Scope | Shaded? |
|------------|-------|----------|---------|-------|---------|
| FastBoard | fr.mrmicky | fastboard | (inherited from parent) | compile | YES → `org.luckyraven.gangland.dependency.fastboard` |
| ViaVersion API | com.viaversion | viaversion-api | (inherited from parent) | compile | NO (stays original) |
| Keystone | org.luckyraven | keystone-{common,bean,util} | 1.9.2 | provided | NO |
| Bukkit/Spigot | org.spigotmc | spigot-api | (inherited) | provided | NO |

---

## 4. Wiring

### Bean registration

| Bean | Location | Phase | Dependencies |
|------|----------|-------|--------------|
| `scoreboardManager` | FileConfig.java:L122 | FILE | Gangland plugin |
| `scoreboardLifecycleService` | SchedulingConfig.java:L59 | POST-INIT (BeanPostInitialize) | ScoreboardManager, UserManager<Player>, PlayerBootstrapService (for ordering) |
| `scoreboard.yml FileHandler` | KernelConfig.java | LIFECYCLE (via @Bean for FileHandler) | FileManager |

### Reload path
- **Command**: `/glw reload scoreboard` → ReloadCommand → ReloadPlugin.scoreboardReload()
- **What happens**: ScoreboardManager reloads driver list (via reflection); existing scoreboards continue running
- **Entries in commands.json**: `reload_scoreboard` with usage "/glw reload scoreboard"

### bStats tracking
- **Chart**: `scoreboard_driver` (line 135 in Gangland.java)
- **Data**: Reports which driver is active (V1/V2/V3)

### Settings and Messages constants

#### Settings (gangland-api)
- `Settings.isScoreboardEnabled()` → Scoreboard:Enable in settings.yml
- `Settings.getScoreboardDriver()` → Scoreboard:Driver in settings.yml (returns enum or String)

#### Messages (gangland-api)
- **None found** — scoreboard has no user-facing error messages in the Messages enum

#### Plugin permissions
- **None** — no scoreboard-specific permissions in plugin.yml

---

## 5. Tests

| Test File | Assertions |
|-----------|------------|
| `ScoreboardTest.java` | Tests `Scoreboard.start()` with synchronous scheduling; verifies FastBoard interaction |
| `LineTest.java` | Tests line rendering: placeholder injection, color code handling, static vs. dynamic lines |

---

## 6. Placeholder Rendering

### Scoreboard.yml uses these %gangland_*% PAPI placeholders:

1. `%gangland_bank_balance%` — User's bank account balance
2. `%gangland_bank_name%` — Bank name display
3. `%gangland_flashif:user_wanted-level>0:15:5:user_wanted%` — Conditional flash: wanted level if > 0, cycles every 15 ticks, text "user_wanted"
4. `%gangland_gang_display-name%` — Gang name
5. `%gangland_gang_members-size%` — Gang member count
6. `%gangland_gang_online-members-size%` — Online member count
7. `%gangland_money_symbol%` — Currency symbol (e.g. $)
8. `%gangland_user_balance%` — User's pocket money
9. `%gangland_user_bounty%` — User's bounty
10. `%gangland_user_contributed-amount%` — Turf contribution amount
11. `%gangland_user_experience-percentage%` — Level XP %
12. `%gangland_user_level%` — User level

### Resolution path
- **Line class** calls `PlaceholderService.convert(player, textWithPlaceholders)`
- **PlaceholderService** aggregates two sources:
  1. Internal Gangland placeholders (GanglandPlaceholder bean, CONFIG phase)
  2. PlaceholderAPI expansion (PAPI provider, soft-dep on PlaceholderAPI)
- **GanglandPlaceholder.java** (CONFIG-phase bean) implements the 12 placeholders above
- **All placeholders resolve successfully** through PlaceholderAPI's `%gangland_*%` namespace

---

## 7. Surprises

1. **ViaVersion tight coupling in DriverV3**: DriverV3 imports and uses ViaAPI directly to detect client version. If ViaVersion is absent, the driver may degrade gracefully (reflect or fallback), but the api import is hard-coded.

2. **User field holds live Scoreboard**: User.scoreboard is a mutable field set on every player login and nulled on logout. This couples domain objects to the scoreboard lifecycle — moving scoreboard to a plugin means extracting this field.

3. **Reflection-based driver discovery**: ScoreboardManager uses reflection (ReflectionUtil.findClasses) to auto-discover DriverV1/V2/V3 at static initialization. The new plugin must replicate this or hard-code the drivers.

4. **Async timer handling**: Line rendering with placeholders happens on a timer started by Scoreboard.start(). The scoreboard is per-player and the timer must be managed (stopped on logout by RemoveAccountListener).

5. **No dependency on any domain**: scoreboard-api itself imports zero gangland domain types (no Gang, User, Member, etc.). It is purely a rendering layer, making it easy to extract.

6. **UserManager.onPreClear() nuance**: UserManager's cleanup path (called on reload/shutdown) stops and nulls all scoreboards before data is cleared — the new plugin must hook into UserManager's reload/clear cycle to maintain this invariant.

---

## 8. Planner Summary (5 hardest couplings)

1. **User.scoreboard field** — The domain User class (gangland-domain) holds a Scoreboard instance. Decoupling requires either keeping the field but making it Optional<Scoreboard>, or moving the field entirely to a plugin-side wrapper/cache.

2. **Settings/scoreboard.yml split** — Settings.yml controls Enable/Driver; scoreboard.yml controls line content. The new plugin must read BOTH configs, requiring either a dual-config model or a migration step.

3. **gangland-domain pom → scoreboard-api dependency** — The domain module (used by all features) currently imports scoreboard-api (to declare the User.scoreboard field type). Removing this import breaks the field unless we parameterize or delete it.

4. **PlaceholderService integration** — The internal Gangland placeholder resolver (GanglandPlaceholder bean) is wired into PlaceholderService, which Line.java calls. The new plugin must either re-export GanglandPlaceholder or shift placeholder resolution to pure PAPI.

5. **ScoreboardLifecycleService depends on PlayerBootstrapService** — Scoreboard creation is ordered after PlayerBootstrapService (via constructor param). The new plugin must ensure users load before scoreboards are created, or lose the ordering guarantee.
