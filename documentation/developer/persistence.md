# Persistence Layer

Developer reference for the Gangland Warfare database and file persistence systems.

---

## Overview

The persistence layer is split across two modules:

| Module               | Role                                                                                    |
|----------------------|-----------------------------------------------------------------------------------------|
| `plugin-persistence` | Generic repository pattern, database abstraction, file handling, query builder          |
| `gangland-impl`      | Concrete table definitions, concrete repositories, database setup, auto-save scheduling |

Key characteristics:

- **Generic repository pattern** -- `IRepository<T>` / `AbstractRepository<T>` backed by a `Table<T>` definition.
- **Dual database support** -- MySQL and SQLite via HikariCP connection pooling.
- **Auto-discovery** -- `@Repository`-annotated classes are scanned, dependency-sorted, and registered at startup.
- **Async saves** -- Writes run on Bukkit async scheduler threads through `DatabaseHelper`.
- **Schema migration** -- `Table.validateSchema()` adds missing columns, drops obsolete ones, and fixes nullability
  mismatches automatically.

### Package layout

```
plugin-persistence
  me.luckyraven.persistence
    ├── database
    │   ├── component       # Table, Attribute, AttributeLink
    │   ├── query           # QueryBuilder, Column
    │   ├── type            # MySQL, SQLite implementations
    │   ├── Database.java           # Core database interface
    │   ├── DatabaseHandler.java    # Abstract HikariCP lifecycle
    │   ├── DatabaseHelper.java     # Sync/async query execution
    │   ├── DatabaseManager.java    # Multi-database registry + backup
    │   └── DatabaseSettingsProvider.java  # Settings contract
    ├── repository
    │   ├── IRepository.java
    │   ├── AbstractRepository.java
    │   ├── Repository.java         # @Repository annotation
    │   └── RepositoryRegistry.java
    ├── FileHandler.java
    ├── FileLoader.java
    ├── FileManager.java
    └── FolderLoader.java

gangland-impl
  me.luckyraven.database
    ├── GanglandDatabase.java
    ├── GanglandDatabaseSettings.java
    ├── tables/          # Concrete Table<T> definitions
    └── repositories/    # Concrete AbstractRepository<T> implementations
```

---

## Repository Pattern

### IRepository\<T\> Interface

The top-level contract that every repository must implement:

```java
public interface IRepository<T> {

    Collection<T> loadAll();

    void save(T data);

    void saveAll(Collection<T> collection);

    void saveAllFromMemory();

    void delete(T data);

    void setDataSupplier(Supplier<Collection<T>> dataSupplier);
}
```

| Method                | Description                                                                                     |
|-----------------------|-------------------------------------------------------------------------------------------------|
| `loadAll()`           | Reads all rows from the database and returns domain objects. Called once during initialization. |
| `save(T)`             | Persists a single entity asynchronously (upsert -- insert or update).                           |
| `saveAll(Collection)` | Persists a batch of entities asynchronously.                                                    |
| `saveAllFromMemory()` | Persists every entity supplied by the registered `dataSupplier`. Used by auto-save.             |
| `delete(T)`           | Removes the entity from the database asynchronously.                                            |
| `setDataSupplier()`   | Registers a `Supplier<Collection<T>>` that provides the in-memory dataset for bulk saves.       |

### AbstractRepository\<T\>

Template-method base class that handles the sync/async plumbing. Concrete repositories extend this and implement four
abstract methods:

```java
public abstract class AbstractRepository<T> implements IRepository<T> {

    // --- Abstract (must implement) ---

    /** Load all rows from DB. Called inside a synchronized DatabaseHelper block. */
    protected abstract Collection<T> doLoadAll() throws SQLException;

    /** Optional pre-save hook. Return null to skip. */
    protected abstract <E> Consumer<E> processSave();

    /** Return the Table<T> that defines the schema for this repository. */
    protected abstract Table<T> getTable();

    /** Delete a single entity from DB. */
    protected abstract void doDelete(T data) throws SQLException;
}
```

#### Save flow (upsert)

When `save(T)` or `saveAll(Collection)` is called, `AbstractRepository` executes the following for each entity:

1. Call `processSave()` -- if non-null, the returned `Consumer` is invoked on the entity (pre-save hook).
2. Call `isRowAvailable(data, database)` -- executes a SELECT using the table's `searchCriteria()`.
3. If the row exists: call `updateData(data, database)` which delegates to `Table.updateTableQuery()`.
4. If the row does not exist: call `insertData(data, database)` which delegates to `Table.insertTableQuery()`.

All save operations run asynchronously via `DatabaseHelper.runQueriesAsync()` unless the plugin is disabled (in which
case they run synchronously on the current thread).

#### Constructor

```java
public AbstractRepository(JavaPlugin plugin, DatabaseHandler databaseHandler)
```

The `DatabaseHandler` provides the live `Database` connection. A `DatabaseHelper` is created internally to manage query
execution.

### RepositoryRegistry

Central registry that manages all repository instances. Keyed by entity type (`Class<?>`).

```java
public class RepositoryRegistry {

    RepositoryRegistry(JavaPlugin plugin, DatabaseHandler databaseHandler);

    // Auto-discovery
    void scanAndRegisterRepositories(String basePackage);

    // Manual registration
    <T> void registerRepository(IRepository<T> instance, Class<T> entityType);

    // Retrieval
    <T> IRepository<T> getRepository(Class<T> entityType);
    <T> IRepository<T> getGenericRepository(Class<?> rawEntityType);
    boolean hasRepository(Class<?> entityType);
    Collection<IRepository<?>> getAllRepositories();

    // Table management
    List<Table<?>> getRegisteredTables();
    Table<?> getTable(String tableName);
    void createTables();

    // Bulk save
    void saveAll();
    void saveAll(Runnable onComplete);
}
```

#### Auto-discovery flow

`scanAndRegisterRepositories("me.luckyraven.database.repositories")` performs:

1. **Scan** -- Uses `ReflectionUtil.findClasses()` to find all classes under the package.
2. **Filter** -- Keeps only classes annotated with `@Repository` that implement `IRepository`.
3. **Condition check** -- If `@Repository(condition = "...")` is set, invokes the static method reflectively. Skips the
   repository if it returns `false`.
4. **Dependency sort** -- `sortRepositoriesByDependencies()` topologically orders repositories so that tables referenced
   by foreign keys are registered first.
5. **Instantiate** -- For each repository class, finds a public constructor accepting `(JavaPlugin, DatabaseHandler)` or
   `(JavaPlugin, DatabaseHandler, Table...)`. Table parameters are resolved from already-registered tables.
6. **Extract table** -- Reflectively calls `getTable()` on the new instance and registers the `Table` in `tablesByName`.
7. **Store** -- The repository is stored in `Map<Class<?>, RepositoryEntry>` keyed by the entity type from
   `@Repository(value = ...)`.

> **IMPORTANT:** Registering two repositories under the same entity class **overwrites** the first. The map key is the
> entity `Class<?>`, not the repository class. Tests that register multiple repos for the same type will only see the
> last
> one in `getAllRepositories()`.

#### Table creation

`createTables()` sorts registered tables by foreign key dependencies via `sortTablesByDependencies()`, then for each
table:

1. Calls `table.createTableQuery(database)` to generate the `CREATE TABLE` DDL.
2. Calls `database.table(name).createTable(...)` to execute it.
3. Calls `table.validateSchema(database)` to add missing columns, drop obsolete ones, and fix nullability mismatches.

#### Bulk save with completion tracking

`saveAll(Runnable onComplete)` uses an `AtomicInteger` countdown. Each `AbstractRepository` gets
`saveAllFromMemory(countDown)` which triggers the async save and invokes the countdown runnable when finished. When the
counter reaches zero, `onComplete` fires.

### @Repository Annotation

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Repository {

    /** The entity class this repository manages. Used as the registry key. */
    Class<?> value();

    /** Optional custom name for the repository. */
    String name() default "";

    /** Condition method (e.g. "SettingAddon.isGangEnabled()"). Empty = always register. */
    String condition() default "";

    /** When true, the registry handles generic type matching more flexibly. */
    boolean isGeneric() default false;
}
```

---

## Database Layer

### Database (interface)

The core database operations interface in `plugin-persistence`. Both `MySQL` and `SQLite` implement this.

Key methods:

| Method                                                | Description                                               |
|-------------------------------------------------------|-----------------------------------------------------------|
| `initialize(credentials, schema)`                     | Connect to database with credentials                      |
| `table(String tableName)`                             | Scope subsequent operations to a specific table           |
| `connect()` / `disconnect()`                          | Manage the JDBC connection                                |
| `createTable(String... values)`                       | Execute CREATE TABLE with column definitions              |
| `insert(columns, values, types)`                      | INSERT with PreparedStatement placeholders                |
| `select(row, placeholders, types, columns)`           | SELECT with WHERE clause                                  |
| `selectAll()` / `selectAll(String[])`                 | SELECT all rows (physical order or explicit column order) |
| `update(row, rowPH, rowTypes, cols, colPH, colTypes)` | UPDATE with WHERE clause                                  |
| `delete(column, value, type)`                         | DELETE single-condition                                   |
| `delete(whereClause, placeholders, types)`            | DELETE multi-condition                                    |
| `addColumn(name, type)`                               | ALTER TABLE ADD COLUMN                                    |
| `createSchema(name)` / `dropSchema(name)`             | Schema lifecycle                                          |
| `switchSchema(name)`                                  | Change active schema (MySQL only)                         |
| `handlesConnectionPool()`                             | Returns `true` for HikariCP-backed implementations        |
| `getColumns()`                                        | List column names for the current table                   |
| `validateSchema()`                                    | See Table.validateSchema below                            |
| `isValidIdentifier(String)`                           | SQL injection guard for DDL identifiers                   |

All data-manipulation methods use `PreparedStatement` with typed placeholders (`java.sql.Types` constants) via
`preparePlaceholderStatements()`.

### DatabaseHandler (abstract)

Abstract base that manages database lifecycle with HikariCP.

```java
public abstract class DatabaseHandler {

    public static final int MYSQL = 0, SQLITE = 1;

    // Must implement
    abstract void createSchema() throws SQLException, IOException;
    abstract void createTables() throws SQLException;
    abstract void insertInitialData() throws SQLException;
    abstract String getSchema();

    // Lifecycle
    void initialize();                 // Runs createSchema -> createTables -> insertInitialData
    void enforceType(int type);        // Force a specific DB type (throws on failure)
    void setType(int type);            // Try type, fall back to SQLite if MySQL fails

    // Accessors
    Database getDatabase();
    int getType();
    DatabaseSettingsProvider getSettings();
}
```

**`enforceType()` behavior:** When `MYSQL` is requested but the connection fails, HikariCP throws
`PoolInitializationException` (a `RuntimeException`), **not** `SQLException`. The `catch (SQLException)` block is
bypassed and `this.database` may be left as a non-null but uninitialized `MySQL` instance. Code checking for MySQL
failure must verify `db == null || db.getConnection() == null`, not `assertNull(db)`.

**`setType()` fallback:** When `MYSQL` is requested but fails, `setType()` automatically falls back to SQLite if
`DatabaseSettingsProvider.isSqliteFailedMysql()` returns `true`.

### DatabaseSettingsProvider (interface)

Contract for providing database connection settings. Decouples `plugin-persistence` from `gangland-impl`'s `Settings`
class.

```java
public interface DatabaseSettingsProvider {
    boolean isSqliteBackup();
    boolean isSqliteFailedMysql();
    String getMysqlHost();
    int getMysqlPort();
    String getMysqlUsername();
    String getMysqlPassword();
}
```

Implemented by `GanglandDatabaseSettings` in `gangland-impl`, which delegates to `Settings.*` static methods.

### DatabaseHelper

Manages connection lifecycle and provides sync/async query execution.

```java
public class DatabaseHelper {

    DatabaseHelper(JavaPlugin plugin, DatabaseHandler databaseHandler);

    /** Synchronous query execution with auto-reconnect and rollback-on-error. */
    void runQueries(QueryRunnable queryRunnable);

    /** Async execution via Bukkit scheduler. Falls back to sync if plugin is disabled. */
    void runQueriesAsync(QueryRunnable queryRunnable);
    void runQueriesAsync(QueryRunnable queryRunnable, Runnable onComplete);

    /** Rolls back the current transaction if not in auto-commit mode. */
    void rollbackConnection();

    @FunctionalInterface
    interface QueryRunnable {
        void run(Database database) throws SQLException;
    }
}
```

**Error handling:** If an exception occurs during `runQueries()`, the connection is rolled back. If the database does
not handle its own connection pool (`handlesConnectionPool() == false`), the connection is disconnected after each query
block.

**Async execution:** `runQueriesAsync()` schedules the work via `Bukkit.getScheduler().runTaskAsynchronously()`. If the
plugin is already disabled (`!plugin.isEnabled()`), the query runs synchronously on the current thread to ensure
shutdown saves complete.

### DatabaseManager

Manages multiple `DatabaseHandler` instances and provides cross-database backup.

```java
public class DatabaseManager {

    DatabaseManager(JavaPlugin plugin, DatabaseSettingsProvider settings);

    void addDatabase(DatabaseHandler database);
    void initializeDatabases();          // Calls initialize() on each handler
    void closeConnections();             // Disconnects all, optionally backing up first
    DatabaseHandler startBackup(DatabaseHandler handler);  // Backup to opposite DB type
    List<DatabaseHandler> getDatabases();
}
```

### GanglandDatabase (gangland-impl)

The concrete `DatabaseHandler` for Gangland Warfare. Wraps HikariCP and owns the `RepositoryRegistry`.

```java
public class GanglandDatabase extends DatabaseHandler {

    GanglandDatabase(JavaPlugin plugin, String schema, DatabaseSettingsProvider settings);

    RepositoryRegistry getRepositoryRegistry();

    // Implementation delegates table creation to RepositoryRegistry.createTables()
    // Schema: "gangland" for MySQL, "database/gangland" for SQLite
}
```

---

## Table Definitions

### Table\<T\> (abstract)

Each database table is defined as a `Table<T>` subclass that declares its columns as `Attribute` objects.

```java
public abstract class Table<T> {

    Table(String name);

    // Must implement
    abstract Object[] getData(T data);                    // Entity -> column values array
    abstract Map<String, Object> searchCriteria(T data);  // WHERE clause for upsert lookups

    // Column management
    protected void addAttribute(Attribute<?> attribute);
    Attribute<?> get(String column);
    Set<String> getColumns();                             // Insertion-ordered column names
    Map<String, Attribute<?>> getAttributes();

    // DDL generation
    String[] createTableQuery(Database database);         // CREATE TABLE column definitions
    void validateSchema(Database database);               // Add/drop/fix columns vs. live DB

    // DML execution
    void insertTableQuery(Database database, T data);     // INSERT row
    void updateTableQuery(Database database, T data);     // UPDATE row (excludes search columns)
    List<Object[]> selectAllTableQuery(Database database); // SELECT with explicit column order

    // Helper for building searchCriteria maps
    protected Map<String, Object> createSearchCriteria(
        String searchQuery, Object[] queryPlaceholder,
        int[] queryDataTypes, int[] ignoredIndexes);
}
```

**`searchCriteria()` return format:**

The returned map must contain four keys:

- `"search"` -- the WHERE clause string with `?` placeholders (e.g. `"uuid = ?"`)
- `"info"` -- `Object[]` of placeholder values
- `"type"` -- `int[]` of JDBC type constants
- `"index"` -- `int[]` of column indexes to exclude from UPDATE (typically the primary key indexes)

**`validateSchema()`** performs three operations after table creation:

1. Adds columns defined in code but missing from the live DB.
2. Drops columns present in the live DB but absent from the definition.
3. Fixes nullability mismatches (SQLite: recreate table via rename-create-copy-drop; MySQL:
   `ALTER TABLE MODIFY COLUMN`).

**`selectAllTableQuery()`** issues `SELECT col1, col2, ...` with an explicit column list derived from `getColumns()` (
definition order), rather than `SELECT *` (physical DB order). This ensures correct positional mapping after
`ALTER TABLE ADD COLUMN` operations.

### Attribute\<T\>

Describes a single database column.

```java
public class Attribute<T> {

    // Constructors
    Attribute(String name, boolean primaryKey, Class<T> classType);
    Attribute(String name, boolean primaryKey, int size, Class<T> classType);
    Attribute(String name, int type, boolean primaryKey, Class<T> classType);
    Attribute(String name, boolean primaryKey, int type, int size, Class<T> classType);

    // Properties
    String getName();          // Always lowercase
    int getType();             // java.sql.Types constant (auto-inferred from classType)
    int getSize();             // Column size (UUID=36, String=255, others=0)
    boolean isPrimaryKey();
    boolean isUnique();
    boolean isCanBeNull();     // Default: false
    T getDefaultValue();       // SQL DEFAULT value

    // Foreign key
    void setForeignKey(Attribute<?> attribute, Table<?> associatedTable);
    Attribute<?> getForeignKey();
    Table<?> getAssociatedTable();
}
```

Default sizes by type: `UUID` = 36, `String` = 255, everything else = 0 (no size constraint).

### AttributeLink (annotation)

Field-level annotation for declarative attribute configuration:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AttributeLink {
    boolean primaryKey() default false;
    boolean unique() default false;
    boolean nullable() default true;
    String defaultValue() default "";
}
```

### Concrete Tables

All table classes are in `gangland-impl` under `me.luckyraven.database.tables.*`.

| Table Class            | DB Table Name      | Entity Type                     | Key Columns                                                                                                                    |
|------------------------|--------------------|---------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| `UserTable`            | `user`             | `User<? extends OfflinePlayer>` | uuid (PK), balance, kills, deaths, mob_kills, bounty, level, experience, wanted                                                |
| `BankTable`            | `bank`             | `Bank`                          | uuid (PK, FK->user), name, balance                                                                                             |
| `GangTable`            | `gang`             | `Gang`                          | id (PK), name, display_name, description, color, balance, level, experience, bounty, created                                   |
| `MemberTable`          | `member`           | `Member`                        | uuid (PK, FK->user), gang_id, contribution, rank_id (FK->rank_tree), join_date                                                 |
| `GangAllianceTable`    | `gang_ally`        | `GangAlliance`                  | gang_id (PK, FK->gang), ally_id (UNIQUE, FK->gang), since                                                                      |
| `RankTable`            | `rank_tree`        | `Rank`                          | id (PK), name                                                                                                                  |
| `RankParentTable`      | `rank_parent`      | `RankParent`                    | id (PK), parent_id (UNIQUE, FK->rank_tree)                                                                                     |
| `RankPermissionTable`  | `rank_permission`  | `RankPermission`                | rank_id (PK, FK->rank_tree), permission_id (UNIQUE, FK->permission)                                                            |
| `PermissionTable`      | `permission`       | `Permission`                    | id (PK), name                                                                                                                  |
| `PluginDataTable`      | `plugin_data`      | `PluginData`                    | id (PK), activate_date, scan_date, scheduled_scan_date                                                                         |
| `WaypointTable`        | `waypoint`         | `Waypoint`                      | id (PK), gang_id (FK->gang, nullable), name, world, x, y, z, yaw, pitch, type, shield, timer, cooldown, cost, radius           |
| `WeaponTable`          | `weapon`           | `Weapon`                        | uuid (PK), type                                                                                                                |
| `ParkedCarTable`       | `parked_car`       | `ParkedCar`                     | id (PK), car_id, world, x, y, z, yaw, fuel, max_fuel, durability, placer_uuid (nullable), exhaust_side (nullable)              |
| `LootChestTable`       | `loot_chest`       | `LootChestData`                 | id (PK), world, x, y, z, loot_table_id, tier_id (nullable), respawn_time, inventory_size, display_name, last_opened, is_looted |
| `CopSpawnerTable`      | `cop_spawner`      | `CopSpawner`                    | id (PK), world, x, y, z, yaw, pitch                                                                                            |
| `CivilianSpawnerTable` | `civilian_spawner` | `CivilianSpawner`               | id (PK), type_id (nullable), group_id (nullable), world, x, y, z, yaw, pitch                                                   |
| `JailTable`            | `jail`             | `Jail`                          | id (PK), world, x, y, z, max_capacity                                                                                          |
| `DetainmentTable`      | `detainment`       | `DetainedPlayer`                | player_uuid (PK), jail_id (UNIQUE, nullable, FK->jail), state                                                                  |

#### Foreign key dependency graph

```
user
  <- bank.uuid
  <- member.uuid

rank_tree
  <- rank_parent.parent_id
  <- rank_permission.rank_id
  <- member.rank_id

permission
  <- rank_permission.permission_id

gang
  <- gang_ally.gang_id
  <- gang_ally.ally_id
  <- waypoint.gang_id

jail
  <- detainment.jail_id
```

Tables with no foreign keys (can be created first): `user`, `gang`, `rank_tree`, `permission`, `plugin_data`, `weapon`,
`cop_spawner`, `civilian_spawner`, `jail`, `loot_chest`, `parked_car`.

---

## Concrete Repositories

All repository classes are in `gangland-impl` under `me.luckyraven.database.repositories.*`.

| Repository                  | Entity Type       | Table Dependency                                  |
|-----------------------------|-------------------|---------------------------------------------------|
| `UserRepository`            | `User` (generic)  | `UserTable`                                       |
| `BankRepository`            | `Bank`            | `BankTable(UserTable)`                            |
| `GangRepository`            | `Gang`            | `GangTable`                                       |
| `GangAllianceRepository`    | `GangAlliance`    | `GangAllianceTable(GangTable)`                    |
| `MemberRepository`          | `Member`          | `MemberTable(UserTable, RankTable)`               |
| `RankRepository`            | `Rank`            | `RankTable`                                       |
| `RankParentRepository`      | `RankParent`      | `RankParentTable(RankTable)`                      |
| `RankPermissionRepository`  | `RankPermission`  | `RankPermissionTable(RankTable, PermissionTable)` |
| `PermissionRepository`      | `Permission`      | `PermissionTable`                                 |
| `PluginDataRepository`      | `PluginData`      | `PluginDataTable`                                 |
| `WaypointRepository`        | `Waypoint`        | `WaypointTable(GangTable)`                        |
| `WeaponRepository`          | `Weapon`          | `WeaponTable`                                     |
| `ParkedCarRepository`       | `ParkedCar`       | `ParkedCarTable`                                  |
| `LootChestRepository`       | `LootChestData`   | `LootChestTable`                                  |
| `CopSpawnerRepository`      | `CopSpawner`      | `CopSpawnerTable`                                 |
| `CivilianSpawnerRepository` | `CivilianSpawner` | `CivilianSpawnerTable`                            |
| `JailRepository`            | `Jail`            | `JailTable`                                       |
| `DetainmentRepository`      | `DetainedPlayer`  | `DetainmentTable(JailTable)`                      |

---

## QueryBuilder

Fluent SQL builder that wraps the `Database` interface, replacing raw parallel arrays with named column-value pairs.

```java
QueryBuilder qb = QueryBuilder.on(database, "users");

// INSERT
qb.insert()
  .set("name", "Alice")
  .set("balance", 500.0)
  .execute();

// SELECT -- single row
Object[] row = qb.select("name", "balance")
  .where("uuid", uuid.toString())
  .executeOne();

// SELECT -- all matching rows
List<Object[]> rows = qb.select("*")
  .where("active", true)
  .executeAll();

// UPDATE
qb.update()
  .set("balance", 600.0)
  .where("uuid", uuid.toString())
  .execute();

// DELETE -- multi-condition
qb.delete()
  .where("uuid", uuid.toString())
  .where("active", false)
  .execute();
```

### Column record

```java
public record Column(String name, Object value, int type) {
    static Column of(String name, Object value);               // JDBC type auto-inferred
    static Column of(String name, Object value, int type);     // Explicit JDBC type
}
```

The `QueryBuilder` is composed of four nested classes: `Insert`, `Select`, `Update`, `Delete`. Each supports:

- `.set(name, value)` or `.set(Column)` for data columns.
- `.where(name, value)` or `.where(Column)` for conditions (combined with AND).
- `.execute()` / `.executeOne()` / `.executeAll()` to run the query.

---

## Auto-Save Flow

The periodic save cycle is managed by `PeriodicalUpdates` in `gangland-impl`:

```
PeriodicalUpdates(gangland, interval)
  |
  v  (RepeatingTimer fires every `interval` seconds)
task()
  |
  v
updatingDatabase(onComplete)
  |
  +--> Save user + bank data directly via table queries
  |    (handles online + offline user cache, then clears offline cache)
  |
  +--> repositoryRegistry.saveAll(onComplete)
         |
         +--> For each RepositoryEntry:
         |      AbstractRepository.saveAllFromMemory(countDown)
         |        |
         |        +--> dataSupplier.get()  (snapshot the in-memory collection)
         |        +--> DatabaseHelper.runQueriesAsync(...)
         |              |
         |              +--> Bukkit.getScheduler().runTaskAsynchronously(...)
         |                     |
         |                     +--> For each entity: saveRow(data, database)
         |                     |      1. consumeSave(data)  -- pre-save hook
         |                     |      2. isRowAvailable()   -- SELECT check
         |                     |      3. updateData() or insertData()
         |                     |
         |                     +--> countDown.run()
         |
         +--> When AtomicInteger reaches 0: onComplete.run()
```

### Data supplier registration

Each manager registers its data supplier after repository initialization:

```java
IRepository<Gang> gangRepo = repositoryRegistry.getRepository(Gang.class);
gangRepo.setDataSupplier(() -> gangManager.getGangs().values());
```

This supplier is invoked by `saveAllFromMemory()` to snapshot the current in-memory state before the async save begins.

---

## File Persistence

The file persistence system handles YAML configuration files.

### FileHandler

Wraps a single YAML file with read/write/reload support.

```java
public class FileHandler {

    FileHandler(JavaPlugin plugin, File file);
    FileHandler(JavaPlugin plugin, String name, String fileType);
    FileHandler(JavaPlugin plugin, String name, String directory, String fileType);

    void create(boolean inJar);         // Create file; copy from JAR resources if inJar=true
    void delete();
    void save();                        // Save current FileConfiguration to disk
    void reloadData();                  // Reload and validate config version
    void createNewFile();               // Backup old file and create fresh copy from JAR

    FileConfiguration getFileConfiguration();
    boolean isLoaded();
    String getName();
    String getDirectory();
}
```

**Config versioning:** If the file contains a `Config_Version` key that differs from the plugin version, `FileHandler`
automatically backs up the old file (renaming it with `-old` suffix) and creates a fresh copy from the JAR resources.

### FileManager

Registry for all loaded `FileHandler` instances.

```java
public class FileManager {

    FileManager(JavaPlugin plugin);

    void addFile(FileHandler file, boolean create);
    FileHandler getFile(String fileName);
    boolean contains(String fileName);
    boolean filesLoaded();
    void checkFileLoaded(String name);
    void reloadFiles();
    YamlConfiguration loadFromResources(String resourceFile);
    Set<FileHandler> getFiles();
}
```

### FileLoader\<T\> (abstract)

Base class for loading structured data from files with retry support.

```java
public abstract class FileLoader<T> {

    abstract void clear();
    abstract void loadData(Consumer<T> consumer, FileManager fileManager);

    void load(boolean disable, Consumer<T> consumer, FileManager fileManager);
    void tryAgain(boolean disable, Consumer<T> consumer, FileManager fileManager);
    boolean isDataLoaded();
}
```

### FolderLoader (abstract)

Extends `FileLoader<FileHandler>` to load all YAML files from a directory. Used for configs that span multiple files (
e.g., one file per weapon type).

```java
public abstract class FolderLoader extends FileLoader<FileHandler> {

    FolderLoader(JavaPlugin plugin, String folder);

    abstract void initialize();

    void addFile(FileHandler fileHandler);
    void addExpectedFile(FileHandler fileHandler);
    String getFolderName();
    List<FileHandler> getFiles();
}
```

**Loading flow:**

1. Check if the folder exists and has files.
2. If empty/missing: use expected files list, create them from JAR resources.
3. If populated: scan all `.yml` files in the folder.
4. Register each file with the `FileManager` and pass to the `consumer` callback.

---

## How to Create a New Repository

### Step 1: Define the table

Create a `Table<T>` subclass in `gangland-impl/src/main/java/me/luckyraven/database/tables/`.

```java
package me.luckyraven.database.tables.example;

import me.luckyraven.data.example.Bounty;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

public class BountyTable extends Table<Bounty> {

    public BountyTable() {
        super("bounty");  // SQL table name

        // Define columns
        Attribute<UUID>   uuid   = new Attribute<>("uuid", true, UUID.class);   // primary key
        Attribute<String> target = new Attribute<>("target", false, String.class);
        Attribute<Double> reward = new Attribute<>("reward", false, Double.class);

        // Configure defaults and constraints
        reward.setDefaultValue(0D);
        target.setCanBeNull(true);

        // Register columns in order (LinkedHashMap preserves insertion order)
        this.addAttribute(uuid);
        this.addAttribute(target);
        this.addAttribute(reward);
    }

    @Override
    public Object[] getData(Bounty data) {
        // Must match column order from addAttribute calls
        return new Object[]{
            data.getUuid().toString(),
            data.getTarget(),
            data.getReward()
        };
    }

    @Override
    public Map<String, Object> searchCriteria(Bounty data) {
        // Define WHERE clause for upsert lookups
        // "index" array contains the column indexes to EXCLUDE from UPDATE (typically PKs)
        return createSearchCriteria(
            "uuid = ?",                                    // search query
            new Object[]{data.getUuid().toString()},       // placeholder values
            new int[]{Types.CHAR},                         // placeholder types
            new int[]{0}                                   // ignored indexes (column 0 = uuid)
        );
    }
}
```

**With foreign keys:**

```java
public class BountyTable extends Table<Bounty> {

    public BountyTable(UserTable userTable) {
        super("bounty");

        Attribute<UUID> uuid = new Attribute<>("uuid", true, UUID.class);
        // ... other attributes ...

        // Establish foreign key: bounty.uuid -> user.uuid
        uuid.setForeignKey(userTable.get("uuid"), userTable);

        this.addAttribute(uuid);
        // ...
    }
}
```

### Step 2: Create the repository

Create an `AbstractRepository<T>` subclass in `gangland-impl/src/main/java/me/luckyraven/database/repositories/`.

```java
package me.luckyraven.database.repositories.example;

import me.luckyraven.data.example.Bounty;
import me.luckyraven.database.tables.example.BountyTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.function.Consumer;

@Repository(value = Bounty.class)
public class BountyRepository extends AbstractRepository<Bounty> {

    private final BountyTable bountyTable;

    public BountyRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
        super(plugin, databaseHandler);
        this.bountyTable = new BountyTable();
    }

    @Override
    protected Collection<Bounty> doLoadAll() throws SQLException {
        List<Bounty> bounties = new ArrayList<>();

        List<Object[]> data = bountyTable.selectAllTableQuery(getDatabase());

        for (Object[] result : data) {
            int    v      = 0;
            UUID   uuid   = UUID.fromString(String.valueOf(result[v++]));
            String target = (String) result[v++];
            double reward = (double) result[v];

            bounties.add(new Bounty(uuid, target, reward));
        }

        return bounties;
    }

    @Override
    protected <E> Consumer<E> processSave() {
        // Return null for no pre-save processing,
        // or return a Consumer to run before each save
        return null;
    }

    @Override
    protected Table<Bounty> getTable() {
        return bountyTable;
    }

    @Override
    protected void doDelete(Bounty data) throws SQLException {
        Database table = getDatabase().table(bountyTable.getName());
        table.delete("uuid", data.getUuid().toString(), Types.VARCHAR);
    }
}
```

### Step 3: Wire the data supplier

In the manager or service that owns the in-memory data:

```java
IRepository<Bounty> bountyRepo = repositoryRegistry.getRepository(Bounty.class);
bountyRepo.setDataSupplier(() -> bountyManager.getAllBounties());
```

This enables `saveAllFromMemory()` to find the data to persist during auto-save cycles.

### Step 4: Use the repository

```java
// Load all at startup
Collection<Bounty> bounties = bountyRepo.loadAll();

// Save one entity (async)
bountyRepo.save(bounty);

// Delete one entity (async)
bountyRepo.delete(bounty);

// Bulk save from memory (async, used by auto-save)
bountyRepo.saveAllFromMemory();
```

No additional registration code is needed -- the `@Repository` annotation ensures auto-discovery during
`scanAndRegisterRepositories()`.

### Conditional registration

To only register a repository when a feature is enabled:

```java
@Repository(value = Bounty.class, condition = "me.luckyraven.file.configuration.SettingAddon.isBountyEnabled()")
public class BountyRepository extends AbstractRepository<Bounty> {
    // ...
}
```

The condition string must point to a public static method that returns `boolean`.

### Generic entity types

For entities with type parameters (e.g., `User<? extends OfflinePlayer>`):

```java
@Repository(value = User.class, isGeneric = true)
public class UserRepository extends AbstractRepository<User<? extends OfflinePlayer>> {
    // ...
}

// Retrieve with:
IRepository<User<? extends OfflinePlayer>> repo = repositoryRegistry.getGenericRepository(User.class);
```

---

## Testing Notes

### HikariCP + SQLite on Windows

HikariCP's `minimumIdle=5` eagerly opens 5 native SQLite file handles at pool startup. On Windows, these handles are not
released synchronously after `dataSource.close()`, so JUnit's `@TempDir` auto-cleanup fails with file-locking errors.

**Required practices for database tests:**

1. Use `@TempDir(cleanup = CleanupMode.NEVER)` -- never plain `@TempDir`.
2. Call `MockPluginFactory.releaseDbFiles(tempDir)` at the end of every `@AfterEach` teardown.
3. Always disconnect the database in `@AfterEach` **before** calling `releaseDbFiles`.
4. Track every `DatabaseHandler` created in a test class (use a `List<TestDatabaseHandler> toClose` field + a
   `tracked()` helper) so none are leaked.

Do **not** use retry loops as the primary cleanup strategy -- Windows file locks can persist for several seconds.

### RepositoryRegistry key semantics

The registry is a `Map<Class<?>, RepositoryEntry>` keyed by entity type. Registering two repositories under the same
entity class **overwrites** the first. Tests that register multiple repos for the same type will only see the last one
in `getAllRepositories()`.

### MySQL failure assertions

HikariCP throws `PoolInitializationException` (a `RuntimeException`) when it cannot connect. Assertions about MySQL
failure must check `db == null || db.getConnection() == null`, not `assertNull(db)`.
