# CONTRACTS - Cops N Crooks 0.15 "Lose them"

Exact seams shared by two or more tasks. Paths are relative to the repository root (your lane worktree, CONSTRAINTS "Where";
the integration worktree is `E:/Programming/java/wt/gangland-0.15.0`).
Abbreviations: `CORE` = `gangland-core/src/main/java/org/luckyraven/gangland/core`, `API` =
`gangland-api/src/main/java/org/luckyraven/gangland`, `IMPL` = `gangland-impl/src/main/java/org/luckyraven/gangland`,
`CNC` = `gangland-features/cops-n-crooks/src/main/java/org/luckyraven/gangland/copsncrooks`, `CNCR` =
`gangland-features/cops-n-crooks/src/main/resources`, `CIV` = `gangland-features/gangland-civilians/src/main/java/org/luckyraven/gangland/civilians`.
"Tn" = the PLAN.md task that creates the member; everyone else only calls it. Line numbers are master 16d1f064.
All methods below follow the house brace style even where this file abbreviates bodies.

## C0. Placement (why each type lives where it does)
| Type | Module / package | Why |
|---|---|---|
| WantedCause, WantedDecayPolicy, WantedStars, Wanted/event cause fields | gangland-core `core.wanted`, `core.events.wanted` | `Wanted` (CORE/wanted/Wanted.java:59) and `WantedExecutor` (CORE/wanted/WantedExecutor.java:18, `execute` :49) are core and must call them; core cannot see gangland-api (api depends on core, `gangland-api/pom.xml:33`). Modules still reach them because gangland-api re-exports core at compile scope; they count as api surface, covered by the single 2.1 bump. |
| MoneyFormula | gangland-core `core.money` | Spec: "one small class in gangland-core"; callers are core (`WantedStars.drop`) and impl (`PlayerDeathListener`); `ScientificCalculator` is already a core dependency (CORE/user/Level.java:116). |
| CrimeCommittedEvent, CrimeService, Crimes, WantedEvasionStateEvent, EvasionState | gangland-api `events.crime`, `crime`, `events.wanted` | Spec places them in the api; publishers are cops-n-crooks and gangland-civilians (modules see only api), the consumer is cops-n-crooks. No core code needs them. |
| Settings keys (Take_Money.Enable/Formula, Bounty.Pay_Notoriety) | gangland-api `file/configuration/Settings.java` | Spec names them as settings.yml keys; Settings lives in the api. |
| Heat ledger, evasion clock, HUD, charge sheet, regroup, shot noise, chase config | cops-n-crooks | Gameplay that needs cops/squads; module-owned YAML. The core never names these classes. |
| Bounty posted/notoriety split + persistence | gangland-core `Bounty` + gangland-impl tables/loaders | `Bounty` is core (CORE/bounty/Bounty.java:18); the user table and loaders are impl. |

## C1. WantedCause (T1) - new `CORE/wanted/WantedCause.java`
```java
package org.luckyraven.gangland.core.wanted;
public enum WantedCause {
	CRIME,    // heat ledger / kill path (EntityDamageListener.handleWanted)
	SIGN,     // [WANTED] sign INCREASE, REMOVE, CLEAR
	ADMIN,    // /glw wanted add|remove|clear
	RESTORE,  // level loaded at login (UserDataLoader)
	DECAY,    // Repeating_Timer safety-net tick (WantedExecutor)
	EVASION,  // line-of-sight evasion clock (cops-n-crooks)
	BRIBE,    // handcuff bribe (BribeService)
	ARREST,   // jail intake (JailIntakeService)
	DEATH,    // death / down reset (EntityDamageListener.onPlayerDeathResetWanted)
	UNKNOWN   // legacy no-cause calls
}
```

## C2. Wanted and its events carry the cause (T1)
`CORE/wanted/Wanted.java` (keeps every existing member):
```java
public void setLevel(int level, WantedCause cause)   // THE choke point; body = today's setLevel(int) (:59-90) with the cause
                                                    // passed into every event; the off-thread hop (:73) becomes
                                                    // runTask(plugin, () -> setLevel(level, cause))
public void setLevel(int level)                      // unchanged signature -> setLevel(level, WantedCause.UNKNOWN)
public void incrementLevel(WantedCause cause)        // setLevel(level + increments, cause); incrementLevel() -> UNKNOWN
public void decrementLevel(WantedCause cause)        // setLevel(level - 1, cause); decrementLevel() -> UNKNOWN
public void reset(WantedCause cause)                 // setLevel(0, cause) + stopTimer(); reset() -> UNKNOWN
```
Events (`CORE/events/wanted/`), each gets `private final WantedCause cause` with a Lombok getter `getCause()` and a NEW
constructor; the old constructor stays and passes `WantedCause.UNKNOWN`:
- `WantedLevelChangeEvent(Player player, Wanted wanted, int oldLevel, int newLevel, WantedCause cause)` (old 4-arg :59).
- `WantedStartEvent(Player player, Wanted wanted, int wantedLevel, WantedCause cause)` (old 3-arg :19).
- `WantedEndEvent(Player player, Wanted wanted, WantedCause cause)` (old 2-arg :18). This is the spec's "WantedEndEvent carries the cause".
Unchanged on purpose (docket CJ-27 stays open): `WantedLevelChangeEvent` still fires BEFORE the level mutates and is
cancellable; every listener reads `event.getNewLevel()`, never `wanted.getLevel()`, inside it.

## C3. WantedDecayPolicy, WantedStars, WantedExecutor, WantedSettings (T1; T9 changes only the price in drop)
`CORE/wanted/WantedDecayPolicy.java`:
```java
@FunctionalInterface
public interface WantedDecayPolicy {
	/** True while this policy drives the player's star decay, so the Repeating_Timer tick does nothing. Main thread.
	 *  The policy never gets a wallet: it lowers stars only through WantedStars.drop. */
	boolean handlesDecay(Player player, Wanted wanted);
}
```
`CORE/wanted/WantedStars.java` (one instance, core bean `DataConfig.wantedStars(...)` added by T1 next to
`wantedKillTrackers()` IMPL/config/DataConfig.java:136):
```java
public final class WantedStars {
	public WantedStars(JavaPlugin plugin, WantedSettings settings)

	public void installDecayPolicy(@Nullable WantedDecayPolicy policy)        // volatile; null uninstalls
	public boolean isDecayHandled(Wanted wanted)                               // policy != null && owner != null && policy.handlesDecay(owner, wanted)

	public void suppressStarChat(@Nullable BooleanSupplier whileTrue)          // HUD installs () -> hud.starCard()
	public boolean isStarChat()                                                // true unless the supplier answers true

	/** THE star-raising method (spec "one method", docket WB-20). Adds stars (>0) with origin, clamped to maxLevel,
	 *  then - when the player is wanted and settings.isTimerEnabled() - (re)starts the safety-net clock at the new
	 *  level's interval (same as handleWanted today, IMPL/listener/player/EntityDamageListener.java:214-219).
	 *  Main thread only. Returns the stars actually added: 0 when stars <= 0, already at max, or the change was cancelled. */
	public int raise(WantedContext context, int stars, WantedCause origin)

	/** Login restore (docket WB-04, US-18 wanted half). Any thread; hops with Bukkit.getScheduler().runTask(plugin, ..)
	 *  when off the main thread. Sets the level with RESTORE, then starts the clock when level > 0, the owner is
	 *  online and settings.isTimerEnabled(). */
	public void restore(WantedContext context, int level)

	/** Lowers by `stars` with `cause` (DECAY, EVASION, ...). Order: compute the target, wanted.setLevel(target, cause);
	 *  if the level did not change (cancelled) return 0 and charge nothing; else charge the star-drop price (below)
	 *  through context.withdraw, send settings.getWantedDecreasedMessageTemplate() with %level%/%stars% of the NEW level,
	 *  then settings.formatMoneyLoss(charged) when charged != 0; stopTimer() when the level reached 0.
	 *  Off the main thread it re-schedules itself with runTask and returns 0. Returns stars actually dropped. */
	public int drop(WantedContext context, int stars, WantedCause cause)

	/** Starts (replacing any running one) the safety-net clock when settings.isTimerEnabled() and wanted:
	 *  Timer t = new WantedExecutor(plugin, new WantedEvent(false, wanted), context, settings, this).createTimer();
	 *  t.start(false); return t;   (Keystone Timer.start(boolean) returns void, keystone-common timer/Timer.java:55)
	 *  SYNC timer and sync event (docket WB-06 wanted half). Returns the timer or null. */
	public @Nullable Timer startDecayClock(WantedContext context)
}
```
Star-drop price inside `drop`, per star dropped, for each level `l` from the level before the drop down to target+1:
- T1 (W1, today's rule moved verbatim from WantedExecutor.java:54-60): `amount > 0 ? Currency.multiply(amount, multiplier^l) : 0`.
- T9 (W2, the spec rule): `0` unless `settings.isTakeMoneyEnabled()`; else
  `MoneyFormula.evaluate(settings.getTakeMoneyFormula(), vars, amount * multiplier^l)` where
  `vars = context instanceof User<?> u ? MoneyFormula.userVariables(u) : new HashMap<>()` plus `amount`, `multiplier`,
  `wanted = l`. The summed price is withdrawn once (`context.withdraw` clamps at the balance, CORE/user/User.java:100-114).

`CORE/wanted/WantedExecutor.java`:
```java
public WantedExecutor(JavaPlugin plugin, WantedEvent event, WantedContext context, WantedSettings settings, WantedStars stars)
public WantedExecutor(JavaPlugin plugin, WantedEvent event, WantedContext context, WantedSettings settings) // kept: stars = new WantedStars(plugin, settings)
```
`execute(Timer)` becomes: stop when not wanted -> `if (stars.isDecayHandled(wanted)) return;` -> reset + fire the
WantedEvent, return when cancelled (T-112 rule kept) -> `stars.drop(context, 1, WantedCause.DECAY)` -> stop when not wanted.
`createTimer()` unchanged (interval frozen at the level the clock was built, as today).

`CORE/wanted/WantedSettings.java` additions (default methods only, so mocks and old impls keep compiling):
```java
default boolean isTimerEnabled() { return true; }                                // T1; GanglandWantedSettings -> Settings.isWantedTimerEnabled()
default boolean isTakeMoneyEnabled() { return false; }                           // T9; GanglandWantedSettings -> Settings.isWantedTakeMoneyEnabled()
default String getTakeMoneyFormula() { return "amount * multiplier ^ wanted"; }  // T9; -> Settings.getWantedTakeMoneyFormula()
```
(written here on one line for brevity; the code uses own-line braces.) `getTakeMoneyAmount`, `getTakeMoneyMultiplier`,
`formatMoneyLoss` stay. Note for tests: a Mockito `mock(WantedSettings.class)` answers false/null for default methods too, so
a test that needs the clock stubs `isTimerEnabled()` to true (and T9 tests stub the take-money pair).

## C4. MoneyFormula (T2) - new `CORE/money/MoneyFormula.java`
```java
package org.luckyraven.gangland.core.money;
@CustomLog
public final class MoneyFormula {
	/** Never throws. Builds new ScientificCalculator(formula, variables) and evaluates it. If building or evaluating
	 *  throws (bad syntax, unknown variable, division by zero, bad factorial) or the result is NaN, Infinite or < 0,
	 *  returns the fallback and logs ONE warning per distinct formula text ("Money formula '<f>' failed: <why>; using <fallback>").
	 *  Whatever it returns (fallback included) is clamped to >= 0; a NaN/negative fallback returns 0. A 0 result means
	 *  "no charge": callers skip the withdraw and the message. */
	public static double evaluate(String formula, Map<String, Double> variables, double fallback)
	/** A NEW mutable HashMap: balance (wallet), level, experience, bounty (total amount), wanted (current level) -
	 *  exactly the set PlayerDeathListener.amountDeduction builds today (IMPL/listener/player/PlayerDeathListener.java:213-220). */
	public static Map<String, Double> userVariables(User<?> user)
	static void resetWarnings()                        // package-private test seam: clears the warned-formula set
	static Consumer<String> warnSink                   // package-private test seam, default log::warn
}
```
The warned-formula set is static for the whole surefire JVM: `MoneyFormulaTest` calls `resetWarnings()` in `@BeforeEach` and
restores `warnSink` in `@AfterEach`. Only `MoneyFormulaTest` (same package) asserts warnings; callers' tests (T9's
`WantedStarsTest`, T2's `PlayerDeathListenerTest`) prove routing by price (a custom formula whose result differs from the
fallback), never by reading the seams.

## C5. Settings and settings.yml (T2)
`API/file/configuration/Settings.java` adds (static Lombok getters, read beside :544-545 and :527-533):
```java
private static @Getter boolean wantedTakeMoneyEnabled;   // Wanted.Take_Money.Enable, default false   -> Settings.isWantedTakeMoneyEnabled()
private static @Getter String  wantedTakeMoneyFormula;   // Wanted.Take_Money.Formula, default "amount * multiplier ^ wanted"
private static @Getter boolean bountyPayNotoriety;       // Bounty.Pay_Notoriety, default false         -> Settings.isBountyPayNotoriety()
```
`gangland-impl/src/main/resources/settings.yml`: `Wanted.Take_Money` block replaced by the spec's block verbatim
(SPEC-0.15.md lines 131-157: Enable false, the variable comment, Formula, Amount 50, Multiplier 5); `Bounty.Pay_Notoriety: false`
with a comment ("true also pays the server-made notoriety part on a kill, as before 0.15.0"); `User.Death.Money.Lose_Money`
comment rewritten ("false = dying costs nothing; use Command for a payout"). No other key moves.

## C6. Crime event bus (T3) - gangland-api
`API/events/crime/CrimeCommittedEvent.java`:
```java
@Getter
public class CrimeCommittedEvent extends Event implements Cancellable {
	public CrimeCommittedEvent(Player player, String crimeId, Location location, boolean seenByCop, int witnesses) // super(false); stores location.clone()
	Player getPlayer(); String getCrimeId(); Location getLocation(); boolean isSeenByCop(); int getWitnesses();
	boolean isCancelled(); void setCancelled(boolean);
	static HandlerList getHandlerList(); HandlerList getHandlers();
}
```
`seenByCop` = what the publisher knows; the heat ledger additionally applies its own squad check (C12). `witnesses` = 0
from every 0.15 publisher (field reserved by the spec; no consumer in 0.15).

`API/crime/CrimeService.java` (core bean `WiringConfig.crimeService()` in IMPL/config/WiringConfig.java, no params):
```java
public final class CrimeService {
	/** Main thread only. Fires CrimeCommittedEvent through Bukkit; true when no listener cancelled it. */
	public boolean commit(Player player, String crimeId, Location location, boolean seenByCop, int witnesses)
	public boolean commit(Player player, String crimeId, Location location)   // seenByCop false, witnesses 0
}
```
`API/crime/Crimes.java` - crime ids are plain Strings (new crimes need no api bump); constants for the 0.15 publishers and
the shipped weight table: `KILL_PLAYER = "Kill_Player"`, `KILL_CIVILIAN = "Kill_Civilian"`, `KILL_COP = "Kill_Cop"`,
`ASSAULT_COP = "Assault_Cop"`, `ASSAULT_CIVILIAN = "Assault_Civilian"`, `RESISTING_ARREST = "Resisting_Arrest"`.
Publishers in 0.15 (all T7): `HeatWantedTracker.recordKill` (Kill_Player, Kill_Cop), `CivilianDeathRewardListener`
(Kill_Civilian), `HeatLedger.reportAssault` via the CopManager attacked hook (Assault_Cop), `BreakFreeService` (Resisting_Arrest).

## C7. WantedEvasionStateEvent (T3) - gangland-api
```java
package org.luckyraven.gangland.events.wanted;
public enum EvasionState { SEEN, SEARCHING, EVADED, OFF }
@Getter
public class WantedEvasionStateEvent extends Event {           // not cancellable, super(false), own HandlerList
	public WantedEvasionStateEvent(Player player, EvasionState state, int level, int secondsLeft,
	                               @Nullable Location zoneCentre, double zoneRadius)
}
```
Fired only by `EvasionClock` (C13): on every state change, and while SEARCHING whenever `secondsLeft` changes. `secondsLeft`
is 0 unless SEARCHING; `zoneCentre` non-null in SEARCHING and EVADED; `level` = stars after the event's change.

## C8. Dormant events fired through Bukkit (T3) - cops-n-crooks
- `CopListener.onCopDeath` (CNC/listener/detainment/CopListener.java:230-250) ends with
  `Bukkit.getPluginManager().callEvent(new CopDeathEvent(cop, event.getEntity().getKiller()))` (CNC/events/npc/CopDeathEvent.java:21). Docket CJ-39.
- `KillComboEvent` (CNC/events/combo/KillComboEvent.java) gains `public enum Kind { INCREMENT, WANTED_TRIGGER, RESET }`,
  a `Kind kind` getter and ctor `KillComboEvent(Player player, KillComboTracker tracker, Kind kind)`; the old 2-arg ctor = INCREMENT.
  `KillCombo` fires it through Bukkit at recordKill (INCREMENT, :54), the threshold trigger (WANTED_TRIGGER, :122) and
  reset (RESET, :148; the tracker timer runs async, so RESET is fired inside `Bukkit.getScheduler().runTask(plugin, ..)`).
  The in-process Consumers keep working. Docket WB-29 (and WB-28: the increment moment now has a public signal).

## C9. Chase config and strings (T4) - cops-n-crooks
Files registered in `CNC/config/CopsNCrooksYamlConfig.java:28-41` with
`fileManager.addFile(new FileHandler(plugin, "<name>", "npc", ".yml", loader), true)` for `wanted` and `wanted_messages`.
`CNCR/npc/wanted.yml` (root `Wanted:`; every key also an in-code default equal to this):
```yaml
Wanted:
   Heat:
      Enable: true
      Star_Thresholds: [100, 250, 450, 700, 1000]   # written as a block list in the file
      Streak_Bonus: 1.5
      Seen_By_Cop_Multiplier: 1.5
      Turf_War_Multiplier: 0.5
      Assault_Repeat_Seconds: 10
      Crimes: {the 12 weights of CONSTRAINTS "Heat"}  # written as a block map in the file
   Evasion:
      Enable: true
      Lost_Sight_Seconds: 3
      Drop_Mode: ONE_STAR
      Search_Radius: [40, 60, 90, 130, 180]
      Seconds_To_Drop: [10, 20, 30, 45, 60]
      Outside_Zone_Speed: 2.0
   Hud:
      Boss_Bar: {Enable: true}
      Star_Card: {Enable: true}
      Title: {Enable: true}
      Siren: {Enable: true, Sound: "BLOCK_NOTE_BLOCK_BELL", Volume: 1.0, Pitch: 0.5}
      Zone_Ring: {Enable: true, Particle: "DUST", Points: 48}
      Compass: {Enable: true}
   Charge_Sheet:
      Enable: true
      Base: 200
      Per_Wanted_Level: 250
      Maximum: 10000
      Seconds_Per_Unpaid: 0.1
      Max_Extra_Seconds: 600
```
(flow braces above are shorthand for this document only; the shipped file is block style.)
Records in `CNC/wanted/config/` (each with `public static final X DEFAULT` equal to the file):
```java
public record HeatSettings(boolean enabled, List<Integer> starThresholds, double streakBonus, double seenByCopMultiplier,
                           double turfWarMultiplier, int assaultRepeatSeconds, Map<String, Integer> crimeWeights) {
	public int weightOf(String crimeId)                 // 0 when absent (exact, case-sensitive id)
	public int starsFor(double heat, int maxLevel)      // thresholds resized with NumberUtil.resizeLinear when shorter than
	                                                    // maxLevel (KillCombo.java:130 rule); count of the first maxLevel <= heat
	public double floorOf(int level, int maxLevel)      // 0 for level <= 0, else threshold[min(level, maxLevel) - 1]
}
public enum DropMode { ONE_STAR, ALL_STARS }
public record EvasionSettings(boolean enabled, int lostSightSeconds, DropMode dropMode, List<Integer> searchRadius,
                              List<Integer> secondsToDrop, double outsideZoneSpeed) {
	public int radiusFor(int level)                     // list[clamp(level, 1, size) - 1]
	public int secondsToDropFor(int level)              // same indexing
}
public record HudSettings(boolean bossBar, boolean starCard, boolean title, boolean siren, String sirenSound,
                          float sirenVolume, float sirenPitch, boolean zoneRing, String zoneParticle, int zonePoints,
                          boolean compass)
public record ChargeSheetSettings(boolean enabled, double base, double perWantedLevel, double maximum,
                                  double secondsPerUnpaid, int maxExtraSeconds) {
	public double fineFor(int wantedLevel)              // min(maximum, base + max(0, wantedLevel) * perWantedLevel)
	public int extraSecondsFor(double unpaid)           // unpaid <= 0 ? 0 : min(maxExtraSeconds, (int) ceil(unpaid * secondsPerUnpaid))
}
public record ChaseConfig(HeatSettings heat, EvasionSettings evasion, HudSettings hud, ChargeSheetSettings chargeSheet) {
	public static final ChaseConfig DEFAULT;
	public static ChaseConfig parse(@Nullable NodeReader wantedRoot, ConfigReport report) // null section -> that block's DEFAULT
}
public class ChaseConfigLoader extends FileLoader<ChaseConfig> {   // mirrors CopLoader (CNC/npc/police/config/CopLoader.java)
	public ChaseConfigLoader(JavaPlugin plugin, FileManager fileManager)
	public ChaseConfig get()                            // never null: ChaseConfig.DEFAULT before load / when the file is missing
}
```
Consumers take `ChaseConfigLoader` and call `get()` per use (a reload is seen live). `Drop_Mode` unknown -> ONE_STAR + report.

`CNC/wanted/WantedMessages.java` `extends LocalizedModuleYaml` (API/file/configuration/LocalizedModuleYaml.java:30), base
`wanted_messages` (English file only; `_es` optional and not shipped):
```java
public enum Key {
	BAR_SEEN("Hud.Bar.Seen", "&c%stars% &4&lIN SIGHT"),
	BAR_SEARCHING("Hud.Bar.Searching", "&e%stars% &6&lSEARCHING %time%"),
	BAR_EVADED("Hud.Bar.Evaded", "&a%stars% &2&lSTAR LOST"),
	TITLE("Hud.Title", "&c%stars%"),
	CARD_RAISE("Hud.Card.Raise", "&f%crime%&7: &e%tier% &7inbound, %stance%"),
	STANCE_CUFFS("Hud.Card.Stance_Cuffs", "they still want you in cuffs"),
	STANCE_SHOOT("Hud.Card.Stance_Shoot", "they shoot first"),
	CARD_DROP_EVASION("Hud.Card.Drop_Evasion", "&aYou stayed out of sight"),
	CARD_DROP_DECAY("Hud.Card.Drop_Decay", "&aThe trail went cold"),
	CARD_DROP_OTHER("Hud.Card.Drop_Other", "&aA star is gone"),
	SHEET_HEADER("Charge_Sheet.Header", "&8&m-----&r &6&lCHARGE SHEET &8&m-----"),
	SHEET_CRIME("Charge_Sheet.Crime", "&7- &f%crime% &8x%count%"),
	SHEET_TOTAL("Charge_Sheet.Total", "&7Fine: &c%money_symbol%%amount%"),
	SHEET_PAID("Charge_Sheet.Paid", "&7Paid from your wallet: &a%money_symbol%%amount%"),
	SHEET_EXTRA("Charge_Sheet.Extra_Time", "&7Unpaid &c%money_symbol%%amount% &7served as &c+%time%"),
	PAPERWORK_FINE("Charge_Sheet.Paperwork", "&7Fine paid: &a%money_symbol%%paid% &8| &7Extra time: &c%time%");
	public final String path; public final String fallback;
}
public WantedMessages(FileManager fileManager)
public String format(Key key, Map<String, String> values)  // color(key.path, key.fallback) (LocalizedModuleYaml's protected
                                                            // helper; there is no `colour`), then every %name% replaced
public String crimeName(String crimeId)                     // Crimes.<id> in the file, else crimeId.replace('_', ' ')
public static String duration(int seconds)                  // "45s", "1m 05s"
```
`wanted_messages.yml` ships `Crimes:` names for all 12 ids (e.g. `Assault_Cop: "Assault on an officer"`,
`Kill_Cop: "Killing an officer"`, `Kill_Civilian: "Killing a civilian"`, `Kill_Player: "Murder"`,
`Resisting_Arrest: "Resisting arrest"`) plus `Unknown_Crime: "Reported crime"` (the star card's text when the ledger has no
crime, e.g. a sign or admin raise) and every Key above.
Beans: new `CNC/config/ChaseModuleConfig.java` (`@Configuration`, ctor `(JavaPlugin plugin, DependencyContainer container)`):
`@Bean ChaseConfigLoader chaseConfigLoader(FileManager)` (registerInitializer + initializeAll like `copLoader`,
CopsNCrooksModuleConfig.java:299-306) and `@Bean WantedMessages wantedMessages(FileManager)` (registerInitializer).
T4 also creates the EMPTY shells `CNC/config/HeatModuleConfig.java` and `CNC/config/EvasionModuleConfig.java` (same ctor,
no beans) and registers all three after `CopsNCrooksModuleConfig` in `CopsNCrooksModule.configure` (CNC/CopsNCrooksModule.java).
T7 fills HeatModuleConfig, T8 fills EvasionModuleConfig; nobody else edits CopsNCrooksModule.java.

## C10. cops.yml additions and cop-config getters (T5)
`CNCR/npc/cops.yml` under `Cops:` (after `Backup`):
```yaml
   Regroup:
      Enabled: true
      Casualties: 2
      Window_Seconds: 20
      Fall_Back_Seconds: 15
      Cooldown_Seconds: 60
      Arrival_Radius: 24.0
   Shot_Noise:
      Enabled: true
      Radius:
         GUN: 48
         THROWABLE: 16
         MELEE: 0
```
and in `Cops.Radio`: `Cooldown_Ticks` += `Regroup: 1200`, `Regroup_Push: 1200`, `Shots_Fired: 60` (one squad = one hunted
player, so the per-squad key cooldown is the "once per shooter every 3 s" throttle). `Priority` is NOT extended: every
existing cops.yml has a `Priority:` list and `RadioSettings.read` replaces the defaults with it wholesale
(gangland-api npc/radio/RadioSettings.java:81-83), so a new priority entry would never reach an upgraded server. The two
regroup lines are spoken through the gap-bypassing follow-up path instead (C11, `CopRadio.sayFromLeaderLater`), which
works the same on fresh and upgraded files. Cooldowns DO merge key by key (`readCooldowns`), so the three new cooldowns
reach upgraded servers as code defaults.
`CNC/npc/police/config/RegroupSettings.java`:
`public record RegroupSettings(boolean enabled, int casualties, long windowMs, long fallBackMs, long cooldownMs, double arrivalRadius)`
with `DEFAULT = new RegroupSettings(true, 2, 20_000L, 15_000L, 60_000L, 24.0)`.
`CNC/npc/police/config/ShotNoiseSettings.java`:
`public record ShotNoiseSettings(boolean enabled, Map<String, Double> radius)` with `DEFAULT = (true, {GUN=48, THROWABLE=16, MELEE=0})`
and `public double radiusFor(String weaponType)` (0 when disabled or the upper-cased type is unlisted).
`CopConfigProvider` (CNC/npc/police/config/CopConfigProvider.java) adds
`default RegroupSettings getRegroupSettings()` -> DEFAULT and `default ShotNoiseSettings getShotNoiseSettings()` -> DEFAULT;
`COP_RADIO_DEFAULTS` (:27-62) adds cooldowns `Regroup` 60000, `Regroup_Push` 60000, `Shots_Fired` 3000 (ms); its priority
set is unchanged. `YamlCopConfigProvider` parses both blocks like `parseBackupSettings` (:859-873). Callers
null-guard the getter (Mockito provider mocks return null): `x != null ? x : X.DEFAULT`.
Radio lines (DEFAULT_LINES in CNC/npc/police/radio/CopRadioMessages.java:29-77, `Lines:` in `CNCR/npc/cop_radio_messages.yml`
and `_es.yml`): `Regroup` ("Two down! Pull back to cover, backup is coming!", "We're losing men, fall back and wait for
backup!"), `Regroup_Push` ("Backup's here! All units, push together!", "Everyone move in, now!"), `Shots_Fired`
("Shots fired! Converge on the sound!", "Gunfire, he's close! Move in!"). No `%member%` in any of them.

## C11. CopManager / CopGroup seams (T5; regroup state T14)
`CNC/npc/police/CopManager.java`:
```java
public void addAiTickHook(BiConsumer<Player, @Nullable CopGroup> hook) // CopyOnWriteArrayList; called on EVERY aiTick run
        // (:671) for an online player: on the normal path at the END, after fieldCare.tick and before drainRadioCalls; on the
        // null-or-empty-group branch (:679-687) BEFORE its early return (before stopAITask/groups.remove), passing the group
        // as found (null or empty) - so a chase whose cops are all dead or released still ticks the hooks (EvasionClock
        // turns OFF there). Each call wrapped in try/catch(RuntimeException) -> log.warn, so a bad hook never stops the loop
public void addCopAttackedHook(BiConsumer<CopNpc, Player> hook) // same list type; called first thing in onCopAttackedAlert (:190)
public @Nullable CopGroup groupOf(UUID playerId)               // public groups.get; package-private groupFor (:389) delegates to it
```
`recycles(...)` (:558) calls `group.markTipOff(now)` immediately before its `group.getSquad().reportSighting(..)` tip-off.
`CNC/npc/police/CopGroup.java` (T5):
```java
public void markTipOff(long now)                          // radio-clock ms of the latest stuck-recycle tip-off
public boolean tippedOffWithin(long now, long windowMs)   // tipOffAt > 0 && now - tipOffAt <= windowMs
```
Regroup state on CopGroup (T14, W3):
```java
public void recordCasualty(long now)                                  // keeps casualty times newer than the window
public boolean shouldRegroup(long now, RegroupSettings r)             // r.enabled() && isCombatAlert() && !regrouping
                                                                      //  && now >= regroupReadyAt && casualties in window >= r.casualties()
public void startRegroup(long now, RegroupSettings r)                 // regrouping = true; fallBackUntil = max(fallBackUntil, now + fallBackMs);
                                                                      //  regroupReadyAt = now + cooldownMs; casualties cleared
public boolean isRegrouping()
public void endRegroup()                                              // regrouping = false; fallBackUntil = 0
```
"Cuff-first" = `!isCombatAlert()` (the only group-level fight-vs-cuff flag, CopGroup.java:55,170).
Regroup radio (T14, `CNC/npc/police/radio/CopRadio.java`):
```java
/** sayFromLeader's speaker pick (leader, else the first cop with an entity; silent when none), spoken through
 *  SquadRadio.sayLater `steps` ack delays later, past the squad AND player gaps (key cooldowns still apply). */
public void sayFromLeaderLater(CopGroup group, String key, int steps, BooleanSupplier stillRelevant)
```
`Regroup` = `sayFromLeaderLater(group, "Regroup", 2, group::isRegrouping)` (the Man_Down that triggers it sets the
players' last-heard stamp, so a direct `say` in the same call would be swallowed by the player gap);
`Regroup_Push` = `sayFromLeaderLater(group, "Regroup_Push", 1, () -> true)`.

## C12. Heat ledger (T7) - cops-n-crooks
```java
package org.luckyraven.gangland.copsncrooks.wanted.heat;
public record CrimeRecord(String crimeId, double heat, long at, Location location) { }
public final class HeatLedger {
	public HeatLedger(ChaseConfigLoader config, UserManager<Player> users, CrimeService crimes,
	                  Predicate<Player> copSight, Predicate<Location> contestedTurf,
	                  IntSupplier streakWindowSeconds, LongSupplier clock)
	public void setStarTrigger(@Nullable Consumer<Player> trigger)  // the core handler replayed by WantedKillTrackers
	public double record(CrimeCommittedEvent event)                 // heat added, 0 when ignored
	public void reportAssault(Player attacker, UUID victimId, Location at) // commits Assault_Cop at most once per
	                                                                 // (attacker, victim) per Heat.Assault_Repeat_Seconds
	public void onLevelChanged(UUID playerId, int oldLevel, int newLevel, int maxLevel, WantedCause cause)
	public void clear(UUID playerId)
	public double heatOf(UUID playerId)
	public @Nullable CrimeRecord lastCrime(UUID playerId)           // HUD star card
	public List<CrimeRecord> chaseCrimes(UUID playerId)             // charge sheet; oldest first; unmodifiable copy; empty when none
}
```
`record(e)`: ignore when `!heat.enabled()`, no online User, or `weightOf(id) <= 0`. Record created lazily with heat =
`floorOf(currentLevel)`. `mult = 1`; `x streakBonus` when the previous crime is within `streakWindowSeconds` (wired to
`Settings.isWantedKillComboEnabled() ? Settings.getWantedKillComboResetAfter() : 0`); `x seenByCopMultiplier` when
`e.isSeenByCop() || copSight.test(player)` (copSight = the player's group squad `hasFreshSighting()`, reached through
`copManager.groupOf`); `x turfWarMultiplier` only for `Kill_Player` when `contestedTurf.test(location)` (a turf from
`TurfManager.findAt` whose `getRuntimeState(id).getState() == TurfState.CONTESTING`; one null-safe helper
`public static @Nullable Turf contestedTurfAt(TurfManager turfs, Location at)` on HeatLedger serves both turf predicates).
Defender kills never reach `record`: HeatWantedTracker drops them first (below). Append the CrimeRecord FIRST, then
`target = starsFor(heat, maxLevel)` and call the trigger while `wanted.getLevel() < target`, at most maxLevel times, stopping
when a call leaves the level unchanged. `onLevelChanged`: newLevel < oldLevel -> `heat = min(heat, floorOf(newLevel))`;
newLevel > oldLevel and cause != CRIME -> `heat = max(heat, floorOf(newLevel))`. `clear` drops the record (chase over).
`CNC/seam/HeatWantedTracker.java implements WantedKillTracker` REPLACES `KillComboWantedTracker` (deleted; its tests move here):
```java
public HeatWantedTracker(ChaseConfigLoader config, HeatLedger ledger, CrimeService crimes, KillCombo killCombo, NpcMarkManager marks,
                         BiPredicate<Player, Location> defendingOwnTurf)
countsForWanted(victim)    -> EntityMarks.countsForWanted(victim, marks)
recordKill(k, w, v, reset) -> FIRST, on both heat paths: a real-player victim (Player && !NpcSupport.isNpc) killed while
                              defendingOwnTurf.test(k, v.getLocation()) -> return (no crime, no combo, no star; docket TF-49,
                              owner ruling "turf-defender kills none", Gang & Turf P6.4). defendingOwnTurf (built in
                              installCoreSeams) = `Turf t = HeatLedger.contestedTurfAt(turfs, loc)`; `t != null &&
                              t.getOwnerGangId() != null && gangs.gangIdOf(k.getUniqueId()) != -1 && t.getOwnerGangId() ==
                              gangs.gangIdOf(k.getUniqueId())` (GangMembership API/data/gang/GangMembership.java:40; -1 when the
                              gang module is absent, so the exemption is inert without it). Then:
                              heat off: Settings.isWantedKillComboEnabled() ? killCombo.recordKill(k, w, v, reset) : trigger.accept(k)
                              heat on: POLICE mark -> crimes.commit(k, KILL_COP, v.getLocation()); real player (Player && !NpcSupport.isNpc)
                              -> KILL_PLAYER; CIVILIAN mark -> nothing (gangland-civilians publishes Kill_Civilian); anything else -> nothing
resetCombo(id)             -> killCombo.resetCombo(id)
onWantedTrigger(h)         -> killCombo.setOnWantedLevelTrigger(e -> h.accept(e.getPlayer())); ledger.setStarTrigger(h); keep h for heat-off
onComboReset(h)            -> killCombo.setOnComboReset(..)   (combo path only)
onVictimDeath(h)           -> killCombo.setOnPlayerDeath(h::accept)   (death still resets stars via WantedLevelListener -> KillCombo)
```
Installed ALWAYS by `CopsNCrooksModuleConfig.installCoreSeams()` (:360-374) in place of `KillComboWantedTracker` (TurfManager,
GangMembership and HeatLedger from `container.getInstance(..)`; @PostConstruct runs after every bean phase); the heat
switch is read per call, so toggling Heat.Enable on reload works. Routing rule shared with T6: `EntityDamageListener` sends
every counted kill to `wantedKills.recordKill(..)` whenever `wantedKills.isActive()` (the `&& Settings.isWantedKillComboEnabled()`
is removed at :110, :139, :145, :160); the delegate applies Kill_Combo.Enable. Without cops-n-crooks the else branch
(`handleWanted`) runs as today. The self-defence and posted-bounty exemptions sit upstream in EntityDamageListener (C16), so
they hold with or without cops-n-crooks and on both heat paths.
`CNC/listener/wanted/HeatListener.java` (`@ListenerHandler`, ctor `(HeatLedger)`): `CrimeCommittedEvent` MONITOR
ignoreCancelled -> `record`; `WantedLevelChangeEvent` MONITOR ignoreCancelled -> `onLevelChanged(.., event.getCause())`;
`WantedEndEvent` MONITOR -> `clear`. `HeatModuleConfig`: `@Bean HeatLedger heatLedger(ChaseConfigLoader, @Qualifier("online")
UserManager<Player>, CrimeService, CopManager, TurfManager)` and `@PostConstruct` `copManager.addCopAttackedHook((cop, p) ->
ledger.reportAssault(p, cop.getEntity().getUniqueId(), p.getLocation()))` (null entity -> skip).

## C13. Evasion clock (T8) - cops-n-crooks
```java
package org.luckyraven.gangland.copsncrooks.wanted.evasion;
public record EvasionSnapshot(EvasionState state, int level, int secondsLeft, @Nullable Location zoneCentre, double zoneRadius) { }
public final class EvasionClock implements WantedDecayPolicy {
	public EvasionClock(ChaseConfigLoader config, CopManager copManager, DetainmentService detainment, WantedStars stars,
	                    UserManager<Player> users, LongSupplier clock, Consumer<Event> callEvent)
	public boolean handlesDecay(Player player, Wanted wanted)  // Evasion.Enable && groupOf(id) has >= 1 cop isValid() and not RETURNING
	public void tick(Player player, @Nullable CopGroup group)  // registered with copManager.addAiTickHook; null/empty group -> clear
	public @Nullable EvasionSnapshot snapshot(UUID playerId)
	public void clear(Player player)                            // fires OFF once if the player was tracked, then forgets him
}
```
Per AI tick, in order: not enabled / not wanted / null or empty group / no live cop -> `clear` (OFF). Restrained (`detainment.isRestrained`) or
`group.tippedOffWithin(now, Lost_Sight ms)` -> hold (no progress, no state change, lastTick = now).
`unseen = group.getSquad().millisSinceSighting()`; `unseen < Lost_Sight_Seconds*1000` -> SEEN (progress 0; event on entry).
Otherwise SEARCHING: on entry zone centre = `squad.lastKnownLocation()` (player location if null), progress 0, event;
`dt = clamp(now - lastTick, 0, 1000)`; `inside` = same world and `distance(centre) <= radiusFor(level)`;
`progress += dt * (inside ? 1.0 : outsideZoneSpeed)`; when `progress >= secondsToDropFor(level)*1000`:
`stars.drop(user, dropMode == ALL_STARS ? level : 1, WantedCause.EVASION)`; when the drop ended the chase (level now 0: the
drop's WantedEndEvent already made EvasionListener `clear` the player and fire OFF) fire NOTHING more and stop; else fire
EVADED (post-drop level, same centre, new radius), progress 0, and the next tick re-enters SEARCHING (same centre). Else
`secondsLeft = ceil((need - progress) / 1000 / speed)`, fired when it changes. A shot or any sighting resets to SEEN.
`EvasionModuleConfig`: `@Bean EvasionClock evasionClock(ChaseConfigLoader, CopManager, DetainmentService, WantedStars,
@Qualifier("online") UserManager<Player>)` (clock `System::currentTimeMillis`, callEvent `Bukkit.getPluginManager()::callEvent`)
and `@PostConstruct` -> `wantedStars.installDecayPolicy(clock)` + `copManager.addAiTickHook(clock::tick)`.
`CNC/listener/wanted/EvasionListener.java`: `WantedEndEvent` MONITOR and `PlayerQuitEvent` MONITOR -> `clock.clear(player)`.

## C14. HUD inputs (T11 reads, never writes)
Raise card: `ledger.lastCrime(id)` + `messages.crimeName(..)` (null record or a raise whose cause is not CRIME ->
`crimeName("Unknown_Crime")`); tier for `event.getNewLevel()` via
`copSpawnManager.getTierForWantedLevel(level)` (CNC/npc/police/spawn/CopSpawnManager.java:151) and
`copLoader.getLoadedProvider().getTierConfig(tier)` -> `CopRadio.tierName(tier)` (CopRadio.java:190) and `tier.skipCuffing()`
(STANCE_SHOOT when true, else STANCE_CUFFS). Drop card by `event.getCause()`: EVASION -> CARD_DROP_EVASION, DECAY ->
CARD_DROP_DECAY, else CARD_DROP_OTHER; a drop to 0 (the getaway) with cause EVASION or DECAY still sends its drop card (title,
or chat when Title is off) before WantedEndEvent hides the bar. Tier display names are singular as shipped (cops.yml
`Display_Name` "&9Sergeant"), so the card reads "Sergeant inbound"; tests take the name from the tier config, not a literal
plural. Bar state from `WantedEvasionStateEvent`; no event yet = SEEN (red); `WantedHud.state(..)` ignores a player whose bar
is not shown (hidden or never shown), so a late event never re-creates a bar. Stars text =
`Wanted.buildStars(level, maxLevel)` (CORE/wanted/Wanted.java:43). `stars.suppressStarChat(() -> chase.get().hud().starCard())`
in the HUD listener's constructor replaces the chat line at EntityDamageListener.java:241-245 (T6 gates it on `isStarChat()`).

## C15. Charge sheet (T12)
`CNC/detainment/DetainedPlayer.java`: `private Double finePaid;` and `private Integer fineExtraSeconds;` (nullable, Lombok
getter/setter; existing ctors unchanged). `CNC/database/DetainmentTable.java`: nullable `fine_paid` (Double) and
`fine_extra_seconds` (Integer) appended LAST in the attributes and `getData`; `DetainmentRepository`'s legacy SQLite rebuild
(:65-80) gets BOTH edits: the `CREATE TABLE ..._migration (...)` DDL string gains `fine_paid REAL, fine_extra_seconds INTEGER`
before the PRIMARY KEY clause, AND the `columnsToCopy` varargs gain `"fine_paid", "fine_extra_seconds"` last
(`SchemaMigrations.rebuildSqliteTable` builds `INSERT .. (cols) SELECT cols` from the varargs: copy-list only -> the INSERT
names columns the temp table lacks and throws; DDL only -> the fine columns are silently dropped). Rows are read with the
guarded positional cursor (`v < result.length`, :95-115).
`JailIntakeService.admit(Player)` (CNC/detainment/intake/JailIntakeService.java:40-71) new order: read level (:50) and
`ledger.chaseCrimes(id)` BEFORE `clearWanted(id, WantedCause.ARREST)`; after `setWantedAtArrest`: when
`sheet.enabled() && !player.isDead()` (a death-commit already paid the hospital bill; DetainmentListener.onDeath:66-71):
`fine = sheet.fineFor(level)`, `pay = min(economy.getBalance(player), fine)` rounded down to cents, `paid = pay > 0 &&
economy.tryCharge(player, pay) == SUCCESS ? pay : 0`, `extra = sheet.extraSecondsFor(fine - paid)`; set
finePaid/fineExtraSeconds; sentence = `costs.computeSentenceSeconds(level) + extra`; send SHEET_HEADER, one SHEET_CRIME per
distinct crime id (count, first-seen order), SHEET_TOTAL, SHEET_PAID, and SHEET_EXTRA when extra > 0. A fine of 0 sends nothing.

## C16. Paid bounties (T13)
`CORE/bounty/Bounty.java`: the two ledger maps become `Map<String, BigDecimal>` keyed by
`public static String posterId(CommandSender s)` (Player -> uuid string, ConsoleCommandSender -> "console", else
`String.valueOf(s.getName())`); every existing public method keeps its CommandSender signature. Additions:
```java
public BigDecimal getPostedAmount()        // sum of the PAID figures = escrow players actually put up
public BigDecimal getNotoriety()           // max(0, amount - postedAmount): the server-made part
public void addNotoriety(BigDecimal x)     // amount += max(0, x)
public BigDecimal claimPosted()            // returns postedAmount; amount = max(0, amount - posted); clears both maps
public String serializeLedger()            // "" when empty; "id=posted:paid" joined by ';', toPlainString()
public void restoreLedger(@Nullable String s) // call AFTER setAmount. null = pre-0.15 row: ledger = {"legacy" = amount/amount}
                                           // (every old bounty counts as posted once); "" = empty; bad entries skipped
```
User table (IMPL/database/tables/player/UserTable.java:17-54): `new Attribute<>("bounty_posters", false, 4096, String.class)`,
`setCanBeNull(true)`, appended LAST; `getData` appends `serializeLedger()`. Keystone's schema diff adds the column with NULL
for existing rows (AbstractJdbcBackend.applySchema). Readers: UserDataLoader (:93-100, :150) and UserRepository.doLoadAll
(:44-66) read it as the last ordinal, null-safe, then `restoreLedger`; RemoveAccountListener (:96) copies it to the offline user.
Claim (EntityDamageListener.handlePlayerKills :100, claim block :122-148): `payAll = Settings.isBountyPayNotoriety()`;
`payout = payAll ? amount : postedAmount`; no payout when `gangs.alliedOrSame(killer, victim)` (GangMembership, API/data/gang/GangMembership.java:58).
payout > 0 -> deposit, `payAll ? resetBounty() : claimPosted()`, BOUNTY_CLAIMED, combo reset; when postedAmount was > 0 the
kill is NOT recorded as a crime (return before :145). Otherwise, self-defence check (below): a self-defence kill returns here
(no `handleBounty`, no crime path); else `handleBounty` + the normal crime path.
Self-defence (owner decision "Yes, unless it was self-defence", spec principle "Defending yourself ... is not a crime"), all in
EntityDamageListener: a private `Map<String, Long> firstHit` keyed `attackerUuid + ">" + victimUuid` and a
`Map<String, Long> lastExchange` keyed by the unordered pair; package-private `LongSupplier clock = System::currentTimeMillis`
(test seam). In `onPlayerEntityDeath`, before the death check, when the hit entity is a real player (`userManager.getUser(target)
!= null`): drop the pair when `now - lastExchange > FIGHT_WINDOW_MS` (30_000), then `firstHit.putIfAbsent(a>v, now)`,
`lastExchange.put(pair, now)`. `selfDefence(killer, victim)` = `firstHit(victim>killer) != null && (firstHit(killer>victim) == null
|| firstHit(victim>killer) < firstHit(killer>victim))`, with the pair dropped first when its last exchange is older than the
window. Every real-player kill forgets the pair. `// ponytail: fixed 30 s fight window, first hit wins, pairs that never meet
again linger until restart; a config key / quit-time prune when it shows`.
BountyExecutor (CORE/bounty/BountyExecutor.java:41-74) grows notoriety only: stop when notoriety <= 0 or >= Maximum;
`grown = min(Maximum, scale(notoriety * Multiple, level))`; event amount = grown - notoriety; `addNotoriety(grown - notoriety)`;
timers start with `start(false)` and `UserBountyEvent(false, ..)` (WB-06/US-18 bounty half).

## C17. Shot noise (T15)
`CNC/listener/police/ShotNoiseListener.java` (`@ListenerHandler`, ctor `(CopManager, CopLoader, CopRadio,
@Qualifier("online") UserManager<Player>)`), `@EventHandler(priority = MONITOR, ignoreCancelled = true)
onWeaponShoot(org.luckyraven.bartizan.api.event.WeaponShootEvent)`: shooter must be a Player and `!NpcSupport.isNpc`;
`r = settings.radiusFor(event.getWeapon().getCategory().name())`, r <= 0 -> return; shooter wanted; `group = copManager.groupOf(id)`;
nearest cop of the group (isValid, not RETURNING, same world) within r of the shooter, none -> return;
`group.getSquad().reportSighting(shooter.getLocation())` (Keystone NpcSquad.java:186, silent) and
`copRadio.sayAs(group, nearest, "Shots_Fired", Map.of())` (CopRadio.java:143).

## C18. Every star change site and its cause (T6 wires, C2/C3 methods)
| Site | Today | 0.15 |
|---|---|---|
| EntityDamageListener.handleWanted :206-246 | incrementLevel + new WantedExecutor async | `stars.raise(user, wanted.getIncrements(), CRIME)`; chat line :241-245 only when `stars.isStarChat()` |
| EntityDamageListener.onPlayerDeathResetWanted :196-204 | reset() | null-guard the player, `reset(DEATH)` |
| WantedAspect INCREASE/REMOVE/CLEAR (IMPL/sign/aspect/WantedAspect.java:28-55) | setLevel / decrement loop / reset | `raise(user, amount, SIGN)` / `setLevel(max(0, l - amount), SIGN)` / `reset(SIGN)` |
| WantedAddCommand :49, :87 | setLevel | `raise(user, amount, ADMIN)`, report the returned count |
| WantedRemoveCommand :50, :87 / WantedClearCommand :46, :71 | setLevel | `setLevel(.., ADMIN)` |
| UserDataLoader :105 + :165-170 | setLevel off-thread + async timer | `stars.restore(user, wanted)`; the timer block goes |
| WantedClearContract.clearWanted (CNC/detainment/wanted/WantedClearContract.java:24) | setLevel(0) | add `default void clearWanted(UUID id, WantedCause cause)`; Gangland impl -> `setLevel(0, cause)`; JailIntakeService :54 ARREST, BribeService :70 BRIBE |
| WantedExecutor tick | decrementLevel | `stars.drop(ctx, 1, DECAY)` (T1) |
| EvasionClock | - | `stars.drop(user, n, EVASION)` (T8) |
| CivilianDeathRewardListener :48 | incrementLevel | `crimes.commit(killer, KILL_CIVILIAN, killer.getLocation())` unless hostile+COMBAT (T7) |
Injection: `WantedStars` reaches EntityDamageListener (ctor), WantedCommand -> its three subs (ctor), SignManager ->
WantedSign -> WantedAspect (ctors; bean IMPL/config/GameplayConfig.java:255), UserDataLoader (ctor; bean DataConfig.java:97-100).

## C19. Threads
Main thread: CrimeService.commit, WantedStars.raise/drop/startDecayClock, every listener above, EvasionClock.tick (CopManager AI
task is sync, CopManager.java:452), HUD timer (`runTaskTimer`). Off-thread-safe: `WantedStars.restore` and `drop` (they hop).
