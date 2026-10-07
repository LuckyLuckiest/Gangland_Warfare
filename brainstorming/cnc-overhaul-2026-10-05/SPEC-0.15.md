# Cops N Crooks Overhaul — 0.15 "Lose them" SPEC

Source: artifact https://claude.ai/artifact/U6qF8Ey5xUGJSxrz3Gvdw9 ("Cops N Crooks Overhaul", re-audited 2026-10-04 against
0.12.0 + the 0.13.0 branch). Extracted 2026-10-05. This file is the binding spec for the 0.15 wave.

## Owner rulings (2026-10-05)
- ALL "Decisions for you" take their RECOMMENDED answer (the first option of each item in the Guardrails section below).
  In particular: line-of-sight evasion REPLACES the fixed decay timer as the decay driver (Repeating_Timer stays as the
  safety net; an Evasion Enable switch restores today's behaviour); Drop_Mode ONE_STAR default with ALL_STARS option;
  the wanted boss bar is approved (shows own stars + search countdown, never cop awareness); star-drop charge off by
  default; bounties pay only posted money (Bounty.Pay_Notoriety default false, every pre-upgrade bounty counts as posted once);
  Lose_Money:false = no death charge; turf-war kills half heat, turf-defender kills none.
- H13 (0.13.0) is MERGED into master (415f34a5, 2026-10-05) with Keystone 1.14.0 and Bartizan 0.6.1 on their masters.
- Gang & Turf (0.14.0 / W4) has NOT started. 0.15 goes first on branch `cnc-lose-them`; Gang & Turf will rebase onto it.
  Therefore the gangland-api bump for this wave is the NEXT minor after the current one (read GanglandApi.VERSION; expected 2.0 -> 2.1),
  not "2.2" as the artifact says — the artifact numbered it assuming Gang & Turf's 2.1 shipped first.
- Plugin revision for the wave: 0.15.0.

## Foundations (all four ship in 0.15 unless marked otherwise; only the 0.15 ones are in scope)
        Foundations
            Wanted decay and star-source seamgangland-core + gangland-api0.15
            Add a module-installed WantedDecayPolicy and a heat star source next to WantedKillTracker. WantedExecutor defers to the policy when one is installed and keeps Repeating_Timer as the safety net. The policy gets no wallet or money parameter: a falling star can only change the level and send a message. Every star increment (handleWanted, CivilianDeathRewardListener, the [WANTED] sign INCREASE, /wanted add) goes through one method that guarantees a decay clock and tags its origin (crime, sign or command). Every decrease and clear also tags its cause (decay, sign or contact, bribe, arrest, death, admin), and WantedEndEvent carries that cause, so later features can tell a real getaway from a bought one. A WantedEvasionStateEvent (state, seconds left, zone centre and radius) goes into gangland-api 2.2.
            UnblocksHeat ledger · Line-of-sight evasion · Hot-cash bag
            Crime event busgangland-api + cops-n-crooks0.15
            CrimeCommittedEvent(player, crime id string, location, seenByCop, witnesses; cancellable) and a CrimeService in gangland-api 2.2. Crime ids stay strings so new crimes need no API bump. The same change also fires the dormant CopDeathEvent and KillComboEvent through Bukkit.
            UnblocksVehicle events · Heat ledger · Pursuit report
            Chase acceptance scenariosGangland tests + test-server harness0.15
            Reuse the 0.13.x acceptance scenarios as a regression suite and add one scenario per “Done when” test line from 0.15 on (first: break line of sight at two stars and a star drops with no death). Pass criterion for 0.15: the 1/3/5-star, break-line-of-sight, cuffed, logout and evasion-drop scenarios pass against the 0.15.0 build.
            Districts, regions and place namesgangland-api + gangland-core0.16
            gangland-api 2.3 RegionProvider SPI (id, name, 3D cuboid or sphere, owner, tags such as restricted/hideout) plus PlaceNames.locate(Location). gangland-core keeps admin regions; gangland-turf contributes owned turfs and their names, so cops never depend on turf. A district is public geography; a turf stays a gang claim.
            UnblocksStation registry · New crimes
            Police station registrycops-n-crooks0.16
            A station has an id, a name, a district, its spawner ids and an optional jail link. Existing spawners are grouped under stations through an additive column. Dispatch, patrol beats, clock-in, payoff and transport destinations all read the same registry.
            UnblocksAdmin setup wand · Dispatch from stations · Patrols & pull-overs · Criminal record
            Admin setup wandcops-n-crooks0.16
            One wand item with modes (station, district corners, hideout, pickup point, restricted zone, breaker structure + trigger), a particle outline of the selection, and /cops setup list|remove|tp. It writes through each registry's repository.
            UnblocksHideouts · Crooked contacts · Pursuit breakers
            Vehicle eventsgangland-gadget + gangland-api0.17
            gangland-api 2.4 events: car enter/exit, move (with speed), damage, theft attempt. They fire from the gadget module and a theft also publishes a crime. VehicleSession gains a speed multiplier that VehicleMovementTask applies, for spike strips.
            UnblocksNew crimes · Patrols & pull-overs · Prisoner transport · Roadblocks
            Arrest origin tagcops-n-crooks0.18
            An origin enum (CUFFED, SURRENDERED, BUSTED_DOWNED, DEAD_OR_ALIVE) is persisted on DetainedPlayer. DetainmentCostsContract gets default multiplier methods read by sentence, bail and fine.
            UnblocksSurrender · Busted or wasted
            Cop stand-down flagcops-n-crooks0.18
            A per-target 'cease fire, cuff calmly' flag on CopGroup. CombatBehavior, PursuingBehavior and ranged attackers honour it. Any hit on a cop clears it through the existing escalate path.
            UnblocksSurrender
            Temporary blocks with guaranteed restoregangland-core0.19
            Place or replace N blocks, remember the originals in a persisted snapshot, and restore them on a timer, on shutdown and on the next startup. It skips chunks that are not loaded until they load.
            UnblocksRoadblocks · Pursuit breakers
            Duty stategangland-api + cops-n-crooks0.20
            gangland-api 2.5 DutyView: isOnDuty, side, and a switch cooldown. Cop, civilian and turf target filters exclude on-duty players. The flag is persisted per user.
            UnblocksOn-duty police
            NPC carrier bodiesKeystone keystone-npc0.20
            Keystone 1.16.0, product-free: (1) hover, holding an altitude over a moving anchor with gravity off; (2) mount and dismount a rideable carrier, dismounting when the rider dies; (3) spawn an animal-type NPC through NpcSupport, which degrades cleanly when Citizens is absent. No cop concepts.
            UnblocksPolice chopper · K9 units · Mounted units
            Safe money formulasgangland-core0.15
            One small class in gangland-core: evaluate(formula, variables, fallback). It builds a ScientificCalculator and evaluates it. If building or evaluating throws, or the result is NaN, Infinity or negative, it uses the fallback and logs one console warning per formula text. Whatever it returns, fallback included, is clamped to zero or above. A result of 0 means no charge: callers skip the withdraw and the message, as WantedExecutor.java:68 and :85 do today. One helper builds the shared variables from a User (balance, level, experience, bounty, wanted), the same set amountDeduction builds today; callers add their own (the star-drop charge adds amount and multiplier). Two callers: the star-drop charge (fallback Amount × Multiplier^wanted) and the death bill (fallback the shipped 'balance * 0.15'), so the death formula stops throwing out of the event handler. Bail and bribes can adopt it later. Tests: a valid formula, bad syntax, an unknown variable, division by zero and a negative result each return the right value; a negative fallback returns 0; the warning is logged once.
            UnblocksFines at the end · Hospital & one bill

## 0.15 features, money rules, star ladder
##### LADDER

        Escalation
        What each star means
        Today each star is one tier in bigger numbers, and a star drops on a fixed clock. The proposal gives every star its own mix of units and something new to deal with, and lets you shake it by staying out of sight. Treat the numbers as starting values to tune.
                Today
                Proposed
                Stars
                Squad
                A star drops after
                Squad
                New at this star
                Search zone
                Unseen time to drop
                ★★★★★
                2 Officers (tier 1, melee, wooden sword)0.13: Pointman, Assault
                132 s-250
                2 Officers, nearest patrol first
                Radio chatter, warnings, witnesses
                40 blocks
                10 s
                ★★★★★
                3 Sergeants (tier 2, melee, stone sword)0.13: Pointman, Assault, Assault
                145 s-1,250
                2 Officers, 2 Sergeants
                Dogs in foot chases, mounted units in open country
                60 blocks
                20 s
                ★★★★★
                4 Lieutenants (tier 3, rifle or crossbow, fan 200)0.13: Commander, Pointman, Defender, Marksman
                159 s-6,250
                2 Sergeants, 2 Lieutenants with rifles
                Chopper, roadblocks. Last star you can surrender at
                90 blocks
                30 s
                ★★★★★
                5 SWAT (tier 4, no cuffing, fan 270)0.13: Commander, Pointman, Defender, Marksman, Medic
                175 s-31,250
                2 Lieutenants, 4 SWAT
                Spike strips, a second dog, no cuffing
                130 blocks
                45 s
                ★★★★★
                6 Military (tier 5, no cuffing, fan 330)0.13: Commander, Pointman, Defender, Marksman, Medic, Assault (level 5 reuses level 4's list)
                193 s-156,250
                3 SWAT, 4 Military
                Two choppers, checkpoints, Dead or Alive
                180 blocks
                60 s
        Today's interval is 120 × 1.1stars seconds, rounded down and fixed by the last star you gained, so a five-star player waits 193 seconds for every drop. The cash column shows what each drop takes from your wallet today: 50 × 5stars, which adds up to $195,250 to go from five stars to zero. You can switch it off today with Wanted.Take_Money.Amount: 0. From 0.15.0 the charge is off by default: an admin turns it on with Wanted.Take_Money.Enable: true and sets the price with Formula, whose default amount * multiplier ^ wanted gives this column exactly. With the defaults, stars fall for free and money moves only when a chase ends or when you choose to spend. Squad sizes come from Cops.Count (Base 2, Per_Level 1). The 0.13 line is the role mix from Squad_Composition on the H13 branch; five stars reuses the four-star list.
      
##### MONEY SECTION

        The wanted economy
        Money and the wanted level
        Losing a star stops costing money out of the box. Today every drop takes 50 × 5^stars from the wallet, which is $156,250 for one drop at five stars, and you can switch it off on the live server now by setting the amount to 0. From 0.15.0 the charge is a setting and is off by default. An admin switches it on and writes the price as a formula, and the starting formula gives today's numbers. A broken formula never stops a star from dropping. With the default settings, money moves only when something ends or when you choose to spend. Busted costs a charge sheet, a trip to hospital costs one bill and never both for the same ending, and bail, bribes, contacts and the fence are your choice. Getting away pays in XP. Cop cash, your own cash on death and paying out the police's price stay switches too, all off by default. The new features add a hot-cash bag you keep only if you escape, a cold trail that winds a chase down when you go quiet, cards that say why you got each star, no stars for self-defence, and bounties that by default pay only what players actually posted. 0.15.0 also stops Lose_Money: false from paying players on every death.
            What an admin can set
            Switch the star-drop charge off today. In the plugin's settings.yml set Wanted.Take_Money.Amount: 0, then restart the server.
            WantedExecutor only charges when the amount is above zero (WantedExecutor.java:57).
            GanglandWantedSettings reads the static Settings value on every call (GanglandWantedSettings.java:30-31), so timers that are already running stop charging too.
            Do NOT delete the Take_Money block before 0.15.0. If the key is missing, Settings.java:544-545 falls back to 50 and 5 and the charge comes back.
            Do NOT set Wanted.Repeating_Timer.Enable: false. That stops stars from falling at all.
            Existing servers never get new keys or defaults (Keystone FileHandler.java:216 skips a file that already exists), so every live server makes this edit by hand.
            From 0.15.0 the charge is off by default. Wanted.Take_Money.Enable is the only on/off switch: it defaults to false, and a missing key counts as false, so upgraded servers stop charging without editing anything. To charge, set Enable: true and write a Formula (variables: amount, multiplier, wanted, balance, level, experience, bounty). Amount and Multiplier are just numbers the formula can use. With no Formula key, the default amount * multiplier ^ wanted gives today's 50 × 5^stars. A price of 0 charges nothing, so a server that set Amount: 0 and later turns Enable on with the default formula still charges nothing until it raises Amount. A broken formula, or a negative or non-number result, falls back to Amount × Multiplier^stars (never below 0), warns once in the console, and the star still drops. A server that wants to keep today's charge after upgrading adds Enable: true and keeps Amount at 50.
            Optional, also config only. Each one is a switch an admin can turn back on:
            Set Bounty.Repeating_Timer.Enable: false to stop the server-made bounty doubling every 300 s, which an alt can claim (settings.yml:260-266).
            In items/money.yml, set Money.Drop_Sources.COP.Enabled: false so cops stop dropping 100-2,000 of server-made cash.
            To make a death cost one thing, set Money.Drop_Sources.PLAYER.Enabled: false in items/money.yml and keep User.Death.Money.Formula as the hospital bill.
            Before 0.15.0, never set User.Death.Money.Lose_Money: false for this. It does not turn the loss off: it PAYS the player the formula amount on every death (PlayerDeathListener.java:148-154 deposits when it is false). To charge nothing on death, set Formula: "0". Players at or under Death.Money.Threshold already pay nothing (PlayerDeathListener.java:116-119). The shipped default is 1,000 (settings.yml:227). From 0.15.0, Lose_Money: false means no death charge.
            settings.yml from 0.15.0
Wanted:
   Enable: true
   # Money taken from a player each time one of their stars drops.
   Take_Money:
      # The on/off switch. Off by default, and a missing key counts as false.
      # Set to true to charge on every star drop.
      Enable: false
      # The price of one star drop. Variables you can use:
      #   amount     - the Amount below
      #   multiplier - the Multiplier below
      #   wanted     - the player's stars before this drop
      #   balance    - the player's wallet (never the bank)
      #   level      - the player's level
      #   experience - the player's current experience
      #   bounty     - the bounty on the player
      # Amount and Multiplier are only numbers for the formula.
      # They do not switch the charge on or off.
      # The default gives 50 * 5 ^ stars: $250 at 1 star, $156,250 at 5.
      # Gentler examples: "amount * wanted" or "balance * 0.02 * wanted".
      # A price of 0 charges nothing.
      # If the formula is broken, or its result is negative or not a number,
      # the console warns once and amount * multiplier ^ wanted is used
      # instead, never below 0.
      # The star always drops, and a wallet never goes below zero.
      Formula: "amount * multiplier ^ wanted"
      Amount: 50
      Multiplier: 5
        The rules a chase follows
          Stars fall free unless your server says otherwiseOut of the box, losing a star never touches your wallet. An admin can switch on a charge for each drop and set its price with a formula, but it stays off unless they do. Stars fall because you stayed out of sight, not because a timer ran.
          Money moves at an ending or a choiceWith the default settings you pay only when something ends (busted, or a trip to hospital) or when you choose to spend (bail, a bribe, a contact, the fence). You never pay on a timer or when a star changes unless your server switches the star-drop charge on.
          One ending, one billBusted costs a charge sheet and a hospital trip costs a bill. You never pay both for the same ending, and each is charged once, from your wallet only, never below zero and never from the bank.
          You lose what you stole, not what you savedCash you grab while you're wanted is at risk until you get away. Your clean wallet and your bank are not.
          Every star has a reason you can seeEach star names the crime that caused it. Defending yourself, or claiming a bounty that players posted, is not a crime.
          Getting away pays in reputationEscaping earns XP, not new cash. The only cash you keep is what you stole and carried out, capped per day.
          A bounty pays only what players put upThe price the police put on you is notoriety, not a cash prize, so no one can farm money that nobody paid. A server can choose to pay it out, but that is off by default.
          Admins hold the switchesEvery money moment this plan turns off stays a setting: the star-drop charge, cop cash drops, your own cash dropped on death, and paying out the police's price on your head. The defaults follow these rules, and an admin can turn any of them back on. A broken money formula never breaks the game: it falls back to a safe price and the console warns once.
        Where money changes hands
            MomentTodayIn this planWhy it makes senseShips in
              A star falls (the chase tax)Each drop burns 50 × 5^stars from the wallet, using the level before the drop: $250 at 1 star, up to $156,250 at 5, and $195,250 to go from 5 stars to 0. A wallet that cannot cover it is emptied. Setting Amount to 0 is the only off switch (WantedExecutor.java:54-70, :85-87; settings.yml:273-279).Off by default, and an admin setting. Wanted.Take_Money.Enable is the only switch (default false, also when the key is missing). Formula sets the price, and its default 'amount * multiplier ^ wanted' gives today's numbers exactly; Amount and Multiplier are just numbers the formula uses. A price of 0 charges nothing. A broken formula, or a negative or non-number result, falls back to Amount × Multiplier^stars, never below zero, and warns once. The star always drops and the wallet never goes below zero. Until 0.15.0, set Amount to 0 to switch it off.Time passing is not a crime and nobody receives the money, so it stays off unless a server wants its chases to cost. When a server does, the admin sets the price.today (config); toggle + formula 0.15Fines at the end
              Busted (jail intake)No money moves. Your items are seized and returned on release, your stars are cleared, and the sentence is 180 s + 60 s per star (JailIntakeService.java:50-64).A charge sheet with a price for each crime from this chase, priced linearly by base plus per star until the heat ledger has prices, and capped. Your wallet pays what it holds and the rest becomes extra time, also capped. You are charged once and never also get a hospital bill.It is a court fine for named crimes, charged at the moment you are caught. A broke player pays in time and nobody goes into debt.0.15Fines at the end
              Wasted (any death or down, wanted or not)Death.Money.Formula 'balance * 0.15' is burned from the wallet, minus the bank-tier discount, and is skipped when the balance is at or under Death.Money.Threshold. The shipped default is 1,000 (settings.yml:227). It is charged on both the death path and the downed path (PlayerDeathListener.java:81-85, :91-114, threshold check :116-119, charge :131-157). A broken formula throws out of the death handler (:213-228).Still one hospital bill per death, wanted or not, from the existing formula. A broken formula no longer breaks deaths: it falls back to the shipped 'balance * 0.15' and warns once. On the downed path the bill moves to the moment you actually respawn, so a down that turns into an arrest pays only the charge sheet. No second medical charge is added.You pay for your treatment once. Poor players are not ruined and the bank is never touched.today (config); safe formula 0.15; bill at respawn 0.16Hospital & one bill
              Dying with Lose_Money set to falseDeath.Money.Lose_Money: false pays the player the death formula amount instead of charging it (PlayerDeathListener.java:148-154; settings.yml:217-218 calls it 'lose or gain'). At the default 15%, every death above the threshold adds 15% to the wallet, money nobody paid.false means no death charge, and nothing is paid out. A server that wants a payout on death runs it through Death.Money.Command.A switch named 'lose money' should turn the loss off, not print money.0.15Fines at the end
              Your own cash dropped on deathA cash item is rolled at 10-2,000, scaled by floor(balance × 0.15) and clamped to the variation max. It is withdrawn from the wallet and dropped at the body, on top of the death bill (MoneyDropListener.java:67-88; items/money.yml Drop_Sources.PLAYER).An admin toggle, Money.Drop_Sources.PLAYER.Enabled in items/money.yml. The bundled file ships it false, so a death costs one bill. Existing servers keep the value they have until an admin changes it. Once the bag exists, only your hot bag spills on the street.One death should cost one thing. Spilling stolen cash makes sense, but scattering your savings does not.today (config); bundled default off 0.16Hospital & one bill
              Cop cash dropsEvery cop killed drops server-made cash worth 100-2,000 (money.yml Drop_Sources.COP, medium 70 / large 30).An admin toggle, Money.Drop_Sources.COP.Enabled in items/money.yml. The bundled file ships it false, so cops carry no cash and killing cops is no longer a money faucet. Existing servers keep the value they have until an admin changes it.A wallet worth thousands on every officer turns the police into piñatas and works against the point of being wanted.today (config); bundled default off 0.17Hot-cash bag
              Cash you pick up while wantedIt goes straight to your wallet.It goes into a bag, and whether you are wanted is checked when you pick it up, so the drop from the kill that gave your first star also goes in. Get away and the bag banks, up to a daily cap. If the chase ends any other way (a sign, a contact, a bribe), the bag converts at the fence's cut. Busted: it is seized. Wasted, or quitting while the server runs: it spills on the street. A server stop or reload saves it with your account.Stolen cash is only yours once you have got away with it. Nothing makes new money, and nothing vanishes without a reason you can see.0.17Hot-cash bag
              Fence the bagDoes not exist.While unseen, call the fence to turn the bag into clean cash minus a 35% cut. Bribes can be paid from the bag first.It is a choice in the middle of a chase: always worse than getting away and better than being busted, so it cannot be farmed.0.17Fence the bag
              Getting awayNothing is paid for it, and each star drop on the way down takes 50 × 5^stars unless the server set Amount to 0.Getaway XP by peak stars, where one star pays nothing, capped per day. Crewmates who are wanted in the same chase get half, and your bag banks.Escaping is the win, so it pays in progress, never in newly made cash.0.18Clean getaway
              Bounty on your head is claimedYour killer gets the whole bounty. That is what players posted, plus a server-made part added for every star you gain (scaled by level), which doubles every 300 s up to 20,000, and the last doubling can go past that cap (EntityDamageListener.java:126-141, :221-225; BountyExecutor.java:57-71). Only the total survives a restart (UserDataLoader.java:96, :150), and who posted what is held in memory only (Bounty.java:22, :31).By default only the money players posted is paid. The server-made part stays on you as notoriety and is not paid in cash. Paying it stays an admin choice: a new Bounty.Pay_Notoriety switch (default false) pays it out as today, and its growth keeps its existing keys, Bounty.Kill.Each and Bounty.Repeating_Timer.Enable. At the 0.15.0 upgrade, every existing bounty counts as posted once, so nobody's paid-in money is lost.Players paid it, so a player collects it. An alt can no longer kill you for money nobody put up.0.15Paid bounties
              Post or cancel a bountyThe money moves from the poster's wallet into escrow and is refunded on cancel, but the per-poster record is lost on a restart.Unchanged, except that the posted total is now saved, so a cancel still refunds after a restart. By default this escrow is the only money a bounty pays.Money leaves only by the player's own choice.today; saved total 0.15Paid bounties
              Handcuff bribeCosts 500 + 250 × stars, all or nothing, clears your stars, and the money is burned (BribeService.java:59, :70; settings.yml:426-428).Same price, paid from the bag first. Anything left in the bag converts at the fence's cut.It is a purchase you choose that ends the chase at once.today; bag funding 0.17Fence the bag
              BailCosts 2,500 + 1,000 × stars at arrest, all or nothing, and is optional against serving the sentence (BailService.java:42; settings.yml:431-433).Unchanged, and kept separate from the charge sheet, so the paperwork offers 'pay or serve'. Surrendering lowers it.Buying your freedom is a choice, not a penalty.today; surrender 0.18Surrender
              Jail bribeCosts 1,000 + 500 × stars with a 35% chance. On failure the money is kept and 60 s is added (BribeService.java:92-105; settings.yml:437-441).Unchanged. Later, on-duty player cops can take it.It is a gamble you opt into.today; player cops 0.20Corruption & IA
              [WANTED] sign removes starsCharges the sign's flat price (MoneyAspect withdraw, WantedSign.java:37-40) and works anywhere, even with cops watching. canExecute only checks that you have stars (WantedAspect.java:62-79).Same price, but it works only while no cop can see you, with the contacts cooldown. If you are carrying a bag, it converts at the fence's cut.Paying to lie low is fine. Paying to vanish in front of a squad is not, and neither is using the sign as a cheaper fence.0.16; bag rule 0.17Crooked contacts
              Crooked contact on the phoneDoes not exist.A price per star, only while unseen, on a 10-minute cooldown.A chosen purchase with a cost and a risk.0.16Crooked contacts
              SurrenderDoes not exist.Half the charge sheet and lower bail.Giving up cleanly should cost less than being dragged in.0.18Surrender
              Post bail for a friendDoes not exist.You pay your friend's bail once, from your wallet.Your choice, your money, and a clear reason.0.19Bail for a friend
              Bounty hunter payoutDoes not exist.The posted bounty is paid exactly once, by whichever ending comes first: the kill claim if the target dies, or jail intake if a hunter brings them in alive. Alive delivery also pays a capped share of the target's charge sheet. Notoriety is not paid unless the server turns Bounty.Pay_Notoriety on.The players who posted, and the criminal's own fine, fund the capture. By default the server prints nothing.0.20Hunter license
              Civilian and mob cash dropsServer-made small stacks: civilian 10-500, mob 10-100.Unchanged while you're not wanted. While you're wanted, they go into the bag.A wallet on a body is believable. The cost of the crime is the star, not money.today; bag 0.17Hot-cash bag
      
##### f-heat

              Headline
              Heat ledger
              GTA V
RDR2
                Effort
M
                Ships in
0.15
                Status
To build
              Every crime pays out heat, and heat turns into stars. Shooting a cop lights you up at once. Shoving a stranger barely registers. Crimes chained inside a few seconds still stack up fast, so the kill combo keeps its bite.
              Replace the one-point-per-kill combo with a crime-weight table. Today KillCombo.recordKill passes 1 point per kill and the star table is Wanted.Kill_Combo.Kill_Counter. Heat becomes a per-player ledger in cops-n-crooks fed by CrimeCommittedEvent, star thresholds read heat, and the combo window becomes a streak bonus. Both timer-less star sources (handleWanted, CivilianDeathRewardListener) go through the same ledger, which also fixes the decay-less pedestrian star. Kills inside a contested turf earn half heat (Turf_War_Multiplier 0.5) and kills by turf defenders mint none (Gang & Turf P6.4).
              Sketch for the module's own config
Heat:
   Star_Thresholds: [100, 250, 450, 700, 1000]
   Streak_Bonus: 1.5               # crimes chained inside Kill_Combo.Reset_After
   Seen_By_Cop_Multiplier: 1.5
   Turf_War_Multiplier: 0.5        # kills inside a contested turf
   Crimes:
      Brandish_Near_Cop: 25        # only after an ignored warning
      Assault_Civilian: 30
      Car_Theft: 60
      Kill_Player: 80
      Kill_Civilian: 100
      Assault_Cop: 100             # one hit is enough for the first star
      Resisting_Arrest: 100
      Kill_Cop: 150
      Safe_Cracking: 150
      Store_Robbery: 200
      Trespass_Restricted: 300
      Jailbreak: 450
              Builds onKillComboTracker.addKill(entity, points) (cops-n-crooks combo/, already takes points) and KillCombo.recordKill (hardcoded 1) · gangland-core seam WantedKillTracker/WantedKillTrackers -> KillComboWantedTracker · Wanted.Kill_Combo.Kill_Counter (Settings.java:554) becomes Star_Thresholds in cops-n-crooks YAML · EntityDamageListener.handleWanted (gangland-impl :206) and CivilianDeathRewardListener (gangland-civilians) routed through the ledger · Wanted.incrementLevel/setLevel + WantedLevelChangeEvent
              Needs firstCrime event bus · Wanted decay seam
          
##### n-cnc-regroup

              New
              Pull back and regroup
              F.E.A.R.
Halo
GTA V
                Effort
S
                Ships in
0.15
                Status
To build
              Drop two cops in quick succession from a good spot and the rest stop running into your sights. They pull back into cover, call for backup, and come at you together once it arrives.
              Two casualties within 20 seconds make the leader radio Regroup, set fallBackUntil (up to 15 s) and request backup. The fall-back ends on the spawnTick where the live count reaches the target; the leader radios Push and everyone leaves cover on the same tick. One regroup per 60 s. Squads still in cuff-first mode never regroup.
              Builds onCopRadio.listenerFor (CopRadio.java:79-91) MAN_DOWN/LEADER_DOWN · CopGroup.fallBackUntil/isFallingBack/requestBackup (CopGroup.java:70,144,149) · CopRetreat.takeCover · CopManager.spawnTick targetCount
          
##### f-evasion

              Headline
              Line-of-sight evasion and the search zone
              GTA V
GTA IV
                Effort
M
                Ships in
0.15
                Status
To build
              While any cop has eyes on you, your stars stay solid. Break line of sight and they start to flash as a search zone forms around the last place you were seen. Stay unseen and the clock runs; get out of the zone and it runs faster. When it finishes you lose a star. Get spotted and the zone snaps back onto you.
              The squad already knows, every AI tick, whether anyone saw you in the last 1.5 seconds (NpcSquad.hasFreshSighting) and where (lastKnownLocation). A new evasion clock in cops-n-crooks reads that for each wanted player and drives star decay through the wanted decay seam policy. The old Repeating_Timer stays as the safety net when no cop can spawn, and the Evasion block lives in cops-n-crooks YAML, not settings.yml.
                  ★★★☆☆ IN SIGHT
                  Seen
                  ★★★☆☆ SEARCHING 0:23
                  Searching
                  ★★☆☆☆ STAR LOST
                  Evaded
              Sketch for the wanted config
Wanted:
   Evasion:
      Enable: true
      Lost_Sight_Seconds: 3                  # stars flash after this long unseen
      Drop_Mode: ONE_STAR                    # ALL_STARS plays by GTA V rules
      Search_Radius: [40, 60, 90, 130, 180]  # blocks, per star
      Seconds_To_Drop: [10, 20, 30, 45, 60]  # unseen time, per star
      Outside_Zone_Speed: 2.0
      Hideout_Speed: 1.5
   Take_Money:
      Enable: false                          # off by default; an admin sets the price in Formula
              A clean five-star escape takes about three minutes of staying unseen (10 + 20 + 30 + 45 + 60 seconds), less if you leave each zone. Today it takes 16 minutes and 195,250 cash, or one death.
              Builds onKeystone NpcSquad.hasFreshSighting / lastKnownLocation / millisSinceSighting via CopGroup.getSquad() · gangland-core Wanted.createTimer + WantedExecutor.execute (behind wanted decay seam) · Wanted.decrementLevel · WantedLevelChangeEvent / WantedStartEvent / WantedEndEvent · CopManager.onWantedLevelChange
              Needs firstWanted decay seam
          
##### f-hud

              Flashing stars, boss bar and escape compass
              GTA V
                Effort
S
                Ships in
0.15
                Status
To build
              One glance tells you where you stand. Every new star flashes a card that says why you got it and what it brings ('Assault on an officer: Sergeants inbound, they still want you in cuffs'). The boss bar shows your stars in red while you're seen, flashing yellow with a countdown while they search, and green when a star drops. Red particles trace the edge of the search zone, visible only to you. Your phone, which is already a compass, points at the nearest way out.
              A per-player boss bar shows your stars: red while you're seen, flashing yellow with a countdown while they search, and green when a star drops. It must coexist with the turf and car bars and leave the action bar to Gang & Turf P2.3. The first slice is a title flash and a siren on WantedLevelChangeEvent. The title's subtitle is a star card. It names the last crime the heat ledger stored ('Assault on an officer') and says what the new star brings, built from fields the tier config already has: the display name, and skipCuffing for 'they want you in cuffs' or 'they shoot first'. When a star drops, the card says what kept you hidden. The card replaces the bare chat line at EntityDamageListener.java:241-245. The zone ring and compass read WantedEvasionStateEvent.
              Builds onWanted.buildStars / getLevelStars (gangland-core) · WantedLevelChangeEvent · WantedEvasionStateEvent (wanted decay seam) · GanglandPlaceholder wanted placeholders · phone unique item (unique_items.yml:19, COMPASS) · TurfBossBarListener as the per-viewer BossBar pattern
              Needs firstLine-of-sight evasion · Heat ledger
          
##### n-shots-give-you-away

              New
              Shots give you away
              GTA V
Hitman
Payday 2
                Effort
S
                Ships in
0.15
                Status
To build
              If you fire a gun while the cops are searching for you, every unit within earshot learns where you are. You can fight your way out, but to slip away you have to keep quiet, so a knife or a bow becomes a stealth tool.
              A ShotNoiseListener on WeaponShootEvent (player shooters only) uses a per-WeaponType noise radius in cops.yml (GUN 48, THROWABLE 16, MELEE 0). If the shooter is wanted and a cop of their group is inside the radius, it calls reportSighting(shot location), which re-centres the line-of-sight evasion zone. The nearest cop radios Shots_Fired, at most once per shooter every 3 seconds.
              Builds on[BZ 0.6.0] bartizan-api WeaponShootEvent.java:12 · [GL] DetainmentListener.java:46 (existing WeaponShootEvent handler), CopManager.getCopsForPlayer, CopNpc.squadFor, CopRadio.sayFromLeader · [KS 1.14.0] NpcSquad.reportSighting :186 (public, no signal)
              Needs firstLine-of-sight evasion
          
##### f-fines

              Fines at the end, star-drop charge off by default
              GTA V
                Effort
M
                Ships in
0.15
                Status
Part built
              Out of the box, losing a star costs you nothing. Your server can choose to charge for each drop, and if it does, the price follows the formula its admin set. You pay when the chase ends badly. Get busted and you get a charge sheet with a price for each crime. Your wallet pays what it can, and you serve the rest as extra time. Your bank is never touched.
              The star-drop charge stays as an admin setting, off by default. Wanted.Take_Money gets Enable, the only on/off switch (default false, also when the key is missing, so upgraded servers stop charging), and Formula (default 'amount * multiplier ^ wanted', which gives today's numbers exactly). Amount and Multiplier stay as the formula's amount and multiplier, plain inputs that no longer switch anything, next to balance, level, experience, bounty and wanted (the stars before the drop, as today). WantedExecutor evaluates it through the shared money-formula evaluator: a broken formula, NaN, Infinity or a negative result falls back to amount × multiplier^wanted, clamped to zero or above, and warns once. A price of 0 skips the withdraw and the message, the star always drops, and User.withdraw keeps the wallet at zero or above. WantedSettings gains isTakeMoneyEnabled and getTakeMoneyFormula; getTakeMoneyAmount, getTakeMoneyMultiplier and formatMoneyLoss stay, so gangland-api changes stay additive. The bundled settings.yml ships the new keys with a comment listing the variables. Death.Money.Lose_Money: false stops paying players: it means no death charge, and a server that wants a payout on death uses Death.Money.Command. The busted fine becomes a charge sheet priced by crime, and any shortfall is served as extra time.
              Where it standsRemaining: (1) Make the star-drop charge a setting. Today every star drop burns 50 × 5^stars from the wallet (WantedExecutor.java:54-60 compute, :68-70 withdraw, :85-87 message; settings.yml:273-279), and Amount: 0 is the only off switch. There is no Enable key, and existing settings.yml files are never rewritten (Keystone FileHandler.java:216), so the in-code default of the new Enable key decides what upgraded servers do. Until 0.15.0 ships, live servers set Take_Money.Amount to 0 by hand. If Gang & Turf W4 is still open, the WantedExecutor change can ride W4, which owns core wanted. (2) The charge sheet at JailIntakeService.admit (:50-64). DetainmentEconomyContract.tryCharge is all-or-nothing (GanglandDetainmentEconomyContract.java:31), so the fine charges min(balance, fine) through getBalance plus tryCharge, with no new contract method. The hospital bill stays Death.Money.Formula (PlayerDeathListener.java:131-157, :213-228), and a bust never also pays it. (3) Death.Money.Lose_Money: false pays the player the formula amount on every death (PlayerDeathListener.java:148-154), which prints money.
              Builds onWantedExecutor.execute (gangland-core core/wanted/WantedExecutor.java:54-60, :68-70, :85-87) · WantedSettings + GanglandWantedSettings (gangland-impl file/configuration/wanted/GanglandWantedSettings.java:29-37, :44-47; add isTakeMoneyEnabled and getTakeMoneyFormula) · WantedContext.withdraw + User.withdraw (core/user/User.java:99-114, kept) · Settings.java (gangland-api :94-95, :537, :544-545; add the Enable and Formula reads) · settings.yml:273-279 · WantedExecutorTest (stubs getTakeMoneyAmount; add off, formula, zero-price and broken-formula cases) · PlayerDeathListener.handleMoney Lose_Money branch (gangland-impl :148-154) and settings.yml:217-218 · JailIntakeService.admit (cops-n-crooks detainment/intake/JailIntakeService.java:50-64) · DetainmentCostsContract (add computeFineCost next to computeBailCost :41-43) · DetainmentEconomyContract.getBalance + tryCharge · DetainedPlayer (fine-paid flag next to wantedAtArrest) · PaperworkView · heat ledger crime list
              Needs firstHeat ledger · Money formulas
          
##### n-paid-bounties

              New
              Bounties somebody paid
              RDR2 (bounties paid at the post office)
GTA Online (player-funded bounties)
                Effort
M
                Ships in
0.15
                Status
Part built
              The price on your head is real money that someone put up. Kill a player with a bounty and you collect what other players posted. The police's own price is your notoriety: it shows how hot you are and draws hunters, but nobody gets paid cash for it unless your server chooses to pay it.
              The claim pays the posted total and resets only that part. The claim branch checks the posted amount, not hasBounty(), so a target who has only notoriety takes the normal crime path. With Bounty.Pay_Notoriety: true the claim pays posted plus notoriety and resets both, which is today's behaviour. Notoriety stays until death or arrest clears it, as today. BountyExecutor grows notoriety only, clamped to Maximum. Exploit guard: the alt-kill farm works like this today: keep your wallet under the death threshold, sit at five stars while the timer doubles, then have an alt kill you. After this change it pays only posted money by default, which came out of someone's wallet. Refunds keep using the paid figure. A bounty you post on your own alt pays back only what you put in. Tests: an upgrade with a non-zero saved bounty can be claimed in full once; a restart keeps the posted total and the refund; Pay_Notoriety true pays the whole amount once.
              Where it standsPartly built. Posting a bounty already charges the poster, and Bounty tracks what each poster set and paid (Bounty.java:22, :31, :89-110), but only in memory. Only the scalar total is saved (UserDataLoader.java:96, :150), so who posted what is lost on a restart. Missing: today a kill pays the whole amount (EntityDamageListener.java:126-141), and most of it is server-made. Every star adds an auto bounty (:221-225, Bounty.getAutoBountyIncrease :120), and BountyExecutor doubles it every 300 s up to 20,000 (BountyExecutor.java:57-71). The cap check reads the old amount, so the last doubling can go past 20,000.
              Builds onEntityDamageListener.handlePlayerKills bounty claim (gangland-impl listener/player/EntityDamageListener.java:126-141) · handleWanted auto bounty (:221-225) · BountyExecutor.execute (gangland-core core/bounty/BountyExecutor.java:57-71) · Bounty userSetBounty/userPaidBounty/resetBounty (Bounty.java:22, :31, :60-66) · BountySetCommand / BountyClearCommand (escrow and refund) · UserDataLoader bounty read (:96, :150) plus one additive posted-total column in the user table · Settings.java Bounty section (gangland-api :524-529) plus one Pay_Notoriety read
          
## Roadmap entry for 0.15 (scope, Done-when)
        Roadmap
        6 releases after Gang & Turf, each playable on its own. Every release has a test you can run in game to know it's done, and names the Keystone, Bartizan and API versions it needs.
            0.15
Lose them
Keystone 1.14.0
gangland-api 2.2
              Make escaping a skill. Stars fall when you stay unseen rather than on a fixed timer, and by default a star dropping costs nothing; an admin can switch a charge on and set its price with a formula. Every crime feeds one heat ledger, and you can see where you stand.
                Heat ledger
                Line-of-sight evasion
                Wanted HUD & compass
                Fines at the end
                Shots give you away
                Pull back & regroup
                Paid bounties
              FoundationsWanted decay seam · Crime event bus · Chase test scenarios · Money formulas
              This is the biggest lever and it is mostly built: NpcSquad.hasFreshSighting and lastKnownLocation exist, and nothing connects them to WantedExecutor. Every later stage needs the crime bus and the decay seam. All items are S/M and Gangland-only on Keystone 1.14.0. It starts only after Gang & Turf W4 has merged (0.14.0, or 0.14.1 if W4 splits off), because W4 owns core wanted and EntityDamageListener. The H12/H13 live acceptance happens earlier, on the 0.13.x acceptance track, since H13 must merge before W4. 0.15 only extends that harness: every release from here adds its “Done when” test as a scenario. Owner decision needed before work starts (evasion vs the H11 decay-freeze decline).
            Done whenGet two stars and break line of sight behind a building: the boss bar flashes yellow with a countdown and a star drops when it ends, with no death needed. Get spotted and it turns red again. Fire a gun while hidden and the squad converges on the shot with a 'Shots fired' radio line. Hit a cop once and the heat ledger gives you a star, and the star card names the crime. With default settings a star dropping moves no money, including on an upgraded server whose settings.yml has no Enable key. Set Take_Money.Enable: true and the default formula charges today's 50 × 5^stars; write a broken formula and the console warns once, the fallback price is charged, and the star still drops. Get busted with $300 on you against a $700 charge sheet: you pay $300 and serve the rest as extra time, and the paperwork lists both. Kill a player with a posted bounty and you collect only what players posted, never the server-made part. A bounty saved before the upgrade is still paid in full, once. Kill two cops in quick succession from cover and the rest pull back, radio for backup and push together when it lands. The scripted 1/3/5-star, break-line-of-sight, cuffed and logout-while-wanted scenarios pass against the 0.15.0 build, plus new evasion-drop, no-money-on-drop, charge-switched-on and bounty-upgrade scenarios.

## Guardrails and owner decisions (recommended option = decided)
        Constraints
        Guardrails
            Rules for every feature
              Behind a toggle. Each feature gets its own Enable key in the module's own config, which is where the module API rules put new settings.
              Stock APIs only. Boss bars, per-player particles, map renderers, compass targets and Citizens NPCs cover everything here. The core jar ships no NMS, and this plan doesn't need any.
              Inside the AI budget. Choppers, dogs and roadblock cops count toward Max_Per_Player, and their sight checks run on the existing AI_Tick_Rate of 10 ticks.
              Fair PvP. No bounty payouts between gang mates or allies, a cooldown on switching sides, no clocking in while wanted, and a per-target cooldown on hunter contracts.
              Keystone stays generic. Only product-free pieces move into Keystone, and only once a second plugin uses them: the out-of-sight spawn helper and hold-post leash in 1.15.0, NPC carrier bodies (hover, mount, animal) in 1.16.0. Wanted levels, crimes and stations stay in Gangland.
              Spigot 1.16.5 floor. Nothing here needs newer Bukkit API or Paper. Sight checks use World.rayTraceBlocks, never Paper's hasLineOfSight(Location).
              Additive API. New surface such as CrimeCommittedEvent, WantedEvasionStateEvent, RegionProvider, the vehicle events and DutyView is added, never changed, with one minor bump per release that adds any (2.2 to 2.5, after Gang & Turf's 2.1).
            Decisions for you
              Evasion clock vs the H11/H12 decline of a wanted-decay freeze: may stars stop falling while a cop can see you? Approve line-of-sight evasion as a replacement for the fixed timer, not a freeze bolted onto it. Stars fall faster than today when you are unseen (a skill path), Repeating_Timer stays as a safety net, and an Evasion.Enabled switch restores today's behaviour. Decide before 0.15 starts. Keep the fixed timer and only add the HUD; or let seen time merely slow the timer instead of pausing it.
              One star at a time, or all at once? Drop one star per completed clock, with Drop_Mode (ONE/ALL) in cops-n-crooks YAML so a server can switch to GTA V behaviour. Clear every star on evade (GTA V).
              Should death still wipe stars? Yes, but with busted or wasted a cop standing over you turns the down into an arrest, and criminal record keeps the district bounty. Dying is no longer the free exit it is today (15% cash versus 195,250 and about 16 minutes to wait out five stars). Keep stars through death (RDR2 style); or wipe stars and record.
              Do turf wars draw the police? Half heat (Turf_War_Multiplier 0.5, owned by heat ledger) for kills inside a contested turf, and zero for kills by turf defenders, matching Gang & Turf P6.4. Zero heat for all turf-war kills (police-free turf fights); or full heat.
              Police as a faction or a shift? A shift anyone can clock into at a station, with a switch cooldown and a record/wanted gate, so nobody is locked out of crime for good. A permanent faction chosen once.
              A wanted boss bar after the declined 'awareness' boss bar Approve the wanted HUD & compass bar: it shows your own stars and the search countdown, not cop awareness or a detection meter. Ship the title flash and siren slice in 0.15 regardless. Action-bar-only stars (conflicts with Gang & Turf P2.3); or placeholders only.
              Release order against Gang & Turf and H13 acceptance Run the H12/H13 live acceptance first, on the 0.13.x acceptance track (scripted 1/3/5-star, break-line-of-sight, cuffed and logout scenarios), so H13 can merge, which Gang & Turf W4 needs. Cops N Crooks 0.15.0 starts only after W4 has merged (0.14.0, or 0.14.1 if the owner ships 0.14.0 without W4), because 0.15 edits core wanted and EntityDamageListener, which W4 owns. Run 0.15 in parallel on a separate branch and rebase onto W4 (merge-conflict risk in the same files); or start 0.15 after 0.14.0 even if W4 slipped to 0.14.1 (not recommended: same files).
              Keystone promotions and the shared 1.15.0 slot Promote the out-of-sight spawn helper and hold-post + leash into Keystone 1.15.0 at the start of 0.16 (Cops N Crooks is the second consumer). 1.15.0 also carries the Gang & Turf CommandMap helper (admin-chosen short labels) if it is pulled in by then; if it comes later it takes 1.15.x. Keep the map painter, block restore and perch finder in Gangland. Add carrier bodies (hover, mount, animal) as Keystone 1.16.0 for 0.20 only. Keep everything in Gangland and accept two copies of the spawn and post logic; or build the chopper and mount bodies in Gangland behind Citizens reflection.
              Perception changes: darkness and crowds, and the police scanner Approve both. Darkness and crowds only scales the existing alert range (no glimpse meter). The scanner extends radio range only for players carrying the item, which is not the declined radio operator NPC. Drop darkness & crowds; or keep the radio strictly at 32 blocks.
              gangland-api bump cadence One additive minor per release that adds surface: 2.2 (0.15), 2.3 (0.16), 2.4 (0.17), 2.5 (0.20), all after Gang & Turf's 2.1. 0.18 and 0.19 add none. Batch every Cops N Crooks API surface into one bump at 0.15 (front-loads design of seams not yet needed).
              Chopper body and event payout Chopper: a Citizens NPC with gravity off, behind the Keystone NpcSupport carrier; spike it before committing 0.20. Event payout: verify a gang bank in gangland-gang during Gang & Turf W5; if none exists, pay the boss's balance. An armour-stand marker riding a bat/phantom carrier; or add a gang bank in 0.20.
              What does being wasted cost: a hospital bill, or your cash on the street? One hospital bill for every death, wanted or not. Use the existing Death.Money formula: 15% of the wallet, skipped at or under the threshold, minus the bank discount, and charged at respawn on the downed path. Ship the PLAYER cash drop off in the bundled file, as a switch an admin can turn back on. Once the bag exists, only hot cash spills. Keep the street drop as the only loss and set Death.Money.Formula to '0', so someone picks up your cash instead of it being burned. Or charge the bill only while you're wanted (gate the formula on wanted > 0). Or keep both charges, as today.
              How does getting away pay you? XP by peak stars, shared only with crewmates who were wanted in the same chase and capped per day, plus banking your bag up to a daily cap. No cash reward of its own. A cash bonus per star escaped, which alts and staged chases can farm. Or nothing beyond keeping the bag.
              What does busted take? The charge sheet (from the wallet only, with any shortfall served as time) plus the bag, and never a hospital bill as well. Seized items still come back on release, and the rest of the wallet and the bank are untouched. Time and items only, as today, with no fine. Or a share of the wallet on top of the fine.
              Who pays a bounty, and what happens to bounties that exist at the upgrade? By default only money players posted is paid out. The server-made part becomes notoriety that criminal record and the hunters read, and a server can turn Bounty.Pay_Notoriety on to pay it as today. At the 0.15.0 upgrade, every existing bounty counts as posted once, so no player's paid-in money is lost. The cost is one last payout of server-made money. Treat every existing bounty as notoriety only (no one-time payout, but money players already posted is lost). Or keep the server-made bounty, cap it hard and stop the 300 s doubling.
              Is killing a wanted player nobody has put money on a crime? Yes, unless it was self-defence. Only posted-bounty takedowns are exempt. Licensed hunters and on-duty cops get their own rules when those features land. Exempt any kill of a player at or above a notoriety or star threshold, which makes vigilante play easy and lets a crew farm one of their own for free kills.
              What does logging out mid-chase do? Your stars persist, as today. Your bag spills where you logged out (a server stop saves it instead). On rejoin, the pursuit comes from a station after a short grace period, so quitting is never the best way out. Freeze the stars and spawn nothing until your next crime, which is today's free escape. Or count a logout while you're seen as a crime on your record.
              When an admin switches the star-drop charge on, what should the starting formula be? Keep today's 'amount * multiplier ^ wanted' (50 × 5^stars). Switching it on then gives exactly the old charge, and the settings comment shows gentler examples. The formula engine has no min or max, so a hard cap would need its own key. A gentler default such as 'amount * wanted' ($50 a star, $750 from five stars to zero), or a wallet share such as 'balance * 0.02 * wanted'. Either one surprises a server that turns the charge back on expecting the old numbers. Or add a Max key so even a steep formula stops at a set amount.
              What should Death.Money.Lose_Money: false do? Charge nothing on death. Today it pays the player the formula amount on every death, which prints money. A server that wants a payout on death can still run one through Death.Money.Command. Keep the payout and warn in the settings comment that it prints money. Or move the payout behind a new key with a clear name, such as Death.Money.Pay_On_Death.
        Re-audited on 4 October 2026. Code references are to Gangland Warfare 0.12.0 (master, commit ff9d813f) and, where marked, the 0.13.0 branch npc-roles-medics (563ff351). Keystone references are to 1.13.0 and the 1.14.0 branch. GTA 6 references come from its public trailers.
