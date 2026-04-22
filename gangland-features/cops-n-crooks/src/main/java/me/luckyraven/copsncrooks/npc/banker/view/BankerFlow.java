package me.luckyraven.copsncrooks.npc.banker.view;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.inventory.flow.MultiPanelInventory;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Entry point for the banker NPC flow. Builds a fresh {@link MultiPanelInventory} per-viewer with the banker menu
 * registered as the single in-flow panel; the five existing subviews (amount, create, upgrade, rename, claim) stay as
 * standalone legacy views reached via {@link MultiPanelInventory#end()} hand-offs inside the menu panel.
 *
 * <p>The {@link #startFromPhone(Player)} overload is the link from {@code phone_banking.yml} — no physical banker is
 * present, so {@code BankerFlowSession#banker} is {@code null} and display strings fall back to "Online Banking".
 */
@RequiredArgsConstructor
public final class BankerFlow {

	private final JavaPlugin     plugin;
	private final BankerMenuView menuPanel;

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
		host.openAt(BankerFlowSession.PANEL_MENU);
	}

}
