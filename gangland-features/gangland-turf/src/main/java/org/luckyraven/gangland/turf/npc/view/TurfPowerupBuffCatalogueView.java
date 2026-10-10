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
import org.luckyraven.gangland.turf.powerups.PowerupDefinition;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.keystone.inventory.chest.ChestMenuBuilder;
import org.luckyraven.keystone.inventory.component.FillComponent;
import org.luckyraven.keystone.inventory.component.ItemComponent;
import org.luckyraven.keystone.inventory.flow.MenuFlow;
import org.luckyraven.keystone.inventory.flow.Panel;
import org.luckyraven.keystone.item.ItemBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.luckyraven.gangland.turf.npc.view.QuartermasterAccess.money;
import static org.luckyraven.gangland.turf.npc.view.QuartermasterAccess.refusal;

/**
 * Buff catalogue panel: one clickable potion per {@link PowerupDefinition}, nine to a page with previous and next arrows
 * when there is more than one page (TF-18). Click activates the buff after the shared re-check
 * ({@link QuartermasterAccess#denyKey}, TF-19) and debits the viewer's gang bank.
 */
@CustomLog
@RequiredArgsConstructor
public final class TurfPowerupBuffCatalogueView implements Panel<TurfPowerupFlowSession> {

	/** Boosts per page: the nine slots of rows two and three. */
	static final int PER_PAGE = 9;

	private static final int ROWS       = 4;
	private static final int BUFF_START = 9;
	private static final int SLOT_PREV  = 29;
	private static final int SLOT_INFO  = 31;
	private static final int SLOT_NEXT  = 33;
	private static final int SLOT_BACK  = 27;
	private static final int SLOT_CLOSE = 35;

	private final PowerupRegistry     registry;
	private final ActiveBuffManager   buffs;
	private final QuartermasterAccess access;
	private final TurfMessages        messages;
	private final String              fillItem;
	private final String              fillName;

	/** Number of pages for {@code count} boosts; an empty catalogue still has one page. */
	static int pages(int count) {
		return Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
	}

	/** The boosts on zero-based {@code page}; empty past the last page. */
	static List<PowerupDefinition> page(Collection<PowerupDefinition> all, int page) {
		List<PowerupDefinition> list = new ArrayList<>(all);
		int from = page * PER_PAGE;
		if (from >= list.size()) {
			return List.of();
		}
		return new ArrayList<>(list.subList(from, Math.min(from + PER_PAGE, list.size())));
	}

	static boolean hasNext(int page, int pages) {
		return page + 1 < pages;
	}

	static boolean hasPrevious(int page) {
		return page > 0;
	}

	@Override
	public int rows(TurfPowerupFlowSession session) {
		return ROWS;
	}

	@Override
	public String title(TurfPowerupFlowSession session) {
		return messages.text("Quartermaster.Title_Buffs", "&8&l[&b&lBuffs &8— &6%npc%&8&l]",
		                     "npc", session.getNpcDisplayName());
	}

	@Override
	public void render(MenuFlow<TurfPowerupFlowSession> flow, ChestMenuBuilder builder, TurfPowerupFlowSession session) {
		int  count  = pages(registry.all().size());
		int  page   = Math.min(Math.max(0, session.getBuffPage()), count - 1);
		session.setBuffPage(page);

		int slot = BUFF_START;
		for (PowerupDefinition def : page(registry.all(), page)) {
			final PowerupDefinition captured = def;
			builder.slot(slot++, ItemComponent.of(buffItem(captured, session))
			                                  .onAnyClick(ctx -> attemptBuy(flow, ctx.player(), session, captured)));
		}

		if (hasPrevious(page)) {
			ItemBuilder previous = new ItemBuilder(Material.ARROW)
					.setDisplayName(messages.text("Quartermaster.Page_Previous", "&7← Previous page"));
			builder.slot(SLOT_PREV, ItemComponent.of(previous).onAnyClick(ctx -> {
				session.setBuffPage(page - 1);
				flow.rerender();
			}));
		}
		if (hasNext(page, count)) {
			ItemBuilder next = new ItemBuilder(Material.ARROW)
					.setDisplayName(messages.text("Quartermaster.Page_Next", "&7Next page →"));
			builder.slot(SLOT_NEXT, ItemComponent.of(next).onAnyClick(ctx -> {
				session.setBuffPage(page + 1);
				flow.rerender();
			}));
		}
		if (count > 1) {
			ItemBuilder info = new ItemBuilder(Material.PAPER)
					.setDisplayName(messages.text("Quartermaster.Page_Info", "&7Page %page% of %pages%",
					                              "page", page + 1, "pages", count));
			builder.slot(SLOT_INFO, ItemComponent.of(info));
		}

		ItemBuilder back = new ItemBuilder(Material.ARROW)
				.setDisplayName(messages.text("Quartermaster.Back", "&7← Back"));
		builder.slot(SLOT_BACK, ItemComponent.of(back).onAnyClick(ctx -> flow.back()));

		ItemBuilder close = new ItemBuilder(Material.BARRIER)
				.setDisplayName(messages.text("Quartermaster.Close", "&cClose"));
		builder.slot(SLOT_CLOSE, ItemComponent.of(close).onAnyClick(ctx -> flow.end()));

		builder.fill(FillComponent.of(materialOf(fillItem)).name(fillName));
	}

	private ItemBuilder buffItem(PowerupDefinition def, TurfPowerupFlowSession session) {
		List<String> lore = new ArrayList<>();
		lore.add(messages.text("Quartermaster.Buffs_Effect", "&7Effect: &f%effect% &7x&f%magnitude%",
		                       "effect", def.effectType().name(), "magnitude", def.magnitude()));
		lore.add(messages.text("Quartermaster.Buffs_Duration", "&7Duration: &b%seconds%s",
		                       "seconds", def.durationSeconds()));
		lore.add(messages.text("Quartermaster.Buffs_Cost", "&7Cost: &6%money%", "money", money(def.cost())));
		lore.add(" ");
		boolean canAfford = session.getOwnerGang().getEconomy().getAmount().compareTo(def.cost()) >= 0;
		lore.add(clickLine(session, canAfford));
		return new ItemBuilder(Material.POTION)
				.setDisplayName(def.displayName())
				.setLore(lore);
	}

	/** The buy action line: allies get the look-only refusal, the owner gets click or insufficient. */
	String clickLine(TurfPowerupFlowSession session, boolean canAfford) {
		if (QuartermasterAccess.isAllyView(session)) {
			return refusal(messages, QuartermasterAccess.ALLIES_LOOK);
		}
		return canAfford
		       ? messages.text("Quartermaster.Buffs_Click", "&aClick to activate.")
		       : messages.text("Quartermaster.Buffs_Insufficient", "&cInsufficient gang funds.");
	}

	/** One boost purchase, re-checked on this click (TF-19). Package-private for the test seam. */
	void attemptBuy(MenuFlow<TurfPowerupFlowSession> flow, Player viewer, TurfPowerupFlowSession session,
	                PowerupDefinition def) {
		String deny = access.denyKey(viewer, session);
		if (deny != null) {
			viewer.sendMessage(refusal(messages, deny));
			if (QuartermasterAccess.closesPanel(deny)) {
				flow.end();
			} else {
				flow.rerender();
			}
			return;
		}

		Gang  gang = session.getOwnerGang();
		Turf  turf = session.getTurf();
		if (gang.getEconomy().getAmount().compareTo(def.cost()) < 0) {
			viewer.sendMessage(cannotAfford(def));
			flow.rerender();
			return;
		}
		try {
			gang.getEconomy().withdrawAmount(def.cost());
		} catch (EconomyException exception) {
			viewer.sendMessage(cannotAfford(def));
			flow.rerender();
			return;
		}

		buffs.activate(turf.getId(), def);
		log.info("Quartermaster: {} ({}) bought {} for {} on turf {} ({})",
		         viewer.getName(), gang.getName(), def.id(), money(def.cost()), turf.getId(), turf.getDisplayName());
		viewer.sendMessage(messages.text("Quartermaster.Bought_Boost", "&aActivated &f%boost% &aon &f%turf%&a.",
		                                 "boost", def.id(), "turf", turf.getDisplayName()));
		flow.rerender();
	}

	private String cannotAfford(PowerupDefinition def) {
		return messages.text("Quartermaster.Cannot_Afford", "&cYour gang bank can't cover &f%money%&c.",
		                     "money", money(def.cost()));
	}

	private static Material materialOf(String name) {
		return XMaterial.matchXMaterial(name).map(XMaterial::get).orElse(Material.BLACK_STAINED_GLASS_PANE);
	}
}
