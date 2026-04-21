package me.luckyraven.copsncrooks.listener.civilian;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.npc.CivilianDeathEvent;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.config.CivilianDropConfig;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.item.ItemParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;

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

		// PLAYER-type NPC deaths fire PlayerDeathEvent, whose NMS handler drops inventory
		// independently of getDrops(). setKeepInventory(true) suppresses that path.
		if (event instanceof PlayerDeathEvent playerDeathEvent) {
			playerDeathEvent.setKeepInventory(true);
		}

		CivilianDropConfig dropConfig = npc.getTypeConfig().drops();

		// Roll each configured drop independently; entries without an explicit chance default to 1.0.
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (CivilianDropConfig.DropEntry drop : dropConfig.itemEntries()) {
			if (drop.chance() < 1.0 && random.nextDouble() >= drop.chance()) continue;

			ItemStack item = resolveItem(drop.entry());
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
