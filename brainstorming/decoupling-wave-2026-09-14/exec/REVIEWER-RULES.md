# Gate reviewer rules (decoupling wave execution)

You review one gate batch. You have no shell: read the three files the dispatch names — the lead's BRIEF, the
gate report, and the review package (a unified diff of the whole batch, untracked files included) — then open any
worktree file you need to verify a claim. Do not re-run tests; the report carries the evidence. Do not spawn agents.

## Lens, in this order
1. **Spec compliance** — one row per step of the plan's gate table for the gates in this batch: ✅ done as
   specified / ❌ missing or different (say what) / ⚠️ cannot verify from the diff (say where it would live).
   Binding text: the plan file(s) the brief names (for WS7: `WS7-gadget-ownership.md`, §0c overrides earlier text;
   the K1–K9 contracts in `gadget-wave-2026-09-16/README.md`) plus the user decisions and orchestrator rulings
   listed in the brief. A deviation the report declares still counts as ❌ unless the brief authorises it.
2. **Red-first evidence** — every new or flipped test must show a red run before the green one; missing evidence
   is Important.
3. **Correctness** — NPE paths, nullable `getWorld()`, async Bukkit calls, listener/bean double registration,
   soft-dependency linkage (a class whose method *signature* or *field type* names a Bartizan/Citizens/Oriel type
   while it can be loaded without that plugin; a `@Bean` returning such a type; `getDeclaredMethods` scans), YAML
   keys the loader never reads or reads under another name, `commands.json` missing an entry, missing
   `ItemRefresher`, Keystone artifacts shaded, Paper APIs, Java 21 APIs, API above Spigot 1.16.5.
4. **House style** — braces on their own lines, `@CustomLog`, XSeries/SoundEffect, YAML block style and
   `Capitalized_Underscore` keys, `&` colour codes, no unrequested abstraction (one-implementation interface,
   registry for one entry, config for a constant), `ponytail:` comments where a ceiling was chosen.
5. **Docket** — every id the plan's §11 names for these gates appears in the report with a status.

## Output (write it to the path the dispatch gives you, then return only the verdict line)
```
# Review — <stream> <gates> — <date>
Verdict: APPROVED | FIX (n findings) | REWORK
## Spec table            (step → ✅/❌/⚠️ + one line)
## Findings              (severity Critical | Important | Minor; file:line; what is wrong; the smallest fix)
## Cannot verify         (⚠️ items and where to look)
## Notes for the orchestrator (docket candidates, plan defects, cross-gate consequences)
```
Critical = wrong behaviour, data loss, boot abort, a contract (K1–K9, R1–R9) broken. Important = spec gap,
missing evidence, a house rule the reviewers enforce. Minor = style or naming. Only Critical/Important block.

## W48 (2026-09-22) — what counts as a red run
"Red by non-existence" (the test cannot compile until the new member exists) is accepted as the red run for coverage
of a NEWLY ADDED member. A bug pin or a behaviour-change test still needs a genuine failing run captured in the report.

## W56 (2026-09-23)
When a module gate creates or moves a `@Configuration` class, check its constructor: parameters typed as core beans
mean no ordering edge and a boot failure. When a core repository's `setDataSupplier` caller moves into a module, the
repository must move too or gain a core-side supplier.
