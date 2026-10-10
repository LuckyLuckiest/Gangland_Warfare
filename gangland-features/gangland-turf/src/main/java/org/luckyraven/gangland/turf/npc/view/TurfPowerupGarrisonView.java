package org.luckyraven.gangland.turf.npc.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

import static org.luckyraven.gangland.turf.npc.view.QuartermasterAccess.money;
import static org.luckyraven.gangland.turf.npc.view.QuartermasterAccess.refusal;

/**
 * Garrison sub-panel: the turf's guard stock and the price of one more guard, paid from the gang bank. The guard holds
 * the capture bar and fights attackers. Every purchase runs {@link QuartermasterAccess#denyKey} first (TF-19, TF-45),
 * then affordability, then the reserve cap ({@code turf_rules.yml} Garrison.Max_Reserve), then withdraws from the owner gang. The price is
 * {@code turf_powerups.yml} Garrison.Cost_Per_Guard times any active garrison_discount (TF-17, TF-02).
 */
@CustomLog
@RequiredArgsConstructor
public final class TurfPowerupGarrisonView implements Panel<TurfPowerupFlowSession> {

	private static final int ROWS       = 3;
	private static final int SLOT_INFO  = 4;
	private static final int SLOT_BUY   = 13;
	private static final int SLOT_BACK  = 18;
	private static final int SLOT_CLOSE = 26;

	private final GarrisonManager      garrisons;
	private final PowerupRegistry      registry;
	private final ActiveBuffManager    buffs;
	private final QuartermasterAccess  access;
	private final TurfRules            rules;
	private final IntUnaryOperator     aliveGuards;
	private final TurfMessages         messages;
	private final String               fillItem;
	private final String               fillName;

	@Override
	public int rows(TurfPowerupFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TurfPowerupFlowSession session) {
		return messages.text("Quartermaster.Title_Garrison", "&8&l[&c&lGarrison &8— &6%npc%&8&l]",
		                     "npc", session.getNpcDisplayName());
	}

	@Override
	public void render(MenuFlow<TurfPowerupFlowSession> flow, ChestMenuBuilder builder, TurfPowerupFlowSession session) {
		Turf turf = session.getTurf();
		Gang gang = session.getOwnerGang();

		builder.slot(SLOT_INFO, ItemComponent.of(infoItem(turf, gang)));
		builder.slot(SLOT_BUY, ItemComponent.of(buyItem(session))
		                                   .onAnyClick(ctx -> attemptBuy(flow, ctx.player(), session)));

		ItemBuilder back = new ItemBuilder(Material.ARROW)
				.setDisplayName(messages.text("Quartermaster.Back", "&7← Back"));
		builder.slot(SLOT_BACK, ItemComponent.of(back).onAnyClick(ctx -> flow.back()));

		ItemBuilder close = new ItemBuilder(Material.BARRIER)
				.setDisplayName(messages.text("Quartermaster.Close", "&cClose"));
		builder.slot(SLOT_CLOSE, ItemComponent.of(close).onAnyClick(ctx -> flow.end()));

		builder.fill(FillComponent.of(materialOf(fillItem)).name(fillName));
	}

	/** The price of one guard on {@code turfId} right now: the YAML price times any active garrison_discount. */
	BigDecimal price(int turfId) {
		double multiplier = buffs.effectiveMultiplier(turfId, EffectType.GARRISON_DISCOUNT);
		return registry.garrisonCost().multiply(BigDecimal.valueOf(multiplier)).setScale(0, RoundingMode.HALF_UP);
	}

	private ItemBuilder infoItem(Turf turf, Gang gang) {
		List<String> lore = new ArrayList<>();
		lore.add(messages.text("Quartermaster.Garrison_Stock", "&7Stock on &f%turf%&7: &f%count%",
		                       "turf", turf.getDisplayName(), "count", garrisons.count(turf.getId())));
		lore.add(messages.text("Quartermaster.Garrison_Per_Guard", "&7Per defender: &6%money%",
		                       "money", money(price(turf.getId()))));
		lore.add(" ");
		lore.add(messages.text("Quartermaster.Garrison_Bank", "&7Gang bank: &a%money%",
		                       "money", money(gang.getEconomy().getAmount())));
		return new ItemBuilder(Material.IRON_HELMET)
				.setDisplayName(messages.text("Quartermaster.Garrison_Name", "&c&lGarrison"))
				.setLore(lore);
	}

	private ItemBuilder buyItem(TurfPowerupFlowSession session) {
		BigDecimal price     = price(session.getTurf().getId());
		boolean    canAfford = session.getOwnerGang().getEconomy().getAmount().compareTo(price) >= 0;
		List<String> lore = new ArrayList<>();
		lore.add(messages.text("Quartermaster.Garrison_Buy_Cost", "&7Cost: &6%money%", "money", money(price)));
		lore.add(" ");
		lore.add(clickLine(session, canAfford));
		return new ItemBuilder(Material.IRON_SWORD)
				.setDisplayName(messages.text("Quartermaster.Garrison_Buy_Name", "&aBuy &f1 &aDefender"))
				.setLore(lore);
	}

	/** The buy action line: allies get the look-only refusal, the owner gets click or insufficient. */
	String clickLine(TurfPowerupFlowSession session, boolean canAfford) {
		if (QuartermasterAccess.isAllyView(session)) {
			return refusal(messages, QuartermasterAccess.ALLIES_LOOK);
		}
		return canAfford
		       ? messages.text("Quartermaster.Garrison_Buy_Click", "&aClick to add &f1 &ato the garrison.")
		       : messages.text("Quartermaster.Garrison_Buy_Insufficient", "&cInsufficient gang funds.");
	}

	/** One guard purchase, re-checked on this click (TF-19). Package-private for the test seam. */
	void attemptBuy(MenuFlow<TurfPowerupFlowSession> flow, Player viewer, TurfPowerupFlowSession session) {
		String deny = access.denyKey(viewer, session);
		if (deny != null) {
			refuse(flow, viewer, deny);
			return;
		}

		Turf       turf   = session.getTurf();
		int        turfId = turf.getId();
		Gang       gang   = session.getOwnerGang();
		BigDecimal price  = price(turfId);

		if (gang.getEconomy().getAmount().compareTo(price) < 0) {
			viewer.sendMessage(cannotAfford(price));
			flow.rerender();
			return;
		}

		// r2: guards still in the field count against the reserve, so a stock of 0 cannot be rebought past the cap.
		if (garrisons.count(turfId) + aliveGuards.applyAsInt(turfId) >= rules.getMaxReserve()) {
			viewer.sendMessage(messages.text("Quartermaster.Reserve_Full", "&cThis turf already holds %max% guards.",
			                                 "max", rules.getMaxReserve()));
			flow.rerender();
			return;
		}
		try {
			gang.getEconomy().withdrawAmount(price);
		} catch (EconomyException exception) {
			viewer.sendMessage(cannotAfford(price));
			flow.rerender();
			return;
		}

		garrisons.add(turfId, 1);
		log.info("Quartermaster: {} ({}) bought {} for {} on turf {} ({})",
		         viewer.getName(), gang.getName(), "a guard", money(price), turfId, turf.getDisplayName());
		viewer.sendMessage(messages.text("Quartermaster.Bought_Guard", "&aBought &f1 &adefender for &f%turf%&a.",
		                                 "turf", turf.getDisplayName()));
		flow.rerender();
	}

	private void refuse(MenuFlow<TurfPowerupFlowSession> flow, Player viewer, String key) {
		viewer.sendMessage(refusal(messages, key));
		if (QuartermasterAccess.closesPanel(key)) {
			flow.end();
		} else {
			flow.rerender();
		}
	}

	private String cannotAfford(BigDecimal price) {
		return messages.text("Quartermaster.Cannot_Afford", "&cYour gang bank can't cover &f%money%&c.",
		                     "money", money(price));
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}
}
