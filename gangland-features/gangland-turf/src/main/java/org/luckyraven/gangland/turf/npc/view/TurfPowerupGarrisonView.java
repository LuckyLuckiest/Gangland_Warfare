package org.luckyraven.gangland.turf.npc.view;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderConfig;
import org.luckyraven.gangland.turf.npc.defender.TurfGarrison;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.GuardBuffs;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

import static org.luckyraven.gangland.turf.npc.view.QuartermasterAccess.money;
import static org.luckyraven.gangland.turf.npc.view.QuartermasterAccess.refusal;

/**
 * Garrison sub-panel: the guards the turf owns and the price of one more guard, paid from the gang bank. The guard holds
 * the capture bar and fights attackers. Every purchase runs {@link QuartermasterAccess#denyKey} first (TF-19, TF-45),
 * then affordability, then the owned-guards cap ({@code turf_rules.yml} Garrison.Max_Reserve), then withdraws from the
 * owner gang. The price is {@code turf_powerups.yml} Garrison.Cost_Per_Guard times any active garrison_discount (TF-17,
 * TF-02).
 * The info item shows guards owned against Garrison.Max_Reserve, on duty, in reserve and the contest top-up size; a
 * top-up spends guards already paid for, never money.
 */
@CustomLog
@RequiredArgsConstructor
public final class TurfPowerupGarrisonView implements Panel<TurfPowerupFlowSession> {

	private static final int ROWS       = 5;
	private static final int SLOT_INFO  = 20;
	private static final int SLOT_BUY   = 24;
	private static final int SLOT_BACK  = 36;
	private static final int SLOT_CLOSE = 44;

	private final GarrisonManager      garrisons;
	private final PowerupRegistry      registry;
	private final ActiveBuffManager    buffs;
	private final QuartermasterAccess  access;
	private final TurfRules            rules;
	private final IntUnaryOperator     aliveGuards;
	private final IntFunction<TurfDefenderConfig> waves;
	private final TurfMessages         messages;
	private final Supplier<MenuStyle> style;

	@Override
	public int rows(TurfPowerupFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TurfPowerupFlowSession session) {
		return messages.text("Quartermaster.Title_Garrison", "&5Garrison", "npc", session.getNpcDisplayName());
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

		style.get().decorate(builder);
	}

	/**
	 * The price of one guard on {@code turfId} right now: the YAML price times any active garrison_discount, kept between
	 * 10 % and 100 % of Cost_Per_Guard and never below 1 (T-275).
	 */
	BigDecimal price(int turfId) {
		return GuardBuffs.guardPrice(buffs.effectiveMultiplier(turfId, EffectType.GARRISON_DISCOUNT),
		                             registry.garrisonCost());
	}

	/** The info lore in order: Owned, On duty, In reserve, Contest top-up, price, bank, then the where lines (W7-H). */
	List<String> infoLore(Turf turf, Gang gang) {
		int turfId = turf.getId();
		int stock  = garrisons.count(turfId);
		int alive  = aliveGuards.applyAsInt(turfId);

		List<String> lore = new ArrayList<>();
		lore.add(messages.text("Quartermaster.Garrison_Owned", "&7Owned: &f%count%&7/&f%max%",
		                       "count", stock, "max", rules.getMaxReserve()));
		lore.add(messages.text("Quartermaster.Garrison_On_Duty", "&7On duty: &f%count%", "count", alive));
		lore.add(messages.text("Quartermaster.Garrison_In_Reserve", "&7In reserve: &f%count%",
		                       "count", Math.max(0, stock - alive)));
		lore.add(messages.text("Quartermaster.Garrison_Next_Wave",
		                       "&7Contest top-up: &f+%count%&7, no extra cost",
		                       "count", nextWave(stock, alive, waves.apply(turfId))));
		lore.add(messages.text("Quartermaster.Garrison_Per_Guard", "&7Per defender: &6%money%",
		                       "money", money(price(turfId))));
		lore.add(" ");
		lore.add(messages.text("Quartermaster.Garrison_Bank", "&7Gang bank: &a%money%",
		                       "money", money(gang.getEconomy().getAmount())));
		lore.add(" ");
		lore.add(messages.text("Quartermaster.Garrison_Where_One", "&7Guards stand on the turf while the"));
		lore.add(messages.text("Quartermaster.Garrison_Where_Two", "&7area is loaded. The rest wait in reserve."));
		return lore;
	}

	/**
	 * Guards the next contest top-up sends: at most Wave_Size, the reserve (owned minus those standing) and Max_Alive
	 * minus those on duty (no cooldown).
	 */
	static int nextWave(int stock, int alive, TurfDefenderConfig config) {
		return TurfGarrison.toSpawn(stock - alive, alive, config.maxAlive(), config.waveSize(),
		                            config.waveSize(), -1L, 0L, 0L);
	}

	private ItemBuilder infoItem(Turf turf, Gang gang) {
		return new ItemBuilder(Material.ARMOR_STAND)
				.setDisplayName(messages.text("Quartermaster.Garrison_Name", "&c&lGarrison"))
				.setLore(infoLore(turf, gang));
	}

	private ItemBuilder buyItem(TurfPowerupFlowSession session) {
		BigDecimal price     = price(session.getTurf().getId());
		boolean    canAfford = session.getOwnerGang().getEconomy().getAmount().compareTo(price) >= 0;
		List<String> lore = new ArrayList<>();
		lore.add(messages.text("Quartermaster.Garrison_Buy_Cost", "&7Cost: &6%money%", "money", money(price)));
		lore.add(" ");
		lore.add(buyLine(session, canAfford));
		return new ItemBuilder(Material.EMERALD)
				.setDisplayName(messages.text("Quartermaster.Garrison_Buy_Name", "&aBuy &f1 &aDefender"))
				.setLore(lore);
	}

	/** The buy item's action line: a turf at the owned cap says so for the owner (T-823); allies keep the look-only line. */
	String buyLine(TurfPowerupFlowSession session, boolean canAfford) {
		Turf turf = session.getTurf();
		boolean full = garrisons.count(turf.getId()) >= rules.getMaxReserve();
		if (full && !QuartermasterAccess.isAllyView(session)) {
			return messages.text("Quartermaster.Garrison_Buy_Full", "&cThis turf already owns &f%max% &cguards.",
			                     "max", rules.getMaxReserve());
		}
		return clickLine(session, canAfford);
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
			QuartermasterAccess.refuse(flow, viewer, messages, deny);
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

		// The count is guards owned, standing or in reserve; a body in the field is not a second guard.
		if (garrisons.count(turfId) >= rules.getMaxReserve()) {
			viewer.sendMessage(messages.text("Quartermaster.Owned_Full", "&cThis turf already has %max% guards.",
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
		boolean standing = garrisons.count(turfId) <= TurfGarrison.target(Integer.MAX_VALUE, false, waves.apply(turfId));
		viewer.sendMessage(standing
		                   ? messages.text("Quartermaster.Bought_Guard_Post",
		                                   "&7It takes its post on &f%turf% &7shortly while the area is loaded.",
		                                   "turf", turf.getDisplayName())
		                   : messages.text("Quartermaster.Bought_Guard_Reserve",
		                                   "&7It waits in the reserve and joins when &f%turf% &7is attacked.",
		                                   "turf", turf.getDisplayName()));
		flow.rerender();
	}

	private String cannotAfford(BigDecimal price) {
		return messages.text("Quartermaster.Cannot_Afford", "&cYour gang bank can't cover &f%money%&c.",
		                     "money", money(price));
	}
}
