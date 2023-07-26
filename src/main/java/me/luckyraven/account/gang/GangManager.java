package me.luckyraven.account.gang;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class GangManager {

	private final Map<Integer, Gang> gangs;
	private final Gangland           gangland;

	public GangManager(Gangland gangland) {
		this.gangland = gangland;
		this.gangs = new HashMap<>();
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
		helper.runQueries(database -> {
			if (rowsData.get() == null) rowsData.set(database.table("data").selectAll());

			// data information
			for (Object[] result : rowsData.get()) {
				int    id          = (int) result[0];
				String name        = String.valueOf(result[1]);
				String displayName = String.valueOf(result[2]);
				String color       = String.valueOf(result[3]);
				String description = String.valueOf(result[4]);
				double balance     = (double) result[5];
				double bounty      = (double) result[6];
				long   created     = (long) result[8];

				Gang gang = new Gang(id);

				gang.setName(name);
				gang.setDisplayName(displayName);
				gang.setColor(color);
				gang.setDescription(description);
				gang.setBalance(balance);
				gang.setBounty(bounty);
				gang.setCreated(created);

				gangsMap.put(id, gang);
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
				String aliasList = String.valueOf(result[7]);

				if (aliasList != null && !aliasList.isEmpty()) {
					List<String> aliases = database.getList(aliasList);
					Gang         gang    = gangsMap.get(id);

					if (gang != null) {
						Set<Gang> aliasSet = aliases.stream()
						                            .map(aliasId -> gangsMap.get(Integer.parseInt(aliasId)))
						                            .collect(Collectors.toSet());
						gang.getAlly().addAll(aliasSet);
					}
				}
			}
		});
	}

	public void add(Gang gang) {
		gangs.put(gang.getId(), gang);
	}

	public boolean remove(Gang gang) {
		Gang g = gangs.remove(gang.getId());
		return g != null;
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
		return Collections.unmodifiableMap(gangs);
	}

}
