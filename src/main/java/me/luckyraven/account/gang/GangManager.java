package me.luckyraven.account.gang;

import com.google.common.base.Preconditions;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.util.UnhandledError;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class GangManager {

	private final Set<Gang>  gangs;
	private final JavaPlugin plugin;

	public GangManager(JavaPlugin plugin) {
		this.plugin = plugin;
		gangs = new HashSet<>();
	}

	public void initialize(GangDatabase gangDatabase) {
		DatabaseHelper helper = new DatabaseHelper(plugin, gangDatabase);

		Map<Integer, Gang>              gangsMap = new HashMap<>();
		AtomicReference<List<Object[]>> rowsData = new AtomicReference<>();

		// Initialize the Gang object
		initGang(gangsMap, rowsData, helper);

		// To add the alias list
		initAlias(gangsMap, rowsData, helper);
	}

	private void initGang(Map<Integer, Gang> gangsMap, AtomicReference<List<Object[]>> rowsData,
	                      DatabaseHelper helper) {
		helper.runQueries(database -> {
			try {
				List<Object[]> rowsAccount = database.table("account").selectAll();
				if (rowsData.get() == null) rowsData.set(database.table("data").selectAll());

				for (Object[] result : rowsAccount) {
					int    id      = (int) result[0];
					double balance = (double) result[1];

					Gang gang = new Gang(id, new HashSet<>());
					gang.setBalance(balance);

					gangsMap.put(id, gang);
				}

				for (Object[] result : rowsData.get()) {
					int    id          = (int) result[0];
					String name        = String.valueOf(result[1]);
					String description = String.valueOf(result[2]);

					List<String> tempMembers = database.getList(String.valueOf(result[3]));
					Set<UUID>    members     = tempMembers.stream().map(UUID::fromString).collect(Collectors.toSet());

					double bounty = (double) result[4];

					Date created = Date.from(((LocalDateTime) result[6]).atZone(ZoneId.systemDefault()).toInstant());

					Gang gang = gangsMap.get(id);

					if (gang != null) {
						gang.setName(name);
						gang.setDescription(description);
						gang.setGroup(members);
						gang.setBounty(bounty);
						gang.setCreated(created);
					}
				}

				gangs.addAll(gangsMap.values());
			} catch (SQLException exception) {
				plugin.getLogger().warning(UnhandledError.SQL_ERROR + ": " + exception.getMessage());

				exception.printStackTrace();
			}
		});
	}

	private void initAlias(Map<Integer, Gang> gangsMap, AtomicReference<List<Object[]>> rowsData,
	                       DatabaseHelper helper) {
		helper.runQueries(database -> {
			try {
				if (rowsData.get() == null) rowsData.set(database.table("data").selectAll());

				for (Object[] result : rowsData.get()) {
					int    id        = (int) result[0];
					String aliasList = String.valueOf(result[5]);

					if (aliasList != null && !aliasList.isEmpty()) {
						List<String> aliases = database.getList(aliasList);
						Gang         gang    = gangsMap.get(id);

						if (gang != null) {
							Set<Gang> aliasSet = aliases.stream().map(
									aliasId -> gangsMap.get(Integer.parseInt(aliasId))).collect(Collectors.toSet());
							gang.getAlias().addAll(aliasSet);
						}
					}
				}
			} catch (SQLException exception) {
				plugin.getLogger().warning(UnhandledError.SQL_ERROR + ": " + exception.getMessage());

				exception.printStackTrace();
			}
		});
	}

	public void add(Gang gang) {
		gangs.add(gang);
	}

	public void remove(Gang gang) {
		Preconditions.checkArgument(gang != null, "Gang can't be null!");

		gangs.remove(gang);
	}

	public boolean contains(Gang gang) {
		return gangs.contains(gang);
	}

	public Gang getGang(String name) {
		for (Gang gang : gangs) if (gang.getName().equalsIgnoreCase(name)) return gang;
		return null;
	}

	public int size() {
		return gangs.size();
	}

	public Set<Gang> getGangs() {
		return new HashSet<>(gangs);
	}

}
