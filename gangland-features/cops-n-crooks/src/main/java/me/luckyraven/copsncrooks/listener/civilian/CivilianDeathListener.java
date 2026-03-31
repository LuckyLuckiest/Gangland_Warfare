package me.luckyraven.copsncrooks.listener.civilian;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.npc.CivilianDeathEvent;
import me.luckyraven.copsncrooks.npc.civilian.CivilianNpc;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianDropConfig;
import me.luckyraven.item.ItemParser;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Handles civilian NPC death: clears vanilla drops, applies configured item drops, fires {@link CivilianDeathEvent} (XP
 * reward is handled by the gangland-impl listener), and marks the NPC for removal.
 */
@ListenerHandler
@RequiredArgsConstructor
public class CivilianDeathListener implements Listener {

	private final CivilianService civilianService;
	@Nullable
	private final ItemParser      itemParser;

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCivilianDeath(EntityDeathEvent event) {
		Entity entity = event.getEntity();

		CivilianNpc npc = civilianService.getNpc(entity.getUniqueId());
		if (npc == null) return;

		// Suppress vanilla drops and XP — we control them entirely
		event.getDrops().clear();
		event.setDroppedExp(0);

		CivilianDropConfig dropConfig = npc.getTypeConfig().drops();

		// Resolve and add configured drops
		for (String entry : dropConfig.itemEntries()) {
			ItemStack item = resolveItem(entry);
			if (item != null) {
				event.getDrops().add(item);
			}
		}

		// Fire event — XP reward handled by CivilianDeathRewardListener in gangland-impl
		Player killer = event.getEntity().getKiller();
		Bukkit.getPluginManager().callEvent(new CivilianDeathEvent(npc, killer, dropConfig.experience()));

		npc.markForRemoval();
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	@Nullable
	private ItemStack resolveItem(String entry) {
		if (entry == null || entry.isBlank()) return null;
		if (itemParser != null) return itemParser.parse(entry);
		return null;
	}
}
