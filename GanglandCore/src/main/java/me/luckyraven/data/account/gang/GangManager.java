package me.luckyraven.data.account.gang;

import me.luckyraven.Gangland;
import me.luckyraven.Pair;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.GangAlliesTable;
import me.luckyraven.database.tables.GangTable;

import java.util.*;

public class GangManager {

	private final Gangland                                gangland;
	private final Map<Integer, Gang>                      gangs;
	private final Set<Pair<Integer, Pair<Integer, Long>>> gangsAllie;

	public GangManager(Gangland gangland) {
		this.gangland   = gangland;
		this.gangs      = new HashMap<>();
		this.gangsAllie = new HashSet<>();
	}

	public void initialize(GangTable gangTable, GangAlliesTable gangAlliesTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			List<Object[]> gangsData      = database.table(gangTable.getName()).selectAll();
			List<Object[]> gangAlliesData = database.table(gangAlliesTable.getName()).selectAll();

			// set up the gangs
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

				gangs.put(id, gang);
			}

			// set up the gang allies
			// store the data
			for (Object[] result : gangAlliesData) {
				int  gangId  = (int) result[0];
				int  allieId = (int) result[1];
				long since   = (long) result[2];

				Pair<Integer, Long>                allieDate   = new Pair<>(allieId, since);
				Pair<Integer, Pair<Integer, Long>> stackedData = new Pair<>(gangId, allieDate);

				gangsAllie.add(stackedData);
			}

			// add the gang allies to the specified gang
			for (int gangId : gangs.keySet()) {
				// get data associated to specific id
				List<Pair<Integer, Long>> alliedGangsIdData = gangsAllie.stream()
						.filter(pair -> pair.first() == gangId)
						.map(Pair::second)
						.toList();

				// convert each id to Gang instance
				List<Pair<Gang, Long>> alliedGangsData = alliedGangsIdData.stream()
						.map(pair -> new Pair<>(
								gangs.get(pair.first()),
								pair.second()))
						.toList();

				// add the gang to the new gang
				gangs.get(gangId).addAllAllies(alliedGangsData);
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
}
