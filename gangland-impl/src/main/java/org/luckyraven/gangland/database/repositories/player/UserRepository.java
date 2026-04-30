package org.luckyraven.gangland.database.repositories.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.database.tables.player.UserTable;
import org.luckyraven.gangland.economy.Currency;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserFactory;
import org.luckyraven.gangland.persistence.database.Database;
import org.luckyraven.gangland.persistence.database.DatabaseHandler;
import org.luckyraven.gangland.persistence.database.component.Table;
import org.luckyraven.gangland.persistence.repository.AbstractRepository;
import org.luckyraven.gangland.persistence.repository.Repository;

import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.function.Consumer;

@Repository(value = User.class, isGeneric = true)
public class UserRepository extends AbstractRepository<User<? extends OfflinePlayer>> {

	private final UserTable userTable;

	public UserRepository(JavaPlugin plugin, DatabaseHandler databaseHandler) {
		super(plugin, databaseHandler);

		this.userTable = new UserTable();
	}

	@Override
	protected Collection<User<? extends OfflinePlayer>> doLoadAll() throws SQLException {
		Map<UUID, User<? extends OfflinePlayer>> users = new HashMap<>();

		// UserRepository is instantiated reflectively by RepositoryRegistry with a fixed (JavaPlugin, DatabaseHandler)
		// signature, so UserFactory cannot be constructor-injected. By the time doLoadAll runs (DATABASE phase, after
		// KERNEL where UserFactory was built), the bean is in the container.
		UserFactory userFactory = ((Gangland) getPlugin()).getContext().get(UserFactory.class);

		List<Object[]> data = userTable.selectAllTableQuery(getDatabase());

		for (Object[] result : data) {
			int    v          = 0;
			UUID   uuid       = UUID.fromString(String.valueOf(result[v++]));
			double balance    = (double) result[v++];
			int    kills      = (int) result[v++];
			int    deaths     = (int) result[v++];
			int    mobKills   = (int) result[v++];
			double bounty     = (double) result[v++];
			int    level      = (int) result[v++];
			double experience = (double) result[v++];
			int    wanted     = (int) result[v];

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
		Database table = getDatabase().table(userTable.getName());
		table.delete("uuid", data.getUuid().toString(), Types.VARCHAR);
	}
}
