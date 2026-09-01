package org.luckyraven.gangland.listener.player;

import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.luckyraven.gangland.copsncrooks.combo.KillCombo;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
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
		if (CitizensAPI.getNPCRegistry().isNPC(event.getEntity())) return;

		killCombo.handlePlayerDeath(event.getEntity().getUniqueId());
	}

	@EventHandler
	public void onPlayerDowned(PlayerDownedEvent event) {
		if (killCombo == null) return;

		killCombo.handlePlayerDeath(event.getPlayer().getUniqueId());
	}

}
