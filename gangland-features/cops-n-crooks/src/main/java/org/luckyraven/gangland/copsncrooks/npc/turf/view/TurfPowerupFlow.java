package org.luckyraven.gangland.copsncrooks.npc.turf.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Entry point for the Quartermaster panel chain. Registers the three panels (root menu / buff catalogue / garrison) on
 * a fresh {@link MultiPanelInventory} per viewer and opens the menu. Click handlers in each panel navigate via
 * {@link MultiPanelInventory#switchTo(String)} / {@link MultiPanelInventory#back()}.
 */
@RequiredArgsConstructor
public final class TurfPowerupFlow {

	private final JavaPlugin                   plugin;
	private final TurfPowerupMenuView          menuView;
	private final TurfPowerupBuffCatalogueView buffsView;
	private final TurfPowerupGarrisonView      garrisonView;

	public void start(Player viewer, Turf turf, Gang ownerGang, Gang viewerGang, String npcDisplayName) {
		TurfPowerupFlowSession session = new TurfPowerupFlowSession(turf, ownerGang, viewerGang,
		                                                            npcDisplayName);
		MultiPanelInventory<TurfPowerupFlowSession> host = new MultiPanelInventory<>(plugin, viewer, session);
		host.register(TurfPowerupFlowSession.PANEL_MENU, menuView);
		host.register(TurfPowerupFlowSession.PANEL_BUFFS, buffsView);
		host.register(TurfPowerupFlowSession.PANEL_GARRISON, garrisonView);
		host.openAt(TurfPowerupFlowSession.PANEL_MENU);
	}
}
