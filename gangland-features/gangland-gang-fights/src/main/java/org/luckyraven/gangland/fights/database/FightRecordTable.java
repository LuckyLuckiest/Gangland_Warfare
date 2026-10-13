package org.luckyraven.gangland.fights.database;

import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/** {@code fight_record}: id, gang_a, gang_b, winner, bet, ranked, ended_at. */
public class FightRecordTable extends Table<FightRecord> {

	public FightRecordTable() {
		super("fight_record");

		this.addAttribute(new Attribute<>("id", true, Long.class));
		this.addAttribute(new Attribute<>("gang_a", false, Integer.class));
		this.addAttribute(new Attribute<>("gang_b", false, Integer.class));
		this.addAttribute(new Attribute<>("winner", false, Integer.class));
		this.addAttribute(new Attribute<>("bet", false, Double.class));
		this.addAttribute(new Attribute<>("ranked", false, Integer.class));
		this.addAttribute(new Attribute<>("ended_at", false, Long.class));
	}

	@Override
	public Object[] getData(FightRecord data) {
		return new Object[]{data.id(), data.gangA(), data.gangB(), data.winner(), data.bet(), data.ranked() ? 1 : 0,
		                    data.endedAt()};
	}

	@Override
	public Map<String, Object> searchCriteria(FightRecord data) {
		return createSearchCriteria("id = ?", new Object[]{data.id()}, new int[]{Types.BIGINT}, new int[]{0});
	}
}
