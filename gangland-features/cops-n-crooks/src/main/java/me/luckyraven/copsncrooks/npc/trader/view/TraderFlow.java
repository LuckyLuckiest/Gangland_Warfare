package me.luckyraven.copsncrooks.npc.trader.view;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.inventory.flow.MultiPanelInventory;
import me.luckyraven.shop.ShopDefinition;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the trader NPC flow. Builds a fresh {@link MultiPanelInventory} per-viewer, registers the three
 * in-flow panels ({@link ModeSelectView}, {@link ShopView}, {@link NegotiationView}), and opens at the mode-select
 * panel. Sell + barter + quantity-selector continue to live as standalone legacy views that the negotiation panel
 * bridges into via {@link MultiPanelInventory#suspend()} / {@link MultiPanelInventory#resume()}.
 */
@RequiredArgsConstructor
public final class TraderFlow {

	private final JavaPlugin      plugin;
	private final ModeSelectView  modeSelectPanel;
	private final ShopView        shopPanel;
	private final NegotiationView negotiationPanel;

	public void start(Player viewer, TraderNpc trader, ShopDefinition definition, TraderTraitDefinition trait) {
		TraderFlowSession                      session = new TraderFlowSession(trader, definition, trait);
		MultiPanelInventory<TraderFlowSession> host    = new MultiPanelInventory<>(plugin, viewer, session);
		host.register(TraderFlowSession.PANEL_MODE_SELECT, modeSelectPanel);
		host.register(TraderFlowSession.PANEL_SHOP, shopPanel);
		host.register(TraderFlowSession.PANEL_NEGOTIATION, negotiationPanel);
		host.openAt(TraderFlowSession.PANEL_MODE_SELECT);
	}

}
