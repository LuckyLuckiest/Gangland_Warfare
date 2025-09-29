package me.luckyraven.bukkit.sign;

import me.luckyraven.Gangland;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.SignTable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SignManager {

	private final Gangland           gangland;
	private final Map<Integer, Sign> signs;

	public SignManager(Gangland gangland) {
		this.gangland = gangland;
		this.signs    = new HashMap<>();
	}

	public void initialize(SignTable signTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			List<Object[]> data = database.table(signTable.getName()).selectAll();

			for (Object[] result : data) {
				int    v            = 0;
				int    id           = (int) result[v++];
				String signType     = String.valueOf(result[v++]).toLowerCase();
				String world        = String.valueOf(result[v++]);
				double x            = (double) result[v++];
				double y            = (double) result[v++];
				double z            = (double) result[v++];
				long   lastTimeUsed = (long) result[v];

				Sign sign;

				switch (signType) {
					case "glw-buy" -> {
//						sign = new
					}
					case "glw-sell" -> { }
					case "glw-view" -> { }
					case "glw-wanted" -> { }
					default -> sign = null;
				}
			}
		});
	}

}
