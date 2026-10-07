# Planner brief (Opus agents)

You are planning one flip of the module split sprint. Read, in order: `README.md` (this folder), `TEMPLATE.md`
(this folder), `documentation/module-loader.md`, the mail module under `gangland-features/gangland-mail/` (the
reference recipe), `bootstrap/GanglandContext.java`, `config/DatabaseConfig.java`, `config/KernelConfig.java`, and
Keystone's `docs/keystone-module.md` + `keystone-module/src/main/java/**` at `E:\Programming\java\Keystone`.

## Hard constraints on you

- **Read-only.** You write exactly one file: `brainstorming/module-split-2026-09-07/<feature>.md`. Do not edit
  source, poms, YAML, docs, or any other file. Do not run Maven. Do not create branches or commits. Do not touch
  `.claude/worktrees/**`.
- Run `graphify query` / `graphify explain` / `graphify affected` first; read raw files only after the graph has
  oriented you (graph refreshed 2026-09-07 16:30). Use identifiers as they appear in code as query terms.
- Every claim in the inventory carries a `path:line` source. Every task names exact files, symbols and target
  packages. A Sonnet executor with no other context will follow your file literally.
- Follow `TEMPLATE.md`'s structure exactly, plus a `## 0. Summary for the scrum master` at the top: number of impl
  files moved / split / kept, the seams you introduce, the p0-wave-3 overlap, task groups with rough sizes, and the
  three biggest risks.

## Design rules that decide MOVE vs SEAM

1. A core file that exists only to serve the feature (contract implementation, repository, table, feature
   command, feature-only listener, feature-only config bean) **moves** into the module under
   `org.luckyraven.gangland.<pkg>.<role>` (`.database`, `.listener.<sub>`, `.command`, `.config` or the module root
   for `<Feature>ModuleConfig`).
2. A core file that mixes core and feature concerns is **split**: the feature slice moves, the core keeps a
   feature-free remainder.
3. When the core must *call into* the feature at a moment the module cannot push (startup metrics, a registry that
   is consumed before modules could register, a placeholder resolver, a sign-type catalogue), prefer, in this order:
   (a) the module's own `@Bean` method injects the existing core registry bean and registers into it (no new
   interface); (b) a small core seam interface + module bean pulled through `container.getAllInstances(Seam.class)`
   (`command/extension/CommandContribution` is the model). Name seams after the core concept and put them beside the
   core class that consumes them. State the phase in which the consumer reads the seam and prove the module bean
   exists by then (module `@Configuration`s are folded into the same phased pipeline; see `GanglandContext`).
4. Bean parameters that only encode load order must be preserved when a method moves (see memory rule
   "bean ordering via params").
5. `Settings`, `Messages`, managers and every other core type may be imported by the module. Only the reverse is
   forbidden. If `Settings` or `Messages` *reference a feature type*, that reference must be removed; plain feature
   config keys/sections stay in core.
6. YAML defaults move to the module jar at exactly the data-folder path (`weapon/rifle.yml`, `npc/cops.yml`,
   `items/cars.yml`, `turf/turf.yml`) — the `FileHandler` 5-arg constructor reads `<directory>/<name><type>` from the
   loader, and the loader is parent-first so the same path must disappear from the core jar. Module-side `FileHandler`
   construction needs the module classloader: obtain the `ModuleLoader` bean (registered by `GanglandContext`) or
   the `ModuleContext`; state which and where.
7. Sub-arguments under a core `/glw` command (e.g. under `gang`, `debug`, `item`) go through `CommandContribution`;
   top-level commands are scanned from the module's command package. Help entries move from core `commands.json`
   to the module's `commands.json` (list the exact keys) and `InformationManagerTest`'s count changes by exactly
   that number (state the new number).
8. Tests: impl tests that reference the feature move into the module's `src/test`. Module poms never gain test
   dependencies (inherited). Add `<Feature>ModuleTest` modelled on `MailModuleTest`. New tests must be shown red
   first; say how.
9. Feature-to-feature Maven dependencies that are still in the core when this flip runs become `provided` in the
   module pom (they resolve from the core jar at runtime). Feature-to-feature dependencies on modules that already
   flipped become `provided` too, plus a `module.yml` `Depends:` entry; the later flip's checklist owns the edit to
   the earlier module's pom and `module.yml`.
10. Anything `p0-wave-3` is editing (list in README) that your plan moves must be flagged in the task's
    **Watch out** so the scrum master merges `0.8.3` before the executor starts.

## Shared seams (so parallel planners converge on one design)

- `MetricsContributor` — owned by the **weapon** plan (replaces `Gangland.bStats()`'s `WeaponAddon` reference).
  Other plans may reuse it if they add charts.
- Item refreshers — `ItemConfig.itemRefresherRegistry` hard-lists `WeaponRefresher`, `AmmunitionItemRefresher`
  (weapon) and `CarItemRefresher`, `WearableRefresher` (gadget). The **gadget** plan (flip 2) designs the
  registration mechanism (rule 3a preferred); the **weapon** plan reuses it. Both plans must describe the same
  mechanism; if you cannot see the other plan, describe yours fully and mark it "converge".
- Sign types (`sign/type`, `sign/validation`, `sign/aspect`) reference gadget and weapon. Same rule: gadget designs,
  weapon reuses.
- `TurfNpcsConfig` straddles cops and turf. The **cops** plan (flip 1) moves the cops half and leaves a turf-only
  remainder in core; the **turf** plan (flip 3) moves the remainder and adds `Depends: [turf]` to cops.
- Placeholders (`data/placeholder`) and economy (`data/economy`) referencing cops: cops plan decides move vs seam.

## Deliverable quality bar

An executor must be able to run your tasks top to bottom with a compile gate after each group and reach gates
G1–G4 in `README.md` without asking a question. If a decision genuinely needs the user, put it in section 6 with
the default you chose.
