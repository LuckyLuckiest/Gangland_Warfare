package me.luckyraven.util;

import me.luckyraven.Gangland;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.event.Event;
import org.bukkit.plugin.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Level;

public final class ReloadUtil {

	public static void reload(Plugin plugin) throws FileNotFoundException {
		if (plugin != null) {
			unload(plugin);
			load(plugin);
		}
	}

	private static boolean load(Plugin plugin) throws FileNotFoundException {

		Plugin target = null;

		File pluginDir = new File("plugins");

		if (!pluginDir.isDirectory()) throw new FileNotFoundException(
				"Could not find the file and failed to search descriptions.");

		File pluginFile = new File(pluginDir, plugin.getName() + ".jar");

		if (!pluginFile.isFile()) {
			for (File f : Objects.requireNonNull(pluginDir.listFiles()))
				if (f.getName().endsWith(".jar")) try {
					PluginDescriptionFile desc = plugin.getPluginLoader().getPluginDescription(f);
					if (desc.getName().equalsIgnoreCase(plugin.getName())) {
						pluginFile = f;
						break;
					}
				} catch (InvalidDescriptionException exception) {
					plugin.getLogger().warning("Could not find the file and failed to search descriptions.");
					exception.printStackTrace();
					return false;
				}
		}

		try {
			target = Bukkit.getPluginManager().loadPlugin(pluginFile);
		} catch (InvalidDescriptionException exception) {
			plugin.getLogger().warning("Invalid description.");
		} catch (InvalidPluginException exception) {
			plugin.getLogger().warning("Not a valid plugin.");
		}

		assert target != null;
		target.onLoad();
		Bukkit.getPluginManager().enablePlugin(target);

		return true;
	}

	@SuppressWarnings("unchecked")
	private static void unload(Plugin plugin) {

		String name = plugin.getName();

		PluginManager pluginManager = Bukkit.getPluginManager();

		SimpleCommandMap commandMap = null;

		List<Plugin> plugins = null;

		Map<String, Plugin>                       names     = null;
		Map<String, Command>                      commands  = null;
		Map<Event, SortedSet<RegisteredListener>> listeners = null;

		boolean reloadListeners = true;

		if (pluginManager != null) {

			pluginManager.disablePlugin(plugin);

			try {

				Field pluginsField = Bukkit.getPluginManager().getClass().getDeclaredField("plugins");
				pluginsField.setAccessible(true);
				plugins = (List<Plugin>) pluginsField.get(pluginManager);

				Field lookupNamesField = Bukkit.getPluginManager().getClass().getDeclaredField("lookupNames");
				lookupNamesField.setAccessible(true);
				names = (Map<String, Plugin>) lookupNamesField.get(pluginManager);

				try {
					Field listenersField = Bukkit.getPluginManager().getClass().getDeclaredField("listeners");
					listenersField.setAccessible(true);
					listeners = (Map<Event, SortedSet<RegisteredListener>>) listenersField.get(pluginManager);
				} catch (Exception e) {
					reloadListeners = false;
				}

				Field commandMapField = Bukkit.getPluginManager().getClass().getDeclaredField("commandMap");
				commandMapField.setAccessible(true);
				commandMap = (SimpleCommandMap) commandMapField.get(pluginManager);

				Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
				knownCommandsField.setAccessible(true);
				commands = (Map<String, Command>) knownCommandsField.get(commandMap);

			} catch (NoSuchFieldException | IllegalAccessException exception) {
				plugin.getLogger().log(Level.SEVERE, "Failed to unload the plugin.");
			}

		}

		pluginManager.disablePlugin(plugin);

		if (plugins != null) plugins.remove(plugin);

		if (names != null) names.remove(name);

		if (listeners != null && reloadListeners) for (SortedSet<RegisteredListener> set : listeners.values())
			for (Iterator<RegisteredListener> it = set.iterator(); it.hasNext(); ) {
				RegisteredListener value = it.next();
				if (value.getPlugin() == plugin) it.remove();
			}

		if (commandMap != null)
			for (Iterator<Map.Entry<String, Command>> it = commands.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<String, Command> entry = it.next();
				if (entry.getValue() instanceof PluginCommand c) if (c.getPlugin() == plugin) {
					c.unregister(commandMap);
					it.remove();
				}
			}

		ClassLoader cl = plugin.getClass().getClassLoader();

		if (cl instanceof URLClassLoader) {
			try {
				Field pluginField = cl.getClass().getDeclaredField("plugin");
				pluginField.setAccessible(true);
				pluginField.set(cl, null);
				Field pluginInitField = cl.getClass().getDeclaredField("pluginInit");
				pluginInitField.setAccessible(true);
				pluginInitField.set(cl, null);
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException |
			         IllegalAccessException exception) {
				exception.printStackTrace();
			}

			try {
				((URLClassLoader) cl).close();
			} catch (IOException exception) {
				exception.printStackTrace();
			}

		}

		System.gc();

	}

}

