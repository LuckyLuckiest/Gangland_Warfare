package me.luckyraven.listener.player;

import me.luckyraven.copsncrooks.combo.KillCombo;
import me.luckyraven.util.downed.PlayerDownedEvent;
import me.luckyraven.util.listener.ListenerHandler;
import net.citizensnpcs.api.CitizensAPI;
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
		if (CitizensAPI.getNPCRegistry().isNPC(event.getEntity())) return;

		killCombo.handlePlayerDeath(event.getEntity().getUniqueId());
	}

	@EventHandler
	public void onPlayerDowned(PlayerDownedEvent event) {
		if (killCombo == null) return;

		killCombo.handlePlayerDeath(event.getPlayer().getUniqueId());
	}

}
