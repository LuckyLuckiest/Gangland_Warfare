package org.luckyraven.gangland.compatibility;

import lombok.CustomLog;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.utilities.ReflectionUtil;

import java.lang.reflect.Constructor;

@CustomLog
public final class CompatibilitySetup {

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

			log.warn("There was no {} found... Unsupported version?", bukkitVersion);
		} catch (InternalError exception) {
			log.error("Failed to load the compatible version.", exception);
		}

		return null;
	}

}
