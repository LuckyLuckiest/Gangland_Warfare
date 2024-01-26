package me.luckyraven.util;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class ReflectionUtil {

	private static final String BUKKIT_VERSION;
	private static final String NMS_VERSION;
	private static final String CRAFTBUKKIT_VERSION;
	private static final int    MINECRAFT_VERSION;

	static {
		BUKKIT_VERSION      = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
		MINECRAFT_VERSION   = Integer.parseInt(BUKKIT_VERSION.split("_")[1]);
		NMS_VERSION         = "net.minecraft.server." + BUKKIT_VERSION + ".";
		CRAFTBUKKIT_VERSION = "org.bukkit.craftbukkit." + BUKKIT_VERSION + ".";
	}

	private ReflectionUtil() { }

	public static int getMinecraftVersion() {
		return MINECRAFT_VERSION;
	}

	public static <T> T newInstance(@NotNull Class<T> constructorClass, Object... parameters) {
		Class<?>[] classes = new Class[parameters.length];
		for (int i = 0; i < parameters.length; i++) {
			classes[i] = parameters[i].getClass();

			classes[i] = switch (classes[i].getSimpleName()) {
				case "Double" -> double.class;
				case "Integer" -> int.class;
				case "Float" -> float.class;
				case "Boolean" -> boolean.class;
				case "Byte" -> byte.class;
				case "Short" -> short.class;
				case "Long" -> long.class;
				default -> classes[i];
			};
		}

		try {
			return newInstance(constructorClass.getConstructor(classes), parameters);
		} catch (NoSuchMethodException exception) {
			throw new InternalError("Failed to instantiate class " + constructorClass + ".", exception);
		}
	}

	public static <T> T newInstance(@NotNull Constructor<T> constructor, Object... parameters) {
		try {
			return constructor.newInstance(parameters);
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
				 InvocationTargetException exception) {
			throw new InternalError("Failed to instantiate class " + constructor + ".", exception);
		}
	}

	public static <T> Constructor<T> getConstructor(@NotNull Class<T> clazz, Class<?>... parameters) {
		try {
			return clazz.getConstructor(parameters);
		} catch (NoSuchMethodException | SecurityException exception) {
			throw new InternalError("Failed to get constructor.", exception);
		}
	}

}
