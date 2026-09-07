package org.luckyraven.gangland.copsncrooks.npc.turf.view;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;

import java.math.BigDecimal;
import java.util.List;

/**
 * Garrison sub-panel — view current defender stock, click to buy +1 defender. The garrison auto-deploys at the turf's
 * centre when an enemy starts capturing (see GarrisonDeployListener) and is consumed on deploy. Bottom row navigation:
 * back to menu + close.
 *
 * <p>Per-defender cost is hardcoded for now; will move to {@code turf_powerups.yml} under {@code Garrison}
 * once that section is added back to the config.
 */
@RequiredArgsConstructor
public final class TurfPowerupGarrisonView implements Panel<TurfPowerupFlowSession> {

	private static final int SIZE       = 27;
	private static final int SLOT_INFO  = 4;
	private static final int SLOT_BUY   = 13;
	private static final int SLOT_BACK  = 18;
	private static final int SLOT_CLOSE = 26;

	private static final BigDecimal PER_DEFENDER_COST = BigDecimal.valueOf(1500);

	private final GarrisonManager garrisons;
	private final String          fillItem;
	private final String          fillName;

	@Override
	public int size(TurfPowerupFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(TurfPowerupFlowSession session) {
		return "&8&l[&c&lGarrison &8— &6" + session.getNpcDisplayName() + "&8&l]";
	}

	@Override
	public void render(MultiPanelInventory<TurfPowerupFlowSession> host, InventoryHandler handler, Player viewer,
	                   TurfPowerupFlowSession session) {
		Turf turf = session.getTurf();
		Gang gang = session.getViewerGang();

		handler.setItem(SLOT_INFO, infoItem(turf, gang), false, (p, inv, b) -> { });
		handler.setItem(SLOT_BUY, buyItem(gang), false,
		                (p, inv, b) -> attemptBuy(host, viewer, session));

		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&7← Back");
		handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> host.back());

		ItemBuilder close = new ItemBuilder(Material.BARRIER).setDisplayName("&cClose");
		handler.setItem(SLOT_CLOSE, close, false, (p, inv, b) -> host.end());

		InventoryUtil.fillInventory(handler, new Fill(fillName, fillItem));
	}

	private ItemBuilder infoItem(Turf turf, Gang gang) {
		return new ItemBuilder(Material.IRON_HELMET)
				.setDisplayName("&c&lGarrison")
				.setLore(List.of(
						"&7Stock on &f" + turf.getDisplayName() + "&7: &f" + garrisons.count(turf.getId()),
						"&7Per defender: &6$" + NumberUtil.valueFormat(PER_DEFENDER_COST),
						" ",
						"&7Gang bank: &a$" + NumberUtil.valueFormat(gang.getEconomy().getAmount())));
	}

	private ItemBuilder buyItem(Gang viewerGang) {
		boolean canAfford = viewerGang.getEconomy().getAmount().compareTo(PER_DEFENDER_COST) >= 0;
		return new ItemBuilder(Material.IRON_SWORD)
				.setDisplayName("&aBuy &f1 &aDefender")
				.setLore(List.of(
						"&7Cost: &6$" + NumberUtil.valueFormat(PER_DEFENDER_COST),
						" ",
						canAfford ? "&aClick to add &f1 &ato the garrison."
						          : "&cInsufficient gang funds."));
	}

	private void attemptBuy(MultiPanelInventory<TurfPowerupFlowSession> host, Player viewer,
	                        TurfPowerupFlowSession session) {
		Gang gang = session.getViewerGang();
		try {
			gang.getEconomy().withdrawAmount(PER_DEFENDER_COST);
		} catch (EconomyException exception) {
			viewer.sendMessage(ChatUtil.color("&cYour gang bank can't cover &f$"
			                                  + NumberUtil.valueFormat(PER_DEFENDER_COST) + "&c."));
			return;
		}
		garrisons.add(session.getTurf().getId(), 1);
		viewer.sendMessage(ChatUtil.color("&aBought &f1 &adefender for &f"
		                                  + session.getTurf().getDisplayName() + "&a."));
		host.rerender();
	}
}
