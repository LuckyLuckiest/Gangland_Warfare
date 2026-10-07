# WS5 fix — T-55 (P0 ambiguous UserManager bean)

Worktree `E:\Programming\java\wt\gangland-0.10.0-ws5`, reset to branch `0.10.0-ws5` at the `0.10.0` tip
`fcc41a32` (`git checkout -B 0.10.0-ws5 0.10.0`) per instruction — this tip already carried the T-53/T-54 fixes.
Nothing committed this round; `fix-T55-package.diff` is the sole change artifact (92 files, working tree only,
nothing staged/committed).

## Root cause

`GangConfig`'s 4 `@Bean` methods (`gangPlaceholderContribution`, `gangOptionContribution`,
`gangDebugContribution`, `gangItemSourceContribution`) each took a bare `UserManager<Player> userManager`
parameter with no `@Qualifier`. `DataConfig` registers **two** `UserManager` beans under the same erased raw
type (`@Bean(name = "online", isGeneric = true)` / `@Bean(name = "offline", isGeneric = true)`) — Java generics
erase `UserManager<Player>` and `UserManager<OfflinePlayer>` to the same `UserManager.class` key, so any
consumer that doesn't name which one it wants is ambiguous.

**Confirmed by reading `BeanFactory.resolveParameter`** (`E:\Programming\java\Keystone\keystone-bean\src\main\java\org\luckyraven\keystone\bean\BeanFactory.java:499-532`) that this is specific to `@Bean` method
parameter resolution: it calls `container.getAllInstances(type)` and throws
`IllegalStateException("Ambiguous bean for parameter '...' of type ...: N candidates registered. Add
@Qualifier to disambiguate.")` the moment more than one distinct candidate comes back (line 524-528) — the
exact text and mechanism the 8-module smoke hit.

**Also confirmed the asymmetry that shaped the sweep's severity assessment**: constructor injection (used for
every `@ListenerHandler`/`@CommandHandler`-scanned class, and for `@Configuration` classes' own construction)
goes through a **different** path, `DependencyContainer.resolveConstructorParameters` →
`DependencyContainer.getInstance(Class)`, which is documented "Returns the first registered instance if
multiple exist" (line 53-62 of `DependencyContainer.java`) — it never throws. Since `DataConfig`'s "offline"
bean depends on `@Qualifier("online") UserManager<Player>` as one of its own parameters, `BeanGraph`'s
topological sort structurally guarantees "online" is always registered before "offline", so every unqualified
constructor-injected `UserManager<Player>` consumer in this codebase was already deterministically resolving
to the correct instance — not by luck, but by that dependency edge. This is why only the 4 `@Bean`-method
consumers actually crashed boot; the 90-file constructor-injection sweep below is a **latent, non-crashing**
hardening fix (silent "first registered wins" instead of an explicit, order-independent `@Qualifier`), done
because it was explicitly asked for and because it removes a landmine: any future change to `DataConfig`'s
bean-graph shape (or the two beans' own registration order) would silently start mis-wiring every unqualified
`UserManager<OfflinePlayer>` consumer with the online instance.

## Fix 1 — GangConfig's 4 @Bean methods

All 4 now carry `@Qualifier("online")`. Checked what the pre-WS5 predecessor of each used, before defaulting to
"online" by type-match:

| `GangConfig` method | Pre-WS5 predecessor | Its qualifier |
|---|---|---|
| `gangItemSourceContribution` | `GameplayConfig.gangItemSourceProvider` (gangland-impl, commit `837966c3`, line 142) constructing `GangItemSourceProvider` | `@Qualifier("online")` |
| `gangDebugContribution` | `DebugCommand`'s own constructor (`837966c3`, line 77) — handled `gang-data`/`member-data`/`rank-data` debug entries inline before WS5 split them out | `@Qualifier("online")` |
| `gangPlaceholderContribution` | `WiringConfig.ganglandPlaceholder` (`837966c3`, line 100) constructing `GanglandPlaceholder`, which answered `gang_*` placeholders before WS5 split them into `GangPlaceholderContribution` | `@Qualifier("online")` |
| `gangOptionContribution` | None — `/glw option gang rank` is new in WS5 (`OptionCommand.java` at `837966c3` has zero `Rank`/`UserManager` references) | Matched by type + body: `GangOptionContribution.userManager.getUser(player)` is called on an online `Player`, same pattern as its 3 siblings |

Every one of these 4 parameters is declared `UserManager<Player>` — the exact generic type `DataConfig`'s
"online" bean returns (`public UserManager<Player> userManager(...)`), so "online" is also the only
type-correct choice independent of the archaeology above.

## Fix 2 — reactor-wide sweep

**Other ambiguous bean types**: `grep -rn "@Bean(name" --include="*.java" .` across the whole reactor returns
exactly 2 hits, both in `DataConfig.java` (the "online"/"offline" `UserManager` pair). `UserManager` is the
**only** bean type registered more than once under names anywhere in this codebase — nothing else needed
checking.

**UserManager consumers swept**: every constructor (the `@ListenerHandler`/`@CommandHandler`/plain
constructor-injection path) and every `@Bean` method parameter in `gangland-impl/src/main` and
`gangland-features/*/src/main` that takes `UserManager<Player>` or `UserManager<OfflinePlayer>` with no
`@Qualifier`. Found and fixed **100 unqualified parameters across 90 files** (plus `GangConfig`'s 4, reported
separately above). Verified with a precise parser (not a bare grep) that walks each constructor's/`@Bean`
method's actual parameter list via brace/paren matching, so it does not misfire on: field declarations,
`@Bean` methods' own return-type declarations (e.g. `DataConfig.userManager()`/`offlineUserManager()`
themselves), or plain instance/static helper methods that receive an already-resolved manager as a normal
argument (`BalanceCommand.cachedNames(userManager, offlineUserManager)`, `NameLookup.findByName(...)` — these
aren't DI injection points; the real injection point is whatever constructor populated the fields passed into
them).

Qualifier choice is mechanical and unambiguous: every `UserManager<Player>` parameter got `@Qualifier("online")`
(matching `DataConfig`'s `UserManager<Player> userManager(...)` bean exactly); every `UserManager<OfflinePlayer>`
parameter got `@Qualifier("offline")` (matching `UserManager<OfflinePlayer> offlineUserManager(...)` exactly) —
there is no type-safe alternative assignment for either. All 90 files are pre-existing (impl files predate this
whole gate; the gang/mail/gadget/npc-shops module files were created earlier in WS5/the Bartizan split, not this
round), so there was no "pre-WS5 predecessor" archaeology to do beyond the type-match rule itself — this was the
"fix every hit the same way and list them" instruction, not the "check what the pre-WS5 class used" one (that
was specific to the 4 hand-picked `GangConfig` methods above).

Full file-by-file table (module-prefixed path, parameter type, qualifier applied):

```
cops-n-crooks:integration/detainment/GanglandDetainmentEconomyContract.java     UserManager<Player>         online
cops-n-crooks:integration/detainment/GanglandWantedClearContract.java          UserManager<Player>         online
gadget:command/CarGiveCommand.java                                             UserManager<Player>         online
gadget:command/CarInfoCommand.java                                             UserManager<Player>         online
gadget:sign/CarBuySign.java                                                    UserManager<Player>         online
gadget:sign/CarSellSign.java                                                   UserManager<Player>         online
gadget:sign/CarSignContribution.java                                           UserManager<Player>         online
gang:command/sub/gang/GangBalanceCommand.java                                  UserManager<Player>         online
gang:command/sub/gang/GangColorCommand.java                                    UserManager<Player>         online
gang:command/sub/gang/GangCreateCommand.java                                   UserManager<Player>         online
gang:command/sub/gang/GangDeleteCommand.java                                   UserManager<Player>         online
gang:command/sub/gang/GangDemoteCommand.java                                   UserManager<Player>         online
gang:command/sub/gang/GangDepositCommand.java                                  UserManager<Player>         online
gang:command/sub/gang/GangDescriptionCommand.java                              UserManager<Player>         online
gang:command/sub/gang/GangDisplayCommand.java                                  UserManager<Player>         online
gang:command/sub/gang/GangKickCommand.java                                     UserManager<Player>         online
gang:command/sub/gang/GangLeaveCommand.java                                    UserManager<Player>         online
gang:command/sub/gang/GangMembersCommand.java                                  UserManager<Player>         online
gang:command/sub/gang/GangPromoteCommand.java                                  UserManager<Player>         online
gang:command/sub/gang/GangRenameCommand.java                                   UserManager<Player>         online
gang:command/sub/gang/GangTransferCommand.java                                 UserManager<Player>         online
gang:command/sub/gang/GangWithdrawCommand.java                                 UserManager<Player>         online
gang:command/sub/gang/ally/GangAllyAbandonCommand.java                         UserManager<Player>         online
gang:command/sub/gang/ally/GangAllyCommand.java                                UserManager<Player>         online
gang:gang/command/debug/GangDebugContribution.java                            UserManager<Player>         online
gang:gang/menu/GangMenuItemSourceContribution.java                            UserManager<Player>         online
gang:gang/placeholder/GangPlaceholderContribution.java                        UserManager<Player>         online
mail:command/GangAllyMailContribution.java                                     UserManager<Player>         online
mail:command/GangMailContribution.java                                        UserManager<OfflinePlayer>  offline
mail:command/GangMailContribution.java                                        UserManager<Player>         online
mail:command/ally/GangAllyAcceptCommand.java                                   UserManager<Player>         online
mail:command/ally/GangAllyPendingCancelCommand.java                            UserManager<Player>         online
mail:command/ally/GangAllyPendingCommand.java                                  UserManager<Player>         online
mail:command/ally/GangAllyRejectCommand.java                                   UserManager<Player>         online
mail:command/ally/GangAllyRequestCommand.java                                  UserManager<Player>         online
mail:command/invite/GangInviteAcceptCommand.java                               UserManager<Player>         online
mail:command/invite/GangInviteCancelCommand.java                               UserManager<OfflinePlayer>  offline
mail:command/invite/GangInviteCancelCommand.java                               UserManager<Player>         online
mail:command/invite/GangInviteCommand.java                                     UserManager<OfflinePlayer>  offline
mail:command/invite/GangInviteCommand.java                                     UserManager<Player>         online
npc-shops:integration/GanglandBankerEconomy.java                               UserManager<Player>         online
npc-shops:integration/GanglandTraderEconomy.java                               UserManager<Player>         online
impl:bootstrap/PeriodicalUpdates.java                                          UserManager<OfflinePlayer>  offline  (both constructors)
impl:bootstrap/PeriodicalUpdates.java                                          UserManager<Player>         online   (both constructors)
impl:bootstrap/PlayerBootstrapService.java                                     UserManager<OfflinePlayer>  offline
impl:bootstrap/PlayerBootstrapService.java                                     UserManager<Player>         online
impl:command/sub/bank/BankBalanceCommand.java                                  UserManager<Player>         online
impl:command/sub/bank/BankCreateCommand.java                                   UserManager<Player>         online
impl:command/sub/bank/BankDepositCommand.java                                  UserManager<Player>         online
impl:command/sub/bank/BankResetCapCommand.java                                 UserManager<Player>         online
impl:command/sub/bank/BankWithdrawCommand.java                                 UserManager<Player>         online
impl:command/sub/bounty/BountyClearCommand.java                                UserManager<Player>         online
impl:command/sub/bounty/BountySetCommand.java                                  UserManager<Player>         online
impl:command/sub/economy/EconomyDepositCommand.java                            UserManager<Player>         online
impl:command/sub/economy/EconomyResetCommand.java                              UserManager<Player>         online
impl:command/sub/economy/EconomySetCommand.java                                UserManager<Player>         online
impl:command/sub/economy/EconomyWithdrawCommand.java                           UserManager<Player>         online
impl:command/sub/fuel/FuelAddCommand.java                                      UserManager<Player>         online
impl:command/sub/fuel/FuelDefuelCommand.java                                   UserManager<Player>         online
impl:command/sub/fuel/FuelInfoCommand.java                                     UserManager<Player>         online
impl:command/sub/fuel/FuelRefuelCommand.java                                   UserManager<Player>         online
impl:command/sub/fuel/FuelRemoveCommand.java                                   UserManager<Player>         online
impl:command/sub/item/money/ItemMoneyCommand.java                              UserManager<Player>         online
impl:command/sub/item/money/ItemMoneyGiveCommand.java                          UserManager<Player>         online
impl:command/sub/item/money/ItemMoneyInfoCommand.java                          UserManager<Player>         online
impl:command/sub/item/unique/ItemUniqueCommand.java                            UserManager<Player>         online
impl:command/sub/item/unique/ItemUniqueGiveCommand.java                        UserManager<Player>         online
impl:command/sub/item/unique/ItemUniqueInfoCommand.java                        UserManager<Player>         online
impl:command/sub/level/LevelAddCommand.java                                    UserManager<Player>         online
impl:command/sub/level/LevelNextCommand.java                                   UserManager<Player>         online
impl:command/sub/level/LevelRemoveCommand.java                                 UserManager<Player>         online
impl:command/sub/level/experience/LevelExperienceAddCommand.java               UserManager<Player>         online
impl:command/sub/level/experience/LevelExperienceCommand.java                  UserManager<Player>         online
impl:command/sub/level/experience/LevelExperienceRemoveCommand.java            UserManager<Player>         online
impl:command/sub/wanted/WantedAddCommand.java                                  UserManager<Player>         online
impl:command/sub/wanted/WantedClearCommand.java                                UserManager<Player>         online
impl:command/sub/wanted/WantedRemoveCommand.java                               UserManager<Player>         online
impl:command/sub/waypoint/WaypointCooldownCommand.java                         UserManager<Player>         online
impl:command/sub/waypoint/WaypointCostCommand.java                             UserManager<Player>         online
impl:command/sub/waypoint/WaypointCreateCommand.java                           UserManager<Player>         online
impl:command/sub/waypoint/WaypointDeleteCommand.java                           UserManager<Player>         online
impl:command/sub/waypoint/WaypointDeselectCommand.java                         UserManager<Player>         online
impl:command/sub/waypoint/WaypointGangIdCommand.java                           UserManager<Player>         online
impl:command/sub/waypoint/WaypointListCommand.java                             UserManager<Player>         online
impl:command/sub/waypoint/WaypointRadiusCommand.java                           UserManager<Player>         online
impl:command/sub/waypoint/WaypointSelectCommand.java                           UserManager<Player>         online
impl:command/sub/waypoint/WaypointShieldCommand.java                           UserManager<Player>         online
impl:command/sub/waypoint/WaypointTimerCommand.java                            UserManager<Player>         online
impl:command/sub/waypoint/WaypointTypeCommand.java                             UserManager<Player>         online
impl:data/placeholder/worker/GanglandPlaceholder.java                          UserManager<Player>         online
impl:file/configuration/gang/GanglandUserLookup.java                           UserManager<Player>         online
impl:file/configuration/inventory/InventoryRuntimeContext.java                 UserManager<Player>         online
impl:sign/SignManager.java                                                     UserManager<OfflinePlayer>  offline
impl:sign/SignManager.java                                                     UserManager<Player>         online
impl:sign/type/trade/BuySign.java                                              UserManager<Player>         online
impl:sign/type/trade/SellSign.java                                             UserManager<Player>         online
```

(`impl:` = `gangland-impl/src/main/java/org/luckyraven/gangland/`, `gang:` = `gangland-features/gangland-gang/src/main/java/org/luckyraven/gangland/`, `mail:` = `.../gangland-mail/.../mail/`, `gadget:` = `.../gangland-gadget/.../gadget/`, `npc-shops:` = `.../gangland-npc-shops/.../npcshops/`, `cops-n-crooks:` = `.../cops-n-crooks/.../copsncrooks/`.)

**Consumers checked in the modules the ruling specifically named**: `gangland-lootchest` and
`gangland-civilians` have **zero** `UserManager` consumers anywhere (`grep -rn "UserManager<" .../src/main`
in both returns nothing) — nothing to fix there. `gang`, `mail`, `gadget`, `npc-shops` and `cops-n-crooks`
("the contributions in impl" plus every module that does have a `UserManager` consumer) are all covered in the
table above.

Every touched file that lacked `import org.luckyraven.keystone.bean.Qualifier;` got it added (inserted next to
the existing `org.luckyraven.keystone.bean.*` import block, or after the last import when there was no such
block), with a blank line preserved before the class declaration.

## Red-first test

Extended `gangland-features/gangland-gang/src/test/java/org/luckyraven/gangland/gang/GangConfigBeanGraphTest.java`:

- `setUp()` now registers **two** `UserManager` mocks under `"online"`/`"offline"`, mirroring `DataConfig`
  exactly (both stored under the same raw `UserManager.class` key) — the existing single-mock rig could never
  reproduce the ambiguity.
- New test `gangConfig_userManagerConsumers_resolveUnambiguously` — runs the real `factory.instantiate()`
  against the real `GangConfig` and asserts it doesn't throw. Confirmed genuinely red before the fix
  (temporarily stashed `GangConfig.java`'s `@Qualifier` additions and reran): failed with the exact production
  error, `IllegalStateException: Ambiguous bean for parameter 'arg0' of type UserManager for bean
  GangConfig.gangItemSourceContribution(): 2 candidates registered. Add @Qualifier to disambiguate.` — the
  same text and the same method (`gangItemSourceContribution`) the 8-module smoke hit. Confirmed green after
  restoring the fix.
- New test `unqualifiedBeanParameter_ofAmbiguouslyRegisteredType_fails` — standalone isolation repro (mirrors
  the existing T-53 isolation test's pattern): a minimal fixture `@Configuration` class with one unqualified
  `@Bean` method parameter of type `UserManager<Player>`, run against a container with two ambiguously-named
  `UserManager` mocks; asserts the thrown message contains `"Ambiguous bean"` and `"2 candidates registered"`.
- The existing `gangMembershipInstaller_resolvesAndInstalls` test (T-53's pin) also now exercises this same
  ambiguity as a side effect of the two-mock `setUp()`, and was confirmed to fail alongside the new test during
  the red-first check — both recovered together once the fix was restored.

`GangConfigBeanGraphTest` now has 4 tests (was 2).

## Gate

**Module gate** (`gangland-impl` + every module the sweep touched — `gangland-gang`, `cops-n-crooks`,
`gangland-gadget`, `gangland-mail`, `gangland-npc-shops`, `-am`): 0 failures, 0 errors (542 tests by surefire
`.txt` summation across those 6 modules — superseded by the reactor-wide console rollup below per W52).

**Full reactor `mvn clean verify`** (never `install`): `BUILD SUCCESS`, exit 0, all 18 reactor modules.

**Console rollup** (W52-compliant, non-quiet `mvn test`, per-module `Tests run:` summary lines):

| Module | Tests | Failures/Errors |
|---|---|---|
| Gangland Core | 67 | 0/0 |
| Gangland Item | 43 | 0/0 |
| Sign API | 63 | 0/0 |
| Gangland API | 14 | 0/0 |
| Gangland (impl) | 238 | 0/0 |
| Gangland Gangs | 106 | 0/0 |
| Gangland Mail | 25 | 0/0 |
| Gangland Civilians | 23 | 0/0 |
| Gangland Turf | 91 | 0/0 |
| Cops N Crooks | 76 | 0/0 |
| Gangland Gadgets | 113 | 0/0 |
| Gangland NPC Shops | 17 | 0/0 |
| Gangland Loot Chests | 53 | 0/0 |
| **Total** | **929** | **0/0** |

Reconciles exactly against the baseline of 927: **927 + 2 = 929** — the 2 new `GangConfigBeanGraphTest` tests
(`"Tests run: 4, ... -- in GangConfig bean graph - real BeanFactory wiring (T-53)"`, up from 2). No other
module's count moved — the sweep's 100 qualifier insertions were all existing-parameter edits, not new tests.

**`mvn -pl gangland-build -am package -DskipTests` jar check**: `BUILD SUCCESS`, exit 0. Note: the first
attempt at this ran **concurrently** with the full-reactor `clean verify` in the background and its `target/`
output was clobbered by the other build's `clean` phase mid-run (both builds share the same worktree's
`target/` directories) — re-ran it **sequentially** after the other two background builds finished, which
produced a clean, trustworthy result. All jars confirmed present: `target/gangland_warfare-0.10.0.jar` (core)
and all 8 module jars (`cops-n-crooks`, `gangland-civilians`, `gangland-gadget`, `gangland-gang`,
`gangland-lootchest`, `gangland-mail`, `gangland-npc-shops`, `gangland-turf`), sizes consistent with a normal
build.

## Not done this round (out of scope per the ruling)

- No commits — diff only (working tree has all changes; nothing staged/committed).
- No smoke test — the 0.10.0 lead re-runs the 8-module rows after fast-forwarding.
- No reviewers.
- ≤2 haiku subagents were not needed — the sweep was done directly with a precise parsing script rather than
  delegated, since correctness (avoiding false-positive matches on fields/static helpers/bean-return-types)
  needed close verification at each step.
