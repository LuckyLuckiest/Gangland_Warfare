# 0.16.1 tab completion hides command aliases

Status: fixed in lanes, committed, not pushed or merged.

## Root cause

The owner's Paper 1.21.11 client gets root and sub-level suggestions from the client-side Brigadier tree, not from the Bukkit `TabCompleter`. `BrigadierTabRegistrar.thenWithAliases` attached every alias as a sibling literal (KS-CM-19), and `BrigadierPaperListener.handle` re-added master aliases after stripping them (KS-CM-18). Both were added on purpose to make aliases tab-complete, and they produced the list the owner reported. Sub-level aliases (`everyone`) also leaked through `extraTokens`.

The server side had no alias fallback at the root and none at deep levels, so a typed alias returned nothing (`gv` gave `[]`).

## Owning repo

Keystone (`keystone-command`). Gangland only bumps `<keystone.version>`. No Gangland code change: dispatch in `CommandManager` (label or alias match) is unchanged, and the chat "did you mean" hint at `CommandManager.java:88` is out of scope.

## Rule

For each level, P = permitted, visible primary tokens (root: command labels plus `help`; sub-level: permitted literal children's primaries plus `help` where the help predicate allows). A = aliases of those items, lower-cased, deduped, excluding any equal to a primary. OptionalArgument children are excluded from both and keep their custom suggestions.

- `result(prefix)` = case-insensitive `startsWith` matches in P, sorted and distinct. If empty, use matches in A.
- Empty prefix with P non-empty returns P.
- On a tree client (`clientTree` flag set by the registrar), P is used for the decision but omitted from output. Output is OptionalArgument suggestions plus the alias fallback. This avoids duplicating the client's literals.

Client tree: primary literals only. If a parent has at least one permitted alias and no free-text child of its own, one fallback child `argument("alias", string())` with `NOOP` and `ASK_SERVER` suggestions, carrying a `greedyString("rest")` child with the same. Typed aliases still execute. On Paper each master alias is a redirect literal at the root pointing at the primary tree, so `/gangland` completes exactly like `/glw` (judge round 1, finding tab-1).

Also: `BrigadierTabRegistrar.receivesTree(Player)` gates tree delivery on protocol 393 through ViaVersion reflection (pre-1.13 clients keep the old behaviour), and `CommandTabCompleter.offersHelp(Command)` gates the sub-level `help` literal so it appears only where the server offers it on both backends.

## Files

Keystone lane `E:/Programming/java/wt/ks1151-tabalias`, branch `ks-1.15.1-tabalias`:
- `keystone-command/src/main/java/org/luckyraven/keystone/command/CommandTabCompleter.java`: alias rule helper, `clientTree` flag, `offersHelp`.
- `keystone-command/src/main/java/org/luckyraven/keystone/command/brigadier/BrigadierTabRegistrar.java`: primary-only literals, alias fallback, `receivesTree`, help gating.
- `keystone-command/src/main/java/org/luckyraven/keystone/command/brigadier/BrigadierPaperListener.java`: re-add loop removed.
- Tests: `CommandTabCompleterTest.java`, `BrigadierTabRegistrarTest.java`, `BrigadierPaperListenerTest.java`.
- `pom.xml` root `<revision>` 1.15.0 to 1.15.1; `docs/phase-h15-tab-aliases.md` (new); `docs/README.md`; `CLAUDE.md` (Keystone version reference).
- Commits: `f05520a` (red tests), `44d84fc` (fix, version 1.15.1, phase doc); judge round 1: `527ad7e` (red tests), `502e2b9` (fix).

Gangland lane `E:/Programming/java/wt/cnc161-tabalias`, branch `cnc-0.16.1-tabalias`:
- `pom.xml` line 70 `<keystone.version>` 1.15.0 to 1.15.1.
- Docket: `brainstorming/cross-docket-2026-09-10/bugs.json`, `cross-project-bug-docket.html`, `docket_template.html`, `keystone/findings/command-framework.txt` (KS-CM-18/19 superseded, KS-CM-26/27/29 decided, KS-CM-32/33 added).
- Commit: `6de5daee`.

## Tests and red proof

Red (committed in `f05520a`, before the fix):
- `CommandTabCompleterTest.aliasSuggestedWhenNoPrimaryMatchesRoot`: expected `[gv]`, was `[]`.
- `CommandTabCompleterTest.subArgumentAliasFallback`: expected `[everyone]`, was `[]`.
- `CommandTabCompleterTest.clientTreeOmitsPrimaries`: compile failure on `setClientTree(boolean)` (the spec's expected red). A stub run showed the assertion failing for the right reason.
- `BrigadierTabRegistrarTest.aliasesAreNotLiteralsOnBothBackends` (flipped KS-CM-19): expected null, was literal `g`.
- `BrigadierTabRegistrarTest.aliasFallbackOnBothBackends`: no free-text alias fallback at root.
- `BrigadierPaperListenerTest.masterAliasNotReAddedAsLiteral` (flipped KS-CM-18): expected null, was literal `m`. Later renamed and flipped again in round 1 (see below).
- Stub run from the prior pass: 45 tests, 6 failures, 0 errors.

Green in this pass (re-run today, offline):
- Keystone: `mvn -o -pl keystone-command -am test -Dtest=CommandTabCompleterTest,BrigadierTabRegistrarTest,BrigadierPaperListenerTest,CommandManagerTest,ArgumentTreeTest`: ArgumentTreeTest 17, BrigadierPaperListenerTest 4, BrigadierTabRegistrarTest 8, CommandManagerTest 21, CommandTabCompleterTest 16. Total 66, 0 failures, 0 errors. BUILD SUCCESS.
- Gangland: `mvn -o -pl gangland-impl -am test`: 460 tests, 0 failures, 0 errors. BUILD SUCCESS.
- Keystone 1.15.1 installed locally with `mvn -o clean install -DskipTests` in the Keystone lane (done in the prior pass).

Pinned tests that must stay green (`aliasResolvesToSameCommand`, `CommandManagerTest.onCommandResolvesAliases`, `ArgumentTreeTest.matchesRootAlias`, `ArgumentTreeTest.equalsMatchesAnyAliasCaseInsensitively`) pass in the run above.

The red proof for the two tests named by the prior pass is now real, recorded in round 1 below (`helpLiteralFollowsTheCompleterPredicate` re-run with the offersHelp gate reverted; `masterAliasNotReAddedAsLiteral` superseded).

## Keystone version

Bumped: 1.15.0 to 1.15.1 (Keystone root pom, Gangland `<keystone.version>`). Keystone 1.15.1 is in the local Maven repo.

## Follow-ups

- Resolved in round 1 (finding tab-1) and rounds 2-3: `/gangland` completes through a redirect literal, as `/glw` does. Spigot: Commodore covers alias completion through its own redirects.
- Unverified in game on Paper 1.21.11: no duplicates between literal and server suggestions; typed aliases render valid; pre-1.13 ViaVersion path (`receivesTree`, no unit test because ViaVersion is not on the Keystone test classpath); Spigot Commodore alias leak (KS-CM-28, open); Spigot fallback requires (KS-CM-31, open).
- Adjacent findings to triage: deep-level aliases never listed (now covered by KS-CM-33), `clientTree` duplicate risk (covered by the flag, to confirm in game).
- The artifact page `4a903fb6-cbdd-4810-90b8-88a863e9013c` was not republished; status rows for KS-CM-18/19/26/27/29/30/32/33 were written to its `bugs` collection in the prior pass.
- Commit messages: the Keystone and Gangland commits already exist under earlier messages (`44d84fc`, `6de5daee`). No new code commit was made in this pass; this report is committed separately.

## Judge round 1 (fixer)

Keystone lane (`E:/Programming/java/wt/ks1151-tabalias`, branch `ks-1.15.1-tabalias`): red tests `527ad7e`, fix `502e2b9`. Keystone 1.15.1 reinstalled with `mvn -o clean install -DskipTests`.
Gangland lane (`cnc-0.16.1-tabalias`): no code change, the pin stays 1.15.1. This report is the only change.

| Finding | Severity | Result | Evidence |
|---|---|---|---|
| tab-1 | blocker | fixed | Each master alias is now a redirect literal at the Paper root pointing at the primary tree. `/gangland` completes like `/glw`. Test `BrigadierPaperListenerTest.masterAliasRedirectsToThePrimaryTree` (flipped from `masterAliasNotReAddedAsLiteral`) asserts the root node exists, `getRedirect()` is the primary instance, the alias has no children, and Brigadier's own `getCompletionSuggestions("m ")` lists `help`. Red on the pre-fix HEAD: `the master alias must complete, so it needs a node at the root ==> expected: not <null>`. |
| tab-2 | minor | fixed | The tree mark is cleared (`removeMetadata`) before the permission gates on every send, so a rejoin with another client or without permission cannot inherit it. Test `treeMarkIsClearedBeforeTheGatesOnEverySend`. Red on the pre-fix HEAD: `Wanted but not invoked: player.removeMetadata("keystone.brigadier-tree.master", Mock for JavaPlugin)`. The javadoc and registrar comment are corrected. |
| tab-3 | minor | fixed | A level with an `OptionalArgument` or `ListArgument` child gets no alias fallback, so it has at most one server-asking child. Test `BrigadierTabRegistrarTest.atMostOneFreeTextChildPerParent` (Paper and Spigot trees). Red on the pre-fix HEAD: `give has several server-asking free-text children: [name, alias] ==> expected: <true> but was: <false>`. The limit (a typed alias at such a level loses its own sub-level completion; no Gangland level has this shape) is in the phase doc. |
| tab-4 | minor | fixed (this section) | Real assertion-level red now recorded for every new or flipped test. `helpLiteralFollowsTheCompleterPredicate` re-run with the `offersHelp` gate temporarily replaced by `if (true)` in both builders: `the server offers no sub-level help, so the client must not ==> expected: <null> but was: <<literal help>>`. The file was restored from git afterwards (clean tree). |

Spigot path: Commodore covers Spigot alias completion through its own redirects (see rounds 2-3).

Test counts:
- Keystone, `mvn -o -pl keystone-command -am test` (full reactor): BUILD SUCCESS. Keystone Command module 162 tests, 0 failures, 0 errors (BrigadierPaperListenerTest 5, BrigadierTabRegistrarTest 9). Other modules: Common 272, Bean 66, Item 143, Persistence 443, Testkit 5, all 0 failures.
- Gangland, `mvn -o -pl gangland-impl -am test`: BUILD SUCCESS. Reactor: Gangland Core 124, Gangland Item 48, Sign API 66, Gangland API 154, Gangland 460 tests, 0 failures, 0 errors (same count as before the round; the Keystone change is covered by the existing alias and tab-completion tests).

## Judge rounds 2-3

Keystone lane (`E:/Programming/java/wt/ks1151-tabalias`, branch `ks-1.15.1-tabalias`):
- tab-5 and tab-6 were fixed in Keystone `4cce5d4`. Namespaced forms (`<plugin>:<label>`) redirect to the primary tree, so a marked client completes `gangland_warfare:glw <TAB>` as `/glw <TAB>`. Each redirect carries the primary's command, so a bare alias parses without the "incomplete command" hint. Test: `BrigadierPaperListenerTest.namespacedFormsRedirectToThePrimaryTree`.
- tab-7 (judge r3, minor): a namespaced redirect is added only for a namespaced stub the server actually sent. With spigot.yml `commands.send-namespaced: false` the listener no longer injects `gangland_warfare:glw` (and the other namespaced forms) into the client root. Bare-alias redirects stay unconditional. Fix commit: Keystone `f844a561`, the same commit as the tab-8 doc fixes. Test: `BrigadierPaperListenerTest.namespacedRedirectsAreOnlyAddedForStubsTheServerSent`. Red before the fix: `no namespaced stub was sent, so none may be injected ==> expected: <null> but was: <<literal testplugin:master>>`. The existing `namespacedFormsRedirectToThePrimaryTree` now also sends its `testplugin:m` alias stub, because the server sends one per alias.
- tab-8 (docs): `CLAUDE.md`, `docs/README.md` and `docs/phase-h15-tab-aliases.md` now describe master aliases and their namespaced forms as redirects to the primary tree. The Spigot alias leak is accepted under the owner ruling. The client-dedupe claim is softened to "possibly deduped by `Suggestions.merge`".

Test counts:
- Keystone `mvn -o -pl keystone-command -am test`: keystone-command 163 before tab-7, 164 after (the new test added). Other modules unchanged: Common 272, Bean 66, Item 143, Persistence 443, Testkit 5. BUILD SUCCESS. `mvn -o clean install -DskipTests` in the Keystone lane: BUILD SUCCESS.
- Gangland, `gangland-impl`: 460 tests (the round-1 figure). Gangland's code did not change in rounds 2-3, and I did not re-run it this round.

Gangland lane (`E:/Programming/java/wt/cnc161-tabalias`, branch `cnc-0.16.1-tabalias`): this report is the only change. The pin stays 1.15.1.

Owner rulings, as now reflected in this report:
- `/gangland` completes through its redirect literal, as `/glw` does. The earlier "owner trade-off: /gangland no longer completes" statements are removed.
- Spigot: Commodore covers alias completion through its own redirects. This matches the Paper behaviour and is accepted under the owner ruling.
- Docket: KS-CM-28 is superseded under the owner ruling. The orchestrator records the docket status. This session did not edit any docket file or artifact.
