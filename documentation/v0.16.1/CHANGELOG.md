# 0.16.1 Changelog

[← Back to Documentation Index](../README.md)

---

## 📦 Requires

- **Keystone 1.15.1 is required** — Replace `Keystone-1.15.0.jar` with `Keystone-1.15.1.jar` in `plugins/`. On an older Keystone Gangland refuses to start and the console says `Gangland 0.16.1 needs Keystone 1.15.1 or newer; found 1.15.0`. Tab completion (below) needs this Keystone too.

## ✨ New

- **Post-escape search and bounty bar** — An escape by evasion no longer ends the hunt. The cops keep searching for `Wanted.Post_Escape.Search_Seconds` (default 120 s), a bounty goes on you once and a chat line says so, and a bounty bar counts the search down. Death, arrest and quitting end it at once. A sighting during the search raises you by `Spotted_Stars` (default 1), which resumes the normal chase.
- **Cop, jail and cuff replies match the core style** — `/glw cop`, `/glw jail` and `/glw cuff` reply with the `GLW >>` prefix, coloured usage, list headers with counts and clickable rows. The text lives in `copsncrooks/commands.yml` (English) and `commands_es.yml` (Spanish), and the setup wand's lines in `copsncrooks/setup.yml`. Every `/glw cop setup` subcommand has a help entry.
- **Turf defenders answer cops who hit your gang** — On a turf its owner's gang protects, the garrison defenders and the Quartermaster fight a cop that hits a member of the owning gang (or of an allied gang). Their stray shots never hurt protected players. On by default. The knobs are in `turf/turf_npcs.yml`:

  ```yaml
  Cop_Response:
     Enabled: true
     Targeting_Radius: 32.0
     Include_Allies: true
  Powerup_Npc:
     Targeting_Radius: 32.0
  ```

  `Powerup_Npc.Targeting_Radius` is the Quartermaster's range and was hard-coded before. An existing server keeps its old `turf_npcs.yml`, so add the `Cop_Response` block by hand to change the defaults. Without it the code defaults apply (the response is on).

## 🔧 Changed

- **Wanted titles are quieter and configurable** — The star title no longer fills the screen on every change. Titles are set per event in `wanted.yml` under `Hud.Title.Gain`, `Hud.Title.Lost` and `Hud.Title.Escaped` (each with its own `Title`, `Subtitle` and fades). `wanted_messages.yml` `Hud.Title` is no longer read. To restore the old look, set `Title: "&c%stars%"`, `Subtitle: "%card%"`, `Stay: 40` and `Fade_Out: 10` on each event.
- **Escape bounty is priced on the chase's peak** — The notoriety added by an escape follows the highest star count of that chase, not the star it dropped from. Nothing is added while notoriety already sits at `Bounty.Kill.Maximum`.
- **Radio names keep their colours** — A cop's name on the radio keeps its colours, and the line's colour resumes after the name. The format is `Speaker_Name` in `copsncrooks/cop_radio_messages.yml` (and `cop_radio_messages_es.yml`), with `{rank}`, `{role}`, `{name}` and `{number}`. `{rank} {name}` gives `Officer Bob`.
- **Tab completion lists primary command names** — Completion suggests the primary name of each command. An alias is suggested only when nothing else matches. Aliases still run, and `/gangland` completes like `/glw`. Needs Keystone 1.15.1.

## 🐛 Fixed

- A bounty-less escape left the red chase bar on screen until death or the next wanted episode.
- A reload of the cops left a searched player with the bounty bar and no cops.
- A cop kill that ended the squad mid-tick let the rest of that tick run on the destroyed group.
- A server whose `wanted_messages.yml` predates `Crimes.Spotted` shows the Spotted star card as "Spotted by the cops", not the key turned into words.

---

[← Back to Documentation Index](../README.md)
