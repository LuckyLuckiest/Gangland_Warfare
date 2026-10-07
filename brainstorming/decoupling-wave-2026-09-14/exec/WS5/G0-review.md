# Review — Gangland 0.10.0 WS5 G0 (identity repackage) — 2026-09-22 (Sonnet, transcribed)
Verdict: FIX (2 Important, 1 Minor).

## Spec table (condensed)
1 user/* move ✅ · 1b contract split ⚠ declared deviation, forced: `gangland-api` depends on `gangland-core` (compile), core has no back-edge, so `IdentitySettingsContract` must live in core; modules still see it through the api's compile-scope re-export · 1c orderingDep deleted ✅; `MemberCachePopulationOrderTest` calls the real `MemberManager.onInitialize()` (not mocked) but not the DataConfig/BeanGraph wiring end-to-end — live proof owed to the merge smoke · 2 bounty/wanted/events ✅ · 3 Permission move ✅ (M3) · 4 api pom swap ✅; gangland-mail/gadget gained a temporary direct gangland-domain dep (removed at G4) · R7 ✅ (GangLookupContract/RankLookupContract untouched).

## Findings
**I1 Important** — five clear-loop bridges (`Gang.removeMember`, `GangDemoteCommand` ~:150, `GangPromoteCommand` ~:180, `GangTransferCommand` ~:180/~:197) write `setPermission(node, false)` where the deleted `User.flushPermissions` used `PermissionAttachment.unsetPermission(name)`. An explicit false overrides a group/Vault/LuckPerms grant; an unset node falls back to it (`VaultPermissionBridge` exists for that case). A `gangland.*` node also granted externally is lost on demote/promote/transfer/leave. Fix: `User.unsetPermission(String)` primitive used at all five sites. The two `initializeUserPermission` bridges are behaviour-identical.
**I2 Important** — no captured red run for the 6 new `UserTest` primitives / 2 ordering tests; "red by non-existence" argued, not shown.
**M3 Minor** — `Permission.setID` widened to public (forced: `RankManager` is cross-module until G1); narrow it again in G1.

## Cannot verify
Reactor 823/0; 246 diff headers vs 245 reported (rename artifact); GR-12/13/35, GR-08 pins spot-checked as import-only.

## Rebase notes
CUT overlap: `DebugCommand`, `GangColorCommand`, `GangCommand`, `KernelConfig`, `InventoryRuntimeContext` — single import-line swaps each; everything else on the CUT list untouched.

## Orchestrator rulings (W48)
I1 fixed in fix round 1 with a genuine red (mocked `PermissionAttachment`: clear path must call `unsetPermission`, never `setPermission(_, false)`). I2 → standing policy W48: "red by non-existence" is accepted for new coverage of NEWLY ADDED members (the test cannot compile before them); every bug pin or behaviour-change test still needs a real failing run. M3 → G1 note. Docket: nothing filed; if G2's `RankPermissionApplier` regresses to explicit deny, file GR-next then.
