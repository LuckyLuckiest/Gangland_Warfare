package me.luckyraven.data.account.gang;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.GangAllieTable;
import me.luckyraven.database.tables.GangTable;
import me.luckyraven.util.Pair;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class GangManager {

	private final Gangland                                gangland;
	private final Map<Integer, Gang>                      gangs;
	private final Set<Pair<Integer, Pair<Integer, Long>>> gangsAllie;

	public GangManager(Gangland gangland) {
		this.gangland   = gangland;
		this.gangs      = new HashMap<>();
		this.gangsAllie = new HashSet<>();
	}

	public void initialize(GangTable gangTable, GangAllieTable gangAllieTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			Set<Pair<Integer, Pair<Integer, Long>>> allied = new HashSet<>();

			List<Object[]> gangsData      = database.table(gangTable.getName()).selectAll();
			List<Object[]> gangAlliesData = database.table(gangAllieTable.getName()).selectAll();

			// set up the gangs
			for (Object[] result : gangsData) {
				int    id          = (int) result[0];
				String name        = String.valueOf(result[1]);
				String displayName = String.valueOf(result[2]);
				String description = String.valueOf(result[3]);
				String color       = String.valueOf(result[4]);
				double balance     = (double) result[5];
				int    level       = (int) result[6];
				double experience  = (double) result[7];
				double bounty      = (double) result[8];
				long   created     = (long) result[9];

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
