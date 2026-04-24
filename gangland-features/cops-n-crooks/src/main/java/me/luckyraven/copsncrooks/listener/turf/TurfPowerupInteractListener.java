package me.luckyraven.copsncrooks.listener.turf;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.turf.TurfPowerupManager;
import me.luckyraven.copsncrooks.npc.turf.TurfPowerupNpc;
import me.luckyraven.copsncrooks.npc.turf.TurfPowerupOpenContract;
import me.luckyraven.core.bean.listener.ListenerHandler;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Routes right-clicks on the per-turf Quartermaster NPC into the panel flow. Owner-gang gating + viewer-has-gang checks
 * happen on the {@link TurfPowerupOpenContract} side (gangland-impl), where the turf and gang modules are both visible
 * — this listener just resolves the entity → turf id and hands off.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfPowerupInteractListener implements Listener {

	private final TurfPowerupManager      manager;
	private final TurfPowerupOpenContract opener;

	@EventHandler
	public void onNpcRightClick(NPCRightClickEvent event) {
		Entity entity = event.getNPC().getEntity();
		if (entity == null) return;

		TurfPowerupNpc qm = manager.getByEntity(entity);
		if (qm == null) return;

		Player player = event.getClicker();
		opener.open(player, qm.getData().getTurfId());
	}
}
