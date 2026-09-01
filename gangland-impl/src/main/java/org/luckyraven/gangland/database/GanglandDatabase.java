package org.luckyraven.gangland.database;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.database.repositories.rank.RankParentRepository;
import org.luckyraven.gangland.database.repositories.rank.RankRepository;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankParent;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.DatabaseManager;
import org.luckyraven.keystone.persistence.database.DatabaseSettingsProvider;
import org.luckyraven.keystone.persistence.database.backend.ConnectionParams;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.backend.MysqlBackend;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GanglandDatabase extends DatabaseHandler {

	private final String                   schema;
	private final JavaPlugin               plugin;
	private final DatabaseSettingsProvider settings;

	@Getter
	private RepositoryRegistry repositoryRegistry;
	@Getter
	private DatabaseBackend    backend;

	public GanglandDatabase(JavaPlugin plugin, String schema, DatabaseSettingsProvider settings) {
		super(plugin, settings);

		this.schema   = schema;
		this.plugin   = plugin;
		this.settings = settings;
	}

	/**
	 * Connects the {@link DatabaseBackend} matching the resolved database type and constructs the
	 * {@link RepositoryRegistry} on the backend path (repositories persist through {@code TableBackend}; table
	 * creation runs through the backend's schema-diff engine).
	 *
	 * <p>Must be called <b>after</b> {@link #setType(int)} so the MySQL→SQLite fallback has been resolved, and —
	 * on MySQL — after {@link #createSchema()}, because the backend pool connects directly to the schema.
	 */
	public void connectBackend() throws SQLException {
		if (backend != null) {
			throw new SQLException("Backend already connected");
		}

		ConnectionParams params;
		switch (getType()) {
			case MYSQL -> {
				this.backend = new MysqlBackend();
				String url = String.format("jdbc:mysql://%s:%d/%s",
				                           settings.getMysqlHost(), settings.getMysqlPort(), getSchemaName());
				params = ConnectionParams.credentialed(url, settings.getMysqlUsername(),
				                                       settings.getMysqlPassword(), Map.of());
			}
			case SQLITE -> {
				this.backend = new SqliteBackend();
				// Exactly the file enforceType() connects the legacy pool to — both stacks share one database.
				String path = plugin.getDataFolder().getAbsolutePath() + File.separator + getSchema() + ".db";
				params = ConnectionParams.of("jdbc:sqlite:" + path);
			}
			default -> throw new SQLException("Unknown database type");
		}

		backend.connect(params);
		this.repositoryRegistry = new RepositoryRegistry(plugin, this, backend);
	}

	@Nullable
	public static GanglandDatabase findInstance(DatabaseManager manager) {
		return manager.getDatabases()
				.stream()
				.filter(handler -> handler instanceof GanglandDatabase)
				.map(GanglandDatabase.class::cast)
				.findFirst()
				.orElse(null);
	}

	@Override
	public void createSchema() throws SQLException, IOException {
		getDatabase().createSchema(getSchema());

		// Switch the schema only when using mysql, because it needs to create the schema from the connection
		// then change the jdbc url to the new database
		if (getType() == MYSQL) getDatabase().switchSchema(getSchema());
	}

	@Override
	public void createTables() throws SQLException {
		// Tables are now created through RepositoryRegistry
		try {
			repositoryRegistry.createTables();
		} catch (Exception e) {
			throw new SQLException("Failed to create tables through repositories", e);
		}
	}

	@Override
	public void insertInitialData() throws SQLException {
		IRepository<Rank>       rankRepo       = repositoryRegistry.getRepository(Rank.class);
		IRepository<RankParent> rankParentRepo = repositoryRegistry.getRepository(RankParent.class);

		if (!(rankRepo instanceof RankRepository repo)) return;
		if (!(rankParentRepo instanceof RankParentRepository parentRepo)) return;

		String head = Settings.getGangRankHead();
		String tail = Settings.getGangRankTail();

		int[] ids = repo.insertInitialRanks(head, tail);

		parentRepo.insertInitialRelation(ids[0], ids[1]);
	}

	@Override
	public String getSchema() {
		return switch (getType()) {
			case DatabaseHandler.MYSQL -> schema;
			case DatabaseHandler.SQLITE -> "database" + File.separator + this.schema;
			default -> null;
		};
	}

	public List<Table<?>> getTables() {
		return Collections.unmodifiableList(repositoryRegistry.getRegisteredTables());
	}
}
