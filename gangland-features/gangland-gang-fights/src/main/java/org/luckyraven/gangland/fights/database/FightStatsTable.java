package org.luckyraven.gangland.fights.database;

import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/** {@code fight_stats}: gang_id, wins, losses, draws. */
public class FightStatsTable extends Table<FightStats> {

	public FightStatsTable() {
		super("fight_stats");

		this.addAttribute(new Attribute<>("gang_id", true, Integer.class));
		this.addAttribute(new Attribute<>("wins", false, Integer.class));
		this.addAttribute(new Attribute<>("losses", false, Integer.class));
		this.addAttribute(new Attribute<>("draws", false, Integer.class));
	}

	@Override
	public Object[] getData(FightStats data) {
		return new Object[]{data.gangId(), data.wins(), data.losses(), data.draws()};
	}

	@Override
	public Map<String, Object> searchCriteria(FightStats data) {
		return createSearchCriteria("gang_id = ?", new Object[]{data.gangId()}, new int[]{Types.INTEGER},
		                            new int[]{0});
	}
}
