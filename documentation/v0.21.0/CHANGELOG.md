# 0.21.0 Changelog - How it ends

[← Back to Documentation Index](../README.md)

Read [migration-0.21.0.md](../migration-0.21.0.md) before you start the server. It lists the blocks to paste, the database
change and the behaviour that changed. Upgrade from 0.20.0.

## Requires

- **Keystone 1.15.3** (unchanged from 0.20.0). Gangland refuses to start on an older Keystone.
- **gangland-api 2.7 and cops-n-crooks `Host_Api` 2.7**, inherited from 0.20.0. 0.21.0 adds no api surface and changes no `module.yml`. Update all the jars together.
- **Citizens** is needed for cop NPCs. Without it there are no surrenders, busts, standoffs, breaches, rescues or getaway XP. Crew heat, lockpicks on `/glw cuff`, the pursuit report and the Most Wanted title still work.
- **The gang module** is needed for crew heat and rescues.

## New

### Arrests and chases

- **Surrender.** At 1 to 3 stars, crouch with empty hands (or a white banner in hand) while the squad sees you. The cops hold fire, and you serve half the sentence, pay half the fine and 75% bail. Standing up before the cuffs land ends it. Hitting a cop within 10 seconds adds Resisting_Arrest. Above `Max_Stars` the cops do not take you alive.
- **Busted.** With downed mode on, a lethal hit with a hunting cop in cuff range arrests you instead of downing you. The charge sheet is the only charge. Your inventory is seized as for any arrest.
- **Lockpicks.** A lockpick in the inventory counts each sneak tap double while you are cuffed, so you break free in half the taps. Each weighted tap can snap the lockpick.
- **Rescues.** A crew mate holds right-click on the cop guarding you for 3 seconds and frees you. Both of you gain a star.
- **Arrest origin.** Every arrest records how it happened (cuffed, surrendered or busted), and the sentence, fine and bail scale by that origin.
- **Standoffs.** When cops cannot reach a suspect (no route, unreachable for 10 seconds, or two cops down within 30 seconds), they ring him with a countdown. A timeout adds a star.
- **Breaches.** Against an indoor suspect, up to three cops stack at a nearby door and go through it together. At 4 stars a flashbang goes in first. No block is broken.
- **Crew heat.** A crime is shared with online gang mates and allies within 16 blocks. Trespass_Restricted, Evading_Police and Brandish_Near_Cop are not shared by default (`Crew_Heat.Exclude_Crimes`).
- **Pursuit report.** Every chase ends with a report: how it ended, how long it lasted, peak stars, cops down, times cuffed and the cost to the city. A chase of 4 stars or more, or of 2 minutes or more, is a server-wide headline.

### Clean getaway and the city's Most Wanted

- **Clean getaway.** When the evasion clock takes your last star after a chase in which a cop saw you, you gain XP: 1 star 0, 2 stars 25, 3 stars 75, 4 stars 175, 5 stars 350 (earned stars only, from crimes; signs and commands never count). The report ends with your getaway line. Half goes to each gang mate or ally who is wanted right now with at least one star earned through crimes. One payout per 600 seconds, and at most 1000 getaway XP per day, crew shares included.
- **The city's Most Wanted.** The online player with the most earned stars, at least 4, holds the title after 10 seconds of holding that level. A change of holder is announced to everyone, at most once every 120 seconds. Police dispatch names the new holder on the radio when he has a cop group. Placeholders `%gangland_cnc_most_wanted%` and `%gangland_cnc_most_wanted_stars%` show the holder for scoreboards.

## Changed

- **Busted replaces the hospital bill** when a hunting cop is in cuff range (downed mode on). A death with no cop nearby is unchanged.
- **Standoffs and breaches** draft only the cops in pursuit or combat. Patrol, warning and overwatch cops stay out, and no overwatch starts on a group in standoff.
- **Surrender at night.** A crouched surrender is still accepted while the squad has a fresh sighting, even at night.

## Fixed

- **T-355** `Cops.Tiers.<n>.Skip_Cuffing` comment now says what the key does.
- **T-357** the SQLite legacy rebuild keeps the new `origin_type` column.
- **T-360** one-shot radio lines (surrender accepted, standoff, standoff over, the first negotiation line) could be lost to a squad gap; they now play.
- **T-361** a standoff could start while a squad cop was cuffing the suspect; it no longer does.
- **T-362** the `Standoff_Over` lines said patrol or stand down; they now say the squad is going in.
- **T-363** a breach stack drafted only the nearest three cops and left the Defender out; the Defender is now in the stack.
- **T-366** standoff posts reported no sightings, so the suspect's stars decayed and the timeout could not be reached; posts now report sightings.
- **T-370 (partial)** the too-hot line is not repeated once the stars drop to zero. The bribe, admin and decay paths are still partial.
- **T-371** a chase that ended without a death left the cop group's wanted level stale; it is now cleared.
- **T-380** a busted arrest could hit the original suspect after the squad retargeted; the bust now follows the cop's own target.
- **T-381** the gadget fall-cancel (NORMAL) runs before Busted (HIGH), so protected landings are not busted.
- **T-398** a groupless beat cop in combat kept attacking a surrendered player after the suspect's squad called a cease-fire; it now stands down.
- **T-399** the white-banner surrender pose was missed by the sight check, so a night banner surrender still shrank the sighting; the pose now counts.
- **CJ-24 (partial)** the rescue path is fixed; the rest of CJ-24 stays open.

## Known issues

- **T-372** a protected lethal fall during a gang fight forfeits or is protected depending on module order, since the gadget fall-cancel and the gang fight damage listener are both NORMAL. Deferred: no change in 0.21.0.
- **T-390** getaway cooldown and daily cap are kept in memory. A restart resets them.
- **T-391** earned stars are kept in memory. After a restart a restored 5-star suspect cannot hold the title or earn getaway XP until he commits new crimes.
- **T-392** (latent) after a restart, the chase learner can count unseen time as contact time.
- **T-393** a rescue, standoff or kill-combo crime raised right after an excluded low-agency crime earns no star (under-count).
- **T-367** a freed prisoner who stands still can be re-cuffed by the guard who was just released, in the same second. Owner check on a two-client rescue.
- **T-395** a busted suspect can be downed by a second lethal cop hit in the same tick (multi-shooter squads at 3 stars or more). Deferred: no grace period in 0.21.0.
- **T-396** the lockpick's lore says Rare chests only and does not mention that it speeds breaking cuffs.
- **T-397** a bust searches at most 6 blocks, so a `Cuff_Radius` above 6 is capped at 6 for busts.
- Surrender, rescue, standoff and breach state, and the pursuit report counters, are kept in memory. A restart forgets a standoff or breach in progress, and a report that spans a restart counts from the restart.

## Not in this release

- `Keep_Legal_Items`.
- Dead-or-Alive tier behaviour (the `DEAD_OR_ALIVE` origin exists but is never written).
- Core downed pre-respawn hook.
- Roadblock and chopper report lines, and the daily paper.
- Vehicle-only crew sharing for passengers who are not in your crew.
- Hostage and prisoner transport.

## Decisions taken by default

Questions with no owner answer were settled by the page's recommendation or the safest default that keeps shipped behaviour: D-21-01 to D-21-30 (and the D-21-FND entries) for the arrest and chase work, and D-W2-01 to D-W2-24 for the getaway and Most Wanted work. The decision log is part of the release record and is not shipped with the plugin.

## API

- **`gangland-api` 2.7**, inherited from 0.20.0. 0.21.0 adds no api member and no module `Host_Api` change.
