package me.luckyraven.database.tables.market;

import me.luckyraven.market.snapshot.DailySnapshot;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class MarketSnapshotTable extends Table<DailySnapshot> {

	public MarketSnapshotTable() {
		super("market_snapshots");

		// Synthetic PK = itemId + "$" + ISO date. Acts as a natural (item_id, snapshot_date) key — the persistence
		// framework only supports a single-attribute PRIMARY KEY inline, so we collapse the composite into one column.
		Attribute<String> snapshotId   = new Attribute<>("snapshot_id", true, String.class);
		Attribute<String> itemId       = new Attribute<>("item_id", false, String.class);
		Attribute<String> snapshotDate = new Attribute<>("snapshot_date", false, String.class);
		Attribute<Double> open         = new Attribute<>("open", false, Double.class);
		Attribute<Double> high         = new Attribute<>("high", false, Double.class);
		Attribute<Double> low          = new Attribute<>("low", false, Double.class);
		Attribute<Double> close        = new Attribute<>("close", false, Double.class);
		Attribute<Long>   volume       = new Attribute<>("volume", false, Long.class);

		this.addAttribute(snapshotId);
		this.addAttribute(itemId);
		this.addAttribute(snapshotDate);
		this.addAttribute(open);
		this.addAttribute(high);
		this.addAttribute(low);
		this.addAttribute(close);
		this.addAttribute(volume);
	}

	public static String makeId(String itemId, String isoDate) {
		return itemId + "$" + isoDate;
	}

	@Override
	public Object[] getData(DailySnapshot data) {
		String isoDate = data.snapshotDate().toString();
		return new Object[]{
				makeId(data.itemId(), isoDate),
				data.itemId(),
				isoDate,
				data.open(),
				data.high(),
				data.low(),
				data.close(),
				data.volume()
		};
	}

	@Override
	public Map<String, Object> searchCriteria(DailySnapshot data) {
		String id = makeId(data.itemId(), data.snapshotDate().toString());
		return createSearchCriteria("snapshot_id = ?", new Object[]{id}, new int[]{Types.VARCHAR}, new int[]{0});
	}
}
