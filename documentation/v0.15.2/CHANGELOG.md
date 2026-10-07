# 🚔 Smart Star Drops — v0.15.2 Changelog

> *The cops learn how chases go. Stop running, and they might let you go with a warning. Go hard and get away every time, and they'll mark you as trouble. Welcome to **Cops N Crooks 0.15.2**.*

---

[← Back to Documentation Index](../README.md)

---

## Overview

Cops N Crooks 0.15.2 introduces **AUTO mode** for star drops: instead of always losing one star or all of them, the cops judge how the chase went. A small getaway, a long quiet spell, or a clean break out of the search zone drop different numbers of stars. The server learns which players keep getting away and how long chases typically last, then tunes the timers and marks repeat offenders so they play a harder game. **Momentum** makes each successive drop faster while you stay hidden.

---

## ✨ New

- **AUTO star-drop judgement** — With `Drop_Mode: AUTO`, the cops read the chase and decide: a rampage locks you to one star at a time; a small chase (1-2 crimes, up to 2 stars) drops them all at once; a long chase with 90+ seconds of quiet drops all stars; leaving the search zone and running outside drops half your stars (rounded up); anything else drops one star and the timer gets faster (momentum). Five named endings — Still Hot, Petty, Cold Trail, Clean Break, Hunker Down — each show a card so you know why.
- **Learning and warm-up** — The server learns two things: how long chases at each star level typically last (contact time from chase start to the last loss of sight), and each player's escape habit (how often they get away compared to what the server expects). Both survive restarts and feed back into the AUTO decision. Cold starts use guessed numbers; warm servers tune based on play. A player who always escapes gets no small-fry or clean-break lumps.
- **Momentum and narrow escapes** — After each star drops, the next timer is shorter (75% by default, or 50% after a narrow escape where the cop kept you in sight for 20+ seconds). A rampage or logout lock freezes the momentum so you face a steady timer while the heat cools. The timer never shrinks below 40% of the base `Seconds_To_Drop`.
- **HUD for AUTO** — New `Evaded_Many` bar line when 2+ stars drop at once, and six ending cards name the cause: Small fry, The trail went stone cold, Clean break, Still hot, They know your face, That was close.
- **Database and persistence** — Two tables track habits (one row per player who finished a chase) and level stats (one per peak star level), capped at 100 samples each. Forgotten after 90 days of inactivity. Autosave and restart-safe.

---

## 🔧 Changed

- **`Drop_Mode` expands** — `ONE_STAR` (the default, unchanged), `ALL_STARS` (unchanged), `AUTO` (new).
- **Evasion learning runs only in AUTO mode** — Keeps the learning tables clean on servers that don't use AUTO; can be toggled per server with `Learning.Enable`.
- **Upgrade path from 0.15.1** — Your old `wanted.yml` stays, but without the new `Auto` block it reads AUTO defaults. Copy the block from `plugins/Gangland_Warfare/modules/cops-n-crooks-0.15.2.jar` (or any copy/paste) into your `Auto:` section to tune, or stay on `ONE_STAR` / `ALL_STARS`.

---

## 🐛 Fixed / Known

- **D9 (open)** — Progress carried over a mid-search star raise stays until 0.16. The bar may flicker if the new level needs more progress than the old one gave.
- **One new docket row** — A P3 finding on AUTO defaults discovered in section 3.6 (E-series worked examples).

---

## ⚙️ Configuration

- **New `copsncrooks/wanted.yml` block** — `Wanted.Evasion.Auto` with 29 tunable keys (Opening_Seconds, Rampage_Crimes, Petty, Cold_Trail, Clean_Break, Momentum, Repeat_Chases, Learning). Shipped inside the cops-n-crooks module jar; falls back to defaults if missing.
- **New message keys** in `copsncrooks/wanted_messages.yml` — `Hud.Bar.Evaded_Many`, `Hud.Card.Drop_Petty`, `Drop_Cold_Trail`, `Drop_Clean_Break`, `Drop_Still_Hot`, `Drop_Known_Face`, `Drop_Narrow`.

---

## 🧩 API 2.2

- **`GanglandApi.VERSION` stays 2.2** — No API change; `gangland-api` is unchanged. Modules built for 2.2 load as-is.
- **No new events** — The HUD reads the pending `DropPlan` from the `ChaseArcs` bean. Upgrade path: a future 0.16 may add `WantedEvasionDropEvent` in API 2.3 when criminal record or XP reads the ending.

---

## ⬆️ Upgrade notes

- **Read the wanted-bounty guide** — See [Wanted & Bounty](../features/wanted-bounty.md) for the full AUTO rules and examples.
- **No restore migration** — No database migration needed; the two new tables start empty (cold server).
- **YAML format** — `Auto` block is block style with Capitalized_Underscore keys, like the rest of `wanted.yml`. One admin copy/paste; 29 keys, all commented.
