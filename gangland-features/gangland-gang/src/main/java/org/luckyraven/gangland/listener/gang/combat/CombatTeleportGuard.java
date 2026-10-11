package org.luckyraven.gangland.listener.gang.combat;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.data.teleportation.Waypoint.WaypointType;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** A player tagged by a rival gang cannot take a gang waypoint (safehouse, rally, admin gang waypoint). */
@ListenerHandler
public class CombatTeleportGuard implements Listener {

	private final CombatTag    combatTag;
	private final GangMessages messages;

	public CombatTeleportGuard(CombatTag combatTag, GangMessages messages) {
		this.combatTag = combatTag;
		this.messages  = messages;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTeleport(TeleportEvent event) {
		if (event.getWaypoint() == null || event.getWaypoint().getType() != WaypointType.GANG) return;

		Player player = event.getUser().getUser();
		if (!combatTag.isTagged(player.getUniqueId())) return;

		event.setCancelled(true);

		long seconds = (combatTag.remaining(player.getUniqueId()).toMillis() + 999L) / 1000L;
		player.sendMessage(messages.text("Combat.Tagged_Teleport",
		                                 "&cYou are in combat: wait %seconds% s before using a gang teleport.",
		                                 "seconds", seconds));
	}
}
