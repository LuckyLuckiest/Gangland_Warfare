# Migrating a server to Gangland 0.21.0 (Gang & Turf III; number confirmed at landing)

[← Back to Documentation Index](./README.md)

Gangland 0.21.0 needs Keystone 1.15.3 (the `keystone.version` in the root `pom.xml`). Nothing needs a new dependency.
`gangland-api` is at `2.6` (additive since 0.16.x) and no module's `Host_Api` changes in this release. Upgrading from
0.16.x: first apply [`migration-0.17.0.md`](./migration-0.17.0.md) and [`migration-0.19.0.md`](./migration-0.19.0.md), then
read the part below before you start the server on the new jar.

## Part W7 - Quartermaster that just works

### 1. Owners place their own Quartermaster

- New command `/glw turf quartermaster`. Standing inside a turf your gang owns, you place the gang's Quartermaster where you
  stand. If the turf already has one, the Quartermaster moves.
- Node: `gangland.command.turf.quartermaster` (default `true`, in `plugin.yml`).
- You need the gang rank that holds `gangland.turf.upgrade` (the top rank always may). Allies cannot place it.
- Placing costs `Powerup_Npc.Placement_Cost` from the gang bank (`turf/turf_npcs.yml`, default `5000.0`). Set it to `0` to
  make placement free.
- Citizens must be installed. Without it, the command refuses and charges nothing.
- The staff command `/glw turf powerupnpc set|remove` is unchanged and still free.

### 2. The Quartermaster hub shows who spends

- The hub now shows the guards on duty on the turf (`Quartermaster.Menu_On_Duty`).
- It also says who may spend: `Quartermaster.Menu_Spend_Owner` for the owning gang, `Quartermaster.Menu_Spend_Ally` for
  allies. Allies can still look, not buy.

### 3. Gang tag above the Quartermaster

- A short gang tag (1 to 4 characters, taken from the gang name) is drawn on a line above the Quartermaster. The line text is
  `Quartermaster.Nameplate_Tag`.
- The tag follows owner changes. After a gang rename, the new name shows after the next owner change or after a restart.

### 4. The refusal names the owner

- `Quartermaster.Only_Owner_Or_Allies` is retired. Nothing reads it any more.
- Its text is replaced by two keys:
  - `Quartermaster.Refuse_No_Gang`: the player is not in a gang.
  - `Quartermaster.Refuse_Stranger`: the player's gang is not allied with the owner.
- Both use `%npc%`, `%owner%` and `%owner_id%`.
- If you customised `Only_Owner_Or_Allies`, copy your text into the two new keys in `turf/turf_messages.yml`.

### 5. New keys

These keys are in the `Quartermaster:` section of `turf/turf_messages.yml`. The defaults are the shipped text. Leave a key
out and the default is used.

| Key | Default | Meaning |
|---|---|---|
| `Quartermaster.Place_Title` | `&8&l[&6&lPlace %npc%&8&l]` | Title of the placement menu. |
| `Quartermaster.Place_Name` | `&a&lPlace the %npc% here` | Button name when the turf has no Quartermaster yet. |
| `Quartermaster.Place_Move_Name` | `&a&lMove the %npc% here` | Button name when the turf already has one. |
| `Quartermaster.Place_Lore_Turf` | `&7Turf: &f%turf%` | Button lore: the turf. |
| `Quartermaster.Place_Lore_Cost` | `&7Cost: &6%money% &7from the gang bank` | Button lore: the price. |
| `Quartermaster.Place_Lore_Click` | `&aClick to place it where you stand.` | Button lore, last line. |
| `Quartermaster.Placed` | `&aThe %npc% now stands here on &f%turf%&a. Paid &6%money%&a.` | Chat after a placement. |
| `Quartermaster.Place_Not_Owner` | `&cOnly the gang that owns %turf% can place its %npc%.` | Chat when the player's gang does not own the turf. Allies get this too. |
| `Quartermaster.Place_No_Citizens` | `&cThe %npc% needs the Citizens plugin, which this server does not have.` | Chat when Citizens is missing. Nothing is charged. |
| `Quartermaster.Place_Outside` | `&cStand inside %turf% to place its %npc%.` | Chat when the player left the turf before clicking. |
| `Quartermaster.Place_Failed` | `&cThe %npc% could not be placed here. Your gang was not charged.` | Chat when the Quartermaster could not spawn (bad `Type_Id`, Citizens spawn cancelled). The charge is refunded. |
| `Quartermaster.Menu_On_Duty` | `&7Guards on duty: &f%count%` | Hub line: guards alive on the turf. |
| `Quartermaster.Menu_Spend_Owner` | `&aYour gang buys here.` | Hub line for the owning gang. |
| `Quartermaster.Menu_Spend_Ally` | `&7Only &e%gang% &7buys here; allies can look.` | Hub line for an allied viewer. |
| `Quartermaster.Refuse_No_Gang` | `&cThis %npc% serves &e%owner%&c. Join a gang, then ally with them: &f/glw gang ally request %owner_id%` | Refusal for a player with no gang. |
| `Quartermaster.Refuse_Stranger` | `&cThis %npc% serves &e%owner%&c. Ally with them to use it: &f/glw gang ally request %owner_id%` | Refusal for a gang that is not allied. |
| `Quartermaster.Nameplate_Tag` | `&8[&e%tag%&8]` | The gang tag line above the Quartermaster. |

The new config key is `Powerup_Npc.Placement_Cost` in `turf/turf_npcs.yml` (default `5000.0`, see item 1).

### For the changelog

- Gangs can now place or move their own Quartermaster with `/glw turf quartermaster`, inside a turf they own.
- The Quartermaster hub shows the guards on duty and which gang may spend there.
- The Quartermaster shows the owning gang's short tag above its name.
- A refused player is told which gang the Quartermaster serves, and how to ally with it.

## Part W7 (continued) - Guards you can steer and hear

### 1. Owner alerts

- Who hears: online members of the owning gang, by chat line and action bar. Allies and rivals get none of these.
- Four triggers: a cop hits a player the turf protects (`Alerts.Cop_Hit`), a guard falls (`Alerts.Guard_Down`), someone
  outside the owning gang and its allies hits the Quartermaster (`Alerts.Quartermaster_Hit`), the Quartermaster is killed
  (`Alerts.Quartermaster_Down`).
- Cooldown: `Owner_Alerts.Cooldown_Seconds` in `turf/turf_npcs.yml` (default `60`), per turf and per kind. `0` = every time.
- A guard's death or the Quartermaster's death is told once per NPC.
- Cop-hit alerts only happen while `Cop_Response.Enabled` is `true`.
- The guard order does not silence these alerts: owners hear of cop hits under Hold too.

### 2. Guard orders: Hold and Defend

- The Quartermaster hub has a **Guard Orders** button in slot 13. Each click switches the turf between Hold and Defend.
- Who may click: the owning gang's rank that holds `gangland.turf.upgrade` (the top rank always may). Allies can look, not switch.
- Defend is the default and is today's behaviour: guards chase attackers and turn on cops.
- Hold: guards keep within `Garrison.Hold_Radius` (`6.0`) of their post. They pick only attackers that close and walk back when
  they stray further. The Quartermaster and the guards answer no cop, and a cop a guard was engaged with is released.
- The order is saved to the database, so it survives a restart.
- The order resets to Defend whenever a turf changes hands (capture, admin, disband, inactivity): the garrison row is dropped with the old owner's stock (T-270). Deleting a turf drops it too.

### 3. Database

- Table `turf_garrison` gains the column `hold` (BOOLEAN, default `false`). It is added automatically at first boot; run no SQL.
- Existing rows read as Defend. A turf with no row is Defend too, and choosing Defend on such a turf writes nothing.

### 4. Garrison panel

- The Garrison panel shows the guards on duty, the reserve as `x/12` (stock plus guards on duty; the cap is
  `Garrison.Max_Reserve` in `turf/turf_rules.yml`, default `12`), and the next wave.
- The next wave comes from the reserve. It costs nothing extra; it was paid for when bought.
- The Buy button's lore says "Reserve full" once stock plus guards on duty reach the cap.

### 5. Staff placement

- `/glw turf powerupnpc set` now works only while the staff member stands inside the selected turf. Anywhere else it is
  refused with a chat message and nothing changes. (T-803.)

### 6. New keys

Defaults below are the shipped text. Existing files are not rewritten: a missing key uses the default, and to customise a
key copy its block into your file.

| Key | Default | Meaning |
|---|---|---|
| `turf_npcs.yml` `Owner_Alerts.Cooldown_Seconds` | `60` | Seconds before the same owner alert for the same turf is sent again. `0` = every time. |
| `turf_npcs.yml` `Garrison.Hold_Radius` | `6.0` | Blocks around its post a guard keeps to while the order is Hold. Minimum `3.0`. |
| `Alerts.Cop_Hit` | `&c%turf%: a cop is attacking &f%player%` | Owner alert: a cop hits a member on the turf. |
| `Alerts.Guard_Down` | `&c%turf%: one of your guards fell` | Owner alert: a guard is killed. |
| `Alerts.Quartermaster_Hit` | `&c%turf%: &f%attacker% &cis attacking your Quartermaster` | Owner alert: an outsider hits the Quartermaster. |
| `Alerts.Quartermaster_Down` | `&c%turf%: your Quartermaster was killed` | Owner alert: the Quartermaster is killed. |
| `Quartermaster.Menu_Order` | `&7Guard order: %order%` | Hub line showing the turf's order. |
| `Quartermaster.Order_Hold` | `&eHold` | The word Hold. |
| `Quartermaster.Order_Defend` | `&cDefend` | The word Defend. |
| `Quartermaster.Order_Name` | `&e&lGuard Orders` | Name of the hub button in slot 13. |
| `Quartermaster.Order_Lore_Hold` | `&7Hold: guards stay by their posts and leave cops alone.` | Button lore under Hold. |
| `Quartermaster.Order_Lore_Defend` | `&7Defend: guards chase attackers and turn on cops.` | Button lore under Defend. |
| `Quartermaster.Order_Click` | `&aClick to switch to %order%&a.` | Button lore, click line. |
| `Quartermaster.Order_Set` | `&aGuards on &f%turf%&a now: %order%` | Chat after a click. |
| `Quartermaster.Garrison_On_Duty` | `&7On duty: &f%count%` | Garrison panel: guards alive on the turf. |
| `Quartermaster.Garrison_Reserve` | `&7Reserve: &f%count%&7/&f%max% &8(stock and on duty)` | Garrison panel: reserve against the cap. |
| `Quartermaster.Garrison_Next_Wave` | `&7Next wave: up to &f%count% &7guards, no extra cost` | Garrison panel: the next wave. |
| `Quartermaster.Garrison_Buy_Full` | `&cReserve full: &f%max% &cguards.` | Buy lore at the cap. |

### For the changelog

- Owners are told by chat and action bar when a cop hits one of their members on their turf, a guard falls, an outsider
  hits the Quartermaster, or the Quartermaster is killed.
- Guard Orders on the Quartermaster hub: Hold keeps guards by their posts and away from cops; Defend is the default.
- The Garrison panel shows guards on duty, the reserve against the cap, and the next wave.
- `/glw turf powerupnpc set` now needs the staff member to stand inside the selected turf.

## Part T-808 - Players can use /glw gang ally

### 1. The ally commands are open to every player

- Seven nodes in `plugin.yml` now default to `true`: `gangland.command.gang.ally`, `.ally.request`, `.ally.accept`,
  `.ally.reject`, `.ally.pending`, `.ally.pending.cancel` and `.ally.abandon`.
- Before this, every `/glw gang ally` subcommand was effectively op-only. Non-op players now reach them.
- The gang rank still gates each command (`GangPermissions.ALLY`). A player without that rank is refused as before.
- `request`, `accept`, `reject` and `pending` need `gangland-mail` loaded. Without it they do nothing.

### 2. Restoring the op-only behaviour

- To keep the ally commands for operators only, negate the parent node in your permissions plugin or `permissions.yml`:
  `gangland.command.gang.ally: false`. Set the same value on the six child nodes if you want them closed too.

### For the changelog (Part T-808)

- Players who are not server operators can use `/glw gang ally` again.
