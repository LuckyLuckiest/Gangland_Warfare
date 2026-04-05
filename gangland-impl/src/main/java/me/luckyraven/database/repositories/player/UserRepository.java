package me.luckyraven.database.repositories.player;

import me.luckyraven.data.account.user.User;
import me.luckyraven.database.tables.player.UserTable;
import me.luckyraven.persistence.database.Database;
import me.luckyraven.persistence.database.DatabaseHandler;
import me.luckyraven.persistence.database.component.Table;
import me.luckyraven.persistence.repository.AbstractRepository;
import me.luckyraven.persistence.repository.Repository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

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
			User<OfflinePlayer> user          = new User<>(getPlugin(), offlinePlayer);

			// Set user data
			user.setKills(kills);
			user.setDeaths(deaths);
			user.setMobKills(mobKills);
			user.getEconomy().setBalance(balance);
			user.getWanted().setLevel(wanted);
			user.getLevel().setLevelValue(level);
			user.getLevel().setExperience(experience);
			user.getBounty().setAmount(bounty);

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
