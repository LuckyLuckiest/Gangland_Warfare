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

	private Logger() { }

	public static org.apache.logging.log4j.Logger getLogger(Class<?> clazz) {
		String moduleName = resolveModuleName(clazz);
		return LogManager.getLogger(moduleName + "] [" + clazz.getSimpleName());
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
