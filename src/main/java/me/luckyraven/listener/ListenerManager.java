package me.luckyraven.listener;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class ListenerManager {

	private final         JavaPlugin    plugin;
	@Getter private final Set<Listener> listeners;

	public ListenerManager(JavaPlugin plugin) {
		this.plugin = plugin;
		this.listeners = new HashSet<>();
	}

	public void registerEvents() {
		PluginManager pluginManager = Bukkit.getPluginManager();
		for (Listener listener : listeners)
			pluginManager.registerEvents(listener, plugin);
	}

	public void addEvent(Listener listener) {
		listeners.add(listener);
	}

}
