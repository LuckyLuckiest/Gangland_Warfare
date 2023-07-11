package me.luckyraven.rank;

import me.luckyraven.Gangland;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.RankDatabase;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RankManager {

	private final Map<Integer, Rank> ranks;
	private final Gangland           gangland;

	public RankManager(Gangland gangland) {
		this.gangland = gangland;
		ranks = new HashMap<>();
	}

	public void initialize(RankDatabase rankDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, rankDatabase);

		helper.runQueries(database -> {
			List<Object[]> rowsData = database.table("data").selectAll();

			// data information
			for (Object[] result : rowsData) {
				int          id          = (int) result[0];
				String       name        = String.valueOf(result[1]);
				List<String> permissions = database.getList(String.valueOf(result[2]));

				Rank rank = new Rank(name, permissions);

				ranks.put(id, rank);
			}
		});
	}

	public void add(Rank rank) {
		ranks.put(rank.getUsedId(), rank);
	}

	public void remove(Rank rank) {
		ranks.remove(rank.getUsedId());
	}

	public Rank get(int id) {
		return ranks.get(id);
	}

	public Rank get(String name) {
		for (Rank rank : ranks.values())
			if (rank.getName().equalsIgnoreCase(name)) return rank;
		return null;
	}

	public void refactorIds(RankDatabase rankDatabase) {
		DatabaseHelper helper = new DatabaseHelper(gangland, rankDatabase);

		helper.runQueries(database -> {
			Database config = database.table("data");

			List<Object[]> rowsData = config.selectAll();

			// remove all the data from the table
			config.delete("", "");

			int tempId = 1;
			for (Object[] result : rowsData) {
				int id = (int) result[0];

				Rank rank = ranks.get(id);
				ranks.remove(rank.getUsedId());

				rank.setUsedId(tempId);
				ranks.put(tempId, rank);

				database.insert(new String[]{"id", "name", "permissions"},
				                new Object[]{rank.getUsedId(), rank.getName(), rank.getPermissions()},
				                new int[]{Types.INTEGER, Types.VARCHAR, Types.VARCHAR});

				tempId++;
			}
		});
	}

	public Map<Integer, Rank> getRanks() {
		return Collections.unmodifiableMap(ranks);
	}

	@Override
	public String toString() {
		return String.format("ranks=%s", ranks);
	}

}
