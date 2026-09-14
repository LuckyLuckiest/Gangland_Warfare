# Oriel P0/P1 findings for independent review (Oriel repo: E:\Programming\java\Oriel, Gradle modules menu-core, menu-data/config, menu-data/database, menu-dependencies/*, menu-editor, menu-inventories/*, menu-plugin)

### [menu-chest #1] P0 — ChestMenu.close skips returnHeldItems on CloseReason.USER, destroying DepositSlotComponent items on Escape
Location: menu-inventories/chest/src/main/java/org/luckyraven/oriel/chest/ChestMenu.java:380-386
Observation: close(Player,CloseReason) drains held items only for non-user reasons: `if (reason != CloseReason.USER) { returnHeldItems(player, reason, true); }`. A DepositSlotComponent's stack physically occupies a slot in the plugin-created Bukkit Inventory, not the player's cursor, so the javadoc's rationale ("Bukkit's normal close flow surfaces the cursor stack to the player") does not apply to it — Bukkit discards a custom inventory's slot contents on close. Verified that no other path compensates: ChestMenuCloseListener.java:70 only calls `target.onClose(event, player)`, and ChestMenu.onClose (ChestMenu.java:422-429) merely runs the user's on_close handler, never returnHeldItems. A player who deposits an item and presses Escape loses it permanently.
Fix: Return (or drop at the player's feet) held ItemHoldingComponent items on USER closes too — Bukkit only restores the cursor stack, never a custom inventory's slot contents
Tests: DepositSlotComponentTest and DepositSlotMultiSlotStressTest both assert today's wrong behaviour and must be flipped with the fix
Confidence: High

### [menu-chest #2] P0 — MenuFlow.switchInternal and end() abandon DepositSlotComponent items on every panel switch
Location: menu-inventories/chest/src/main/java/org/luckyraven/oriel/chest/flow/MenuFlow.java:165-185
Observation: switchInternal builds a fresh ChestMenu for the target panel and swaps it in without ever closing the outgoing one: `ChestMenu newMenu = builder.build(); currentMenu = newMenu; currentPanelId = panelId; newMenu.open(viewer);`. Any item sitting in a DepositSlotComponent slot on the old panel is abandoned inside the discarded Inventory. end() is no better — MenuFlow.java:153 calls `currentMenu.close(viewer)`, the reason-less overload, which routes to the USER path that finding 1 shows never returns held items. So a payment, bid, or appraisal slot inside a flow panel loses its contents on every switchTo, back, and end, and no MenuFlow code path transfers them.
Fix: Close the outgoing panel's menu with a non-USER CloseReason before building the next panel, and pass an explicit reason in end(), so ItemHoldingComponent contents drain to the player
Tests: -
Confidence: High

### [menu-chest #3] P1 — ChestMenuCloseListener fires the outgoing menu's on_close handler during internal navigation
Location: menu-inventories/chest/src/main/java/org/luckyraven/oriel/chest/listener/ChestMenuCloseListener.java:56-70
Observation: ChestMenu.open() tracks the incoming menu before calling player.openInventory(), so when a navigation triggers Bukkit's implicit close of the outgoing inventory, `MenuTracking.tracker().currentMenuOf(player) == target` is already false for that outgoing menu. The listener correctly skips the veto and untrack block in that case, but `target.onClose(event, player);` sits after the closing brace at line 70 and runs unconditionally. Every navigation into a submenu therefore fires the parent menu's YAML on_close actions — message, sound, command — as though the player had genuinely left the menu, when they only clicked through to a child screen.
Fix: Move the `target.onClose(event, player)` dispatch inside the same `currentMenuOf(player) == target` guard already used for the veto and untrack branches
Tests: -
Confidence: High

### [menu-config #1] P1 — ImportService.writeTree truncates an existing imported YAML with no existence check
Location: menu-data/config/src/main/java/org/luckyraven/oriel/config/importer/ImportService.java:158-174
Observation: writeTree opens the destination with `Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)`, whose default CREATE plus TRUNCATE_EXISTING semantics overwrite unconditionally, and no existence check precedes it. The per-menu loop at lines 126-133 resolves `outFile = menuOutDir.resolve(safeFileName(menu.name()) + ".yml")`. Re-running /menu import therefore discards any hand-edits an admin made to a previously imported file under menus/imported/<source>/ with no warning and no report entry. Worse, safeFileName collapses every non-alphanumeric character to an underscore, so two source menus named "Shop A" and "Shop-A" both resolve to shop_a and silently clobber each other inside a single import run — one imported menu is simply lost.
Fix: Check Files.exists(outFile) before writing and warn-and-skip or version-suffix on a collision, instead of silently truncating
Tests: -
Confidence: High

### [menu-core #1] P1 — RequirementHook.evaluateMinimum dispatches onSuccess for every passing set, double-spending consumable gates
Location: menu-core/src/main/java/org/luckyraven/oriel/core/requirement/RequirementHook.java:195-215
Observation: evaluateMinimum() evaluates all sets, then on a pass runs `for (RequirementSet set : passed) { set.dispatchSuccess(dispatchCtx); }` for every set that passed. evaluateAny() (RequirementHook.java:217-228) instead fires success for only the first passing set and returns, and the evaluateMinimum javadoc claims to be "the natural generalisation of evaluateAll (min = M) and evaluateAny (min = 1)" — so min=1 contradicts its own documented contract. `minimum_requirements: 1` is reachable from user YAML (RequirementParser.java:155-156 clamps with `.asInt().min(1)`) and is emitted directly by the DeluxeMenus importer (DeluxeMenusImporter.java:961-965, whose javadoc at line 906 shows `minimum_requirements: 1`). RequirementSet's javadoc calls onSuccess "load-bearing for consumable gates ... the cooldown is spent only" there, so an imported N-of-M gate where two alternative sets both pass spends both resources on a single click instead of one.
Fix: Make evaluateMinimum stop dispatching success once `min` sets have passed (matching evaluateAny for min=1), or dispatch success to exactly the sets that were needed
Tests: RequirementHookTest (exercises minimum=2 only, does not pin minimum=1 dispatch count)
Confidence: High

### [menu-editor #1] P1 — MenuEditor.open calls previous.exit() instead of requestExit(), discarding an unsaved editor session
Location: menu-editor/src/main/java/org/luckyraven/oriel/editor/MenuEditor.java:117-121
Observation: When a player who already has an editor open runs /menu edit again, open() replaces the controller with `EditorController previous = active.remove(player.getUniqueId()); if (previous != null) { previous.exit(); }`. EditorController.exit() (EditorController.java:129-137) unregisters the listener and closes the inventory unconditionally, while requestExit() (EditorController.java:120-126) does `if (session.dirty()) { open(new ExitPromptScreen()); } else { exit(); }`. Every other exit route — Escape, the Hub exit button, back() at the root — goes through the dirty-checked path, so this one call site is the sole way to lose unsaved material, lore, action, requirement and property edits with no warning or prompt.
Fix: Route the replacement of an existing controller through previous.requestExit() so a dirty session opens ExitPromptScreen instead of being torn down silently
Tests: -
Confidence: High

### [menu-editor #2] P1 — SlotOpScreen.apply deletes an occupied destination slot when the grabbed source was cleared meanwhile
Location: menu-editor/src/main/java/org/luckyraven/oriel/editor/screen/SlotOpScreen.java:65-82
Observation: GridScreen.cellFor assigns the cell's edit handlers before the grab marker is applied, and the grab branch only rewrites presentation: `if (isGrab) { cell.name("&e[grabbed] slot " + index).lore(...); }` (GridScreen.java:114-117). The still-grabbed cell therefore keeps its onLeftClick into SlotEditing, so an admin can clear or delete that slot and return to the same GridScreen with `grabbed` unchanged. Shift-clicking an occupied destination then runs apply(), which re-reads `Object from = slots.get(src);` as null and for MOVE does `slots.remove(src); put(slots, dst, from);` — and put() maps a null value to `slots.remove(key)` (SlotOpScreen.java:89-95). The destination's configured item definition is deleted from the working copy even though the screen's own "Overwrites slot N" warning implied a real item was inbound.
Fix: Treat a null resolved source as a cancelled no-op for MOVE and COPY, and clear GridScreen's grabbed marker whenever the grabbed cell's own content is edited or cleared
Tests: -
Confidence: High

### [menu-integration #1] P0 — give_permission and take_permission bypass the allow_elevated_actions gate that locks down op and permission
Location: menu-dependencies/vault/src/main/java/org/luckyraven/oriel/vault/permission/PermissionActions.java:34-49
Observation: PermissionActions registers both actions into the ActionRegistry with only a blank-node check before returning `ctx -> permissions.add(ctx.player(), node);` and `ctx -> permissions.remove(ctx.player(), node);`. The functionally equivalent built-in op and permission actions are hard-gated by allow_elevated_actions, which defaults to false and whose config.yml comment says to leave it off "unless you trust every menu author on the server"; ActionParser refuses them with a warning until an admin opts in. PermissionActions consults no such gate, and its own javadoc notes the core permission action is the scoped alternative "it revokes the node again after the command runs" — so the ungated action is the strictly more powerful one, granting nodes permanently. Any menu author, including one using the in-game editor, can write give_permission with an arbitrary node while the guarded path stays locked. Exploiting it requires menu-authoring access, but the whole purpose of the gate is to bound exactly that actor.
Fix: Gate both PermissionActions registrations behind ActionParser's elevatedActionsGate, warning and no-opping when allow_elevated_actions is false
Tests: -
Confidence: High

### [menu-integration #2] P0 — EconomyActions discards the Vault transaction result, so a failed take_money still lets the rest of the chain reward the player
Location: menu-dependencies/vault/src/main/java/org/luckyraven/oriel/vault/economy/EconomyActions.java:39-46
Observation: Both registrations return statement-expression lambdas against ClickHandler's void onClick — `return ctx -> economy.deposit(ctx.player(), amount);` and `return ctx -> economy.withdraw(ctx.player(), amount);` — so the transactionSuccess-derived boolean from Keystone's EconomyProvider is thrown away. ActionParser.chain runs handlers with `for (ClickHandler h : handlers) h.onClick(ctx);` and never aborts on failure, so a menu whose on_left_click is take_money followed by a reward action hands out the reward even when the player could not be charged — nothing requires an author to pair take_money with a money requirement, and the demo presents that pairing only as the recommended pattern. The same gap applies to the documented check-then-charge flow: EconomyRequirements only ever calls the read-only has(), and RequirementHook has already committed to running the gated handler by the time on_success fires, so a withdraw that fails for a backend reason still yields a free reward. ClickHandler's javadoc documents ctx.cancel() for exactly this signal and EconomyActions never calls it.
Fix: Check the boolean returned by economy.withdraw/deposit and cancel the click or abort the chain when it is false, instead of discarding it
Tests: -
Confidence: High

### [menu-inventory #1] P0 — TraderMenu.open rebuilds MerchantRecipes each open, resetting maxUses so limited-stock trades are unlimited
Location: menu-inventories/trader/src/main/java/org/luckyraven/oriel/trader/TraderMenu.java:68-78
Observation: open() constructs a brand-new Merchant and a brand-new recipe list on every invocation: `for (TradeOffer offer : offers) { MerchantRecipe recipe = offer.toRecipe(); recipes.add(recipe); recipeIndex.put(recipe, offer); }`. TradeOffer.toRecipe (TradeOffer.java:43-50) does `MerchantRecipe recipe = new MerchantRecipe(result.clone(), maxUses);` and never restores a prior use count, so each new instance starts at uses = 0. Minecraft enforces maxUses only within the lifetime of one MerchantRecipe instance, so an admin-configured "limited stock" or "buy once" trade is bought again simply by closing and reopening the trader — no exploit tooling required, just walking away and coming back. Every purchase beyond the intended cap is item and currency creation.
Fix: Persist per-player-per-offer use counts (or reuse the built MerchantRecipe instances) across opens instead of calling offer.toRecipe() fresh in every open()
Tests: TraderMenuStressTest exercises repeated open/close but never asserts uses or maxUses, so the behaviour is unpinned
Confidence: High

### [menu-inventory #2] P1 — TraderMenu.open registers in TraderRegistry before openMerchant, whose implicit close deletes the new entry
Location: menu-inventories/trader/src/main/java/org/luckyraven/oriel/trader/TraderMenu.java:83-86
Observation: open() runs `TraderRegistry registry = TraderSupport.registry(); registry.register(player, this); player.openMerchant(fresh, true); MenuTracking.tracker().track(player, this);` — the registry write precedes openMerchant. Bukkit's openMerchant implicitly closes whatever the player currently has open and fires InventoryCloseEvent synchronously inside that call, and TraderClickListener.onClose (TraderClickListener.java:103-111) unconditionally does `registry.unregister(player);` for any MERCHANT-type close. Opening a trader menu while one is already open therefore writes the new entry, then immediately deletes it when the old screen's close event fires. The next purchase finds no registry entry, so onTrade and onPurchase never fire while the vanilla item exchange still completes — the player trades but the menu's handlers silently do not run.
Fix: Call player.openMerchant(...) first and register afterwards, or make the close handler unregister only when the stored menu is identity-equal to the one being closed
Tests: TraderMenuStressTest asserts the registry is overwritten on reopen but never registers TraderClickListener as a live listener, so it cannot observe the close-driven deletion
Confidence: High

### [menu-persistence #1] P0 — HistoryService.quote hardcodes double quotes, so the player_uuid migration no-ops on MySQL and the column is then dropped
Location: menu-data/database/src/main/java/org/luckyraven/oriel/database/history/HistoryService.java:131-168
Observation: migrateLegacyColumn probes with `backend.query("SELECT player_uuid FROM " + quote(tableName) + " LIMIT 1", ...)`, and quote() is `return '"' + identifier.replace("\"", "\"\"") + '"';` with a javadoc that admits the assumption: "Double quotes for SQL-standard portability — both SQLite and MySQL (in ANSI_QUOTES mode) accept them". MySQL's default sql_mode does not include ANSI_QUOTES, so the double-quoted table name parses as a string literal and the probe throws a syntax-error SQLException even when a real legacy table exists; the catch block then does `migrated.add(menuName); return true;` and no rename happens. Keystone's AbstractJdbcBackend.applySchema afterwards runs `"ALTER TABLE " + quoteIdentifier(schema.tableName()) + " DROP COLUMN " + quoteIdentifier(drop)` (AbstractJdbcBackend.java:132-136) for every live column absent from the declared schema — correctly backtick-quoted, so it succeeds — dropping the still-present player_uuid column. Any MySQL-backed install upgrading from Oriel 0.6.x or earlier permanently loses its audit history, which is exactly the outcome this method's own javadoc says it exists to prevent.
Fix: Quote identifiers through the backend's own quoteIdentifier (backticks on MySQL) instead of hardcoding double quotes, and do not treat a probe SQLException as "nothing to migrate"
Tests: HistoryServiceLegacyMigrationTest covers SQLite only; no MySQL path is tested
Confidence: High

### [menu-persistence #2] P1 — HistoryService.logEvent reports audit-write failures nowhere admins are told to look
Location: menu-data/database/src/main/java/org/luckyraven/oriel/database/history/HistoryService.java:85-93
Observation: logEvent has no try/catch and no return value — `delegate.logEvent(menuName, playerUuid, actionType, payload);` passes straight to Keystone's AuditLogService.logEvent, whose own catch only calls log.warn and never reports to Diagnostics (root cause in Keystone AuditLogService.logEvent). migrateLegacyColumn, one method down in the same file, does the opposite on failure: `Diagnostics hub = Diagnostics.active(); if (hub != null) { hub.report(rename, "history.migrate"); }`, surfacing faults through the DatabaseFaultSink-backed faults table this very module ships. A dropped deposit or return audit row — transient MySQL pool exhaustion, a closed connection — is therefore invisible in the place admins are directed to check, and DepositActions.maybeLog has no way to learn the write failed either.
Fix: Wrap the delegate call and report a failed insert through Diagnostics, mirroring what migrateLegacyColumn already does in the same class
Tests: HistoryServiceSqliteTest and DatabaseFaultSinkSqliteTest exist but neither covers a failing INSERT
Confidence: Medium

### [menu-plugin #1] P1 — A failed database reconnect on /menu reload aborts reloadLifecycleBeans and leaves AnimationTicker cancelled
Location: menu-plugin/src/main/java/org/luckyraven/oriel/plugin/config/PersistenceConfig.java:110-129
Observation: ManagedDatabaseBackend.open() rethrows a connection failure as `throw new IllegalStateException("Database backend '" + type + "' could not connect", ex);` at line 125. Keystone's BeanFactory.reloadLifecycleBeans (BeanFactory.java:333-350) runs three bare loops — onPreClear, onClear, then `for (BeanLifecycle bean : forward) { bean.onInitialize(false); }` — with no per-bean try/catch, so that exception aborts the forward loop. AnimationConfig is `@Configuration(phase = Phase.LIFECYCLE)`, ordered after PersistenceConfig's DATABASE phase, so its AnimationTicker has already had its BukkitTask cancelled by the earlier onClear pass and never receives the onInitialize call that would restart it. An ordinary admin mistake such as a mistyped mysql host therefore silently freezes every animated menu for every viewer until a later reload succeeds end to end, with only ReloadCommand's generic failure message shown.
Fix: Catch and log the connection failure inside ManagedDatabaseBackend.open() rather than rethrowing, so the reload cascade continues to the LIFECYCLE phase
Tests: -
Confidence: High
