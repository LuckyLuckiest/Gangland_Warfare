package me.luckyraven.logging;

import lombok.CustomLog;
import me.luckyraven.file.configuration.Settings;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Reads the {@code Debug} section of {@code settings.yml} and programmatically reconfigures the running log4j2
 * {@link LoggerContext} so {@code log.debug(...)} calls from the selected plugin modules start printing — no
 * {@code log4j2.xml} override or JVM flag required.
 *
 * <p>Paper's bundled log4j2 config attaches its root {@code AppenderRef}s with a {@code level="info"} filter which
 * drops DEBUG events before they reach the console. To bypass that we build a standalone {@link LoggerConfig} per
 * module with {@code additivity=false} and attach Paper's existing appender instances to it directly without any
 * ref-level filter — DEBUG events flow straight to the appenders, skipping the filtered root refs entirely.
 *
 * <p>When {@code Debug.Enabled} is {@code true} and {@code Debug.Modules} is empty, every {@code module.properties}
 * file found on the plugin classpath is discovered and flipped — admins don't have to list each module by hand.
 */
@CustomLog
public final class DebugLoggingInitializer {

	private static final String BASE_PACKAGE    = "me/luckyraven";
	private static final String PROPERTIES_FILE = "module.properties";
	private static final String MODULE_NAME_KEY = "module.name";

	/**
	 * Installs a {@link LoggerConfig} for {@code moduleName} at DEBUG level with {@code additivity=false}, attaching
	 * every existing appender to it directly (no ref-level filter). Appenders themselves have no level threshold in
	 * log4j2 — filtering is done at the AppenderRef or via Filters — so routing DEBUG events through our LoggerConfig
	 * sidesteps the INFO filter Paper's root refs apply.
	 */
	private static void installDebugLogger(org.apache.logging.log4j.core.config.Configuration cfg,
	                                       Collection<Appender> appenders, String moduleName) {
		LoggerConfig existing = cfg.getLoggerConfig(moduleName);
		if (existing.getName().equals(moduleName)) {
			cfg.removeLogger(moduleName);
		}

		LoggerConfig moduleLogger = new LoggerConfig(moduleName, Level.DEBUG, false);
		for (Appender appender : appenders) {
			moduleLogger.addAppender(appender, null, null);
		}
		cfg.addLogger(moduleName, moduleLogger);
	}

	private static List<String> discoverAllModules() {
		Set<String> modules = new LinkedHashSet<>();
		try {
			ClassLoader      classLoader = DebugLoggingInitializer.class.getClassLoader();
			Enumeration<URL> resources   = classLoader.getResources(BASE_PACKAGE);
			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();
				if ("jar".equals(resource.getProtocol())) {
					collectFromJar(resource, modules);
				} else {
					collectFromDirectory(resource, modules);
				}
			}
		} catch (IOException exception) {
			log.warn("Failed to enumerate plugin modules for debug logging: {}", exception.getMessage());
		}
		return new ArrayList<>(modules);
	}

	private static void collectFromJar(URL resource, Set<String> out) {
		String rawPath = resource.getPath();
		int    bang    = rawPath.indexOf('!');
		if (bang < 0 || !rawPath.startsWith("file:")) {
			return;
		}
		String jarPath = URLDecoder.decode(rawPath.substring(5, bang), StandardCharsets.UTF_8);
		try (JarFile jar = new JarFile(jarPath)) {
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				String   name  = entry.getName();
				if (name.endsWith("/" + PROPERTIES_FILE) || name.equals(PROPERTIES_FILE)) {
					try (InputStream in = jar.getInputStream(entry)) {
						readModuleName(in, out);
					}
				}
			}
		} catch (IOException exception) {
			log.warn("Failed to scan jar '{}' for module.properties: {}", jarPath, exception.getMessage());
		}
	}

	private static void collectFromDirectory(URL resource, Set<String> out) {
		String path      = URLDecoder.decode(resource.getPath(), StandardCharsets.UTF_8);
		File   directory = new File(path);
		if (!directory.isDirectory()) {
			return;
		}
		scanDirectory(directory, out);
	}

	private static void scanDirectory(File directory, Set<String> out) {
		File[] children = directory.listFiles();
		if (children == null) {
			return;
		}
		for (File file : children) {
			if (file.isDirectory()) {
				scanDirectory(file, out);
			} else if (file.getName().equals(PROPERTIES_FILE)) {
				try (InputStream in = new java.io.FileInputStream(file)) {
					readModuleName(in, out);
				} catch (IOException exception) {
					log.warn("Failed to read '{}': {}", file.getAbsolutePath(), exception.getMessage());
				}
			}
		}
	}

	private static void readModuleName(InputStream in, Set<String> out) throws IOException {
		Properties props = new Properties();
		props.load(in);
		String moduleName = props.getProperty(MODULE_NAME_KEY);
		if (moduleName != null && !moduleName.isBlank() && !moduleName.startsWith("${")) {
			out.add(moduleName);
		}
	}

	public void initialize() {
		List<String> modules = Settings.getDebugModules();
		if (modules == null || modules.isEmpty()) {
			modules = discoverAllModules();
			if (modules.isEmpty()) {
				log.warn("Debug.Enabled is true but no modules could be discovered on the classpath");
				return;
			}
			log.info("Debug.Modules is empty — enabling DEBUG for every discovered module ({} total)", modules.size());
		}

		LoggerContext                                      ctx           = (LoggerContext) LogManager.getContext(false);
		org.apache.logging.log4j.core.config.Configuration cfg           = ctx.getConfiguration();
		Collection<Appender>                               rootAppenders = cfg.getAppenders().values();

		if (rootAppenders.isEmpty()) {
			log.warn("No appenders registered in the running log4j2 configuration — DEBUG logs cannot be routed");
			return;
		}

		for (String moduleName : modules) {
			if (moduleName == null || moduleName.isBlank()) {
				continue;
			}
			installDebugLogger(cfg, rootAppenders, moduleName);
			log.info("Debug logging enabled for module '{}'", moduleName);
		}

		ctx.updateLoggers();
	}

}
