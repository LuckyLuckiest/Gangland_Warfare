package me.luckyraven.listener.player;

import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

@ListenerHandler
public class WantedLevel implements Listener {

	@SuppressWarnings("EmptyMethod")
	@EventHandler
	public void onPlayerKillEvent(PlayerDeathEvent event) {

	}

}
