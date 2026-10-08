# Migrating a server to Gangland 0.14.0 (gang and turf line)

[← Back to Documentation Index](./README.md)

Gangland 0.14.0 is the gang and turf line: turf ids, captures, alliances, gang transfer and invites are made trustworthy.
Nothing needs a new dependency and `gangland-api` stays at `2.3` with no `Host_Api` change. The new files are created on first
boot. Read the notes below before you start the server on the new jar.

## W0 - trustworthy foundations

### 1. Turf ids and the new `turf_sequence` table

- New table `turf_sequence` (one row, `id = 1`), created automatically on first boot.
- Turf ids are never reused. On the first boot after the upgrade the server reserves `max(existing turf ids)`, so every turf
  that exists today keeps its id.
- The one window left: a turf deleted before the upgrade whose id was never stored can be handed out once more on the first
  boot after the upgrade. From then on the id stays reserved, including across restarts.

### 2. Gang disband, admin turf commands and captures

- Disbanding a gang releases every turf it owned and cancels any capture it was running.
- `/glw turf setowner` and `/glw turf delete` (admin) cancel a running capture, so boss bars and guards are cleaned up.
- A capture whose gang was disbanded part-way through is cancelled.
- `TurfOwnerChangedEvent` now also fires on captures (before `TurfCapturedEvent`), carries `getCause()`
  (`CAPTURE`, `ADMIN`, `INACTIVITY`, `ORPHANED`, `GANG_DISBANDED`) and fires only when the owner actually changes.
- `TurfCaptureFailedEvent.Reason.CANCELLED` is now fired.

### 3. Inactivity auto-release

- The inactivity auto-release first runs 5 minutes after boot, then every 24 hours.
- Server downtime counts as gang inactivity. A server that was down longer than `Capture.Inactivity_Auto_Release_Days`
  (`turf/turf_settings.yml`, default 10) releases those turfs 5 minutes after it comes back.

### 4. Turf NPC config reloads

- `turf/turf_npcs.yml` (Quartermaster type and radius, guard type, radius and lifespan, the `Cop_Response` block) now reloads
  with `/glw reload`. No restart needed.

### 5. Alliances are symmetric

- An alliance stored on only one side now counts for both gangs. Friendly fire is blocked both ways, and turf, guard and
  Quartermaster logic treat the two as allies.
- `Gang.removeAlly(gang)` drops only that gang's side. The in-game commands already drop both sides through `GangManager`, so
  nothing changes for players. Addon code that calls `removeAlly` directly must call it on both gangs.
- `new Gang()` (no-arg) is removed. Use `new Gang(int id)`.

### 6. Gang transfer confirmation

- `/glw gang transfer` and `/glw gang leave` confirmations lapse on their own. The effective window is **30 seconds**: the
  confirm lock is Keystone's fixed `ConfirmArgument` expiry, which has no public setting. A 60-second target needs an upstream
  Keystone change (tracked as a new docket row, see the wave log).

### 7. Gang invites, decline and offline expiry

- New verb `/glw gang decline [gang]`. Accepting one gang's invite cancels the player's other gang invites.
- Invites to online players expire after 60 seconds.
- Invites to offline players expire after `Invite_Offline_Expiry_Hours` (default `72`) in the new `mail/mail_rules.yml`.
  Set it to `0` for the old behaviour (the invite waits until the player next logs in).
- Invites to offline players already stored with `expiresAt = 0` keep never expiring. There is no backfill.
- New files on first boot: `plugins/Gangland_Warfare/mail/mail_rules.yml`, `mail/mail_messages.yml`, `mail/mail_messages_es.yml`.
  They ship in the mail module jar and are copied to the data folder, not to `modules/`.
- Help text: `/glw gang rename <name>` replaces `/glw gang name <name>` (the verb has always been `rename`).

### 8. Ranks

- A rank has exactly the permissions listed on it. Parent ranks order promotion and demotion only; they do not pass
  permissions down. See [ranks](./features/ranks.md).

### 9. Addon authors

- Turf module internals: `TurfDefenderConfig` and `CopGuardConfig` are no longer beans. Read them through
  `TurfNpcsConfigLoader` (`getDefenderConfig` / `getCopGuardConfig`). `TurfPowerupManager` and `TurfCopGuard` take suppliers.
- `ActiveBuffRepositoryContract` gained `deleteExpired(long)`. Any other implementation must add it.
- `CaptureService`, `TurfIncomeDistributor`, `InactivityReleaseTask`, `TurfSetOwnerCommand`, `TurfDeleteCommand` and
  `TurfCommand` gained a `TurfOwnership` parameter. `TurfOwnerChangedEvent` has a 4-argument constructor.
- No `gangland-api` change (still `2.3`), no `Host_Api` change, no config change beyond the keys above.

### 10. By design

- `/glw turf buff <powerup>` is an admin tool and activates a buff for free.

## W1 - to be filled

## W2 - to be filled

## W3 - to be filled

## W4 - to be filled

## W5 - to be filled

## W6 - to be filled

---

[← Back to Documentation Index](./README.md)
