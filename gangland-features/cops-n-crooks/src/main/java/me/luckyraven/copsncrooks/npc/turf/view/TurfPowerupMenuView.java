package me.luckyraven.copsncrooks.npc.turf.view;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.gang.Gang;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.flow.MultiPanelInventory;
import me.luckyraven.inventory.flow.Panel;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.powerups.ActiveBuffManager;
import me.luckyraven.turf.powerups.GarrisonManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Root panel of the Quartermaster flow. Shows turf-state at a glance (owner, garrison stock, active buffs, gang bank
 * balance) and offers two navigation buttons that switch to the buff catalogue or garrison sub-panels. Empty slots are
 * padded with the configured global inventory-fill item (matches BankerMenuView style).
 */
@RequiredArgsConstructor
public final class TurfPowerupMenuView implements Panel<TurfPowerupFlowSession> {

	private static final int SIZE          = 27;
	private static final int SLOT_INFO     = 4;
	private static final int SLOT_BUFFS    = 11;
	private static final int SLOT_GARRISON = 15;
	private static final int SLOT_CLOSE    = 22;

	private final GarrisonManager   garrisons;
	private final ActiveBuffManager buffs;
	private final String            fillItem;
	private final String            fillName;

	@Override
	public int size(TurfPowerupFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(TurfPowerupFlowSession session) {
		return "&8&l[&6&l" + session.getNpcDisplayName() + "&8&l]";
	}

	@Override
	public void render(MultiPanelInventory<TurfPowerupFlowSession> host, InventoryHandler handler, Player viewer,
	                   TurfPowerupFlowSession session) {
		Turf turf       = session.getTurf();
		Gang ownerGang  = session.getOwnerGang();
		Gang viewerGang = session.getViewerGang();

		handler.setItem(SLOT_INFO, infoItem(turf, ownerGang, viewerGang), false, (p, inv, b) -> { });
		handler.setItem(SLOT_BUFFS, buffsButton(turf), false,
		                (p, inv, b) -> host.switchTo(TurfPowerupFlowSession.PANEL_BUFFS));
		handler.setItem(SLOT_GARRISON, garrisonButton(turf), false,
		                (p, inv, b) -> host.switchTo(TurfPowerupFlowSession.PANEL_GARRISON));

		ItemBuilder close = new ItemBuilder(Material.BARRIER).setDisplayName("&cClose");
		handler.setItem(SLOT_CLOSE, close, false, (p, inv, b) -> host.end());

		InventoryUtil.fillInventory(handler, new Fill(fillName, fillItem));
	}

	private ItemBuilder infoItem(Turf turf, Gang ownerGang, Gang viewerGang) {
		List<String> lore = new ArrayList<>();
		lore.add("&7Owning gang: &e" + ownerGang.getName());
		if (!ownerGang.equals(viewerGang)) {
			lore.add("&7You: &b" + viewerGang.getName() + " &8(allied)");
		}
		lore.add("&7Garrison stock: &f" + garrisons.count(turf.getId()));
		lore.add("&7Active buffs: &f" + buffs.active(turf.getId()).size());
		lore.add(" ");
		lore.add("&7Your gang bank: &a$" + NumberUtil.valueFormat(viewerGang.getEconomy().getAmount()));
		return new ItemBuilder(Material.PAPER)
				.setDisplayName("&6&l" + turf.getDisplayName())
				.setLore(lore);
	}

	private ItemBuilder buffsButton(Turf turf) {
		return new ItemBuilder(Material.POTION)
				.setDisplayName("&b&lTimed Buffs")
				.setLore("&7Browse the powerup catalogue and",
				         "&7activate income / defense / garrison buffs.",
				         "&7Currently active: &f" + buffs.active(turf.getId()).size());
	}

	private ItemBuilder garrisonButton(Turf turf) {
		return new ItemBuilder(Material.IRON_SWORD)
				.setDisplayName("&c&lGarrison")
				.setLore("&7View / buy defender stock.",
				         "&7These civilian defenders auto-spawn",
				         "&7when an enemy attacks the turf.",
				         "&7Current stock: &f" + garrisons.count(turf.getId()));
	}
}
