package org.luckyraven.gangland.util;

import lombok.CustomLog;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.update.PluginVersion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * The Keystone runtime floor: the Keystone version this Gangland build was compiled against
 * ({@code keystone-floor.properties}, filtered from {@code <keystone.version>}). A jar older than that lacks API the
 * plugin calls, so it fails here with one readable line instead of a {@code NoSuchMethodError} mid-bootstrap.
 */
@CustomLog
public final class KeystoneFloor {

	private static final String  RESOURCE = "keystone-floor.properties";
	private static final String  KEY      = "keystone.floor";
	private static final Pattern VERSION  = Pattern.compile("^[0-9]+(\\.[0-9]+){0,2}([-+].*)?$");

	private KeystoneFloor() {
	}

	/**
	 * Whether {@code installed} is at or above {@code floor}. An absent, blank or unrecognisable installed version
	 * passes with a warning: {@link PluginVersion#parse} turns junk into 0.0.0, which would refuse a dev build wrongly.
	 */
	public static boolean satisfied(@Nullable String installed, String floor) {
		if (installed == null || !VERSION.matcher(installed.trim()).matches()) {
			log.warn("Keystone version '{}' is not recognisable; skipping the floor check ({})", installed, floor);
			return true;
		}

		return PluginVersion.parse(installed.trim()).compareTo(PluginVersion.parse(floor)) >= 0;
	}

	/** The floor baked into the jar, or null when the resource is missing, unreadable or was never filtered. */
	public static @Nullable String floor(JavaPlugin plugin) {
		try (InputStream in = plugin.getResource(RESOURCE)) {
			if (in == null) return null;

			Properties props = new Properties();
			props.load(in);
			String value = props.getProperty(KEY);
			if (value == null || value.isBlank() || value.contains("${")) return null;

			return value.trim();
		} catch (IOException exception) {
			log.warn("Could not read {}: {}", RESOURCE, exception.getMessage());
			return null;
		}
	}
}
