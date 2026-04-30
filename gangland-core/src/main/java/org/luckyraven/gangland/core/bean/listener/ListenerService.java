package org.luckyraven.gangland.core.bean.listener;

import lombok.CustomLog;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.bean.autowire.DependencyContainer;
import org.luckyraven.gangland.core.utilities.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@CustomLog
public abstract class ListenerService {

	private final JavaPlugin          plugin;
	@Getter
	private final List<ListenerEntry> listeners;
	@Getter
	private final DependencyContainer dependencyContainer;

	/**
	 * Construct the listener service against the project's single root {@link DependencyContainer}. Every
	 * {@code @ListenerHandler} class scanned by {@link #scanAndRegisterListeners(String, JavaPlugin)} is built via this
	 * container's constructor injection, so listeners can declare any registered bean as a constructor parameter and
	 * receive it automatically.
	 */
	protected ListenerService(JavaPlugin plugin, DependencyContainer dependencyContainer) {
		this.plugin              = plugin;
		this.listeners           = new ArrayList<>();
		this.dependencyContainer = dependencyContainer;
	}

	public abstract boolean invokeMethod(String condition) throws InvocationTargetException, IllegalAccessException;

	public void registerEvents() {
		PluginManager pluginManager = Bukkit.getPluginManager();

		listeners.sort(Comparator.comparingInt(entry -> entry.priority.getPriority()));

		for (ListenerEntry entry : listeners) {
			pluginManager.registerEvents(entry.listener, plugin);
		}
	}

	public void addEvent(Listener listener, ListenerPriority priority) {
		listeners.add(new ListenerEntry(listener, priority));
	}

	/**
	 * Automatically scans and registers all classes annotated with {@code @ListenerHandler} in the specified package.
	 * Each class is constructor-injected from the shared root {@link DependencyContainer}, so any bean already
	 * registered (via {@code @Bean} factory methods, repositories, kernel objects) can appear as a parameter on the
	 * listener's constructor and resolve automatically.
	 *
	 * @param basePackage the base package to scan (e.g. {@code "org.luckyraven.listener"})
	 * @param plugin the plugin instance — used only for the resource scan classloader and for Bukkit's
	 *        {@code registerEvents}; listeners themselves do not need to take {@code JavaPlugin} as a constructor parameter
	 * 		unless they want to.
	 */
	public void scanAndRegisterListeners(String basePackage, JavaPlugin plugin) {
		try {
			ClassLoader classLoader = plugin.getClass().getClassLoader();
			String      path        = basePackage.replace('.', '/');

			Set<Class<?>> classes = ReflectionUtil.findClasses(path, classLoader);

			for (Class<?> clazz : classes) {
				if (!clazz.isAnnotationPresent(ListenerHandler.class)) continue;

				if (!Listener.class.isAssignableFrom(clazz)) {
					log.warn("Class {} has @ListenerHandler but doesn't implement Listener!", clazz.getName());
					continue;
				}

				ListenerHandler  annotation = clazz.getAnnotation(ListenerHandler.class);
				ListenerPriority priority   = annotation.priority();
				String           condition  = annotation.condition();

				if (!condition.isEmpty()) {
					boolean invoke = invokeMethod(condition);
					if (!invoke) continue;
				}

				try {
					Listener listener = (Listener) dependencyContainer.createInstance(clazz);
					addEvent(listener, priority);
					log.debug("Listener {} has been registered!", clazz.getName());
				} catch (Exception exception) {
					log.warn("Failed to instantiate listener {}: {}", clazz.getName(), exception.getMessage());
				}
			}
		} catch (Exception exception) {
			log.warn("Error scanning listeners: {}", exception.getMessage());
		}
	}

	private record ListenerEntry(Listener listener, ListenerPriority priority) { }

}
