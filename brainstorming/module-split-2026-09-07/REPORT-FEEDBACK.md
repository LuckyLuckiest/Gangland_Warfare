# Feedback on the design report "Gangland Module Loader" (artifact 1687bb19, 2026-09-03)

Written 2026-09-07 by the sprint's scrum master after the mail pilot (0.8.2) shipped and before flips 1–4 start.

## What held up

- **The verdict was right.** The decoupling is where the value is; the loader belongs in Keystone; Central and
  `libraries:` are correctly deferred; the LuckPerms loader-jar option was correctly rejected. Nothing in the pilot
  argued against any of the four verdict answers.
- **"Remove the dependency and the compiler writes the work list"** is the single most useful sentence in the
  report. It is now the M1 rule of every checklist.
- **Prior-art table and the two Java 17 facts** (no `addURL` on `PluginClassLoader`, no class unloading) saved a
  wrong turn: parent-first child `URLClassLoader`, restart-required updates.
- **Each step ends with a "done when".** That is what makes the report convertible into agent checklists at all.

## What was wrong or missing, in order of cost

1. **The move order is not buildable.** The report's own dependency table shows `gadget → weapon` and
   `cops-n-crooks → weapon + turf`, yet the steps and the Gantt say `mail → turf → weapon → gadget → cops`.
   Flipping turf while cops is still in the core creates the reactor cycle `impl → cops → turf → impl`. The only
   incremental order is reverse-topological: `mail → cops-n-crooks → gadget → turf → weapon`. The artifact still
   shows the wrong order and should be corrected.
2. **Effort was sized by the wrong number.** The table sizes each feature by its own files and lines (cops 179
   files). What drives the work is the *impl-side coupling*: files in `gangland-impl` that name the feature. Those
   are cops 101, weapon 55, turf 30, gadget 29, mail 14. The report never measured that, so "15–20 days" for wave 2
   is not derived from anything. Any future sizing should quote the impl-side count and the number of mixed
   `@Configuration` classes.
3. **Four seams the pilot had to invent were not in the report.** (a) `CommandContribution`: mail's consumers were
   `/glw gang` sub-arguments hand-built in `GangCommand`, so the core needs a way to accept sub-arguments from a
   module. Every remaining feature has sub-arguments under core commands (`debug`, `item`, `gang`), so this seam is
   structural, not a mail quirk. (b) `InformationManager.merge` for per-module `commands.json` — mentioned under
   "risks", absent from the steps. (c) `Diagnostics` is installed in KERNEL, so loader faults during bootstrap can
   only be logged; the report's fault design assumed a hub. (d) Module-private resources must be read from the
   module's own `JarFile`, because the parent-first loader returns the core's `commands.json` first.
4. **The YAML placement rule is stated imprecisely** ("under `resources/<module>/`"). The 5-argument
   `FileHandler` reads `<directory>/<name><type>` from the loader, so the module jar must carry the default at the
   data-folder path (`weapon/rifle.yml`, `npc/cops.yml`), and that path must vanish from the core jar or the
   parent-first loader keeps serving the core copy. `documentation/module-loader.md` repeats the imprecise wording;
   both should say "same path as the data folder".
5. **No living status.** The masthead still says Gangland 0.8.1 / Keystone 1.7.4, and there is no place for the
   decisions taken on 2026-09-04 (Keystone 1.8.0, YAML rule relaxed, contract rule relaxed, module set = the five
   features, group id deferred). A report that is meant to be executed by agents needs a status strip and a
   decision log, or it needs to hand over to a sprint board (which is what `README.md` in this folder does).
6. **Test strategy is a risk bullet, not a step.** "Every module-owning test must run with the module present and
   absent" is right and has no step, no done-when and no owner. The checklists add `<Feature>ModuleTest` and a
   zero-modules bootstrap test as mandatory items (M10).
7. **Failure semantics inside the bean pipeline are unspecified.** The loader skips a module whose descriptor or
   `Main` fails, but nothing says what happens when a module's `@Bean` method throws *inside* `instantiate()`. Today
   that aborts the whole bootstrap, so a broken module can take the core down, which contradicts the "boots with
   any module absent" promise. Worth a Keystone follow-up (fault-isolate per configuration) or at least a documented
   limitation.
8. **Wave 3 is gated on a user action that has been pending for months** (namespace, GPG, token, group id). The
   report says so but treats it as parallel setup. It is the critical path for the only user-visible feature of the
   whole initiative (install/update from the game). The group-id decision should be taken now, while nothing is
   published, because it renames every coordinate later.
9. **Process risk not named:** a concurrent bug-fix stream edits the same mixed config files the split moves
   (`GameplayConfig`, `CopsAndGadgetsConfig`, gang commands). The split must run between bug waves and rebase on
   each landed wave; the sprint README now encodes that as gate G0.
10. **Stale blocker list.** `DebugLoggingInitializer` (named on 2026-09-04) no longer exists in the tree.

## What to change in the artifact

- Correct the order in "Implementation steps" and the Gantt; add the reverse-topological rule and its reason.
- Add an "Executed" strip: wave 1 done (Keystone 1.8.0, 6a79fe7), wave 2 mail pilot done (0.8.2, 66eeb647),
  flips 1–4 tracked in `brainstorming/module-split-2026-09-07/`.
- Add the four seams from item 3 to the "Gangland consumer side" panel.
- Replace the YAML wording per item 4.
- Turn the test-matrix risk into a step with a done-when.
- Add item 7 to "Risks worth naming" with the Keystone follow-up.
- Mark the group-id decision as blocking wave 3, with a recommendation (keep `org.luckyraven` only if the DNS
  record can be added this month; otherwise `io.github.luckyluckiest` now, before any artifact exists).
