# 0.16.1 Changelog

[← Back to Documentation Index](../README.md)

---

## ✨ New

- **Post-escape search and bounty bar** — An escape by evasion no longer ends the hunt. The cops keep searching for `Wanted.Post_Escape.Search_Seconds` (default 120 s), a bounty goes on you once and a chat line says so, and a bounty bar counts the search down. Death, arrest and quitting end it at once. A sighting during the search raises you by `Spotted_Stars` (default 1), which resumes the normal chase.

## 🔧 Changed

- **Wanted titles are quieter and configurable** — The star title no longer fills the screen on every change. Titles are set per event in `wanted.yml` under `Hud.Title.Gain`, `Hud.Title.Lost` and `Hud.Title.Escaped` (each with its own `Title`, `Subtitle` and fades). `wanted_messages.yml` `Hud.Title` is no longer read. To restore the old look, set `Title: "&c%stars%"`, `Subtitle: "%card%"`, `Stay: 40` and `Fade_Out: 10` on each event.
- **Escape bounty is priced on the chase's peak** — The notoriety added by an escape follows the highest star count of that chase, not the star it dropped from. Nothing is added while notoriety already sits at `Bounty.Kill.Maximum`.

## 🐛 Fixed

- A bounty-less escape left the red chase bar on screen until death or the next wanted episode.
- A reload of the cops left a searched player with the bounty bar and no cops.
- A cop kill that ended the squad mid-tick let the rest of that tick run on the destroyed group.

---

[← Back to Documentation Index](../README.md)
