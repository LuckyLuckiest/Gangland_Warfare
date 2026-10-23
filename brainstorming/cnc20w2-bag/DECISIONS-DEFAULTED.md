# cnc20w2 bag - decisions defaulted (bag lane)

| id | question | taken | alternative |
|---|---|---|---|
| D-W2-35 | ChaseConfig.java touch. SPEC-0.20.0-w2 section 9 says 0.20 w2 does not touch ChaseConfig.java; the bag commit b30b5e5ca changed lines 46-49 (comment + `wantedRoot.get("Bag")`) so the unknown-key check accepts `Wanted.Bag`. | Ratified as accepted (judge-fix r1 c5). Scope: lines 46-49 only, they register the Witnesses/Trespass/Bag roots; no code change in this chunk. | Revert the 4 lines and have the bag loader read `Bag` without a ChaseConfig edit, or move the check into BagSettings. |
