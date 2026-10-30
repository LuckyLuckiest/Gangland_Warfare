# Migrating a server from 0.21.3 to Gangland 0.21.4

[← Back to Documentation Index](./README.md)

0.21.4 adds `/glw bounty pay` and the `pay` bounty sign: a target can buy his own bounty off, RDR2-style, and the money
goes to the city. There is no hand edit. The new message keys arrive with the regenerated message files (section 2).

## 1. Swap the jars

- Replace the Gangland core jar with the 0.21.4 build. The module jars need no change; swapping them with the core jar is
  harmless.
- Keystone stays at 1.15.5. `Host_Api` stays 2.7.

## 2. Message files regenerate on first boot

`Config_Version` in `message/message_en.yml` and `message/message_es.yml` follows the plugin version, so on the first
0.21.4 boot Keystone moves your old file to `message/message_<lang>-old.yml` and writes the shipped one. The four new
keys and the Spanish fix below arrive by themselves. **Re-apply your customised wording from the `-old` file**, and do not
paste the old file back over the new one: that would drop these keys.

New keys (both languages):

| Key | Meaning |
|---|---|
| `Commands.Bounty.Paid` | Sent to the payer after a pay-off. `%amount%` is the total. |
| `Commands.Bounty.Paid_Notice` | Sent to each online poster. `%player%` is the payer, `%amount%` is that poster's own stake. |
| `Errors.Bounty.No_Bounty_Self` | Pay-off with no bounty. |
| `Errors.Bounty.Pay_No_Money` | Pay-off with too little money. `%amount%` is the total needed. |

Spanish fix: `message_es.yml` declared `Errors.Bounty` twice, so YAML dropped `Below_Minimum`. The two blocks are merged
now. If you copy Spanish text back from the `-old` file, copy it into the single `Errors.Bounty` block.

## 3. Behaviour that changed

- **`/glw bounty pay`** takes the whole bounty (posted money plus notoriety) from your wallet in one go. It refuses, and
  takes nothing, when you have no bounty or too little money. The money is deposited nowhere: it goes to the city, and
  the posters are not refunded. This is the owner's ruling on docket WB-22. Online posters are told; offline posters are
  not.
- **The `[BOUNTY]` sign** accepts `pay` on line 2 (new). `view` is unchanged. `clear` is kept so signs you already placed
  keep working, and does the same as `pay`.
- The sign `clear` success text changes: it used to show the generic "taken from your account" line and now shows the
  bounty-paid line. A bounty sign with no bounty or too little money now shows the configured refusal instead of
  "Might be missing something!" or raw "Amount exceeding balance".
- A bounty sign with unknown or empty line 2 now answers "Invalid sign!" and no longer throws in the console (WB-21,
  LS-22).
- The `/glw bounty pay` help entry (`bounty_pay`) is in `commands.json`.

## 4. Permissions

`plugin.yml` now declares `gangland.command.bounty` and `gangland.command.bounty.pay` with `default: true`, and both are
children of `gangland.command.player`. The root used to be op-only because it was not declared.

- Players can use `/glw bounty pay`.
- **Side effect:** bare `/glw bounty` (view your own bounty) is now open to players too.
- `set` and `clear` stay op. Denying `gangland.command.bounty.pay` in your permission plugin blocks the command only.
  Using a bounty sign has no permission node of its own, as before, so a placed `[BOUNTY]` sign with `pay` (or a legacy
  `clear`) still pays off for that group. To turn pay-off fully off for a group, also remove or avoid those signs.
