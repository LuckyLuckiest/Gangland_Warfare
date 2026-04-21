package me.luckyraven.logger;

import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class Logger {

	private static final Map<String, String> MODULE_CACHE   = new ConcurrentHashMap<>();
	private static final String              DEFAULT_MODULE = "Unknown";

	/**
	 * True when running inside a live Spigot/Paper server.
	 *
	 * <p>Spigot's log4j2 pattern wraps the logger name in brackets automatically, e.g.
	 * {@code [%logger]}. In that environment the name is stored as {@code "module.Class"} so the server's outer
	 * brackets complete the display to {@code [module.Class]}.
	 *
	 * <p>Outside Spigot (tests, standalone tools) no outer brackets are added, so they are
	 * included in the name directly: {@code "[module.Class]"}.
	 *
	 * <p>The module/class dot separator is deliberate: log4j2's logger hierarchy is dot-delimited, so naming loggers
	 * {@code module.Class} lets {@code Configurator.setAllLevels(module, ...)} cascade to every class-level logger
	 * under that module. This is what powers the {@code Debug.Modules} toggle in {@code settings.yml}.
	 */
	private static final boolean IN_SPIGOT = detectSpigot();

	private Logger() { }

	public static org.apache.logging.log4j.Logger getLogger(Class<?> clazz) {
		String moduleName = resolveModuleName(clazz);
		String loggerName = IN_SPIGOT ?
		                    moduleName + "." + clazz.getSimpleName() :
		                    "[" + moduleName + "." + clazz.getSimpleName() + "]";
		return LogManager.getLogger(loggerName);
	}

	private static boolean detectSpigot() {
		try {
			Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
			return bukkit.getMethod("getServer").invoke(null) != null;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static String resolveModuleName(Class<?> clazz) {
		// Add null/safety checks
		if (clazz == null) {
			return DEFAULT_MODULE;
		}

		Package pkg = clazz.getPackage();
		if (pkg == null) {
			return DEFAULT_MODULE;
		}

		String packageName = pkg.getName();
		if (packageName.isEmpty()) {
			return DEFAULT_MODULE;
		}

		return MODULE_CACHE.computeIfAbsent(packageName, Logger::findModuleName);
	}

	private static String findModuleName(String pkg) {
		// Add safety check
		if (pkg == null || pkg.isEmpty()) {
			return DEFAULT_MODULE;
		}

		String path = pkg;

		// Try to find module.properties by walking UP the package hierarchy
		while (!path.isEmpty()) {
			String resourcePath = path.replace('.', '/') + "/module.properties";

			// Wrap in try-catch to prevent any exceptions from bubbling up
			try {
				// Use the class's own classloader first (more reliable for shaded JARs)
				ClassLoader classLoader = Logger.class.getClassLoader();
				if (classLoader == null) {
					classLoader = ClassLoader.getSystemClassLoader();
				}

				InputStream in = classLoader.getResourceAsStream(resourcePath);
				if (in != null) {
					try (in) {
						Properties props = new Properties();
						props.load(in);

						String name = props.getProperty("module.name");
						if (name != null && !name.isBlank()) {
							return name;
						}
					}
				}
			} catch (IOException | SecurityException exception) {
				// Silently fail and try parent package
			}

			// Move to parent package
			int dot = path.lastIndexOf('.');
			if (dot == -1) break;
			path = path.substring(0, dot);
		}

		return DEFAULT_MODULE;
	}

}
