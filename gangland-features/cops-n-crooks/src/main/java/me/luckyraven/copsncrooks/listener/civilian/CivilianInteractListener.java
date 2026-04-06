package me.luckyraven.copsncrooks.listener.civilian;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianInventoryConfig;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.item.ItemParser;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Opens the configured trader inventory when a player right-clicks a civilian NPC whose type has a
 * {@link CivilianInventoryConfig}.
 */
@ListenerHandler
@RequiredArgsConstructor
public class CivilianInteractListener implements Listener {

	private final JavaPlugin      plugin;
	private final CivilianService civilianService;
	@Nullable
	private final ItemParser      itemParser;

	@EventHandler
	public void onNpcRightClick(NPCRightClickEvent event) {
		Entity entity = event.getNPC().getEntity();
		if (entity == null) return;

		CivilianNpc npc = civilianService.getNpc(entity.getUniqueId());
		if (npc == null) return;

		CivilianInventoryConfig invConfig = npc.getTypeConfig().inventory();
		if (invConfig == null) return;

		Player player = event.getClicker();
		openTraderInventory(player, invConfig);
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private void openTraderInventory(Player player, CivilianInventoryConfig config) {
		String           title   = ChatUtil.color(config.title());
		int              size    = normalizeSize(config.size());
		InventoryHandler handler = new InventoryHandler(plugin, title, size, player);

		for (Map.Entry<Integer, String> entry : config.slotItems().entrySet()) {
			int    slot      = entry.getKey();
			String itemEntry = entry.getValue();

			if (slot < 0 || slot >= size) continue;

			ItemStack item = resolveItem(itemEntry);
			if (item != null) {
				handler.setItem(slot, item, false);
			}
		}

		handler.open(player);
	}

	private int normalizeSize(int size) {
		int clamped = Math.clamp(size, 9, 54);
		return (int) (Math.ceil(clamped / 9.0) * 9);
	}

	@Nullable
	private ItemStack resolveItem(String entry) {
		if (entry == null || entry.isBlank()) return null;
		if (itemParser != null) return itemParser.parse(entry);
		return null;
	}
}
