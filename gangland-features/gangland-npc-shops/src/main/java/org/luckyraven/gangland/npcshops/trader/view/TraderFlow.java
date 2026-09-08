package org.luckyraven.gangland.copsncrooks.npc.trader.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderNpc;
import org.luckyraven.gangland.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.shop.ShopDefinition;

/**
 * Entry point for the trader NPC flow. Builds a fresh {@link MultiPanelInventory} per-viewer, registers every trader
 * panel (mode select, shop, negotiation, sell, barter, quantity), and opens at the mode-select panel. Every in-flow
 * transition is a {@link MultiPanelInventory#switchTo(String)} — the framework re-renders into the same inventory
 * handle when size + title match, or rebuilds otherwise without losing the session.
 */
@RequiredArgsConstructor
public final class TraderFlow {

	private final JavaPlugin           plugin;
	private final ModeSelectView       modeSelectPanel;
	private final ShopView             shopPanel;
	private final NegotiationView      negotiationPanel;
	private final SellView             sellPanel;
	private final BarterView           barterPanel;
	private final QuantitySelectorView quantityPanel;

	public void start(Player viewer, TraderNpc trader, ShopDefinition definition, TraderTraitDefinition trait) {
		TraderFlowSession                      session = new TraderFlowSession(trader, definition, trait);
		MultiPanelInventory<TraderFlowSession> host    = new MultiPanelInventory<>(plugin, viewer, session);
		host.register(TraderFlowSession.PANEL_MODE_SELECT, modeSelectPanel);
		host.register(TraderFlowSession.PANEL_SHOP, shopPanel);
		host.register(TraderFlowSession.PANEL_NEGOTIATION, negotiationPanel);
		host.register(TraderFlowSession.PANEL_SELL, sellPanel);
		host.register(TraderFlowSession.PANEL_BARTER, barterPanel);
		host.register(TraderFlowSession.PANEL_QUANTITY, quantityPanel);
		host.openAt(TraderFlowSession.PANEL_MODE_SELECT);
	}

}
