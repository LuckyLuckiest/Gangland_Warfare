package me.luckyraven.listener.player;

import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
import me.luckyraven.copsncrooks.wanted.WantedEvent;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@ListenerHandler
@RequiredArgsConstructor
public class WantedChange implements Listener {

	private final Gangland gangland;

	@EventHandler
	public void onWantedIncrease(WantedEvent event) { }
}
