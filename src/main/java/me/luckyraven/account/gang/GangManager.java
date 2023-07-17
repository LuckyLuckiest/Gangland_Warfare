package me.luckyraven.account.gang;

import com.google.common.base.Preconditions;
import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.rank.Rank;
import me.luckyraven.rank.RankManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class GangManager {

	private final Map<Integer, Gang> gangs;
	private final Gangland           gangland;

	public GangManager(Gangland gangland) {
		this.gangland = gangland;
		gangs = new HashMap<>();
	}

	public void initialize(GangDatabase gangDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangDatabase);

		Map<Integer, Gang>              gangsMap = new HashMap<>();
		AtomicReference<List<Object[]>> rowsData = new AtomicReference<>();

		// Initialize the Gang object
		initGang(gangsMap, rowsData, helper);

		// To add the alias list
		initAlias(gangsMap, rowsData, helper);
	}

	private void initGang(Map<Integer, Gang> gangsMap, AtomicReference<List<Object[]>> rowsData,
	                      DatabaseHelper helper) {
		RankManager rankManager = gangland.getInitializer().getRankManager();

		helper.runQueries(database -> {
			List<Object[]> rowsAccount = database.table("account").selectAll();
			if (rowsData.get() == null) rowsData.set(database.table("data").selectAll());

			// account information
			for (Object[] result : rowsAccount) {
				int    id      = (int) result[0];
				double balance = (double) result[1];

				Gang gang = new Gang(id, new HashMap<>());
				gang.setBalance(balance);

				gangsMap.put(id, gang);
			}

			// data information
			for (Object[] result : rowsData.get()) {
				int    id          = (int) result[0];
				String name        = String.valueOf(result[1]);
				String description = String.valueOf(result[2]);

				List<String> tempMembers = database.getList(String.valueOf(result[3]));

				Map<UUID, Rank> members = tempMembers.stream().map(value -> value.split(":")).collect(
						Collectors.toMap(data -> UUID.fromString(data[0]), data -> rankManager.get(data[1]),
						                 (existingValue, newValue) -> newValue, HashMap::new));

				List<String> tempContributions = database.getList(String.valueOf(result[4]));

				Map<UUID, Double> contributions = tempContributions.stream().map(value -> value.split(":")).collect(
						Collectors.toMap(data -> UUID.fromString(data[0]), data -> Double.parseDouble(data[1]),
						                 (existingValue, newValue) -> newValue, HashMap::new));

				double bounty = (double) result[5];

				long created = (long) result[7];

				Gang gang = gangsMap.get(id);

				if (gang != null) {
					gang.setName(name);
					gang.setDescription(description);
					gang.setGroup(members);
					gang.setContribution(contributions);
					gang.setBounty(bounty);
					gang.setCreated(created);
				}
			}

			gangs.putAll(gangsMap);
		});
	}

	private void initAlias(Map<Integer, Gang> gangsMap, AtomicReference<List<Object[]>> rowsData,
	                       DatabaseHelper helper) {
		helper.runQueries(database -> {
			if (rowsData.get() == null) rowsData.set(database.table("data").selectAll());

			for (Object[] result : rowsData.get()) {
				int    id        = (int) result[0];
				String aliasList = String.valueOf(result[6]);

				if (aliasList != null && !aliasList.isEmpty()) {
					List<String> aliases = database.getList(aliasList);
					Gang         gang    = gangsMap.get(id);

					if (gang != null) {
						Set<Gang> aliasSet = aliases.stream()
						                            .map(aliasId -> gangsMap.get(Integer.parseInt(aliasId)))
						                            .collect(Collectors.toSet());
						gang.getAlias().addAll(aliasSet);
					}
				}
			}
		});
	}

	public void add(Gang gang) {
		gangs.put(gang.getId(), gang);
	}

	public void remove(@NotNull Gang gang) {
		Preconditions.checkArgument(gang != null, "Gang can't be null!");

		gangs.remove(gang.getId());
	}

	public boolean contains(Gang gang) {
		return gangs.containsKey(gang.getId());
	}

	public Gang getGang(int id) {
		return gangs.get(id);
	}

	public int size() {
		return gangs.size();
	}

	public Map<Integer, Gang> getGangs() {
		return new HashMap<>(gangs);
	}

}
