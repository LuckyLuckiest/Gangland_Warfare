package me.luckyraven.copsncrooks.npc.turf.view;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.economy.exception.EconomyException;
import me.luckyraven.gang.Gang;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.flow.MultiPanelInventory;
import me.luckyraven.inventory.flow.Panel;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.turf.powerups.ActiveBuffManager;
import me.luckyraven.turf.powerups.PowerupDefinition;
import me.luckyraven.turf.powerups.PowerupRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Buff catalogue panel — lists every {@link PowerupDefinition} from the registry as a clickable potion item. Click
 * activates the buff (debits the gang bank, adds to {@link ActiveBuffManager}, re-renders so the new active count is
 * visible). Bottom-row navigation: back to menu + close.
 */
@RequiredArgsConstructor
public final class TurfPowerupBuffCatalogueView implements Panel<TurfPowerupFlowSession> {

	private static final int SIZE       = 36;
	private static final int BUFF_START = 9;
	private static final int BUFF_END   = 18;
	private static final int SLOT_BACK  = 27;
	private static final int SLOT_CLOSE = 35;

	private final PowerupRegistry   registry;
	private final ActiveBuffManager buffs;
	private final String            fillItem;
	private final String            fillName;

	@Override
	public int size(TurfPowerupFlowSession session) {
		return SIZE;
	}

	@Override
	public String title(TurfPowerupFlowSession session) {
		return "&8&l[&b&lBuffs &8— &6" + session.getNpcDisplayName() + "&8&l]";
	}

	@Override
	public void render(MultiPanelInventory<TurfPowerupFlowSession> host, InventoryHandler handler, Player viewer,
	                   TurfPowerupFlowSession session) {
		Gang gang = session.getViewerGang();

		int slot = BUFF_START;
		for (PowerupDefinition def : registry.all()) {
			if (slot >= BUFF_END) break;
			final PowerupDefinition captured = def;
			handler.setItem(slot++, buffItem(captured, gang), false,
			                (p, inv, b) -> attemptBuy(host, viewer, session, captured));
		}

		ItemBuilder back = new ItemBuilder(Material.ARROW).setDisplayName("&7← Back");
		handler.setItem(SLOT_BACK, back, false, (p, inv, b) -> host.back());

		ItemBuilder close = new ItemBuilder(Material.BARRIER).setDisplayName("&cClose");
		handler.setItem(SLOT_CLOSE, close, false, (p, inv, b) -> host.end());

		InventoryUtil.fillInventory(handler, new Fill(fillName, fillItem));
	}

	private ItemBuilder buffItem(PowerupDefinition def, Gang viewerGang) {
		List<String> lore = new ArrayList<>();
		lore.add("&7Effect: &f" + def.effectType().name() + " &7x&f" + def.magnitude());
		lore.add("&7Duration: &b" + def.durationSeconds() + "s");
		lore.add("&7Cost: &6$" + NumberUtil.valueFormat(def.cost()));
		lore.add(" ");
		boolean canAfford = viewerGang.getEconomy().getAmount().compareTo(def.cost()) >= 0;
		lore.add(canAfford ? "&aClick to activate." : "&cInsufficient gang funds.");
		return new ItemBuilder(Material.POTION)
				.setDisplayName(def.displayName())
				.setLore(lore);
	}

	private void attemptBuy(MultiPanelInventory<TurfPowerupFlowSession> host, Player viewer,
	                        TurfPowerupFlowSession session, PowerupDefinition def) {
		Gang gang = session.getViewerGang();
		try {
			gang.getEconomy().withdrawAmount(def.cost());
		} catch (EconomyException exception) {
			viewer.sendMessage(ChatUtil.color("&cYour gang bank can't cover &f$"
			                                  + NumberUtil.valueFormat(def.cost()) + "&c."));
			return;
		}
		buffs.activate(session.getTurf().getId(), def);
		viewer.sendMessage(ChatUtil.color("&aActivated &f" + def.id() + " &aon &f"
		                                  + session.getTurf().getDisplayName() + "&a."));
		host.rerender();
	}
}
