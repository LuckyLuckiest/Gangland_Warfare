# Map: wanted-core (worktree E:/Programming/java/wt/gangland-0.15.0, master 16d1f064)

Paths are relative to the worktree. `core` = gangland-core/src/main/java/org/luckyraven/gangland/core. `impl` = gangland-impl/src/main/java/org/luckyraven/gangland.
Graph (fresh) confirms: WantedExecutor (core/wanted/WantedExecutor.java L18), GanglandWantedSettings (impl L12), EntityDamageListener.handleWanted (L206).

## 1. Wanted model - core/wanted/Wanted.java (Lombok @Data, 116 lines)
- Fields L20-32: plugin(no getter), level, increments(no setter), maxLevel, wanted(boolean), owner(Player), repeatingTimer(no setter).
- ctor L34 `Wanted(JavaPlugin, int increments, int maxLevel)`. Built only in User ctor (core/user/User.java:57) from IdentitySettings.getWantedLevelIncrement()/getWantedMaximumLevel() (= Settings Wanted.Level.Increment/Maximum). `wanted.setOwner(user.getPlayer())` User.java:62 (null for OfflinePlayer users, so NO events for them).
- `static buildStars(int level,int maxLevel)` L43: clamp(level,0,max) "★" + rest "☆"; max<0 gives "". `getLevelStars()` L100.
- `createTimer(long seconds, Consumer<RepeatingTimer>)` L51: stopTimer() then new RepeatingTimer(plugin, seconds*20L, consumer); caller starts it. One decay timer per player.
- `setLevel(int)` L59 is THE single choke point (every raise/lower/clear ends here):
  - newLevel = clamp [0,maxLevel]. If owner!=null && old!=new: fires WantedLevelChangeEvent(owner,this,old,new) BEFORE mutating, only on the primary thread; cancelled -> return, level unchanged. Off-thread: `Bukkit.getScheduler().runTask(plugin, () -> setLevel(level))` and RETURN (level stale until the sync task). If old==new off-thread: no reschedule, mutates inline.
  - Then level set, wanted = level>0. owner!=null: 0->N fires WantedStartEvent(owner,this,level) L85; N->0 fires WantedEndEvent(owner,this) L87. N->M (both >0): only the change event. Start/End are not cancellable and carry NO cause/origin.
  - owner==null: pure clamp, no events (how WantedTest runs).
- `incrementLevel()` L92 = setLevel(level+increments). `decrementLevel()` L96 = setLevel(level-1). `reset()` L104 = setLevel(0)+stopTimer(). `stopTimer()` L109. No overloads, no origin tag.
- setLevel(0) from /wanted clear, arrest, bribe does NOT stop the decay timer (only reset() does); the timer self-stops on its next tick via isWanted().

## 2. Decay - core/wanted/WantedExecutor.java (100 lines), extends core/feature/Executor (abstract `Timer createTimer()` / `execute(Timer)`)
- ctor L24 `(JavaPlugin, WantedEvent, WantedContext, WantedSettings)`.
- `createTimer()` L34: pow = settings.isTimerMultiplierEnabled() ? timerMultiplierAmount^wanted.getLevel() : 1; interval=(long)(timerTime*pow) s; returns wanted.createTimer(interval, this::execute). Interval is frozen at the level when the timer was built; fixed repeat period. Defaults 120*1.1^level (5 stars = 193 s).
- `execute(Timer)` L49 (ASYNC: callers `timer.start(true)`):
  1. L52 isWanted(timer,wanted): !wanted.isWanted() -> timer.stop(); return.
  2. L54-60 moneyTaken = takeAmount>0 ? Currency.multiply(takeAmount, takeMoneyMultiplier^level) : 0.
  3. L63-66 `event.setCancelled(false)`; callEvent(event) (the WantedEvent, one instance reused per timer); cancelled -> return (this tick only; pinned by test).
  4. L68-70 `if moneyTaken!=0: moneyTaken = context.withdraw(moneyTaken)` (core/user/User.java:100-114: withdraw all, or on EconomyException drain the balance; never throws). Charged BEFORE the decrement; a cancelled WantedLevelChangeEvent still charges.
  5. L75 newLevel=max(0,level-1) precomputed (setLevel reschedules to main when off-thread, getLevel would be stale); L77 `wanted.decrementLevel()`.
  6. L79-83 message from settings.getWantedDecreasedMessageTemplate() with %level%/%stars%, context.sendMessage; L85-87 money-loss message `settings.formatMoneyLoss(moneyTaken)` only if !=0; L89 second isWanted() reads the stale flag (timer stops one tick later).
- No policy hook; no cause on decrement; always exactly -1 per tick.
- WantedContext (core/wanted/WantedContext.java): getWanted(), withdraw(BigDecimal)->actual withdrawn, sendMessage(String). Implemented by User (core/user/User.java:32 `User<T> implements BountyContext, WantedContext, EconomyOwner`).
- WantedSettings (core/wanted/WantedSettings.java L13): isTimerMultiplierEnabled, getTimerMultiplierAmount, getTimerTime, getTakeMoneyAmount, getTakeMoneyMultiplier, getWantedDecreasedMessageTemplate, formatMoneyLoss. No isTakeMoneyEnabled/formula yet.
- GanglandWantedSettings (impl/file/configuration/wanted/GanglandWantedSettings.java, 48 lines): static delegates, L16 Settings.isWantedTimerMultiplierEnabled, L21 getWantedTimerMultiplierAmount, L26 getWantedTimerTime, L31 getWantedTakeMoneyAmount, L36 getWantedTakeMoneyMultiplier, L41 Messages.WANTED_DECREASED.toString(), L46 formatMoneyLoss = "&c&l-"+Settings.getMoneySymbol()+Settings.formatAmount(amount). Static reads each call, so live timers see config changes. Bean impl/config/FileConfig.java:104 `wantedSettings()`; injected into EntityDamageListener (field L46) and UserDataLoader (L48,54).
- Only starters of a decay clock:
  - impl/listener/player/EntityDamageListener.java:206-221 handleWanted: `if (Settings.isWantedTimerEnabled() && wanted.isWanted()) new WantedExecutor(gangland, wantedEvent, damagerUser, wantedSettings).createTimer().start(true)`; `new WantedEvent(true, wanted)` L208.
  - impl/data/user/UserDataLoader.java:165-170 at login (same guard); level restored at :105 and UserRepository.java:63 via setLevel.
  - NOT started by: CivilianDeathRewardListener, [WANTED] sign INCREASE, /glw wanted add. Stars from those only decay if a timer already runs (with its old interval) or after relog.
- Timer cleanup: impl/listener/player/RemoveAccountListener.java:53 (reload path), :69 (PlayerQuitEvent) `wanted.stopTimer()`; quit copies level to the offline user L93 (owner null, silent) and saves via the user repository. Stars persist across logout/restart.

## 3. Events - core/events/wanted/ (Lombok @Getter)
- WantedEvent (L11, Cancellable, ctor `(boolean async, Wanted)`): the decay-tick event. Fired only by WantedExecutor L64. Built async=true at EntityDamageListener:208 and UserDataLoader:167. NO listener anywhere in the repo.
- WantedLevelChangeEvent (L12, Cancellable): player, wanted, oldLevel, newLevel. Fired Wanted.java:65. Listener cops-n-crooks listener/detainment/CopListener.java:61 -> CopManager.onWantedLevelChange (CopManager.java:175: old==0 -> onWantedStart, new==0 -> onWantedEnd).
- WantedStartEvent (L11): player, wanted, wantedLevel. Fired Wanted.java:85. CopListener.java:41 -> CopManager.onWantedStart (L99: targetingManager.registerWanted, radio dispatch, startSpawnTask).
- WantedEndEvent (L11): player, wanted. Fired Wanted.java:87. CopListener.java:51 -> CopManager.onWantedEnd (L132; Stand_Down announced once per episode L150).
- Player-bearing events use `super(false)`. CopListener is the only consumer of the three.

## 4. Kill-combo seam - core/wanted/WantedKillTracker.java (interface L14), WantedKillTrackers.java (holder, 70 lines)
- Interface: countsForWanted(Entity), recordKill(Player,Wanted,Entity,int resetAfter), resetCombo(UUID), onWantedTrigger/onComboReset(Consumer<Player>), onVictimDeath(Consumer<UUID>).
- Holder: volatile delegate; isActive()=delegate!=null; install() L31 replays pre-registered handlers; everything inert (countsForWanted false) with no delegate. Core bean impl/config/DataConfig.java:137. Module installs it in cops-n-crooks config/CopsNCrooksModuleConfig.java:371 (`@PostConstruct installCoreSeams` L357) with `new KillComboWantedTracker(KillCombo, NpcMarkManager)` (seam/KillComboWantedTracker.java; countsForWanted -> EntityMarks.countsForWanted).
- KillCombo (cops-n-crooks combo/KillCombo.java): recordKill L41 adds hardcoded 1 point; fires onWantedLevelTrigger when pointKillCount >= thresholds[min(level,size-1)] (shouldTriggerWantedLevel L126; thresholds = Wanted.Kill_Combo.Kill_Counter, NumberUtil.resizeLinear to maxLevel). handlePlayerDeath L71 = resetCombo + onPlayerDeath callback. Tracker timer = Reset_After s, restarted per kill.
- Wiring: EntityDamageListener.setupKillComboCallbacks L172 (ctor L58): onWantedTrigger -> handleWanted(user); onComboReset -> chat "&e&lKill combo reset!" (hardcoded); onVictimDeath -> onPlayerDeathResetWanted (L196).

## 5. Every caller that RAISES stars
| Site | File:line | Mechanism | Starts decay timer? | Notes |
|---|---|---|---|---|
| NPC/mob kill | impl/listener/player/EntityDamageListener.java handleMobKills L151-166 | combo active && Settings.isWantedKillComboEnabled() -> wantedKills.recordKill; else handleWanted | via handleWanted | countsForWanted false if CNC not loaded |
| Cop/NPC "player" (no User) kill | same, handlePlayerKills L100-120 | same branch | same | mobKills++ |
| Real player kill | same L120-150 (bounty claim ~L126-141, else handleBounty) | same branch | same | |
| handleWanted | same L206-247 | `wanted.incrementLevel()` L211 (+Level.Increment); starts WantedExecutor timer; auto-bounty getAutoBountyIncrease L222-226; BountyExecutor timer; chat "&c&lWANTED LEVEL: <stars> (Bounty: +$x)" L241-245 hardcoded | yes (if Repeating_Timer.Enable) | Settings.isWantedEnabled (Wanted.Enable) is never read anywhere |
| Civilian kill reward | gangland-features/gangland-civilians/.../listener/npc/CivilianDeathRewardListener.java:31-50 (CivilianDeathEvent, NORMAL, ignoreCancelled) | XP, then `user.getWanted().incrementLevel()` L48 unless civilian isHostile() && state COMBAT | NO | bypasses KillCombo/Kill_Combo.Enable; no bounty, no message |
| [WANTED] sign INCREASE | impl/sign/aspect/WantedAspect.java:29-38 | `setLevel(current + amount)` (sign amount, not * increment) | NO | canExecute true for INCREASE (L62-79) |
| /glw wanted add [n] | impl/command/sub/wanted/WantedAddCommand.java:49 (+1), :87 (amount) | setLevel(min(max, level+amount)) | NO | amount<=0 refused (MUST_BE_NUMBERS); reports realAmount |
| Login restore | UserDataLoader.java:105, UserRepository.java:63 | setLevel(saved) | UserDataLoader starts one L165 | Start event fires at join (PlayerBootstrapService.java:31 comment) |
Command tree: WantedCommand.java:23 `super(gangland,"wanted",true)`; subs add L56, remove L57, clear L58. No `/wanted set`.

## 6. Every caller that LOWERS/clears stars
- WantedExecutor decay (-1 per tick), section 2.
- [WANTED] sign REMOVE: WantedAspect.java:39-47 loops decrementLevel() x amount; CLEAR L49-55 `wanted.reset()`. Sign aspects = MoneyAspect(WITHDRAW) then WantedAspect (impl/sign/type/WantedSign.java:37-40); canExecute for REMOVE/CLEAR requires level>0 (WantedAspect.java:70-75). Registered impl/sign/SignManager.java:149-156; validator sign/validation/WantedSignValidator.java.
- /glw wanted remove: WantedRemoveCommand.java:50 (1) and :87 (amount) `setLevel(max(0, level-amount))`. /glw wanted clear: WantedClearCommand.java:46 self, :71 target `setLevel(0)`.
- Death: EntityDamageListener.onPlayerDeathResetWanted L196-204 `deadUser.getWanted().reset()`; reached ONLY via KillCombo.handlePlayerDeath <- cops-n-crooks listener/player/WantedLevelListener.java:21 (PlayerDeathEvent, skips NPCs via NpcSupport.isNpc; null killCombo = no-op) and :32 (PlayerDownedEvent). impl PlayerDeathListener (L53 onPlayerDeath, L92 onPlayerDowned, handleMoney L131) has NO wanted-clearing code (only the "wanted" formula variable L220).
- Arrest: cops-n-crooks detainment/intake/JailIntakeService.java:50-54 admit: level read via WantedClearContract, inventory snapshot, `clearWanted` L54, then `setWantedAtArrest(level)` L61 and sentence from costs.computeSentenceSeconds(level). Cuffing alone keeps stars (settings.yml ~L416 comment).
- Bribe: detainment/bribe/BribeService.java: handcuff cost via costs.computeHandcuffBribeCost(level) L45/L57, economy.tryCharge L59, then `wantedClearContract.clearWanted(playerId)` L70, release pipeline, cop RETURNING. Jail bribe ~L88-108: tryCharge, 35% roll `costs.getJailBribeSuccessChance()` ~L101; failure extends sentence.
- Contract: cops-n-crooks detainment/wanted/WantedClearContract.java (getWantedLevel, clearWanted) <- integration/detainment/GanglandWantedClearContract.java:31-37 `wanted.setLevel(0)` (online UserManager lookup; offline = no-op). Bean CopsNCrooksModuleConfig.java:180.
- Quit: timers stopped, level saved, no decrement.

## 7. Config keys
gangland-api/src/main/java/org/luckyraven/gangland/file/configuration/Settings.java: fields L93-99 (wantedTakeMoneyAmount BigDecimal; wantedTakeMoneyMultiplier, wantedTimerMultiplierAmount double; wantedEnabled, wantedTimerEnabled, wantedTimerMultiplierEnabled, wantedKillComboEnabled boolean; wantedTimerTime, wantedLevelIncrement, wantedMaximumLevel, wantedKillComboResetAfter int; wantedKillCounter List<Integer>). Load L535-554.
gangland-impl/src/main/resources/settings.yml, `Wanted:` L271:
| Key | settings.yml | Settings.java | In-code default |
|---|---|---|---|
| Wanted.Enable | L272 true | L543 | true (NEVER read) |
| Take_Money.Amount | L275 50 | L544 money(...,"50") | 50 |
| Take_Money.Multiplier | L278 5 | L545 | 5 |
| Repeating_Timer.Enable | L281 true | L546 | true |
| Repeating_Timer.Time | L283 120 | L547 | 120 |
| Repeating_Timer.Multiplier.Enable / Amount | L286 true / L287 1.1 | L548-549 | true / 1.1 |
| Level.Increment / Maximum | L290 1 / L291 5 | L550-551 | 1 / 5 |
| Kill_Combo.Enable / Reset_After / Kill_Counter | L294 true / L296 10 / L298+ [2,5,10,15,20] | L552-554 | true / 10 / list |
Related price-by-star keys: Handcuff_Bribe.Per_Wanted_Level settings.yml:429 (250.0; Settings.java:678), Bail Per_Wanted_Level L434 (1000; :681), Jail_Bribe L440 (500; :684), Sentence.Per_Wanted_Level_Seconds L447 (60; :689). Death.Money L206-227 (Lose_Money L218, Formula L225, Threshold L227).
Messages: gangland-api Messages.java L305-310 (WANTED, WANTED_INCREASED, WANTED_DECREASED, NOT_WANTED, WANTED_CLEARED, PAID_WANTED); text gangland-impl/src/main/resources/message/message_en.yml:632-638 (Decreased: "Your wanted level has been decreased &b%stars%&7." used as executor template with %level%/%stars%); message_es.yml:494.

## 8. Existing tests and what they pin
- gangland-core/src/test/.../core/wanted/WantedTest.java: buildStars (max chars, negative max "", level>max clamp, negative level), ctor zero/not wanted, setLevel clamp with NO owner, wanted flag on 0->N and N->0, incrementLevel adds `increments` clamped, decrementLevel -1 floor 0, getLevelStars delegates, reset zeroes with null timer. Never sets an owner, so events/thread-hop untested.
- WantedExecutorTest.java (one test): cancelled tick keeps level 3, next tick -> 2 (event flag reset). Wanted(null,1,5), mocked context/settings (getTakeMoneyAmount=ZERO), Bukkit static mock. Untested: money take, multiplier, message, interval, isWanted stop.
- WantedKillTrackersTest.java: inert without delegate; handlers registered before install are replayed; forwards after install.
- gangland-impl/src/test/.../command/sub/wanted/WantedAmountGuardTest.java: add|remove refuse non-positive amount, level unchanged, MUST-BE-NUMBERS.
- impl config/HolderSeamBeanTypeTest.java:44 pins DataConfig.wantedKillTrackers() return type.
- cops-n-crooks: seam/KillComboWantedTrackerTest, combo/KillComboTrackerTest; CopManager*Test/CopRadio*Test use Wanted only as input; DetainmentCostsContractTest (per-level costs).
- NO tests: EntityDamageListener/handleWanted, CivilianDeathRewardListener, WantedAspect/WantedSign, GanglandWantedSettings, GanglandWantedClearContract, BribeService, JailIntakeService, WantedLevelListener, Wanted keys in SettingsTest, Wanted events with an owner.

## 9. Spec drift (SPEC-0.15.md vs this tree)
1. Line drift, harmless: spec "settings.yml:273-279" Take_Money: Wanted: is L271, Take_Money L273-278, Repeating_Timer L280+. Handcuff bribe spec 426-428 -> 427-429; Jail_Bribe 437-441 -> 438-440. GanglandWantedSettings spec ":30-31" -> take-amount method L29-32 (":29-37", ":44-47" correct). WantedExecutor :54-60/:68-70/:85-87/:57 all correct. PlayerDeathListener spec :81-85/:91-114/:116-119/:131-157/:148-154 -> actual 82-84 / 92-110 / 118 / 131-155 / 148-154. EntityDamageListener handleWanted :206 correct; spec "message at :241-245" correct.
2. Spec says WantedEndEvent will "carry the cause": today Start/End/Change carry no origin/cause and Wanted has no overloads; only Wanted.setLevel emits, so a tag must be threaded through incrementLevel/decrementLevel/reset/setLevel.
3. Spec says every increment "guarantees a decay clock". Today only handleWanted and login start one; CivilianDeathRewardListener, sign INCREASE and /wanted add do not. Repeating_Timer.Enable gates only those two starters.
4. Wanted.Enable exists (Settings:543, settings.yml:272) but is never read: not a working master switch.
5. "Keeps Repeating_Timer as safety net": there is no policy seam; interval is frozen at timer creation level, not recomputed on level change.
6. Increment semantics differ by site: handleWanted/civilian use Level.Increment; sign INCREASE and /wanted add use the raw amount.
7. "Death still wipes stars": today the wipe lives in impl EntityDamageListener.onPlayerDeathResetWanted but triggers only through the cops-n-crooks KillCombo callback (WantedLevelListener). impl PlayerDeathListener never touches wanted; without cops-n-crooks (no Bartizan) death does not clear stars.
8. Money in WantedExecutor is withdrawn BEFORE decrement and survives a cancelled WantedLevelChangeEvent; spec does not mention this ordering (relevant to "policy cannot touch money").
9. GanglandApi.VERSION = "2.0" (gangland-api GanglandApi.java:30): next minor 2.1, as the owner ruling says; spec body still says 2.2 in places.
10. Commands are add/remove/clear only (no set).
11. Spec says existing settings.yml is never rewritten, so in-code defaults decide upgrades: matches Settings.java (money(...,"50") etc.).

## 10. Seams (no proposals)
- Wanted.setLevel (core/wanted/Wanted.java:59): single mutation + event point; thread-hop L68-75 must survive.
- WantedExecutor.execute L49-90 / createTimer L34: decay driver; money block L54-70, message L79-87 isolated; test seam = WantedSettings mock + WantedContext.
- WantedKillTrackers holder pattern (core/wanted/, DataConfig.java:137, install CopsNCrooksModuleConfig.java:371): template for a decay-policy / star-source holder.
- Two timer starters (EntityDamageListener.java:215, UserDataLoader.java:168) vs three non-starting raisers (CivilianDeathRewardListener:48, WantedAspect:32, WantedAddCommand:49/87).
- Clear sites by cause: GanglandWantedClearContract:36 (arrest + bribe share it), WantedAspect:41/50, commands (Clear:46/71, Remove:50/87), onPlayerDeathResetWanted:203, decay in executor.
- WantedSettings/GanglandWantedSettings + Settings.java:535-554 + settings.yml:271-306 + Messages.WANTED_DECREASED for any new keys.
- CopListener.java:41/51/61 -> CopManager (only consumers of Start/End/Change events).
