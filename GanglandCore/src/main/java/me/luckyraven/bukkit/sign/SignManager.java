package me.luckyraven.bukkit.sign;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.sign.sub.BuySign;
import me.luckyraven.bukkit.sign.sub.SellSign;
import me.luckyraven.bukkit.sign.sub.ViewSign;
import me.luckyraven.bukkit.sign.sub.WantedSign;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.tables.SignTable;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.NumberUtil;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SignManager {

	private final Gangland           gangland;
	@Getter
	private final Map<Integer, Sign> signs;

	public SignManager(Gangland gangland) {
		this.gangland = gangland;
		this.signs    = new HashMap<>();
	}

	// ===== Creation & validation helpers =====
	public static void validateSign(Gangland gangland, String[] lines) throws IllegalArgumentException {
		if (lines == null || lines.length < 4) throw new IllegalArgumentException("The sign must have 4 lines!");
		String type = lines[0] == null ? "" : lines[0].toLowerCase();

		switch (type) {
			case "glw-buy" -> {
				int    amount = NumberUtil.parseFormattedInteger(lines[2]);
				double price  = NumberUtil.parseFormattedDouble(lines[3]);
				new BuySign(gangland, price, amount, new Location(null, 0, 0, 0)).validate(lines);
			}
			case "glw-sell" -> {
				int    amount = NumberUtil.parseFormattedInteger(lines[2]);
				double price  = NumberUtil.parseFormattedDouble(lines[3]);
				new SellSign(gangland, price, amount, new Location(null, 0, 0, 0)).validate(lines);
			}
			case "glw-view" -> new ViewSign(gangland, new Location(null, 0, 0, 0)).validate(lines);
			case "glw-wanted" -> new WantedSign(new Location(null, 0, 0, 0)).validate(lines);
			default -> throw new IllegalArgumentException("Unknown sign type: " + lines[0]);
		}
	}

	public static Sign createSign(Gangland gangland, String[] lines, Location location) {
		String type = lines[0].toLowerCase();
		switch (type) {
			case "glw-buy" -> {
				int    amount = NumberUtil.parseFormattedInteger(lines[2]);
				double price  = NumberUtil.parseFormattedDouble(lines[3]);
				return new BuySign(gangland, price, amount, location);
			}
			case "glw-sell" -> {
				int    amount = NumberUtil.parseFormattedInteger(lines[2]);
				double price  = NumberUtil.parseFormattedDouble(lines[3]);
				return new SellSign(gangland, price, amount, location);
			}
			case "glw-view" -> {
				return new ViewSign(gangland, location);
			}
			case "glw-wanted" -> {
				return new WantedSign(location);
			}
			default -> throw new IllegalArgumentException("Unknown sign type: " + lines[0]);
		}
	}

	public static void translateCreationToDisplay(String[] lines) {
		// Mutates the given lines array into a colored/display form
		if (lines == null || lines.length < 4) return;
		String type = lines[0].toLowerCase();
		lines[0] = ChatUtil.color("&b" + lines[0]);
		lines[1] = ChatUtil.color("&7" + lines[1]);
		if (!type.equals("glw-view")) {
			// 3rd line: amount or wanted stars
			try {
				int amount = NumberUtil.parseFormattedInteger(lines[2]);
				lines[2] = ChatUtil.color("&5" + NumberUtil.valueFormat(amount));
			} catch (Exception ignored) { /* leave as-is */ }
			// 4th line: price, with $ prefix
			try {
				double price = NumberUtil.parseFormattedDouble(lines[3].replace("$", ""));
				lines[3] = ChatUtil.color("&a$" + NumberUtil.valueFormat(price));
			} catch (Exception ignored) { /* leave as-is */ }
		}
	}

	public void initialize(SignTable signTable) {
		DatabaseHelper helper = new DatabaseHelper(gangland, gangland.getInitializer().getGanglandDatabase());

		helper.runQueries(database -> {
			List<Object[]> data = database.table(signTable.getName()).selectAll();

			int maxId = 0;
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

				Location dummy = new Location(null, x, y, z);
				switch (signType) {
					case "glw-buy" -> sign = new BuySign(gangland, 0D, 0, dummy);
					case "glw-sell" -> sign = new SellSign(gangland, 0D, 0, dummy);
					case "glw-view" -> sign = new ViewSign(gangland, dummy);
					case "glw-wanted" -> sign = new WantedSign(dummy);
					default -> {
						continue; // unknown type; skip
					}
				}

				sign.setWorld(world);
				sign.setX(x);
				sign.setY(y);
				sign.setZ(z);
				sign.setLastTimeUsed(lastTimeUsed);

				signs.put(id, sign);
				if (id > maxId) maxId = id;
			}

			// keep ID sequence in sync with DB
			Sign.setID(maxId);
		});
	}
}
