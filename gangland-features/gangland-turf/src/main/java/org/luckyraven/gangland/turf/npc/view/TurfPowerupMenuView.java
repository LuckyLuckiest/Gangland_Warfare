package org.luckyraven.gangland.turf.npc.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;

import java.util.ArrayList;
import java.util.List;

import static org.luckyraven.gangland.turf.npc.view.QuartermasterAccess.money;

/**
 * Root panel of the Quartermaster flow. Shows turf-state at a glance (owner, garrison stock, active buffs, gang bank
 * balance) and offers two navigation buttons that switch to the buff catalogue or garrison sub-panels. Empty slots are
 * padded with the configured global inventory-fill item (matches BankerMenuView style). Every line comes from the
 * turf module's {@code Quartermaster:} section (W2-Q).
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
	private final TurfMessages      messages;
	private final String            fillItem;
	private final String            fillName;

	@Override
	public int rows(TurfPowerupFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TurfPowerupFlowSession session) {
		return messages.text("Quartermaster.Title_Menu", "&8&l[&6&l%npc%&8&l]", "npc", session.getNpcDisplayName());
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

		ItemBuilder close = new ItemBuilder(Material.BARRIER)
				.setDisplayName(messages.text("Quartermaster.Close", "&cClose"));
		builder.slot(SLOT_CLOSE, ItemComponent.of(close).onAnyClick(ctx -> flow.end()));

		builder.fill(FillComponent.of(materialOf(fillItem)).name(fillName));
	}

	private ItemBuilder infoItem(Turf turf, Gang ownerGang, Gang viewerGang) {
		List<String> lore = new ArrayList<>();
		lore.add(messages.text("Quartermaster.Menu_Owner", "&7Owning gang: &e%gang%", "gang", ownerGang.getName()));
		if (!ownerGang.equals(viewerGang)) {
			lore.add(messages.text("Quartermaster.Menu_Allied", "&7You: &b%gang% &8(allied)",
			                       "gang", viewerGang.getName()));
		}
		lore.add(messages.text("Quartermaster.Menu_Stock", "&7Garrison stock: &f%count%",
		                       "count", garrisons.count(turf.getId())));
		lore.add(messages.text("Quartermaster.Menu_Active", "&7Active buffs: &f%count%",
		                       "count", buffs.active(turf.getId()).size()));
		lore.add(" ");
		lore.add(messages.text("Quartermaster.Menu_Bank", "&7Your gang bank: &a%money%",
		                       "money", money(viewerGang.getEconomy().getAmount())));
		return new ItemBuilder(Material.PAPER)
				.setDisplayName(messages.text("Quartermaster.Menu_Turf", "&6&l%turf%", "turf", turf.getDisplayName()))
				.setLore(lore);
	}

	private ItemBuilder buffsButton(Turf turf) {
		return new ItemBuilder(Material.POTION)
				.setDisplayName(messages.text("Quartermaster.Menu_Buffs_Name", "&b&lTimed Buffs"))
				.setLore(messages.text("Quartermaster.Menu_Buffs_Lore_1", "&7Browse the powerup catalogue and"),
				         messages.text("Quartermaster.Menu_Buffs_Lore_2", "&7activate income / defense / garrison buffs."),
				         messages.text("Quartermaster.Menu_Buffs_Active", "&7Currently active: &f%count%",
				                       "count", buffs.active(turf.getId()).size()));
	}

	private ItemBuilder garrisonButton(Turf turf) {
		return new ItemBuilder(Material.IRON_SWORD)
				.setDisplayName(messages.text("Quartermaster.Menu_Garrison_Name", "&c&lGarrison"))
				.setLore(messages.text("Quartermaster.Menu_Garrison_Lore_1", "&7View / buy defender stock."),
				         messages.text("Quartermaster.Menu_Garrison_Lore_2", "&7These civilian defenders auto-spawn"),
				         messages.text("Quartermaster.Menu_Garrison_Lore_3", "&7when an enemy attacks the turf."),
				         messages.text("Quartermaster.Menu_Garrison_Stock", "&7Current stock: &f%count%",
				                       "count", garrisons.count(turf.getId())));
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}
}
