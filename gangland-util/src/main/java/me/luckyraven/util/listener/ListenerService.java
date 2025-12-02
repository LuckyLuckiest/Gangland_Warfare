package me.luckyraven.util.listener;

import lombok.Getter;
import me.luckyraven.util.autowire.AutowireTarget;
import me.luckyraven.util.autowire.DependencyContainer;
import me.luckyraven.util.utilities.ReflectionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public abstract class ListenerService {

	private static final Logger logger = LogManager.getLogger(ListenerService.class.getSimpleName());

	private final JavaPlugin          plugin;
	@Getter
	private final List<ListenerEntry> listeners;
	@Getter
	private final DependencyContainer dependencyContainer;

	public ListenerService(JavaPlugin plugin) {
		this.plugin              = plugin;
		this.listeners           = new ArrayList<>();
		this.dependencyContainer = new DependencyContainer();
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
					continue;
				}

				ListenerHandler  annotation = clazz.getAnnotation(ListenerHandler.class);
				ListenerPriority priority   = annotation.priority();
				String           condition  = annotation.condition();

				// check if the condition is met
				if (!condition.isEmpty()) {
					boolean invoke = invokeMethod(condition);

					if (!invoke) continue;
				}

				// instantiate and register the listener
				try {
					Listener listener = instantiateListener(clazz, plugin);

					if (listener != null) {
						addEvent(listener, priority);
					}
				} catch (Exception exception) {
					logger.warn("Failed to instantiate class {}: {}", clazz.getName(), exception.getMessage());
				}
			}
		} catch (Exception exception) {
			logger.warn("Error scanning listeners: {}", exception.getMessage());
		}
	}

	/**
	 * Scans and registers components annotated with @Component for dependency injection.
	 *
	 * @param basePackage The base package to scan
	 * @param plugin The plugin instance
	 */
	@SuppressWarnings("unchecked")
	public void scanAndRegisterComponents(String basePackage, JavaPlugin plugin) {
		try {
			ClassLoader classLoader = plugin.getClass().getClassLoader();
			String      path        = basePackage.replace('.', '/');

			Set<Class<?>> classes = ReflectionUtil.findClasses(path, classLoader);

			for (Class<?> clazz : classes) {
				if (!clazz.isAnnotationPresent(ListenerComponent.class)) continue;

				ListenerComponent annotation = clazz.getAnnotation(ListenerComponent.class);
				String            condition  = annotation.condition();

				// check if the condition is met
				if (!condition.isEmpty()) {
					boolean invoke = invokeMethod(condition);
					if (!invoke) continue;
				}

				// Create instance with autowiring
				try {
					Object instance = dependencyContainer.createInstance(clazz, plugin);

					dependencyContainer.registerInstance((Class<? super Object>) clazz, instance);
					logger.info("Registered component: {}", clazz.getName());
				} catch (Exception exception) {
					logger.warn("Failed to register component {}: {}", clazz.getName(), exception.getMessage());
				}
			}
		} catch (Exception exception) {
			logger.warn("Error scanning components: {}", exception.getMessage());
		}
	}

	/**
	 * Attempts to instantiate a listener with various constructor patterns. Now with autowiring support.
	 */
	private Listener instantiateListener(Class<?> clazz, JavaPlugin plugin) throws Exception {
		// Check if class wants specific autowired targets
		if (clazz.isAnnotationPresent(AutowireTarget.class)) {
			AutowireTarget autowireTarget = clazz.getAnnotation(AutowireTarget.class);
			Class<?>[]     targetTypes    = autowireTarget.value();

			// Check if all required types are available
			boolean allAvailable = true;
			for (Class<?> type : targetTypes) {
				if (!dependencyContainer.hasInstance(type)) {
					logger.warn("Required autowire target {} not available for {}", type.getName(), clazz.getName());
					allAvailable = false;
					break;
				}
			}

			if (allAvailable) {
				try {
					return (Listener) dependencyContainer.createInstance(clazz, plugin);
				} catch (Exception e) {
					logger.warn("Failed to create listener with autowiring: {}", e.getMessage());
					// Fall through to traditional instantiation
				}
			}
		}

		// Traditional instantiation methods
		try {
			// Try constructor with JavaPlugin parameter
			return (Listener) clazz.getConstructor(JavaPlugin.class).newInstance(plugin);
		} catch (NoSuchMethodException e) {
			try {
				// Try constructor with plugin parameter specifically
				return (Listener) clazz.getConstructor(plugin.getClass()).newInstance(plugin);
			} catch (NoSuchMethodException e2) {
				try {
					// Try no-args constructor
					return (Listener) clazz.getConstructor().newInstance();
				} catch (NoSuchMethodException e3) {
					// Last attempt: use autowiring
					try {
						return (Listener) dependencyContainer.createInstance(clazz, plugin);
					} catch (Exception e4) {
						logger.warn("No suitable constructor found for {}", clazz.getName());
						return null;
					}
				}
			}
		}
	}

	private static class ListenerEntry {
		final Listener         listener;
		final ListenerPriority priority;

		ListenerEntry(Listener listener, ListenerPriority priority) {
			this.listener = listener;
			this.priority = priority;
		}
	}

}
