package me.luckyraven.market.view;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.market.snapshot.DailySnapshot;
import me.luckyraven.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Renders a bar chart over a rectangular slot region of an inventory. Each column is one snapshot; the column's "fill
 * height" is proportional to the day's close price relative to the window's min/max. Coloured panes encode direction
 * versus the previous day: lime = up, red = down, gray = flat.
 */
public final class ChartRenderer {

	private static final String FILL_NAME = " ";

	private ChartRenderer() {
	}

	/**
	 * Draws bars into the {@code [startSlot .. startSlot + cols * 9)} rectangle of the given {@link InventoryHandler}.
	 *
	 * @param handler the inventory to draw into
	 * @param startRow row in the inventory where the chart begins (0-based; chart fills {@code rows} rows downward)
	 * @param rows number of inventory rows used by the chart
	 * @param cols number of snapshot columns (== {@code snapshots.size()} at most — truncated when larger)
	 * @param snapshots ordered oldest → newest; will be truncated to {@code cols}
	 */
	public static void draw(InventoryHandler handler, int startRow, int rows, int cols, List<DailySnapshot> snapshots) {
		if (snapshots.isEmpty() || rows <= 0 || cols <= 0) {
			return;
		}

		List<DailySnapshot> window = snapshots.size() > cols ? snapshots.subList(snapshots.size() - cols,
		                                                                         snapshots.size()) : snapshots;

		double min = window.get(0).close();
		double max = window.get(0).close();
		for (DailySnapshot snapshot : window) {
			if (snapshot.close() < min) {
				min = snapshot.close();
			}
			if (snapshot.close() > max) {
				max = snapshot.close();
			}
		}
		double range = Math.max(1D, max - min);

		ItemStack empty = paneOf(XMaterial.BLACK_STAINED_GLASS_PANE, Material.BLACK_STAINED_GLASS_PANE);

		for (int c = 0; c < window.size(); c++) {
			DailySnapshot snapshot = window.get(c);
			double        prev     = c == 0 ? snapshot.close() : window.get(c - 1).close();

			int barHeight = (int) Math.round((snapshot.close() - min) / range * (rows - 1)) + 1;
			if (barHeight < 1) {
				barHeight = 1;
			}
			if (barHeight > rows) {
				barHeight = rows;
			}

			ItemStack lit = colourFor(snapshot.close(), prev);

			for (int r = 0; r < rows; r++) {
				int       slot = (startRow + (rows - 1 - r)) * 9 + c;
				ItemStack pane = r < barHeight ? lit : empty;
				ItemBuilder builder = new ItemBuilder(pane.clone()).setDisplayName(FILL_NAME)
				                                                   .setLore("&7Date: &f" + snapshot.snapshotDate(),
				                                                            "&7Open: &e" + snapshot.open(),
				                                                            "&7High: &a" + snapshot.high(),
				                                                            "&7Low: &c" + snapshot.low(),
				                                                            "&7Close: &e" + snapshot.close(),
				                                                            "&7Volume: &b" + snapshot.volume());
				handler.setItem(slot, builder, false, (p, inv, b) -> { });
			}
		}
	}

	private static ItemStack colourFor(double current, double previous) {
		if (current > previous) {
			return paneOf(XMaterial.LIME_STAINED_GLASS_PANE, Material.GREEN_STAINED_GLASS_PANE);
		}
		if (current < previous) {
			return paneOf(XMaterial.RED_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE);
		}
		return paneOf(XMaterial.GRAY_STAINED_GLASS_PANE, Material.GRAY_STAINED_GLASS_PANE);
	}

	private static ItemStack paneOf(XMaterial preferred, Material fallback) {
		ItemStack parsed = preferred.parseItem();
		return parsed != null ? parsed : new ItemStack(fallback);
	}
}
