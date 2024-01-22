package me.luckyraven.data.account.gang;

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
		this.gangs    = new HashMap<>();
	}

	public void initialize(GangDatabase gangDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangDatabase);

		AtomicReference<List<Object[]>> rowsData = new AtomicReference<>();

		// Initialize the Gang object
		initGang(rowsData, helper);

		// To add the alias list
		initAlias(rowsData, helper);
	}

	public void add(Gang gang) {
		gangs.put(gang.getId(), gang);
	}

	public boolean remove(Gang gang) {
		Gang g = gangs.remove(gang.getId());
		return g != null;
	}

	public void clear() {
		gangs.clear();
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

	private void initGang(AtomicReference<List<Object[]>> rowsData, DatabaseHelper helper) {
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
				int    level       = (int) result[6];
				double experience  = (double) result[7];
				double bounty      = (double) result[8];
				long   created     = (long) result[10];

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

				gangs.put(id, gang);
			}
		});
	}

	private void initAlias(AtomicReference<List<Object[]>> rowsData, DatabaseHelper helper) {
		helper.runQueries(database -> {
			if (rowsData.get() == null) rowsData.set(database.table("data").selectAll());

			for (Object[] result : rowsData.get()) {
				int    id        = (int) result[0];
				String aliasList = String.valueOf(result[9]);

				if (aliasList != null && !aliasList.isEmpty()) {
					List<String> aliases = database.getList(aliasList);
					Gang         gang    = gangs.get(id);

					if (gang != null) {
						Set<Gang> aliasSet = aliases.stream()
													.map(aliasId -> gangs.get(Integer.parseInt(aliasId)))
													.collect(Collectors.toSet());
						gang.getAlly().addAll(aliasSet);
					}
				}
			}
		});
	}

}
