package me.luckyraven.compatibility;

import me.luckyraven.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

public final class CompatibilitySetup {

	private CompatibilitySetup() { }

	@Nullable
	public static <T> T getCompatibleVersion(Class<T> interfaceClazz, String directory) {
		String version = VersionSetup.getVersionAsString();
		try {
			Class<?> clazz = Class.forName(directory + "." + version, false, interfaceClazz.getClassLoader());
			Object   comp  = ReflectionUtil.newInstance(ReflectionUtil.getConstructor(clazz));
			return interfaceClazz.cast(comp);
		} catch (ClassNotFoundException | ClassCastException ignored) { }
		return null;
	}

}
