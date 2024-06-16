package me.luckyraven.compatibility;

import me.luckyraven.Gangland;
import me.luckyraven.util.ReflectionUtil;
import org.jetbrains.annotations.Nullable;

public final class CompatibilitySetup {

	private final Gangland gangland;

	public CompatibilitySetup(Gangland gangland) {
		this.gangland = gangland;
	}

	@Nullable
	public <T> T getCompatibleVersion(Class<T> interfaceClazz, String directory) {
		String version = this.gangland.getInitializer().getVersionSetup().getVersionString();

		try {
			Class<?> clazz = Class.forName(directory + "." + version, false, interfaceClazz.getClassLoader());
			Object   comp  = ReflectionUtil.newInstance(ReflectionUtil.getConstructor(clazz));

			return interfaceClazz.cast(comp);
		} catch (ClassNotFoundException | ClassCastException exception) {
			Gangland.getLog4jLogger()
					.warn("There was no {} found... Unsupported version?", version);
		}

		return null;
	}

}
