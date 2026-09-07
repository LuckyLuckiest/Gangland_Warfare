package org.luckyraven.gangland.copsncrooks.npc.banker.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerNpc;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;

/**
 * Entry point for the banker NPC flow. Builds a fresh {@link MultiPanelInventory} per-viewer and registers each
 * converted panel. Remaining legacy subviews (amount, create, rename, claim) are still reached via
 * {@link MultiPanelInventory#end()} hand-offs inside the menu panel — they will be registered here as they are
 * migrated.
 *
 * <p>The {@link #startFromPhone(Player)} overload is the link from {@code phone_banking.yml} — no physical banker is
 * present, so {@code BankerFlowSession#banker} is {@code null} and display strings fall back to "Online Banking".
 */
@RequiredArgsConstructor
public final class BankerFlow {

	private final JavaPlugin              plugin;
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
		BankerFlowSession                      session = new BankerFlowSession(banker);
		MultiPanelInventory<BankerFlowSession> host    = new MultiPanelInventory<>(plugin, viewer, session);
		host.register(BankerFlowSession.PANEL_MENU, menuPanel);
		host.register(BankerFlowSession.PANEL_UPGRADE, upgradePanel);
		host.register(BankerFlowSession.PANEL_CLAIM, claimPanel);
		host.register(BankerFlowSession.PANEL_AMOUNT, amountPanel);
		host.register(BankerFlowSession.PANEL_CREATE, createPanel);
		host.openAt(BankerFlowSession.PANEL_MENU);
	}

}
