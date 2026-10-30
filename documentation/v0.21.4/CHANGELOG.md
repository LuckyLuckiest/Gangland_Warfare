# 0.21.4 Changelog - Bounty pay-off

[← Back to Documentation Index](../README.md)

Read [migration-0.21.4.md](../migration-0.21.4.md) before you start the server. Upgrade from 0.21.3. There is no hand
edit: the message files regenerate on first boot, so re-apply any custom wording from the `-old` file.

## Requires

- **Keystone 1.15.5** (unchanged).
- **gangland-api 2.7 and module `Host_Api` 2.7** (unchanged).

## Added

- **`/glw bounty pay`.** Pay off your own bounty in full. The whole total, posted money plus notoriety, is taken from your
  wallet and goes to the city: it is deposited nowhere and nobody is refunded. The bounty is cleared. All or nothing:
  with no bounty ("You don't have a bounty to pay off.") or too little money ("You need $X to pay off your bounty.")
  nothing is taken.
- **Posters are told.** Every poster who is online hears that the target paid off and that their own stake went to the
  city. Offline posters are not told.
- **The `[BOUNTY]` sign pays off with `pay`.** `view` still opens the bounty view; `clear` still works on old signs and
  does the same as `pay`.
- Help entry `bounty_pay` (`/glw bounty pay`).

## Changed

- **Permissions.** `gangland.command.bounty.pay` is open to everyone, and so is the `gangland.command.bounty` root, which
  means bare `/glw bounty` (view your own bounty) is open too. `set` and `clear` stay op.
- The sign `clear` success line is now the bounty-paid line.
- A bounty sign with no bounty or too little money shows the configured refusal, never raw "Amount exceeding balance" or
  "Might be missing something!".

## Fixed

- A `[BOUNTY]` sign with unknown or empty line 2 no longer throws; it answers "Invalid sign!" (WB-21, LS-22).
- `message_es.yml` declared `Errors.Bounty` twice, which dropped `Below_Minimum` in Spanish. The blocks are merged (T-836).
- Paying off no longer touches a user whose data is still loading (it refuses instead of taking money for a reset that
  would be overwritten).

## Decisions

- The money goes to the city by the owner's ruling (docket WB-22, by design).

## For developers

- gangland-api stays 2.7. Additive: four `Messages` constants and `Bounty.stakes()`. See
  [gangland-api.md](../gangland-api.md).
