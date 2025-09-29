package me.luckyraven.util;

import me.luckyraven.Gangland;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public final class ReflectionUtil {

	private ReflectionUtil() { }

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

	public static <T> Constructor<T> getDeclaredConstructor(@NotNull Class<T> clazz, Class<?>... parameters) {
		try {
			return clazz.getDeclaredConstructor(parameters);
		} catch (NoSuchMethodException | SecurityException exception) {
			throw new InternalError("Failed to get constructor.", exception);
		}
	}

	public static Set<Class<?>> getAllClasses(String packageName) {
		try {
			ClassLoader      classLoader = Thread.currentThread().getContextClassLoader();
			Enumeration<URL> resources   = classLoader.getResources(packageName.replace('.', '/'));
			Set<Class<?>>    classes     = new HashSet<>();

			while (resources.hasMoreElements()) {
				URL resource = resources.nextElement();

				if (resource.getProtocol().equals("jar")) classes.addAll(getClassesFromJar(resource, packageName));
				else classes.addAll(getClassesFromDirectory(resource, packageName));

				classes.addAll(getClassesFromResource(resource, packageName));
			}

			return classes;
		} catch (IOException exception) {
			Gangland.getLog4jLogger().error(exception);
		}
		return Collections.emptySet();
	}

	private static Set<Class<?>> getClassesFromResource(URL resource, String packageName) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream()))) {
			return reader.lines()
						 .filter(line -> line.endsWith(".class"))
						 .map(line -> getClass(line, packageName))
						 .collect(Collectors.toSet());
		} catch (IOException exception) {
			Gangland.getLog4jLogger().error(exception);
			return Collections.emptySet();
		}
	}

	private static Set<Class<?>> getClassesFromDirectory(URL resource, String packageName) {
		Set<Class<?>> classes = new HashSet<>();
		try {
			String directoryPath = URLDecoder.decode(resource.getPath(), StandardCharsets.UTF_8);
			File   directory     = new File(directoryPath);

			if (!(directory.exists() && directory.isDirectory())) return classes;

			for (File file : Objects.requireNonNull(directory.listFiles())) {
				if (!file.getName().endsWith(".class")) continue;

				String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
				classes.add(Class.forName(className));
			}
		} catch (Exception exception) {
			Gangland.getLog4jLogger().error(exception);
		}
		return classes;
	}

	private static Set<Class<?>> getClassesFromJar(URL resource, String packageName) {
		Set<Class<?>> classes = new HashSet<>();
		try {
			JarURLConnection      jarConn = (JarURLConnection) resource.openConnection();
			JarFile               jarFile = jarConn.getJarFile();
			Enumeration<JarEntry> entries = jarFile.entries();

			String packagePath = packageName.replace('.', '/');

			while (entries.hasMoreElements()) {
				JarEntry entry     = entries.nextElement();
				String   entryName = entry.getName();

				if (!(entryName.startsWith(packagePath) && entryName.endsWith(".class"))) continue;

				String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
				classes.add(Class.forName(className));
			}
		} catch (Exception exception) {
			Gangland.getLog4jLogger().error(exception);
		}
		return classes;
	}

	private static Class<?> getClass(String className, String packageName) {
		try {
			return Class.forName(packageName + "." + className.substring(0, className.lastIndexOf('.')));
		} catch (ClassNotFoundException exception) {
			throw new InternalError("Failed to get class.", exception);
		}
	}

}
