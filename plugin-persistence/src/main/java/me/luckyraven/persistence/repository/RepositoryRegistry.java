package me.luckyraven.persistence.repository;

import lombok.CustomLog;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.util.utilities.ReflectionUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Central registry for all repositories in the system. Handles auto-discovery, instantiation, and lifecycle management.
 * Also manages table dependency resolution.
 */
@CustomLog
public class RepositoryRegistry {

	private final JavaPlugin                     plugin;
	private final DatabaseHandler                databaseHandler;
	private final Map<Class<?>, RepositoryEntry> repositories;
	private final Map<String, Table<?>>          tablesByName;
	private final List<Table<?>>                 registeredTables;
	private final Map<Class<?>, Integer>         registrationOrder;

	public RepositoryRegistry(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		this.plugin            = plugin;
		this.databaseHandler   = databaseHandler;
		this.repositories      = new ConcurrentHashMap<>();
		this.tablesByName      = new ConcurrentHashMap<>();
		this.registeredTables  = new ArrayList<>();
		this.registrationOrder = new LinkedHashMap<>();
	}

	/**
	 * Scans and registers all @Repository annotated classes in the given package Handles dependencies by registering
	 * repositories in the correct order
	 */
	public void scanAndRegisterRepositories(String basePackage) {
		try {
			ClassLoader classLoader = plugin.getClass().getClassLoader();
			String      path        = basePackage.replace('.', '/');

			Set<Class<?>>  classes           = ReflectionUtil.findClasses(path, classLoader);
			List<Class<?>> repositoryClasses = new ArrayList<>();

			// First pass: collect all repository classes
			for (Class<?> clazz : classes) {
				if (!clazz.isAnnotationPresent(Repository.class)) continue;

				if (!IRepository.class.isAssignableFrom(clazz)) {
					log.warn("Class {} has @Repository but doesn't implement IRepository!", clazz.getName());
					continue;
				}

				Repository annotation = clazz.getAnnotation(Repository.class);
				String     condition  = annotation.condition();

				// Check if the condition is met
				if (!condition.isEmpty()) {
					boolean invoke = invokeCondition(condition);
					if (!invoke) {
						log.info("Skipping repository {} due to condition: {}", clazz.getSimpleName(), condition);
						continue;
					}
				}

				repositoryClasses.add(clazz);
			}

			// Second pass: sort by dependencies and register
			List<Class<?>> sortedClasses = sortRepositoriesByDependencies(repositoryClasses);

			log.info("Registering and linking {} repositories to their tables", sortedClasses.size());
			for (Class<?> clazz : sortedClasses) {
				try {
					registerRepositoryClass(clazz);
				} catch (Exception exception) {
					log.warn("Failed to instantiate repository {}: {}", clazz.getName(), exception.getMessage());
					log.warn(exception.getStackTrace());
				}
			}

		} catch (Exception exception) {
			log.warn("Error scanning repositories: {}", exception.getMessage());
			log.warn(exception.getStackTrace());
		}
	}

	/**
	 * Manually register a repository instance with proper type safety
	 */
	public <T> void registerRepository(IRepository<T> instance, Class<T> entityType) {
		RepositoryEntry entry = new RepositoryEntry(entityType, instance, instance.getClass());
		repositories.put(entityType, entry);
		registrationOrder.put(entityType, registrationOrder.size());

		// Extract and register the table from the repository
		if (instance instanceof AbstractRepository) {
			try {
				Method getTableMethod = AbstractRepository.class.getDeclaredMethod("getTable");
				getTableMethod.setAccessible(true);

				Table<?> table = (Table<?>) getTableMethod.invoke(instance);
				registeredTables.add(table);
				tablesByName.put(table.getName(), table);

				log.info("Registered table '{}' from repository", table.getName());
			} catch (Exception exception) {
				log.warn("Failed to extract table from repository: {}", exception.getMessage());
			}
		}

		log.info("Registered repository for: {}", entityType.getSimpleName());
	}

	/**
	 * Get repository for a specific entity type
	 */
	@SuppressWarnings("unchecked")
	public <T> IRepository<T> getRepository(Class<T> entityType) {
		RepositoryEntry entry = repositories.get(entityType);
		if (entry == null) {
			throw new IllegalStateException("No repository registered for: " + entityType.getName());
		}
		return (IRepository<T>) entry.instance();
	}

	/**
	 * Get repository for a generic entity type (e.g., User<? extends OfflinePlayer>) This method uses the raw class
	 * type but returns the properly typed repository
	 *
	 * @param rawEntityType the raw class (e.g., User.class)
	 *
	 * @return the repository instance with preserved generic signature
	 */
	@SuppressWarnings("unchecked")
	public <T> IRepository<T> getGenericRepository(Class<?> rawEntityType) {
		RepositoryEntry entry = repositories.get(rawEntityType);
		if (entry == null) {
			throw new IllegalStateException("No repository registered for: " + rawEntityType.getName());
		}

		// Verify this is marked as a generic repository
		Repository annotation = entry.repositoryClass().getAnnotation(Repository.class);
		if (annotation != null && !annotation.isGeneric()) {
			log.warn("Repository for {} is not marked as generic but getGenericRepository was called",
					 rawEntityType.getSimpleName());
		}

		return (IRepository<T>) entry.instance();
	}

	/**
	 * Check if repository exists for entity type
	 */
	public boolean hasRepository(Class<?> entityType) {
		return repositories.containsKey(entityType);
	}

	/**
	 * Get all registered repositories in registration order
	 */
	public Collection<IRepository<?>> getAllRepositories() {
		return repositories.values()
				.stream().map(RepositoryEntry::instance).collect(Collectors.toList());
	}

	/**
	 * Get all tables that were registered through repositories
	 */
	public List<Table<?>> getRegisteredTables() {
		return Collections.unmodifiableList(registeredTables);
	}

	/**
	 * Get a table by name
	 */
	public Table<?> getTable(String tableName) {
		return tablesByName.get(tableName);
	}

	/**
	 * Save all data across all repositories
	 */
	public void saveAll() {
		saveAll(null);
	}

	/**
	 * Save all data across all repositories, invoking {@code onComplete} after every repository has finished.
	 * Repositories backed by {@link AbstractRepository} run asynchronously; the callback fires on whichever thread
	 * completes the last save.
	 *
	 * @param onComplete called once when all saves have finished, or {@code null} for fire-and-forget
	 */
	public void saveAll(Runnable onComplete) {
		Collection<RepositoryEntry> entries = repositories.values();
		int                         total   = entries.size();

		if (total == 0) {
			if (onComplete != null) onComplete.run();
			return;
		}

		AtomicInteger remaining = new AtomicInteger(total);
		Runnable countDown = () -> {
			if (remaining.decrementAndGet() == 0 && onComplete != null) {
				onComplete.run();
			}
		};

		for (RepositoryEntry entry : entries) {
			try {
				if (entry.instance() instanceof AbstractRepository<?> repo) {
					repo.saveAllFromMemory(countDown);
				} else {
					entry.instance().saveAllFromMemory();
					countDown.run();
				}
			} catch (Exception e) {
				log.error("Failed to save data for repository: {}", entry.entityType().getSimpleName(), e);
				countDown.run();
			}
		}
	}

	/**
	 * Initialize database tables for all registered repositories
	 */
	public void createTables() throws Exception {
		log.info("Creating {} tables from repositories...", registeredTables.size());

		// Sort tables by dependencies
		List<Table<?>> sortedTables = sortTablesByDependencies(registeredTables);

		for (Table<?> table : sortedTables) {
			try {
				var database = databaseHandler.getDatabase();
				var tableDb  = database.table(table.getName());

				tableDb.createTable(table.createTableQuery(tableDb));
				table.validateSchema(database);
			} catch (Exception e) {
				log.error("Failed to create table {}: {}", table.getName(), e.getMessage());
				throw e;
			}
		}
	}

	/**
	 * Sort repositories by their table dependencies A repository that depends on another repository's table should be
	 * registered after it
	 */
	private List<Class<?>> sortRepositoriesByDependencies(List<Class<?>> repositoryClasses) {
		List<Class<?>> sorted    = new ArrayList<>();
		Set<Class<?>>  processed = new HashSet<>();

		while (sorted.size() < repositoryClasses.size()) {
			boolean addedAny = false;

			for (Class<?> repoClass : repositoryClasses) {
				if (processed.contains(repoClass)) continue;

				// Check if all dependencies are registered
				if (!canRegisterRepository(repoClass)) {
					continue;
				}

				sorted.add(repoClass);
				processed.add(repoClass);
				addedAny = true;
			}

			if (!(!addedAny && sorted.size() < repositoryClasses.size())) {
				continue;
			}

			log.warn("Circular dependency in repositories detected, registering remaining repositories in order");
			for (Class<?> repoClass : repositoryClasses) {
				if (!processed.contains(repoClass)) {
					sorted.add(repoClass);
					processed.add(repoClass);
				}
			}
			break;
		}

		return sorted;
	}

	/**
	 * Check if a repository can be registered Returns true if all its table dependencies are already registered
	 */
	private boolean canRegisterRepository(Class<?> repoClass) {
		try {
			// Get the entity type from annotation
			Repository annotation = repoClass.getAnnotation(Repository.class);
			if (annotation == null) return false;

			// Get constructor to inspect parameter types
			Constructor<?>[] constructors = repoClass.getConstructors();

			for (Constructor<?> constructor : constructors) {
				Class<?>[] paramTypes = constructor.getParameterTypes();

				// Look for constructors that take Table parameters (besides JavaPlugin and DatabaseHandler)
				for (Class<?> paramType : paramTypes) {
					// Skip JavaPlugin and DatabaseHandler
					if (paramType == JavaPlugin.class || paramType == DatabaseHandler.class) {
						continue;
					}

					// Check if it's a Table type
					if (!Table.class.isAssignableFrom(paramType)) {
						continue;
					}

					String tableName = getTableName(paramType);

					// If this table hasn't been registered yet, we can't instantiate this repository
					if (!tablesByName.containsKey(tableName)) {
						continue;
					}

					log.debug("Repository {} depends on table {} which is not yet registered",
							  repoClass.getSimpleName(), tableName);
					return false;
				}
			}

			return true;
		} catch (Exception e) {
			log.warn("Error checking repository dependencies for {}: {}", repoClass.getName(), e.getMessage());
			return true; // Assume it can be registered on error
		}
	}

	/**
	 * Get the table name from a Table class
	 */
	private String getTableName(Class<?> tableClass) {
		try {
			// Instantiate with no args to get name (or use reflection to find name constant)
			Object instance = tableClass.getConstructor().newInstance();
			if (instance instanceof Table<?>) {
				return ((Table<?>) instance).getName();
			}
		} catch (Exception e) {
			// Fallback: extract from class name
			return tableClass.getSimpleName().replace("Table", "").toLowerCase();
		}
		return tableClass.getSimpleName().toLowerCase();
	}

	/**
	 * Non-generic helper method to register a repository class Shares table instances across repositories to maintain
	 * consistency
	 */
	private void registerRepositoryClass(Class<?> repoClass) throws Exception {
		Repository annotation = repoClass.getAnnotation(Repository.class);
		if (annotation == null) {
			throw new IllegalArgumentException("Class must be annotated with @Repository");
		}

		Class<?> entityType = annotation.value();

		// Find and instantiate constructor with table dependencies
		Constructor<?> targetConstructor = null;
		Object[]       constructorArgs   = null;

		Constructor<?>[] constructors = repoClass.getConstructors();

		for (Constructor<?> constructor : constructors) {
			Class<?>[] paramTypes = constructor.getParameterTypes();
			Object[]   args       = new Object[paramTypes.length];
			boolean    canUse     = true;

			for (int i = 0; i < paramTypes.length; i++) {
				Class<?> paramType = paramTypes[i];

				if (paramType == JavaPlugin.class) {
					args[i] = plugin;
				} else if (paramType == DatabaseHandler.class) {
					args[i] = databaseHandler;
				} else if (Table.class.isAssignableFrom(paramType)) {
					// Try to get already-registered table instance
					String   tableName = getTableName(paramType);
					Table<?> table     = tablesByName.get(tableName);

					if (table == null) {
						canUse = false;
						break;
					}

					args[i] = table;
				} else {
					canUse = false;
					break;
				}
			}

			if (!canUse) continue;

			targetConstructor = constructor;
			constructorArgs   = args;
			break;
		}

		if (targetConstructor == null) {
			throw new NoSuchMethodException("No suitable constructor found for " + repoClass.getName() +
											". Must accept (JavaPlugin, DatabaseHandler) or additional Table parameters");
		}

		// Create instance
		IRepository<?> instance = (IRepository<?>) targetConstructor.newInstance(constructorArgs);

		// Extract and register the table from the repository
		if (instance instanceof AbstractRepository) {
			try {
				Method getTableMethod = AbstractRepository.class.getDeclaredMethod("getTable");
				getTableMethod.setAccessible(true);
				Table<?> table = (Table<?>) getTableMethod.invoke(instance);

				registeredTables.add(table);
				tablesByName.put(table.getName(), table);
			} catch (Exception e) {
				log.warn("Failed to extract table from repository {}: {}", repoClass.getSimpleName(), e.getMessage());
			}
		}

		// Store in registry
		RepositoryEntry entry = new RepositoryEntry(entityType, instance, repoClass);
		repositories.put(entityType, entry);
		registrationOrder.put(entityType, registrationOrder.size());
	}

	/**
	 * Sort tables by their foreign key dependencies
	 */
	private List<Table<?>> sortTablesByDependencies(List<Table<?>> tables) {
		List<Table<?>> sorted    = new ArrayList<>();
		Set<String>    processed = new HashSet<>();

		while (sorted.size() < tables.size()) {
			boolean addedAny = false;

			for (Table<?> table : tables) {
				if (processed.contains(table.getName())) continue;

				// Check if all dependencies are processed
				boolean canAdd = true;
				for (var attr : table.getAttributes().values()) {
					if (attr.getAssociatedTable() == null) continue;

					String depName = attr.getAssociatedTable().getName();

					if (processed.contains(depName)) continue;

					canAdd = false;
					break;
				}

				if (!canAdd) continue;

				sorted.add(table);
				processed.add(table.getName());
				addedAny = true;
			}

			if (!(!addedAny && sorted.size() < tables.size())) {
				continue;
			}

			log.warn("Possible circular dependency detected, adding remaining tables");

			for (Table<?> table : tables) {
				if (processed.contains(table.getName())) continue;

				sorted.add(table);
				processed.add(table.getName());
			}

			break;
		}

		return sorted;
	}

	/**
	 * Invoke a condition method
	 */
	private boolean invokeCondition(String condition) {
		try {
			String[] parts = condition.replace("()", "").split("\\.");
			if (parts.length < 2) return false;

			String methodName = parts[parts.length - 1];
			String className  = String.join(".", Arrays.copyOf(parts, parts.length - 1));

			Class<?> clazz  = Class.forName(className);
			Method   method = clazz.getMethod(methodName);
			Object   result = method.invoke(null);

			return result instanceof Boolean && (Boolean) result;
		} catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
				 InvocationTargetException e) {
			log.warn("Failed to invoke condition '{}': {}", condition, e.getMessage());
			return false;
		}
	}

	private record RepositoryEntry(Class<?> entityType, IRepository<?> instance, Class<?> repositoryClass) { }
}
