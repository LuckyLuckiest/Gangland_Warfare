# Planner brief (Opus agents)

You are planning one stream of the Bartizan wave. Read, in order: `README.md` (decisions D1–D10, topology, gates),
`architecture/PICK.md` (the chosen blueprint — binding), the three exploration reports in `exploration/`, `TEMPLATE.md`,
and the reference files those documents name. The previous sprint's checklists in `../module-split-2026-09-07/`
(`cops-n-crooks.md`, `weapon.md`) show the expected level of detail.

## Hard constraints on you

- **Read-only.** You write exactly one file: `brainstorming/bartizan-split-2026-09-08/<stream>.md` where `<stream>` is
  `keystone-1.9.0`, `bartizan` or `gangland-0.9.0`. Do not edit source, poms, YAML or docs. Do not run Maven. Do not
  create branches or commits.
- Every claim in the inventory carries a `path:line` source. Every task names exact files, symbols, target packages
  and repos. A Sonnet executor with no other context will follow your file literally, one task group at a time.
- Follow `TEMPLATE.md`'s structure, plus `## 0. Summary for the orchestrator` at the top: files moved / created /
  deleted per repo, the seams you introduce, the order dependencies on the other two streams (what must already
  exist in `~/.m2` or on disk before each group can start), task groups with rough sizes, the three biggest risks.
- Cross-stream contract: the three checklists must agree on package names, artifact coordinates, seam interfaces
  and descriptor keys. State every cross-stream name you rely on in a `## Contract` section (e.g.
  `org.luckyraven.keystone.item.ItemConverterRegistry`, `org.luckyraven.bartizan:bartizan-api`, `module.yml` key
  `Plugins:`), so the consistency review can diff them.

## Design rules

1. Direction: Gangland modules → `bartizan-api` → Keystone; Bartizan → Keystone; Keystone → nothing of ours.
   Bartizan never names a Gangland type; the Gangland core never names a Bartizan type. A module that needs Bartizan
   compiles against `bartizan-api` at `provided` scope and declares the plugin in `module.yml`.
2. Promotions to Keystone keep behaviour: a promoted class keeps its public API where possible, gets Keystone's package
   (`org.luckyraven.keystone.item.*`, `org.luckyraven.keystone.npc.*`), Keystone's logging prefix and docs (`docs/`
   phase file + module doc), and its tests move with it (`keystone-testkit` helpers where Bukkit is needed).
3. Seams (Keystone/Bartizan side) are generic and named after the concept, never after Gangland: `TargetPolicy`,
   `NpcAttackBehavior`, `ItemConverter`, … Two shapes only: contributions (many providers, `getAllInstances`) and
   holders (one bean with a safe default, consumer installs a delegate). A holder `@Bean` declares its concrete class.
4. Citizens is soft everywhere: `keystone-npc` compiles against the Citizens API at `provided` scope, exposes
   availability, and every consumer gates on it with a readable fault.
5. YAML defaults ship in the jar at exactly the data-folder path (`weapon/rifle.yml` in Bartizan's own data folder;
   `npc/trader_traits.yml` in the npc-shops module jar). Keys `Capitalized_Underscore_Separated`, block style.
6. Bean parameters that only encode load order stay when a method moves.
7. Commands: Bartizan owns `/bartizan` (alias `/btz`) through Keystone's `CommandManager`; Gangland modules that
   lose sub-arguments cut the matching `commands.json` keys and state the new `InformationManagerTest` count.
8. Tests: red first, always; say how. Never add test dependencies to a module pom.
9. Anything that must be decided by the user goes in section 6 with the default you chose.

## Deliverable quality bar

An executor must be able to run your tasks top to bottom with a compile gate after each group and reach the
stream's gate in `README.md` without asking a question.
