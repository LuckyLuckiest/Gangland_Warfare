# Migrating a server from 0.20.0 to Gangland 0.21.0

[← Back to Documentation Index](./README.md)

Gangland 0.21.0 "How it ends" closes the Cops N Crooks overhaul. An arrest now records how it happened: you can surrender
with empty hands, a lethal hit next to a cop busts you instead of downing you, a lockpick can free your cuffs, and a crew
mate can break you out. Crimes are shared with your gang and allies, every chase ends with a pursuit report, and cops stop
feeding officers into standoffs and breach doors instead. A clean getaway pays XP, and the city's most wanted player holds
a title. Every new key has a code default, so an old config keeps working. The notes below list the blocks you may paste
and the behaviour that changed.

## 1. Update the dependencies

- **Keystone 1.15.3 is required**, unchanged from 0.20.0. Gangland refuses to start on an older Keystone.
- **gangland-api 2.7 and the cops-n-crooks `Host_Api` 2.7** are inherited from 0.20.0. 0.21.0 adds no api surface and edits
  no `module.yml`, so there is nothing new to install. Update all the jars together.

## 2. New blocks to paste

A missing block uses its code default, with no warning. Paste a block only to change its text or value. The blocks below are
the shipped defaults.

### 2.1 Arrests, chases and the crew (0.21.0)

**`copsncrooks/detainment.yml`**: the lockpick keys go under `Detainment.Break_Free`.

```yaml
Detainment:
   Break_Free:
      # 0.21.0: each sneak tap counts this much while a lockpick is in the inventory.
      Lockpick:
         Tap_Weight: 2
         # 0.21.0: chance per weighted tap that one lockpick breaks (0.0 - 1.0).
         Snap_Chance: 0.05
```

The surrender, busted and rescue blocks go under `Detainment`.

```yaml
Detainment:
   # 0.21.0: crouch with empty hands (or a white banner) while the cops see you to surrender.
   Surrender:
      # Turns the surrender on or off.
      Enabled: true
      # Highest wanted level you can surrender at; tiers 4 and 5 skip cuffing by default.
      Max_Stars: 3
      # Sentence scale for a surrender.
      Sentence_Multiplier: 0.5
      # Charge-sheet fine scale for a surrender.
      Fine_Multiplier: 0.5
      # Bail scale for a surrender.
      Bail_Multiplier: 0.75
      # Hitting a cop this soon after surrendering adds Resisting_Arrest.
      Resist_Window_Seconds: 10
   # 0.21.0: a lethal hit with a cop within its Cuff_Radius arrests instead of downing (downed mode only).
   Busted:
      # Turns the busted arrest on or off.
      Enabled: true
   # 0.21.0: a crew mate can free a cuffed player by holding right-click on the guarding cop.
   Rescue:
      # Turns the rescue on or off.
      Enabled: true
      # How long the right-click must be held.
      Hold_Seconds: 3
      # Stars added to both the rescuer and the prisoner.
      Stars: 1
```

**`copsncrooks/cops.yml`**: the standoff and breach blocks go under `Cops`.

```yaml
Cops:
   # 0.21.0: cops surround a suspect they cannot reach (or who drops Man_Down_Count cops fast) instead of feeding officers in; every key optional
   Standoff:
      # Cops surround a suspect they cannot reach instead of feeding officers in
      Enabled: true
      # Countdown before the next tier comes in
      Timer_Seconds: 45
      # Gap between Negotiate radio lines
      Negotiate_Seconds: 15
      # Most hunting cops unable to reach him this long starts a standoff
      Unreachable_Seconds: 10
      # This many cops killed...
      Man_Down_Count: 2
      # ...within this many seconds...
      Man_Down_Window_Seconds: 30
      # ...within this many blocks of each other starts a standoff
      Spot_Radius: 8.0
      # Cops posted in a ring around the suspect; the rest take cover
      Post_Count: 4
      # Ring radius in blocks
      Post_Radius: 12.0
      # Stars added when the countdown runs out
      Stars_On_Timeout: 1
      # Minimum gap between standoffs for one suspect
      Cooldown_Seconds: 60
   # 0.21.0: cops stack up at a door and breach an indoor suspect instead of walking in one by one; no block is ever broken; every key optional
   Breach:
      # Cops stack up at a door and breach instead of walking in one by one
      Enabled: true
      # Suspect counts as indoors with more than this many blocks of roof above him
      Roof_Blocks: 2
      # Door search radius around the last-known spot
      Door_Radius: 6
      # Cops in the stack
      Stack_Size: 3
      # Go when the stack is full or after this long
      Stack_Wait_Seconds: 6
      # Gap between officers going through the door
      Entry_Gap_Ticks: 20
      # Minimum gap between breaches for one squad
      Cooldown_Seconds: 30
      Flashbang:
         # Wanted level from which a flashbang goes in first
         Min_Stars: 4
         # Players this close to the door with a clear line to it are blinded
         Radius: 8.0
         # Blindness and Slowness duration
         Ticks: 40
```

**`copsncrooks/wanted.yml`**: crew heat and the pursuit report go under `Wanted`.

```yaml
Wanted:
   # 0.21.0: crimes are shared with your gang mates and allies near the crime (the crew heat)
   Crew_Heat:
      # false = no crew heat
      Enable: true
      # Blocks from the crime spot
      Radius: 16.0
      # Crime ids never shared with the crew. Ids unknown to this build are ignored silently
      Exclude_Crimes:
         - Trespass_Restricted
         - Evading_Police
         - Brandish_Near_Cop

   # 0.21.0: a rundown sent to the suspect when a chase ends
   Pursuit_Report:
      # false = no pursuit report
      Enable: true
      # Chases that never reached this many stars get no report
      Min_Peak_Stars: 1
      # Cost to the city of the chase
      City_Cost:
         # Cost to the city per cop killed
         Per_Cop_Down: 2500.0
         # Cost to the city per point of chase heat
         Per_Heat: 10.0
      # Server-wide headline for big chases
      Breaking_News:
         # false = no headlines
         Enable: true
         # A chase reaching this many stars makes the news
         Min_Stars: 4
         # Or one lasting this many seconds
         Min_Seconds: 120
         # Minimum gap between live headlines server-wide
         Cooldown_Seconds: 300
```

**`copsncrooks/wanted_messages.yml`**: these are top-level blocks (`Surrender`, `Busted`, `Lockpick`, `Crew_Heat`, `Rescue`,
`Pursuit_Report`, `Breaking_News`).

```yaml
# 0.21.0: the surrender, busted, lockpick, crew-heat, rescue, pursuit report and breaking news lines
Surrender:
   # Your hands are up and the cops hold their fire
   Accepted: "&eHands where we can see them. &7The cops hold their fire, stay down."
   # You hit a cop soon after surrendering
   Resisted: "&cYou resisted arrest."
   # Too much heat to surrender; the cops are not taking you alive
   Too_Hot: "&cAt this heat they are not taking you alive."
# A lethal hit with a cop close by arrests instead of downing you (Detainment.Busted)
Busted:
   # The title on the screen when you are cuffed this way
   Title: "&c&lBUSTED"
   # The subtitle under the title
   Subtitle: "&7A cop got the cuffs on you"
# Breaking the lock on a cuff with a lockpick (Detainment.Break_Free.Lockpick)
Lockpick:
   # A lockpick broke while you were picking the lock
   Snapped: "&7Your lockpick snapped."
# Crimes shared with gang mates and allies (Wanted.Crew_Heat); %player% is who committed it, %crime% the crime
Crew_Heat:
   # The crew member's crime lands on you too
   Shared: "&c%player%'s %crime% is on you too."
# Freeing a cuffed player (Detainment.Rescue); %seconds% is the hold time left
Rescue:
   # The rescuer's progress while holding right-click
   Progress: "&eFreeing %player%... &f%seconds%s"
   # Said to the rescuer when the prisoner is freed
   Freed: "&aYou broke %player% out. &cThe cops want you both."
   # Said to the prisoner when a crew mate frees them
   Freed_By: "&a%player% broke you out!"
# The pursuit report shown when a chase ends (Wanted.Pursuit_Report); %ending% is one of the Endings below
Pursuit_Report:
   # First line
   Header: "&8&m-----&r &6&lPURSUIT REPORT &8&m-----"
   # How the chase ended
   Ending: "&7Ending: &f%ending%"
   # How long the chase lasted
   Duration: "&7Duration: &f%time%"
   # The highest wanted level reached
   Peak: "&7Peak: %stars%"
   # How many cops went down; %count% is how many
   Cops_Down: "&7Cops down: &f%count%"
   # How many times you were cuffed; %count% is how many
   Busts: "&7Times cuffed: &f%count%"
   # The cost to the city; %money_symbol% and %amount% are the cost
   Cost: "&7Cost to the city: &c%money_symbol%%amount%"
   Endings:
      # You got away: the search ran out or the timer cleared your stars
      Escaped: "Escaped"
      # You surrendered
      Surrendered: "Surrendered"
      # You were busted
      Busted: "Busted"
      # You were arrested
      Arrested: "Arrested"
      # You were killed
      Wasted: "Wasted"
      # You paid a contact or bribed the cops
      Paid_Off: "Paid off"
      # Your stars were cleared another way (an admin command or a sign)
      Cleared: "Cleared"
# Server-wide headlines for a chase (Wanted.Pursuit_Report.Breaking_News)
Breaking_News:
   # Said while the chase is live; %player% and %stars% are the chase
   Live: "&4&lBREAKING &fPolice are chasing %player% across the city &c%stars%"
   # Said when the chase is over; %ending% is how it ended
   End: "&4&lBREAKING &fThe %stars% &fchase of %player% is over: %ending%"
```

**`copsncrooks/cop_radio_messages.yml`** (and `_es`): seven radio lines under `Lines` (`Surrender_Accepted`, `Standoff`,
`Negotiate`, `Standoff_Over`, `Stack_Up`, `Breach`, `Flashbang`). They are optional, since the code defaults speak them.

```yaml
Lines:
# 0.21.0
   # The suspect surrenders at gunpoint.
   Surrender_Accepted:
      - "He's giving up! Hold your fire, cuff him!"
      - "Hands are up, he's surrendering!"
   # A barricade is set; the squad takes a perimeter.
   Standoff:
      - "Barricade! Perimeter up, everyone to cover!"
      - "He's holed up, set the perimeter!"
   # The leader gives the suspect a countdown to come out.
   Negotiate:
      - "Suspect, come out with your hands where I can see them! %seconds% seconds!"
      - "Talk to me! Step out in %seconds% seconds or we come in!"
   # The barricade ends and the squad moves in.
   Standoff_Over:
      - "Time's up, we're going in style!"
      - "Barricade's down, all units moving in with style!"
   # The squad stacks on the door before a breach.
   Stack_Up:
      - "Stack up on the door! Shield, you're first!"
      - "Stacking up, hold the door!"
   # The breach goes in.
   Breach:
      - "Breach! Breach! Go, go, go!"
      - "Breaching now, move in!"
   # A flashbang is thrown before entry.
   Flashbang:
      - "Flashbang out! Eyes closed!"
      - "Flash out, going in!"
```

### 2.2 Clean getaway and the city's Most Wanted (0.21.0, wave 2)

**`copsncrooks/wanted.yml`**: `Getaway` and `Most_Wanted` go under `Wanted`, after `Pursuit_Report`. The keys are
`Wanted.Getaway.Enable`, `Wanted.Getaway.Experience`, `Wanted.Getaway.Cooldown_Seconds`, `Wanted.Getaway.Daily_Cap`,
`Wanted.Getaway.Exclude_Crimes`, `Wanted.Most_Wanted.Enable`, `Wanted.Most_Wanted.Min_Stars`,
`Wanted.Most_Wanted.Hold_Seconds` and `Wanted.Most_Wanted.Cooldown_Seconds`.

```yaml
Wanted:
   # 0.21.0: getaway XP when the evasion clock takes your last star
   Getaway:
      # false = no getaway XP
      Enable: true
      # XP for getting away, by the peak stars earned through crimes; first entry is one star; the last entry is
      # used above the list length
      Experience:
         - 0
         - 25
         - 75
         - 175
         - 350
      # Seconds a player must wait between getaway payouts
      Cooldown_Seconds: 600
      # Most getaway XP a player gains per day, crew shares included; 0 = no cap
      Daily_Cap: 1000
      # Crimes whose stars never count toward the earned peak of a getaway (lower-case or any case works);
      # these stars do not count toward Getaway or Most Wanted
      Exclude_Crimes:
         - Trespass_Restricted
         - Evading_Police
         - Brandish_Near_Cop

   # 0.21.0: the city's Most Wanted title
   Most_Wanted:
      # false = no Most Wanted title or radio alerts
      Enable: true
      # Stars earned through crimes needed for the title
      Min_Stars: 4
      # Seconds a player must keep the Most Wanted stars before the title is announced
      Hold_Seconds: 10
      # Minimum gap between title broadcasts server-wide
      Cooldown_Seconds: 120
```

**`copsncrooks/wanted_messages.yml`**: the `Getaway` keys (`Getaway.Line`, `Getaway.Capped`, `Getaway.Share`) and the
`Most_Wanted.New` key.

```yaml
# 0.21.0 wave 2: the getaway XP lines (Wanted.Getaway) and the Most Wanted headline (Wanted.Most_Wanted)
Getaway:
   # You got away clean; %xp% is the XP you earned
   Line: "&7Getaway: &a+%xp% XP"
   # The daily XP cap for getaways is reached
   Capped: "&7Getaway: &8daily XP cap reached"
   # Said to each wanted crew mate who gets a share; %player% is who got away, %xp% the mate's XP
   Share: "&a%player% got away clean: &f+%xp% XP &7for the crew"
Most_Wanted:
   # Said when a player becomes the city's Most Wanted; %player% and %stars% are that player and their level
   New: "&4&lMOST WANTED &f%player% &7is now the city's Most Wanted &c%stars%"

```

**`copsncrooks/cop_radio_messages.yml`** (and `_es`): the `Lines.Most_Wanted` radio line. It is optional, since the
code default speaks it.

```yaml
Lines:
   # The wanted-list announcement: the most wanted suspect and where they were last seen.
   Most_Wanted:
      - "All units, be advised: %target% is the city's Most Wanted, last seen near %place%."
```

```yaml
Lines:
   # The wanted-list announcement: the most wanted suspect and where they were last seen.
   Most_Wanted:
      - "Atención a todas las unidades: %target% es el más buscado de la ciudad, visto cerca de %place%."
```

## 3. Database

- **`detainment.origin_type`** is added automatically on SQLite and MySQL at first boot. Old arrest rows read as `CUFFED`.
  There is nothing to run by hand. 0.20.0 ignores the column, so a downgrade is safe.
- **Wave 2 adds no table and no column.** Earned stars, the getaway cooldown and daily cap, and the Most Wanted title are kept
  in memory. A restart resets them (see the changelog's known limits).

## 4. Behaviour changes

- **Busted, not a hospital bill.** While downed mode is on, a lethal hit with a cop of your hunting squad within that cop's
  cuff radius arrests you. The charge sheet is the only charge. Void falls and `/kill` do not count. Set
  `Detainment.Busted.Enabled` to `false` to restore the 0.20.0 death.
- **Surrender** works at 1 to 3 stars: crouch with empty hands (or a white banner in hand) while the squad sees you. Above
  `Max_Stars` the cops do not take you alive. Hitting a cop within `Resist_Window_Seconds` adds `Resisting_Arrest`.
- **Arrest origin scales the sentence.** Sentence, fine and bail follow the arrest origin (`CUFFED`, `SURRENDERED`,
  `BUSTED_DOWNED`) through the multipliers in `detainment.yml`.
- **Lockpicks.** With a lockpick in the inventory, each sneak tap counts `Break_Free.Lockpick.Tap_Weight` (2). Each weighted tap
  can snap the lockpick (`Snap_Chance`).
- **Rescues.** A crew mate who holds right-click on the guarding cop for `Rescue.Hold_Seconds` (3) frees you. Both of you gain
  `Rescue.Stars`.
- **Crew heat.** A crime is shared with online gang mates and allies within `Wanted.Crew_Heat.Radius` (16 blocks). The crimes in
  `Crew_Heat.Exclude_Crimes` are not shared. Set `Crew_Heat.Enable` to `false` to switch it off.
- **Pursuit report.** Every chase ends with a report sent to the suspect. Set `Pursuit_Report.Enable` to `false` to switch it
  off. A chase of 4 stars or more, or lasting 2 minutes or more, is also a server-wide headline (`Breaking_News.Enable`).
- **Standoffs and breaches.** Cops ring a suspect they cannot reach, or stack at a door of an indoor suspect. Only the cops
  hunting you take part. No block is broken. Set `Cops.Standoff.Enabled` or `Cops.Breach.Enabled` to `false` to switch one off.
- **Clean getaway.** When the evasion clock takes your last star after a chase in which a cop saw you, you gain getaway XP
  (`Wanted.Getaway.Experience`). One star pays nothing. Half goes to each gang mate or ally who is wanted right now with at least one star earned
  through crimes. The cooldown and daily cap are in `Wanted.Getaway` in `copsncrooks/wanted.yml`.
- **The city's Most Wanted.** The online player with the most earned stars (at least `Min_Stars`) holds the title after
  `Hold_Seconds` of holding it. Stars from signs or commands never count. The title is announced at most once per
  `Cooldown_Seconds` server-wide. Placeholders: `%gangland_cnc_most_wanted%` (name) and `%gangland_cnc_most_wanted_stars%`
  (earned stars). Both are empty or `0` when the title is vacant.
- **Skip_Cuffing comment.** The `Cops.Tiers.<n>.Skip_Cuffing` comment in `cops.yml` is corrected (T-355). The value is unchanged.

**Without Citizens** there are no cop NPCs. Surrenders, busts, standoffs, breaches and rescues never trigger, and no getaway XP
is paid, because a getaway needs a sighting. Crew heat, lockpicks on `/glw cuff`, the pursuit report and the Most Wanted title
still work. Most Wanted loses only its radio line. **Without the gang module** there is no crew heat, no crew share and no
rescue. **Without Bartizan** cops-n-crooks does not load.
