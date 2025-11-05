package me.luckyraven.compatibility;

import me.luckyraven.Gangland;
import me.luckyraven.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;

public final class CompatibilitySetup {

	private final Gangland gangland;

	public CompatibilitySetup(Gangland gangland) {
		this.gangland = gangland;
	}

	@Nullable
	public <T> T getCompatibleVersion(Class<T> interfaceClazz, String directory) {
		String version = this.gangland.getInitializer().getVersionSetup().getVersionString();

		try {
			String         classPath = directory + "." + version;
			Class<?>       clazz     = Class.forName(classPath, false, interfaceClazz.getClassLoader());
			Constructor<?> cons      = ReflectionUtil.getDeclaredConstructor(clazz);
			Object         comp      = ReflectionUtil.newInstance(cons);

			return interfaceClazz.cast(comp);
		} catch (ClassNotFoundException | ClassCastException exception) {
			String bukkitVersion = Version.getBukkitVersion();

			Gangland.getLog4jLogger().warn("There was no {} found... Unsupported version?", bukkitVersion);
		} catch (InternalError exception) {
			Gangland.getLog4jLogger().error("Failed to load the compatible version.", exception);
		}

		return null;
	}

}
