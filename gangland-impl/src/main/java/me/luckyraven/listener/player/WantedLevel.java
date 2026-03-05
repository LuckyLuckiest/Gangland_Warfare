package me.luckyraven.listener.player;

import me.luckyraven.copsncrooks.combo.KillCombo;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

@ListenerHandler
public class WantedLevel implements Listener {

	private final KillCombo killCombo;

	public WantedLevel(KillCombo killCombo) {
		this.killCombo = killCombo;
	}

	@EventHandler
	public void onPlayerKillEvent(PlayerDeathEvent event) {
		if (killCombo == null) return;

		killCombo.handlePlayerDeath(event.getEntity().getUniqueId());
	}

}
