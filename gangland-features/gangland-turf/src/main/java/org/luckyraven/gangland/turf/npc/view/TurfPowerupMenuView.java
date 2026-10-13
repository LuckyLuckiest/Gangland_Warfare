package org.luckyraven.gangland.turf.npc.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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
import java.util.function.IntUnaryOperator;

import static org.luckyraven.gangland.turf.npc.view.QuartermasterAccess.money;

/**
 * Root panel of the Quartermaster flow. Shows turf-state at a glance (owner, garrison stock, active buffs, gang bank
 * balance) and offers two navigation buttons that switch to the buff catalogue or garrison sub-panels. Empty slots are
 * padded with the configured global inventory-fill item (matches BankerMenuView style). Every line comes from the
 * turf module's {@code Quartermaster:} section (W2-Q).
 */
@CustomLog
@RequiredArgsConstructor
public final class TurfPowerupMenuView implements Panel<TurfPowerupFlowSession> {

	private static final int ROWS         = 3;
	private static final int SLOT_INFO     = 4;
	private static final int SLOT_BUFFS    = 11;
	private static final int SLOT_GARRISON = 15;
	private static final int SLOT_ORDERS   = 13;
	private static final int SLOT_CLOSE    = 22;

	private final GarrisonManager   garrisons;
	private final ActiveBuffManager buffs;
	private final IntUnaryOperator  aliveGuards;
	private final QuartermasterAccess access;
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
		builder.slot(SLOT_ORDERS, ItemComponent.of(ordersButton(session))
		                                      .onAnyClick(ctx -> toggleOrder(flow, ctx.player(), session)));

		ItemBuilder close = new ItemBuilder(Material.BARRIER)
				.setDisplayName(messages.text("Quartermaster.Close", "&cClose"));
		builder.slot(SLOT_CLOSE, ItemComponent.of(close).onAnyClick(ctx -> flow.end()));

		builder.fill(FillComponent.of(materialOf(fillItem)).name(fillName));
	}

	private ItemBuilder infoItem(Turf turf, Gang ownerGang, Gang viewerGang) {
		return new ItemBuilder(Material.PAPER)
				.setDisplayName(messages.text("Quartermaster.Menu_Turf", "&6&l%turf%", "turf", turf.getDisplayName()))
				.setLore(infoLore(turf, ownerGang, viewerGang));
	}

	List<String> infoLore(Turf turf, Gang ownerGang, Gang viewerGang) {
		List<String> lore = new ArrayList<>();
		lore.add(messages.text("Quartermaster.Menu_Owner", "&7Owning gang: &e%gang%", "gang", ownerGang.getName()));
		if (!ownerGang.equals(viewerGang)) {
			lore.add(messages.text("Quartermaster.Menu_Allied", "&7You: &b%gang% &8(allied)",
			                       "gang", viewerGang.getName()));
		}
		lore.add(messages.text("Quartermaster.Menu_Stock", "&7Garrison stock: &f%count%",
		                       "count", garrisons.count(turf.getId())));
		lore.add(messages.text("Quartermaster.Menu_On_Duty", "&7Guards on duty: &f%count%",
		                       "count", aliveGuards.applyAsInt(turf.getId())));
		lore.add(messages.text("Quartermaster.Menu_Order", "&7Guard order: %order%",
		                       "order", orderWord(garrisons.holding(turf.getId()))));
		lore.add(messages.text("Quartermaster.Menu_Active", "&7Active buffs: &f%count%",
		                       "count", buffs.active(turf.getId()).size()));
		lore.add(" ");
		lore.add(messages.text("Quartermaster.Menu_Bank", "&7Your gang bank: &a%money%",
		                       "money", money(viewerGang.getEconomy().getAmount())));
		if (ownerGang.equals(viewerGang)) {
			lore.add(messages.text("Quartermaster.Menu_Spend_Owner", "&aYour gang buys here."));
		} else {
			lore.add(messages.text("Quartermaster.Menu_Spend_Ally", "&7Only &e%gang% &7buys here; allies can look.",
			                       "gang", ownerGang.getName()));
		}
		return lore;
	}

	/** The coloured Hold / Defend word shown in the hub and the chat line. */
	private String orderWord(boolean hold) {
		return hold ? messages.text("Quartermaster.Order_Hold", "&eHold")
		            : messages.text("Quartermaster.Order_Defend", "&cDefend");
	}

	private ItemBuilder ordersButton(TurfPowerupFlowSession session) {
		boolean hold = garrisons.holding(session.getTurf().getId());
		String click = QuartermasterAccess.isAllyView(session)
		               ? QuartermasterAccess.refusal(messages, QuartermasterAccess.ALLIES_LOOK)
		               : messages.text("Quartermaster.Order_Click", "&aClick to switch to %order%&a.",
		                               "order", orderWord(!hold));
		return new ItemBuilder(hold ? Material.SHIELD : Material.IRON_SWORD)
				.setDisplayName(messages.text("Quartermaster.Order_Name", "&e&lGuard Orders"))
				.setLore(messages.text("Quartermaster.Order_Lore_Hold",
				                       "&7Hold: guards stay by their posts and leave cops alone."),
				         messages.text("Quartermaster.Order_Lore_Defend",
				                       "&7Defend: guards chase attackers and turn on cops."),
				         " ", click);
	}

	/** One order flip, re-checked on this click like a purchase (D25=B: only the owning gang with the rank). */
	void toggleOrder(MenuFlow<TurfPowerupFlowSession> flow, Player viewer, TurfPowerupFlowSession session) {
		String deny = access.denyKey(viewer, session);
		if (deny != null) {
			viewer.sendMessage(QuartermasterAccess.refusal(messages, deny));
			if (QuartermasterAccess.closesPanel(deny)) {
				flow.end();
			} else {
				flow.rerender();
			}
			return;
		}
		Turf    turf = session.getTurf();
		boolean hold = !garrisons.holding(turf.getId());
		garrisons.setHolding(turf.getId(), hold);
		log.info("Quartermaster: {} set guard order {} on turf {} ({})", viewer.getName(), hold ? "Hold" : "Defend",
		         turf.getId(), turf.getDisplayName());
		viewer.sendMessage(messages.text("Quartermaster.Order_Set", "&aGuards on &f%turf%&a now: %order%",
		                                 "turf", turf.getDisplayName(), "order", orderWord(hold)));
		flow.rerender();
	}

	private ItemBuilder buffsButton(Turf turf) {
		return new ItemBuilder(Material.POTION)
				.setDisplayName(messages.text("Quartermaster.Menu_Buffs_Name", "&b&lTimed Buffs"))
				.setLore(messages.text("Quartermaster.Menu_Buffs_Lore_1", "&7Browse the powerup catalogue and"),
				         messages.text("Quartermaster.Menu_Buffs_Lore_2", "&7buy timed boosts on this turf."),
				         messages.text("Quartermaster.Menu_Buffs_Active", "&7Currently active: &f%count%",
				                       "count", buffs.active(turf.getId()).size()));
	}

	private ItemBuilder garrisonButton(Turf turf) {
		return new ItemBuilder(Material.IRON_SWORD)
				.setDisplayName(messages.text("Quartermaster.Menu_Garrison_Name", "&c&lGarrison"))
				.setLore(messages.text("Quartermaster.Menu_Garrison_Lore_1", "&7View / buy defender stock."),
				         messages.text("Quartermaster.Menu_Garrison_Lore_2", "&7Guards hold the capture bar"),
				         messages.text("Quartermaster.Menu_Garrison_Lore_3", "&7and fight attackers on this turf."),
				         messages.text("Quartermaster.Menu_Garrison_Stock", "&7Current stock: &f%count%",
				                       "count", garrisons.count(turf.getId())));
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}
}
