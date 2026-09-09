package org.luckyraven.gangland.copsncrooks.listener.player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;

@ListenerHandler
public class WantedLevelListener implements Listener {

	private final KillCombo killCombo;

	public WantedLevelListener(KillCombo killCombo) {
		this.killCombo = killCombo;
	}

	@EventHandler
	public void onPlayerKillEvent(PlayerDeathEvent event) {
		if (killCombo == null) return;
		// T-KR4 (review M4): was CitizensAPI.getNPCRegistry().isNPC(...) unguarded — NpcSupport.isNpc() is the
		// same T-M3 established, never-throws replacement (false when Citizens is absent, so a real player's
		// kill combo still tracks instead of the whole handler silently going dead without Citizens installed).
		if (NpcSupport.isNpc(event.getEntity())) return;

		killCombo.handlePlayerDeath(event.getEntity().getUniqueId());
	}

	@EventHandler
	public void onPlayerDowned(PlayerDownedEvent event) {
		if (killCombo == null) return;

		killCombo.handlePlayerDeath(event.getPlayer().getUniqueId());
	}

}
