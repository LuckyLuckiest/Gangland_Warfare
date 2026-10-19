# 0.20.0 Changelog - Street crime

[← Back to Documentation Index](../README.md)

Read [migration-0.20.0.md](../migration-0.20.0.md) before you start the server. It lists every new key, node, table and
behaviour change, with its default.

## Requires

- **Keystone 1.15.3** (unchanged). Gangland refuses to start on an older Keystone.
- **Bartizan 0.6.0** (`bartizan.version`). Cops, gadget and store hold-ups need it; without it cops-n-crooks is skipped.
- **Citizens 2.0.41 or newer** (soft). Without it there are no patrols, witnesses, overwatch, sirens, tracker tags or hold-ups.
- **gangland-api 2.7** (additive). Four modules move to `Host_Api: 2.7`: `cops-n-crooks`, `gangland-gadget`,
  `gangland-npc-shops` and `gangland-lootchest`. They refuse to load on a 0.19.x core. Update all jars together.

## Police patrols and enforcement

- **Beat cops.** Stations keep patrol cops who walk nearby streets when a player is close, and they despawn when nobody is near.
- **Warnings.** A cop who sees you holding a weapon tells you to put it away. Ignore the warning and you commit brandishing.
- **Pull-overs.** Drive too fast past a cop and you are told to stop. Stop in time and you get a ticket. Flee and you add
  evading police.
- **Tracker tags.** At two stars, a cop who sees you ride a vehicle for a few seconds tags it. The squad then gets your position
  every few seconds while you ride it, and the tag expires after a while.

## Witnesses and 911

- **Car theft and assault on civilians now need a witness.** A civilian who sees the crime calls 911. If the call lands, the
  crime counts. Stop the caller in time by hitting them, aiming a weapon at them, or killing them, and the crime does not count.
- **Assault on a civilian is recorded.** It was not recorded before.

## Masks, outfits and disguises

- **Descriptions.** Every reported crime has a suspect description that the squad radios: head, chest colour, weapon, on foot or
  in a car.
- **Masks.** Wearing a mask item hides you: the description says "unknown suspect".
- **Changing clothes.** Change two of head, chest and vehicle while no cop has a fresh sighting, and cops stop locking on to you.
- **Police outfit.** A full police outfit keeps cops from locking on to you at low stars.

## Scanner, sirens and sight

- **Police Scanner.** Carry it and you hear every cop radio line from squads within range. Admins give it with
  `/glw cop item police_scanner`.
- **Sirens.** A pursuing cop sounds a siren during a chase, which you can hear from a distance.
- **Darkness and crowds.** Cops spot you from less far in the dark, sneaking, or in a crowd. Firing a gun resets your cover.
  SWAT and Military ignore darkness by default.
- **Marksman overwatch.** At four stars, when the squad loses you, a Marksman climbs to a roof, watches, and radios your
  position if he sees you. He gives up after a few failed perches.
- **Scanner Map.** Hold it to see a live map with your squad's cops, a red circle while you are being searched for, and admin
  hideouts. It keeps working after a restart. Admins give it with `/glw cop item scanner_map`.

## Hold-ups, safes and trespass

- **Store hold-ups.** Aim a weapon at a trader and keep aiming. The clerk fills the bag over ten seconds and you get the take.
  The silent alarm commits a store robbery, earlier if the trader dislikes you. A robbed trader refuses hold-ups for a while.
- **Safe cracking.** Cracking a police armory or military vault chest rings an alarm and commits safe cracking.
- **Hotwire.** Use a lockpick on a gadget car you may not use to start it. Success commits car theft.
- **Trespass.** Walking into a restricted area commits trespass. Staff with `gangland.cop.trespass.bypass` are exempt.

## The hot-cash bag, fence and crew lookout (wave 2)

- **The bag.** Cash you pick up while wanted goes into your bag, shown as `Bag: $X` on the action bar. When the chase ends the bag
  banks into your wallet on a getaway, converts at the fence's cut on any other clear, is seized if you are busted, and spills
  on the street if you die or quit.
- **Limits.** The bag is capped per chase, and a getaway banks up to a daily limit. Anything over converts at the fence's cut.
- **Fence the bag.** `/glw contact fence`, or the "Fence the bag" slot on the phone contacts page, sells the bag for wallet cash
  minus the fence's cut. Not while you are in cuffs, and it shares the contacts cooldown.
- **Bribes.** Handcuff and jail bribes take from the bag first.
- **Cops carry no cash.** Cops drop no money with the bundled settings. Existing servers keep their current setting.
- **Crew lookout.** A gang mate who is not wanted passes on the police radio he hears about you, prefixed `[Lookout <name>]`.

## Admin

- **New keys.** `copsncrooks/wanted.yml` (`Wanted.Trespass`, `Wanted.Witnesses`, `Wanted.Bag`), `copsncrooks/cops.yml`
  (`Cops.Disguise`, `Patrol`, `Warning`, `Pull_Over`, `Tracker`, `Scanner`, `Siren`, `Sight`, `Overwatch`, `Scanner_Map`,
  `Lookout`, and `Ignore_Darkness` per tier), `npc/civilians.yml` (`Witness.*`), `npc/trader_settings.yml` (`Hold_Up.*`),
  `lootchests/loot_chest_settings.yml` (`Cracking_Alarm.*`), `gadget/gadget_settings.yml` (`Hotwire.*`, `Car_Events.*`).
- **Money.** `items/money.yml` sets `Money.Drop_Sources.COP.Enabled: false`. A server that keeps its old file keeps cop drops.
- **Phone contacts.** The phone contacts page gets a "Fence the bag" slot.
- **Radio text.** `cop_radio_messages.yml` and `cop_radio_messages_es.yml` gain the new lines and `Lookout_Format`. Regenerate
  them to see the lines in the file.
- **Commands.** `/glw cop item <police_scanner|scanner_map> [player]` (permission `gangland.cop.item`) and `/glw contact fence`.
- **Permissions.** `gangland.cop.trespass.bypass` (exempt from trespass). Not declared in `plugin.yml`: operators have it until
  you grant it.
- **Database.** A new cops table `hot_cash_bag`, created at first boot. Run no SQL.

## Developer

- **gangland-api 2.7.** Five vehicle events (`CarEnterEvent`, `CarExitEvent`, `CarMoveEvent`, `CarDamageEvent`,
  `CarTheftAttemptEvent`) in `org.luckyraven.gangland.events.vehicle`; six `Crimes` ids; `RadioVoice.overhears` and
  `RadioVoice.relaysTo` (both default `false`); `data.economy.HotCash` and `HotCashRouter`; `MoneyDepositService.depositPickup`.
  Nothing removed, renamed or re-signed. `VERSION` is `2.7`.
- **Signatures 0.21.0 relies on.** Unchanged since the 0.20.0 base: `HeatLedger.record(CrimeCommittedEvent)`,
  `CrimeCommittedEvent(Player, String, Location, boolean, int)`, `PerimeterController.isActive(UUID)`,
  `CopManager.addAiTickHook`, `addCopAttackedHook`, `findCopByEntity`, `groupOf`, `CopNpc.holdPost`, `getGuardedPlayerId`,
  `getTierConfig`, `CopRadio.sayFromLeader(group, key[, extra])`, `DetainmentService.isRestrained(Player)`.
- **New seams.** `CopPerception` (`rangeFor`, `sees`, `acquires`, `pursuitRange`, `addSightFactor`, `addLockOnFilter`),
  `CopManager.addCopResolver`, `CopRadio.setOverhearing`, `setExtraPlaceholders`, `setLookout`, and `CopState` values `PATROL`,
  `WARNING`, `OVERWATCH`.
- **Setters, constructors unchanged.** `JailIntakeService.setBagSeizure`, `BribeService.setBagFunding`, `PaperworkView.setBagLine`,
  `CopRadio.setLookout`.
- **Tests.** Reactor `mvn -o verify` on the 0.20.0 tip: 5158 tests, 0 failures, 0 errors, 0 skipped (15 module summaries).

## Fixed

- **Crime witness fields (T-310).** The witness count on a crime event was never read. The witness gate now reads it.
- **Bundled cop cash (T-345).** The bundled settings dropped cash from cops. Cops now drop none with the bundled setting; the
  bag carries the cash instead.
- **Stale wearable docs (T-311).** `wearables.yml` and `WearableConverter` are no longer described as Gangland files; they belong
  to Bartizan.

## Known issues

- Spilled bills drop whole money items, so cents are lost on a spill.
- The contacts cooldown is memory-only. A restart clears it.
- A restrained (handcuffed or in transit) player who dies or quits has the bag seized, not spilled.
- Stolen-car grants reset on a restart.
- Map hideouts are admin regions only. Turf and gang hideouts are not on the map yet.
- `CLAUDE.md` says the civilians and gadget modules declare `Plugins: [Bartizan]`; their `module.yml` files do not (T-312, doc
  drift, not fixed in this release).
- Breaker regions do nothing yet; restricted regions drive trespass from this release.

## API

- **`gangland-api` 2.7** (additive): see the Developer section above and [gangland-api.md](../gangland-api.md), section Api 2.7.

---

*Tests: 5158 run, 0 failures, 0 errors, 0 skipped on the 0.20.0 tip (`mvn -o verify`).*
