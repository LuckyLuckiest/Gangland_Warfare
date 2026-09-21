package org.luckyraven.gangland.turf.npc.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Root panel of the Quartermaster flow. Shows turf-state at a glance (owner, garrison stock, active buffs, gang bank
 * balance) and offers two navigation buttons that switch to the buff catalogue or garrison sub-panels. Empty slots are
 * padded with the configured global inventory-fill item (matches BankerMenuView style).
 */
@RequiredArgsConstructor
public final class TurfPowerupMenuView implements Panel<TurfPowerupFlowSession> {

	private static final int ROWS         = 3;
	private static final int SLOT_INFO     = 4;
	private static final int SLOT_BUFFS    = 11;
	private static final int SLOT_GARRISON = 15;
	private static final int SLOT_CLOSE    = 22;

	private final GarrisonManager   garrisons;
	private final ActiveBuffManager buffs;
	private final String            fillItem;
	private final String            fillName;

	@Override
	public int rows(TurfPowerupFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TurfPowerupFlowSession session) {
		return "&8&l[&6&l" + session.getNpcDisplayName() + "&8&l]";
	}

	@Override
	public void render(MenuFlow<TurfPowerupFlowSession> flow, ChestMenuBuilder builder, TurfPowerupFlowSession session) {
		Turf turf       = session.getTurf();
		Gang ownerGang  = session.getOwnerGang();
		Gang viewerGang = session.getViewerGang();

		builder.slot(SLOT_INFO, ItemComponent.of(infoItem(turf, ownerGang, viewerGang)));
		builder.slot(SLOT_BUFFS, ItemComponent.of(buffsButton(turf))
		                                     .onAnyClick(ctx -> flow.switchTo(TurfPowerupFlowSession.PANEL_BUFFS)));
		builder.slot(SLOT_GARRISON, ItemComponent.of(garrisonButton(turf))
		                                        .onAnyClick(ctx -> flow.switchTo(TurfPowerupFlowSession.PANEL_GARRISON)));

		ItemBuilder close = new ItemBuilder(Material.BARRIER).setDisplayName("&cClose");
		builder.slot(SLOT_CLOSE, ItemComponent.of(close).onAnyClick(ctx -> flow.end()));

		builder.fill(FillComponent.of(materialOf(fillItem)).name(fillName));
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

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}
}
