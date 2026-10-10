package org.luckyraven.gangland.gang.database.repositories.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.database.tables.gang.GangTable;
import org.luckyraven.keystone.color.Color;
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
			String name               = text(result[v++], "");
			String displayName        = text(result[v++], "");
			String description        = text(result[v++], "Conquering the hood");
			String color              = text(result[v++], Color.LIGHT_BLUE.name());
			double balance            = num(result[v++]).doubleValue();
			int    level              = num(result[v++]).intValue();
			double experience         = num(result[v++]).doubleValue();
			double bounty             = num(result[v++]).doubleValue();
			long   created            = num(result[v++]).longValue();
			long   lastMemberOnlineAt = num(result[v++]).longValue();
			String state              = text(result[v], "INVITE");

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

	/**
	 * GR-20: a SQL NULL text column loads as the fallback, never as the literal string {@code "null"}.
	 */
	static String text(Object raw, String fallback) {
		return raw == null ? fallback : String.valueOf(raw);
	}

	/** A SQL NULL numeric column reads as 0; any other value is read through {@link Number}. */
	static Number num(Object raw) {
		return raw instanceof Number number ? number : 0;
	}

	/** D7: OPEN stays OPEN; INVITE, CLOSE, NULL, "null" and anything unknown read as INVITE (never OPEN). */
	static Gang.State parseState(String raw) {
		if ("OPEN".equals(raw)) {
			return Gang.State.OPEN;
		}
		return Gang.State.INVITE;
	}
}
