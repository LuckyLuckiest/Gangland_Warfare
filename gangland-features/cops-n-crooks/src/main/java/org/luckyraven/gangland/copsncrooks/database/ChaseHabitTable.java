package org.luckyraven.gangland.copsncrooks.database;

import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseHabit;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/**
 * {@code chase_habit}: one row per player who finished a counted chase (what {@code Drop_Mode: AUTO} learns about his
 * escapes).
 *
 * @since 0.15.2
 */
public class ChaseHabitTable extends Table<ChaseHabit> {

	public ChaseHabitTable() {
		super("chase_habit");

		this.addAttribute(new Attribute<>("player_uuid", true, String.class));
		this.addAttribute(new Attribute<>("n", false, Double.class));
		this.addAttribute(new Attribute<>("actual", false, Double.class));
		this.addAttribute(new Attribute<>("expected", false, Double.class));
		this.addAttribute(new Attribute<>("last_at", false, Long.class));
	}

	@Override
	public Object[] getData(ChaseHabit data) {
		return new Object[]{data.player().toString(), data.n(), data.actual(), data.expected(), data.lastAt()};
	}

	@Override
	public Map<String, Object> searchCriteria(ChaseHabit data) {
		return createSearchCriteria("player_uuid = ?", new Object[]{data.player().toString()},
		                            new int[]{Types.VARCHAR}, new int[]{0});
	}
}
