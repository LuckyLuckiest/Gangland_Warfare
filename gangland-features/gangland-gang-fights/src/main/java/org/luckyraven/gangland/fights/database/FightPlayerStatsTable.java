package org.luckyraven.gangland.fights.database;

import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/** {@code fight_player_stats}: uuid, wins, losses, kills. */
public class FightPlayerStatsTable extends Table<FightPlayerStats> {

	public FightPlayerStatsTable() {
		super("fight_player_stats");

		this.addAttribute(new Attribute<>("uuid", true, String.class));
		this.addAttribute(new Attribute<>("wins", false, Integer.class));
		this.addAttribute(new Attribute<>("losses", false, Integer.class));
		this.addAttribute(new Attribute<>("kills", false, Integer.class));
	}

	@Override
	public Object[] getData(FightPlayerStats data) {
		return new Object[]{data.uuid().toString(), data.wins(), data.losses(), data.kills()};
	}

	@Override
	public Map<String, Object> searchCriteria(FightPlayerStats data) {
		return createSearchCriteria("uuid = ?", new Object[]{data.uuid().toString()}, new int[]{Types.VARCHAR},
		                            new int[]{0});
	}
}
