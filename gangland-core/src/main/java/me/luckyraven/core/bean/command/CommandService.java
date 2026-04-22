package me.luckyraven.core.bean.command;

import lombok.CustomLog;
import me.luckyraven.core.utilities.ReflectionUtil;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@CustomLog
public abstract class CommandService<T> {

	/**
	 * Evaluates a named condition to determine whether a command should be registered.
	 *
	 * @param condition the condition name defined in {@link CommandHandler#condition()}
	 *
	 * @return {@code true} if the condition is satisfied and the command should be registered
	 */
	public abstract boolean invokeCondition(String condition) throws InvocationTargetException, IllegalAccessException;

	/**
	 * Instantiates a command from its class.
	 *
	 * @param clazz the command class to instantiate
	 *
	 * @return the created command instance
	 *
	 * @throws Exception if instantiation fails
	 */
	protected abstract T createInstance(Class<?> clazz) throws Exception;

	/**
	 * Registers a command instance with the underlying command dispatcher.
	 *
	 * @param command the command to register
	 */
	protected abstract void registerCommand(T command);

	/**
	 * Scans the given package for classes annotated with {@link CommandHandler}, evaluates any conditions, sorts them
	 * by {@link CommandPriority} descending (highest first, lowest last), then registers each.
	 *
	 * @param basePackage the package to scan (e.g., {@code "me.luckyraven.command.sub"})
	 * @param classLoader the class loader to use for scanning
	 */
	public void scanAndRegisterCommands(String basePackage, ClassLoader classLoader) {
		try {
			String        path    = basePackage.replace('.', '/');
			Set<Class<?>> classes = ReflectionUtil.findClasses(path, classLoader);

			List<CommandEntry> entries = new ArrayList<>();

			for (Class<?> clazz : classes) {
				if (!clazz.isAnnotationPresent(CommandHandler.class)) continue;

				CommandHandler annotation = clazz.getAnnotation(CommandHandler.class);
				String         condition  = annotation.condition();

				if (!condition.isEmpty() && !invokeCondition(condition)) continue;

				entries.add(new CommandEntry(clazz, annotation.priority()));
			}

			// highest priority first, lowest priority last
			entries.sort(Comparator.comparingInt((CommandEntry e) -> e.priority().getPriority()).reversed());

			for (CommandEntry entry : entries) {
				try {
					T instance = createInstance(entry.clazz());
					registerCommand(instance);
					log.debug("Command {} has been registered!", entry.clazz.getName());
				} catch (Exception exception) {
					log.warn("Failed to instantiate command {}: {}", entry.clazz().getName(), exception.getMessage());
				}
			}
		} catch (Exception exception) {
			log.warn("Error scanning commands: {}", exception.getMessage());
		}
	}

	private record CommandEntry(Class<?> clazz, CommandPriority priority) { }

}
