package org.luckyraven.gangland.database.repositories.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.database.tables.gang.GangTable;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

@Repository(Gang.class)
public class GangRepository extends AbstractRepository<Gang> {

	private final GangTable gangTable;

	public GangRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.gangTable = new GangTable();
	}

	@Override
	protected Collection<Gang> doLoadAll() throws SQLException {
		List<Gang>     gangs     = new ArrayList<>();
		List<Object[]> gangsData = tableBackend().selectAll();

		for (Object[] result : gangsData) {
			int    v                  = 0;
			int    id                 = (int) result[v++];
			String name               = String.valueOf(result[v++]);
			String displayName        = String.valueOf(result[v++]);
			String description        = String.valueOf(result[v++]);
			String color              = String.valueOf(result[v++]);
			double balance            = (double) result[v++];
			int    level              = (int) result[v++];
			double experience         = (double) result[v++];
			double bounty             = (double) result[v++];
			long   created            = (long) result[v++];
			long   lastMemberOnlineAt = (long) result[v++];
			String state              = String.valueOf(result[v]);

			Gang gang = new Gang(id);

			gang.setName(name);
			gang.setDisplayName(displayName);
			gang.setColor(color);
			gang.setDescription(description);
			gang.getEconomy().setAmount(Currency.of(balance));
			gang.getLevel().setLevelValue(level);
			gang.getLevel().setExperience(experience);
			gang.getBounty().setAmount(Currency.of(bounty));
			gang.setCreated(created);
			gang.setLastMemberOnlineAt(lastMemberOnlineAt);
			gang.setState(parseState(state));

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
		tableBackend().delete("id = ?", data.getId());
	}

	private Gang.State parseState(String raw) {
		if (raw == null || raw.equals("null")) {
			return Gang.State.OPEN;
		}
		try {
			return Gang.State.valueOf(raw);
		} catch (IllegalArgumentException ignored) {
			return Gang.State.OPEN;
		}
	}
}
