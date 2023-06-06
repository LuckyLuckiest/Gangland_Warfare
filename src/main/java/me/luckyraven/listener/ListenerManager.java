package me.luckyraven.listener;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;

public class ListenerManager {

	private @Getter
	final         Set<Listener> listeners;
	private final JavaPlugin    plugin;

	public ListenerManager(JavaPlugin plugin) {
		listeners = new HashSet<>();
		this.plugin = plugin;
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
