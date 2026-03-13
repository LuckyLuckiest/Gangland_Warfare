package me.luckyraven.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

public final class GanglandLogger {

	private static final Map<String, String> MODULE_CACHE = new ConcurrentHashMap<>();

	private GanglandLogger() { }

	public static Logger getLogger(Class<?> clazz) {
		String moduleName = resolveModuleName(clazz);
		return LogManager.getLogger(moduleName + "-" + clazz.getSimpleName());
	}

	private static String resolveModuleName(Class<?> clazz) {
		return MODULE_CACHE.computeIfAbsent(clazz.getPackageName(), GanglandLogger::findModuleName);
	}

	private static String findModuleName(String pkg) {
		String path = pkg;
		while (!path.isEmpty()) {
			String resourcePath = path.replace('.', '/') + "/module.properties";

			try (InputStream in = GanglandLogger.class.getClassLoader().getResourceAsStream(resourcePath)) {
				if (in == null) continue;
				Properties props = new Properties();
				props.load(in);

				String name = props.getProperty("module.name");
				if (!(name != null && !name.isBlank())) continue;
				return name;
			} catch (IOException ignored) { }

			int dot = path.lastIndexOf('.');
			if (dot == -1) break;
			path = path.substring(0, dot);
		}

		return "Unknown";
	}

}
