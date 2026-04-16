package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.trader.TraderBarterEvent;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.shop.message.ShopDisplayResolver;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.configuration.SoundConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class BarterConfirmView {

	private static final int SIZE         = 27;
	private static final int SLOT_TRADER  = 11;
	private static final int SLOT_ARROW   = 13;
	private static final int SLOT_PLAYER  = 15;
	private static final int SLOT_CONFIRM = 21;
	private static final int SLOT_CANCEL  = 23;

	private static final SoundConfiguration SOUND_CONFIRM = new SoundConfiguration(
			SoundConfiguration.SoundType.VANILLA, "ENTITY_VILLAGER_YES", 0.6f, 1.0f);
	private static final SoundConfiguration SOUND_FAIL    = new SoundConfiguration(
			SoundConfiguration.SoundType.VANILLA, "ENTITY_VILLAGER_NO", 0.6f, 0.8f);

	private final JavaPlugin          plugin;
	private final TraderSettings      settings;
	private final ShopDisplayResolver displayResolver;

	private final Map<Player, State> active = new WeakHashMap<>();

	public void open(Player viewer, NegotiationView.NegotiationSession parent, Consumer<Player> onClose) {
		State state = new State(parent, onClose);
		state.handler = new InventoryHandler(plugin, "&8&lBarter", SIZE, viewer);

		ItemStack traderStack = parent.getEntry().getItem().clone();
		ItemBuilder traderItem = new ItemBuilder(traderStack)
				.setDisplayName(displayResolver.cleanDisplayName(traderStack))
				.setLore("&7You receive this.");
		state.handler.setItem(SLOT_TRADER, traderItem, false, (p, inv, b) -> { });

		ItemBuilder arrow = new ItemBuilder(material(XMaterial.SPECTRAL_ARROW, Material.ARROW))
				.setDisplayName("&7⇄ Barter")
				.setLore("&7An equal exchange.");
		state.handler.setItem(SLOT_ARROW, arrow, false, (p, inv, b) -> { });

		ItemStack barterCost = parent.getEntry().getTradeFor();
		ItemBuilder playerItem = new ItemBuilder(barterCost.clone())
				.setLore("&7You give this.",
				         "&7Required: &b" + barterCost.getType().name() + " ×" + barterCost.getAmount());
		state.handler.setItem(SLOT_PLAYER, playerItem, false, (p, inv, b) -> { });

		ItemBuilder confirm = new ItemBuilder(material(XMaterial.EMERALD_BLOCK, Material.EMERALD_BLOCK))
				.setDisplayName("&aCONFIRM barter");
		state.handler.setItem(SLOT_CONFIRM, confirm, false, (p, inv, b) -> submit(p, state));

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER))
				.setDisplayName("&cBack");
		state.handler.setItem(SLOT_CANCEL, cancel, false, (p, inv, b) -> viewer.closeInventory());

		InventoryUtil.fillInventory(state.handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		active.put(viewer, state);
		Bukkit.getPluginManager().registerEvents(new CloseListener(viewer, state), plugin);
		state.handler.open(viewer);
	}

	private void submit(Player viewer, State state) {
		TraderBarterEvent event = new TraderBarterEvent(viewer, state.parent.getTrader(), state.parent.getEntry());
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) {
			SOUND_FAIL.playSound(viewer);
			// The barter listener owns user-facing messaging for failures.
		} else {
			SOUND_CONFIRM.playSound(viewer);
		}
		viewer.closeInventory();
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	// ── State & listener ─────────────────────────────────────────────────

	private static final class State {
		final NegotiationView.NegotiationSession parent;
		final Consumer<Player>                   onClose;
		InventoryHandler handler;

		State(NegotiationView.NegotiationSession parent, Consumer<Player> onClose) {
			this.parent  = parent;
			this.onClose = onClose;
		}
	}

	private final class CloseListener implements Listener {
		private final Player viewer;
		private final State  state;

		CloseListener(Player viewer, State state) {
			this.viewer = viewer;
			this.state  = state;
		}

		@EventHandler
		public void onClose(InventoryCloseEvent event) {
			if (event.getPlayer() != viewer) return;
			if (event.getInventory() != state.handler.getInventory()) return;

			active.remove(viewer);
			HandlerList.unregisterAll(this);

			if (state.onClose != null) {
				Bukkit.getScheduler().runTask(plugin, () -> state.onClose.accept(viewer));
			}
		}
	}

}
