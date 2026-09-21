package org.luckyraven.gangland.npcshops.banker.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.npcshops.banker.BankerNpc;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.flow.MenuFlow;

/**
 * Entry point for the banker NPC flow. Builds a fresh {@link MenuFlow} per-viewer and registers each converted
 * panel. Remaining legacy subviews (amount, create, rename, claim) are still reached via {@link MenuFlow#end()}
 * hand-offs inside the menu panel — they will be registered here as they are migrated.
 *
 * <p>The {@link #startFromPhone(Player)} overload is the link from {@code phone_banking.yml} — no physical banker is
 * present, so {@code BankerFlowSession#banker} is {@code null} and display strings fall back to "Online Banking".
 */
@RequiredArgsConstructor
public final class BankerFlow {

	private final JavaPlugin              plugin;
	private final InventoryService        inventoryService;
	private final BankerMenuView          menuPanel;
	private final BankerUpgradeView       upgradePanel;
	private final BankerClaimView         claimPanel;
	private final BankerAmountView        amountPanel;
	private final BankerCreateAccountView createPanel;

	public void start(Player viewer, BankerNpc banker) {
		startInternal(viewer, banker);
	}

	public void startFromPhone(Player viewer) {
		startInternal(viewer, null);
	}

	private void startInternal(Player viewer, @Nullable BankerNpc banker) {
		BankerFlowSession session = new BankerFlowSession(banker);
		MenuFlow<BankerFlowSession> flow = MenuFlow.builder(inventoryService, plugin, viewer, session)
		                                           .panel(BankerFlowSession.PANEL_MENU, menuPanel)
		                                           .panel(BankerFlowSession.PANEL_UPGRADE, upgradePanel)
		                                           .panel(BankerFlowSession.PANEL_CLAIM, claimPanel)
		                                           .panel(BankerFlowSession.PANEL_AMOUNT, amountPanel)
		                                           .panel(BankerFlowSession.PANEL_CREATE, createPanel)
		                                           .build();
		flow.openAt(BankerFlowSession.PANEL_MENU);
	}

}
