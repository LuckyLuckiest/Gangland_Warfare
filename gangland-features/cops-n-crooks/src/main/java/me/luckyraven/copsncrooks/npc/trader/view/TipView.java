package me.luckyraven.copsncrooks.npc.trader.view;

import com.cryptomorin.xseries.XMaterial;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.trader.TraderTipEvent;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
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
public final class TipView {

	private static final int      SIZE    = 27;
	private static final double[] AMOUNTS = {10D, 50D, 100D, 500D};
	private static final int[]    SLOTS   = {11, 12, 14, 15};

	private static final SoundConfiguration SOUND_TIP = new SoundConfiguration(
			SoundConfiguration.SoundType.VANILLA, "BLOCK_NOTE_BLOCK_CHIME", 0.6f, 1.5f);

	private final JavaPlugin         plugin;
	private final TraderSettings     settings;
	private final Map<Player, State> active = new WeakHashMap<>();

	public void open(Player viewer, NegotiationView.NegotiationSession parent, Consumer<Player> onClose) {
		State state = new State(parent, onClose);
		state.handler = new InventoryHandler(plugin, "&8Tip the Trader", SIZE, viewer);

		ItemBuilder info = new ItemBuilder(material(XMaterial.GOLD_NUGGET, Material.GOLD_NUGGET))
				.setDisplayName("&6Pick a tip amount")
				.setLore("&7Tipping raises the trader's mood toward you,",
				         "&7reducing future asking prices.");
		state.handler.setItem(4, info, false, (p, inv, b) -> { });

		for (int i = 0; i < AMOUNTS.length; i++) {
			double amount = AMOUNTS[i];
			ItemBuilder button = new ItemBuilder(material(XMaterial.GOLD_INGOT, Material.GOLD_INGOT))
					.setDisplayName("&6Tip &e$" + format(amount));
			state.handler.setItem(SLOTS[i], button, false,
			                      (p, inv, b) -> submitTip(p, state, amount));
		}

		ItemBuilder cancel = new ItemBuilder(material(XMaterial.BARRIER, Material.BARRIER))
				.setDisplayName("&cBack");
		state.handler.setItem(22, cancel, false, (p, inv, b) -> viewer.closeInventory());

		InventoryUtil.fillInventory(state.handler,
		                            new Fill(settings.getInventoryFillName(), settings.getInventoryFillItem()));

		active.put(viewer, state);
		Bukkit.getPluginManager().registerEvents(new CloseListener(viewer, state), plugin);
		state.handler.open(viewer);
	}

	private void submitTip(Player viewer, State state, double amount) {
		SOUND_TIP.playSound(viewer);

		TraderTipEvent event = new TraderTipEvent(viewer, state.parent.getTrader(), amount);
		Bukkit.getPluginManager().callEvent(event);
		// The tip listener owns user-facing messaging (success and failure both go through the contract).
		viewer.closeInventory();
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private ItemStack material(XMaterial preferred, Material fallback) {
		ItemStack stack = preferred.parseItem();
		return stack != null ? stack : new ItemStack(fallback);
	}

	private String format(double value) {
		if (value == Math.floor(value) && !Double.isInfinite(value)) return String.valueOf((long) value);
		return String.format("%.2f", value);
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
