package org.luckyraven.gangland.npcshops.trader.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.npcshops.trader.TraderNpc;
import org.luckyraven.gangland.npcshops.trader.trait.TraderTraitDefinition;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.shop.ShopDefinition;

/**
 * Entry point for the trader NPC flow. Builds a fresh {@link MenuFlow} per-viewer, registers every trader panel
 * (mode select, shop, negotiation, sell, barter, quantity), and opens at the mode-select panel. Every in-flow
 * transition is a {@link MenuFlow#switchTo(String)} — the framework re-renders into the same inventory handle when
 * size + title match, or rebuilds otherwise without losing the session.
 */
@RequiredArgsConstructor
public final class TraderFlow {

	private final JavaPlugin           plugin;
	private final InventoryService     inventoryService;
	private final ModeSelectView       modeSelectPanel;
	private final ShopView             shopPanel;
	private final NegotiationView      negotiationPanel;
	private final SellView             sellPanel;
	private final BarterView           barterPanel;
	private final QuantitySelectorView quantityPanel;

	public void start(Player viewer, TraderNpc trader, ShopDefinition definition, TraderTraitDefinition trait) {
		TraderFlowSession session = new TraderFlowSession(trader, definition, trait);
		MenuFlow<TraderFlowSession> flow = MenuFlow.builder(inventoryService, plugin, viewer, session)
		                                           .panel(TraderFlowSession.PANEL_MODE_SELECT, modeSelectPanel)
		                                           .panel(TraderFlowSession.PANEL_SHOP, shopPanel)
		                                           .panel(TraderFlowSession.PANEL_NEGOTIATION, negotiationPanel)
		                                           .panel(TraderFlowSession.PANEL_SELL, sellPanel)
		                                           .panel(TraderFlowSession.PANEL_BARTER, barterPanel)
		                                           .panel(TraderFlowSession.PANEL_QUANTITY, quantityPanel)
		                                           // Unconditional — a no-op on whichever of sell/barter was never
		                                           // entered this session (WS2 G4 §0d, see BarterView/SellView doc).
		                                           .onEnd(s -> {
			                                           sellPanel.onFlowEnd(viewer);
			                                           barterPanel.onFlowEnd(viewer);
		                                           })
		                                           .build();
		flow.openAt(TraderFlowSession.PANEL_MODE_SELECT);
	}

}
