package me.luckyraven.copsncrooks.listener.turf;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.turf.TurfPowerupManager;
import me.luckyraven.core.bean.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Drains {@link TurfPowerupManager}'s pending-spawn queue as Bukkit loads chunks. On a fresh server boot the
 * Quartermaster's chunk may not be loaded (no player has wandered there yet), so its initial spawn is queued. The
 * moment a player walks into render distance and Bukkit fires this event, the matching pending entry is spawned. Once
 * spawned, it leaves the queue and never comes back through this path.
 *
 * <p>Cheap: the queue is normally empty after the first few minutes of play, so the per-event scan is a no-op
 * for the rest of the session.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfPowerupChunkLoadListener implements Listener {

	private final TurfPowerupManager powerupNpcs;

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		powerupNpcs.onChunkLoaded(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ());
	}
}
