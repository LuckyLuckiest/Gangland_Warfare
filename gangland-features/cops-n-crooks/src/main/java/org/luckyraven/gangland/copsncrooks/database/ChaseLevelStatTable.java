package org.luckyraven.gangland.copsncrooks.database;

import org.luckyraven.gangland.copsncrooks.wanted.learn.ChaseLevelStat;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/**
 * {@code chase_level_stat}: at most one row per chase peak level (a missing row means cold start).
 *
 * @since 0.15.2
 */
public class ChaseLevelStatTable extends Table<ChaseLevelStat> {

	public ChaseLevelStatTable() {
		super("chase_level_stat");

		this.addAttribute(new Attribute<>("level", true, Integer.class));
		this.addAttribute(new Attribute<>("n", false, Double.class));
		this.addAttribute(new Attribute<>("escaped", false, Double.class));
		this.addAttribute(new Attribute<>("typical_s", false, Double.class));
		this.addAttribute(new Attribute<>("typical_count", false, Double.class));
		this.addAttribute(new Attribute<>("updated_at", false, Long.class));
	}

	@Override
	public Object[] getData(ChaseLevelStat data) {
		return new Object[]{data.level(), data.n(), data.escaped(), data.typicalSeconds(), data.typicalCount(),
		                    data.updatedAt()};
	}

	@Override
	public Map<String, Object> searchCriteria(ChaseLevelStat data) {
		return createSearchCriteria("level = ?", new Object[]{data.level()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
