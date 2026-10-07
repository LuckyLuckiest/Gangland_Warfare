# <Feature> flip checklist — template

Fill every section. Executors are Sonnet agents that will follow this file literally, one task group at a time,
without the context you have: name exact files, exact symbols, exact target packages, and a verifiable "done when"
for every task. No "etc.", no "and similar". If something is uncertain, say so in **Open questions** and pick a
default the executor should use.

---

# Flip <n>: <feature> → runtime module `<id>`

**Assumes done:** flips <…> (state what is already a module when this runs).
**Module:** `gangland-features/<dir>` · id `<id>` · package root `org.luckyraven.gangland.<pkg>` · jar
`target/modules/<artifact>-0.8.4.jar` · `Depends:` now `[…]`, later `[…]` (which later flip adds it).
**Planner:** opus, <date>. **Graph:** refreshed <date>.

## 1. Inventory (facts, with source locations)

### 1.1 Impl files that reference the feature
Table, one row per file: `path` · role (config / contract impl / repository / table / listener / command /
sign / item / placeholder / other) · what it uses from the feature · decision (**MOVE** to `<target package>` /
**SPLIT** how / **SEAM** which / **KEEP** why) · touched by the `p0-wave-3` worktree? (y/n).

### 1.2 Feature-side files that reference impl (should be none today; list any)

### 1.3 Beans the feature contributes today
Every `@Bean` method in the mixed core configs that produces or consumes a feature type: config class · method ·
phase · parameters · target config class in the module.

### 1.4 Resources
YAML defaults (`gangland-impl/src/main/resources/<dir>/…`), `commands.json` keys (exact key list), messages keys
that stay in core, `module.properties`.

### 1.5 Tests
Impl tests that reference the feature; feature tests that exist; tests that assert counts.

### 1.6 Seams needed (core interfaces the module implements)
Name · package in core · methods · who registers the bean · who consumes it (`getAllInstances`) · why a seam and not
a move.

## 2. Ordered tasks

Group tasks so one executor can finish a group in one sitting (roughly ≤ 25 files per task). Every task has:

### T<n> — <title>  (group <letter>, ~<files> files)
- **Do:** exact steps (create X in Y; move A to B; change signature of C; delete D).
- **Why:** one line.
- **Done when:** a command or assertion the executor can run (`mvn -q -pl … -am install -DskipTests`, a grep,
  a test name).
- **Watch out:** pitfalls (bean ordering params, parent-first resources, static Settings, p0-wave-3 overlap).

Mandatory task order: poms (M1) first so the compiler produces the work list; module entry (M2); then the moves;
seams; YAML; tests; docs; verification gates last. Insert a **compile gate** after every group.

## 3. Tests
- Tests to move (from → to).
- Tests to change (name, what changes, e.g. `InformationManagerTest` 225 → n with the exact n).
- New tests (name, what they assert, how to show them red first).

## 4. Docs and config
Exact edits to `CLAUDE.md`, `documentation/module-loader.md`, `documentation/README.md`, the module's
`commands.json`, `module.yml`, `gangland-build/pom.xml`, memory.

## 5. Verification (gates G1–G4 instantiated)
The exact commands with expected output for this feature.

## 6. Risks and open questions for the scrum master
Anything that needs a human decision, a bug found on the way (docket id or `triage/<slug>.txt`), or a place where
the recipe does not fit this feature.

## 7. Status table (executors fill this)

| Task | Status | Executor | Notes (what changed, what was skipped, failures verbatim) |
|---|---|---|---|
| T1 | todo | | |
