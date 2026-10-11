package org.luckyraven.gangland.listener.gang.combat;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;
import org.luckyraven.gangland.gang.combat.CombatTag;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/** RED stub. */
@ListenerHandler
public class CombatTeleportGuard implements Listener {

	public CombatTeleportGuard(CombatTag combatTag, GangMessages messages) {
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTeleport(TeleportEvent event) {
	}
}
