# Migrating a server to Gangland 0.21.0 (Gang & Turf III; number confirmed at landing)

[← Back to Documentation Index](./README.md)

Gangland 0.21.0 needs Keystone 1.15.1 (the `keystone.version` in the root `pom.xml`). Nothing needs a new dependency.
`gangland-api` stays at `2.3` and no module's `Host_Api` changes. Read the part below before you start the server on the
new jar.

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
