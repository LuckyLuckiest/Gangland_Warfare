package org.luckyraven.gangland.command.sub.debug;

import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.ItemBuilder;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.flow.FlowSession;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;
import org.luckyraven.gangland.inventory.flow.Panel;
import org.luckyraven.gangland.inventory.part.Fill;
import org.luckyraven.gangland.inventory.util.InventoryUtil;

import java.util.function.Consumer;

/**
 * Debug-only panel with a single emerald button that runs {@link #openAction} (typically opens a
 * {@code VillagerInventory}). Used by {@code DebugCommand}'s {@code villager} sub-arg to test the merchant API behind a
 * {@link MultiPanelInventory} hop. The flow ends naturally when the merchant opens — Bukkit fires
 * {@code InventoryCloseEvent} synchronously inside {@code openMerchant}, which the host's close listener picks up and
 * runs cleanup.
 */
@RequiredArgsConstructor
public final class VillagerDebugPanel implements Panel<VillagerDebugPanel.Session> {

	private static final int              SIZE      = 27;
	private static final int              OPEN_SLOT = 13;
	private final        Consumer<Player> openAction;

	@Override
	public int size(Session session) {
		return SIZE;
	}

	@Override
	public String title(Session session) {
		return "&6&lOpen Villager";
	}

	@Override
	public void render(MultiPanelInventory<Session> host,
	                   InventoryHandler handler,
	                   Player viewer,
	                   Session session) {
		ItemBuilder button = new ItemBuilder(Material.EMERALD)
				.setDisplayName(ChatUtil.color("&aClick to open villager merchant"));

		handler.setItem(OPEN_SLOT, button, false, (player, inv, item) -> openAction.accept(player));

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());
		InventoryUtil.fillInventory(handler, fill);
	}

	public static final class Session implements FlowSession { }
}
