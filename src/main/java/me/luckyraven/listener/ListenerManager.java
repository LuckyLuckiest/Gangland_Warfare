package me.luckyraven.listener;

import lombok.Getter;
import me.luckyraven.Gangland;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;

import java.util.HashSet;
import java.util.Set;

public class ListenerManager {

	private @Getter
	final Set<Listener> listeners;

	public ListenerManager() {
		listeners = new HashSet<>();
	}

	public void registerEvents() {
		PluginManager pluginManager = Bukkit.getPluginManager();
		for (Listener listener : listeners)
			pluginManager.registerEvents(listener, Gangland.getInstance());
	}

	public void addEvent(Listener listener) {
		listeners.add(listener);
	}

}
