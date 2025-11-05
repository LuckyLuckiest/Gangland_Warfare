package me.luckyraven.listener;

import lombok.Getter;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.utilities.ReflectionUtil;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class ListenerManager {

	private final JavaPlugin    plugin;
	@Getter
	private final Set<Listener> listeners;

	public ListenerManager(JavaPlugin plugin) {
		this.plugin    = plugin;
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

	/**
	 * Automatically scans and registers all classes annotated with @ListenerHandler in the specified package.
	 *
	 * @param basePackage The base package to scan (e.g., "me.luckyraven.listener")
	 * @param plugin The plugin instance to pass to listener constructors
	 */
	public void scanAndRegisterListeners(String basePackage, JavaPlugin plugin) {
		try {
			ClassLoader classLoader = plugin.getClass().getClassLoader();
			String      path        = basePackage.replace('.', '/');

			Set<Class<?>> classes = ReflectionUtil.findClasses(path, classLoader);

			for (Class<?> clazz : classes) {
				if (!clazz.isAnnotationPresent(ListenerHandler.class)) continue;

				if (!Listener.class.isAssignableFrom(clazz)) {
					String format = String.format("Class %s has @ListenerHandler but doesn't implement Listener!",
												  clazz.getName());
					plugin.getLogger().warning(format);
				}

				ListenerHandler annotation = clazz.getAnnotation(ListenerHandler.class);
				String          condition  = annotation.condition();

				// check if the condition is met
				if (!condition.isEmpty()) {
					Method method = SettingAddon.getSetting(condition);

					if (method != null && method.getReturnType() == Boolean.class) {
						if (!(boolean) method.invoke(null)) continue;
					}
				}

				// instantiate and register the listener
				try {
					Listener listener = instantiateListener(clazz, plugin);

					if (listener != null) {
						addEvent(listener);
					}
				} catch (Exception exception) {
					String format = String.format("Failed to instantiate class %s.", exception.getMessage());
					plugin.getLogger().warning(format);
				}
			}
		} catch (Exception exception) {
			plugin.getLogger().warning(exception.getMessage());
		}
	}

	/**
	 * Attempts to instantiate a listener with various constructor patterns.
	 */
	private Listener instantiateListener(Class<?> clazz, JavaPlugin gangland) throws Exception {
		try {
			// Try constructor with JavaPlugin parameter
			return (Listener) clazz.getConstructor(JavaPlugin.class).newInstance(gangland);
		} catch (NoSuchMethodException e) {
			try {
				// Try constructor with Gangland parameter specifically
				return (Listener) clazz.getConstructor(gangland.getClass()).newInstance(gangland);
			} catch (NoSuchMethodException e2) {
				try {
					// Try no-args constructor
					return (Listener) clazz.getConstructor().newInstance();
				} catch (NoSuchMethodException e3) {
					plugin.getLogger().warning("No suitable constructor found for " + clazz.getName());
					return null;
				}
			}
		}
	}
}
