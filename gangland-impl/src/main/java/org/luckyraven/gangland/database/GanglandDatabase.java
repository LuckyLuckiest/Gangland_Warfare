package org.luckyraven.gangland.database;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.database.repositories.rank.RankParentRepository;
import org.luckyraven.gangland.database.repositories.rank.RankRepository;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankParent;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.DatabaseManager;
import org.luckyraven.gangland.persistence.database.DatabaseSettingsProvider;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.persistence.repository.IRepository;
import org.luckyraven.gangland.persistence.repository.RepositoryRegistry;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class GanglandDatabase extends DatabaseHandler {

	private final String             schema;
	@Getter
	private final RepositoryRegistry repositoryRegistry;

	public GanglandDatabase(JavaPlugin plugin, String schema, DatabaseSettingsProvider settings) {
		super(plugin, settings);

		this.schema             = schema;
		this.repositoryRegistry = new RepositoryRegistry(plugin, this);
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
