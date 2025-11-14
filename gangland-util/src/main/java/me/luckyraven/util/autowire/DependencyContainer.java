package me.luckyraven.util.autowire;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A lightweight dependency injection container that manages component instances and performs automatic dependency
 * injection.
 */
public class DependencyContainer {

	private static final Logger logger = LogManager.getLogger(DependencyContainer.class.getSimpleName());

	// Store instances by type
	private final Map<Class<?>, List<Object>> instances = new ConcurrentHashMap<>();

	// Store instances by name (for named components)
	private final Map<String, Object> namedInstances = new ConcurrentHashMap<>();

	/**
	 * Register a single instance in the container.
	 */
	public <T> void registerInstance(Class<T> type, T instance) {
		instances.computeIfAbsent(type, k -> new ArrayList<>()).add(instance);

		// Also register by all superclasses and interfaces
		registerHierarchy(instance.getClass(), instance);

		logger.debug("Registered instance of type: {}", type.getName());
	}

	/**
	 * Register an instance with a specific name.
	 */
	public <T> void registerInstance(String name, Class<T> type, T instance) {
		registerInstance(type, instance);
		namedInstances.put(name, instance);
		logger.debug("Registered named instance '{}' of type: {}", name, type.getName());
	}

	/**
	 * Get an instance by type. Returns the first registered instance if multiple exist.
	 */
	@SuppressWarnings("unchecked")
	public <T> T getInstance(Class<T> type) {
		List<Object> list = instances.get(type);
		if (list == null || list.isEmpty()) {
			return null;
		}
		return (T) list.getFirst();
	}

	/**
	 * Get an instance by name.
	 */
	@SuppressWarnings("unchecked")
	public <T> T getInstance(String name, Class<T> type) {
		Object instance = namedInstances.get(name);
		if (type.isInstance(instance)) {
			return (T) instance;
		}
		return null;
	}

	/**
	 * Get all instances of a specific type.
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> getAllInstances(Class<T> type) {
		List<Object> list = instances.get(type);
		if (list == null) {
			return Collections.emptyList();
		}
		return list.stream().map(obj -> (T) obj).collect(Collectors.toList());
	}

	/**
	 * Check if an instance of the given type exists.
	 */
	public boolean hasInstance(Class<?> type) {
		List<Object> list = instances.get(type);
		return list != null && !list.isEmpty();
	}

	/**
	 * Create an instance of a class with automatic dependency injection.
	 */
	public <T> T createInstance(Class<T> clazz, Object... additionalArgs) throws Exception {
		// Find the best constructor to use
		Constructor<T> constructor = findBestConstructor(clazz);

		if (constructor == null) {
			throw new IllegalStateException("No suitable constructor found for " + clazz.getName());
		}

		// Resolve constructor parameters
		Object[] args = resolveConstructorParameters(constructor, additionalArgs);

		// Create the instance
		constructor.setAccessible(true);
		return constructor.newInstance(args);
	}

	/**
	 * Clear all registered instances.
	 */
	public void clear() {
		instances.clear();
		namedInstances.clear();
		logger.debug("Cleared all registered instances");
	}

	/**
	 * Get the number of registered types.
	 */
	public int getRegisteredTypeCount() {
		return instances.size();
	}

	/**
	 * Get the total number of registered instances.
	 */
	public int getTotalInstanceCount() {
		return instances.values()
				.stream().mapToInt(List::size).sum();
	}

	/**
	 * Register instances up the class hierarchy.
	 */
	private void registerHierarchy(Class<?> clazz, Object instance) {
		// Register superclasses
		Class<?> superClass = clazz.getSuperclass();
		if (superClass != null && superClass != Object.class) {
			instances.computeIfAbsent(superClass, k -> new ArrayList<>()).add(instance);
			registerHierarchy(superClass, instance);
		}

		// Register interfaces
		for (Class<?> clazzInterface : clazz.getInterfaces()) {
			instances.computeIfAbsent(clazzInterface, k -> new ArrayList<>()).add(instance);
		}
	}

	/**
	 * Find the best constructor for dependency injection. Priority: @Autowired constructor > constructor with most
	 * parameters > no-arg constructor
	 */
	@SuppressWarnings("unchecked")
	private <T> Constructor<T> findBestConstructor(Class<T> clazz) {
		Constructor<?>[] constructors = clazz.getDeclaredConstructors();

		// Look for @Autowired constructor
		for (Constructor<?> constructor : constructors) {
			if (constructor.isAnnotationPresent(Autowired.class)) {
				return (Constructor<T>) constructor;
			}
		}

		// Find constructor with most parameters that we can satisfy
		Constructor<?> bestConstructor      = null;
		int            maxSatisfiableParams = -1;

		for (Constructor<?> constructor : constructors) {
			Parameter[] params           = constructor.getParameters();
			int         satisfiableCount = 0;

			for (Parameter param : params) {
				if (hasInstance(param.getType())) {
					satisfiableCount++;
				}
			}

			if (satisfiableCount > maxSatisfiableParams) {
				maxSatisfiableParams = satisfiableCount;
				bestConstructor      = constructor;
			}
		}

		return (Constructor<T>) bestConstructor;
	}

	/**
	 * Resolve parameters for a constructor using registered instances.
	 */
	private Object[] resolveConstructorParameters(Constructor<?> constructor, Object[] additionalArgs) {
		Parameter[] parameters = constructor.getParameters();
		Object[]    args       = new Object[parameters.length];

		int additionalArgIndex = 0;

		for (int i = 0; i < parameters.length; i++) {
			Parameter param     = parameters[i];
			Class<?>  paramType = param.getType();

			// First, try to use additional args
			if (additionalArgIndex < additionalArgs.length &&
				paramType.isInstance(additionalArgs[additionalArgIndex])) {
				args[i] = additionalArgs[additionalArgIndex++];
				continue;
			}

			// Try to get from container
			Object instance = getInstance(paramType);

			if (instance != null) {
				args[i] = instance;
			} else {
				// Check if required
				boolean required = true;
				if (param.isAnnotationPresent(Autowired.class)) {
					required = param.getAnnotation(Autowired.class).required();
				}

				if (required) {
					String format = String.format("Cannot resolve required parameter of type %s for constructor in %s",
												  paramType.getName(), constructor.getDeclaringClass().getName());

					throw new IllegalStateException(format);
				}

				args[i] = null;
			}
		}

		return args;
	}
}
