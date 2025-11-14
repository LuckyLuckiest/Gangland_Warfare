package me.luckyraven.compatibility;

import me.luckyraven.util.utilities.ReflectionUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;

public final class CompatibilitySetup {

	private static final Logger logger = LogManager.getLogger(CompatibilitySetup.class.getSimpleName());

	private final VersionSetup versionSetup;

	public CompatibilitySetup(VersionSetup versionSetup) {
		this.versionSetup = versionSetup;
	}

	@Nullable
	public <T> T getCompatibleVersion(Class<T> interfaceClazz, String directory) {
		String version = versionSetup.getVersionString();

		try {
			String         classPath = directory + "." + version;
			Class<?>       clazz     = Class.forName(classPath, false, interfaceClazz.getClassLoader());
			Constructor<?> cons      = ReflectionUtil.getDeclaredConstructor(clazz);
			Object         comp      = ReflectionUtil.newInstance(cons);

			return interfaceClazz.cast(comp);
		} catch (ClassNotFoundException | ClassCastException exception) {
			String bukkitVersion = Version.getBukkitVersion();

			logger.warn("There was no {} found... Unsupported version?", bukkitVersion);
		} catch (InternalError exception) {
			logger.error("Failed to load the compatible version.", exception);
		}

		return null;
	}

}
