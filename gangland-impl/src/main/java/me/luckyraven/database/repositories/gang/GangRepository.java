package me.luckyraven.database.repositories.gang;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.database.tables.gang.GangTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Gang.class)
public class GangRepository extends AbstractRepository<Gang> {

	private final GangTable gangTable;

	public GangRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.gangTable = new GangTable();
	}

	@Override
	protected Collection<Gang> doLoadAll() throws SQLException {
		List<Gang> gangs = new ArrayList<>();

		List<Object[]> gangsData = getDatabase().table(gangTable.getName()).selectAll();

		for (Object[] result : gangsData) {
			int    v           = 0;
			int    id          = (int) result[v++];
			String name        = String.valueOf(result[v++]);
			String displayName = String.valueOf(result[v++]);
			String description = String.valueOf(result[v++]);
			String color       = String.valueOf(result[v++]);
			double balance     = (double) result[v++];
			int    level       = (int) result[v++];
			double experience  = (double) result[v++];
			double bounty      = (double) result[v++];
			long   created     = (long) result[v];

			Gang gang = new Gang(id);

			gang.setName(name);
			gang.setDisplayName(displayName);
			gang.setColor(color);
			gang.setDescription(description);
			gang.getEconomy().setBalance(balance);
			gang.getLevel().setLevelValue(level);
			gang.getLevel().setExperience(experience);
			gang.getBounty().setAmount(bounty);
			gang.setCreated(created);

			gangs.add(gang);
		}

		return gangs;
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<Gang> getTable() {
		return gangTable;
	}

	@Override
	protected void doDelete(Gang data) throws SQLException {
		Database table = getDatabase().table(gangTable.getName());
		table.delete("id", data.getId(), Types.INTEGER);
	}
}
