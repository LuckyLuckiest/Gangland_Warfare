package org.luckyraven.gangland.database.repositories.player;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserFactory;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.persistence.database.component.Table;
import org.luckyraven.keystone.persistence.repository.AbstractRepository;
import org.luckyraven.keystone.persistence.repository.Repository;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;

@Repository(value = User.class, isGeneric = true)
@CustomLog
public class UserRepository extends AbstractRepository<User<? extends OfflinePlayer>> {

	/** A SQL NULL reads as 0; any other value is read through Number (MySQL may return Long/BigDecimal). */
	private static Number num(Object raw) {
		return raw instanceof Number number ? number : 0;
	}

	private final UserTable userTable;

	public UserRepository(JavaPlugin plugin, DatabaseHandler databaseHandler, DatabaseBackend backend) {
		super(plugin, databaseHandler, backend);

		this.userTable = new UserTable();
	}

	@Override
	protected Collection<User<? extends OfflinePlayer>> doLoadAll() throws SQLException {
		Map<UUID, User<? extends OfflinePlayer>> users = new HashMap<>();

		// UserRepository is instantiated reflectively by RepositoryRegistry with a fixed (JavaPlugin, DatabaseHandler)
		// signature, so UserFactory cannot be constructor-injected. By the time doLoadAll runs (DATABASE phase, after
		// KERNEL where UserFactory was built), the bean is in the container.
		UserFactory userFactory = ((Gangland) getPlugin()).getContext().get(UserFactory.class);

		List<Object[]> data = tableBackend().selectAll();

		for (Object[] result : data) {
			int    v          = 0;
			UUID uuid;
			try {
				uuid = UUID.fromString(String.valueOf(result[v++]));
			} catch (IllegalArgumentException exception) {
				// T-262: a NULL or malformed uuid skips this row instead of failing the whole load
				log.warn("Skipping a user row with a bad uuid '{}'", result[0]);
				continue;
			}
			double balance    = num(result[v++]).doubleValue();
			int    kills      = num(result[v++]).intValue();
			int    deaths     = num(result[v++]).intValue();
			int    mobKills   = num(result[v++]).intValue();
			double bounty     = num(result[v++]).doubleValue();
			int    level      = num(result[v++]).intValue();
			double experience = num(result[v++]).doubleValue();
			int    wanted     = num(result[v++]).intValue();
			// NULL = a row saved before the ledger existed
			String posters    = result.length > v && result[v] != null ? String.valueOf(result[v]) : null;

			OfflinePlayer       offlinePlayer = Bukkit.getOfflinePlayer(uuid);
			User<OfflinePlayer> user          = userFactory.create(offlinePlayer);

			// Set user data
			user.setKills(kills);
			user.setDeaths(deaths);
			user.setMobKills(mobKills);
			user.getEconomy().setAmount(Currency.of(balance));
			user.getWanted().setLevel(wanted);
			user.getLevel().setLevelValue(level);
			user.getLevel().setExperience(experience);
			user.getBounty().setAmount(Currency.of(bounty));
			user.getBounty().restoreLedger(posters);

			users.put(uuid, user);
		}

		return users.values();
	}

	@Override
	protected <E> Consumer<E> processSave() {
		return null;
	}

	@Override
	protected Table<User<? extends OfflinePlayer>> getTable() {
		return userTable;
	}

	@Override
	protected void doDelete(User<? extends OfflinePlayer> data) throws SQLException {
		tableBackend().delete("uuid = ?", data.getUuid().toString());
	}
}
