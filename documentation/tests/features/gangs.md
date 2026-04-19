# Gangs — Test Checklist

[Back to Test Index](../README.md) | [Feature Doc](../../features/gangs.md)

---

## Overview

Gang creation, membership, ranks within the gang, alliances, gang chat, gang bank. Managed by `GangManager` and
`MemberManager` (both in `gangland-impl`).

**Modules involved:** `gangland-impl`, `plugin-persistence`.

---

## Pre-Conditions

- [ ] Two online players: `A` (will create gang), `B` (will join).
- [ ] `A` has enough balance to pay the gang-creation fee (see `settings.yml`).

---

## Smoke Test

- [ ] `A`: `/glw gang create Test` → gang is created; `A` is founder.
- [ ] `A`: `/glw gang invite B` → `B` receives the invite notification.
- [ ] `B`: `/glw gang accept` → `B` is a member at the lowest rank.
- [ ] `A`: `/glw gang promote B` → `B`'s rank shifts up one tier.
- [ ] `A`: `/glw gang balance` → shows starting balance (0 unless configured).
- [ ] `A`: `/glw gang deposit 100` → gang balance updates; `A`'s wallet debited.
- [ ] `A`: `/glw gang display Testers` → chat display name updates.
- [ ] `A`: `/glw gang color` → colour picker inventory opens; selecting a colour applies it.
- [ ] `A`: `/glw gang desc` → description editor opens.

---

## Edge Cases

- [ ] Duplicate gang name: `/glw gang create Test` by another founder → rejected with clear message.
- [ ] Insufficient funds on create → rejected, no partial state.
- [ ] Invite offline player → graceful error.
- [ ] `B` tries to invite while not having invite permission → denied.
- [ ] `A` leaves while founder → founder succession rules apply (disband or promote highest rank).
- [ ] `A` kicks themselves via `/glw gang kick A` → rejected or treated as leave.

---

## Alliances

- [ ] Founder of gang `X`: `/glw gang ally request <gangId-of-Y>` → request notification to `Y`'s leadership.
- [ ] `Y` accepts (per ally-accept flow) → alliance active.
- [ ] Ally chat / ally indicators show on both sides.
- [ ] `/glw gang ally abandon <id>` → alliance removed on both sides.

---

## Reload Safety

- [ ] During an active gang session (members online, gang chat open), run `/glw reload`.
- [ ] Gang membership, ranks, bank balance survive.
- [ ] Chat channel still routes gang messages to members only.

---

## Persistence

- [ ] Gang, members, ranks, bank balance, alliances persist across server restart.
- [ ] Offline member still shows in `/glw gang` info and can be kicked by online officers.
- [ ] Verified on both SQLite and MySQL.

---

## Regression Risks

- `GangManager`, `MemberManager`, `RankManager` — any refactor here.
- Economy contract (`GanglandMoneyDepositService`) — gang bank deposit/withdraw path.
- Chat routing — gang-only messages, alliance messages.

---

[Back to Test Index](../README.md)
