package org.luckyraven.gangland.turf.npc.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.keystone.inventory.InventoryService;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Entry point for the Quartermaster panel chain. Registers the three panels (root menu / buff catalogue / garrison) on
 * a fresh {@link MenuFlow} per viewer and opens the menu. Click handlers in each panel navigate via
 * {@link MenuFlow#switchTo(String)} / {@link MenuFlow#back()}.
 */
@RequiredArgsConstructor
public final class TurfPowerupFlow {

	private final JavaPlugin                   plugin;
	private final InventoryService             inventoryService;
	private final TurfPowerupMenuView          menuView;
	private final TurfPowerupBuffCatalogueView buffsView;
	private final TurfPowerupGarrisonView      garrisonView;

	public void start(Player viewer, Turf turf, Gang ownerGang, Gang viewerGang, String npcDisplayName) {
		TurfPowerupFlowSession session = new TurfPowerupFlowSession(turf, ownerGang, viewerGang, npcDisplayName);
		MenuFlow<TurfPowerupFlowSession> flow = MenuFlow.builder(inventoryService, plugin, viewer, session)
		                                                .panel(TurfPowerupFlowSession.PANEL_MENU, menuView)
		                                                .panel(TurfPowerupFlowSession.PANEL_BUFFS, buffsView)
		                                                .panel(TurfPowerupFlowSession.PANEL_GARRISON, garrisonView)
		                                                .build();
		flow.openAt(TurfPowerupFlowSession.PANEL_MENU);
	}
}
