package org.luckyraven.gangland.copsncrooks.database;

import org.luckyraven.gangland.copsncrooks.place.SetupPoint;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/** {@code cop_point}: one row per admin-placed setup point (pickup, breaker trigger). */
public class SetupPointTable extends Table<SetupPoint> {

	public SetupPointTable() {
		super("cop_point");

		this.addAttribute(new Attribute<>("id", true, Integer.class));
		this.addAttribute(new Attribute<>("kind", false, String.class));
		this.addAttribute(new Attribute<>("name", false, String.class));
		this.addAttribute(new Attribute<>("world", false, String.class));
		this.addAttribute(new Attribute<>("x", false, Double.class));
		this.addAttribute(new Attribute<>("y", false, Double.class));
		this.addAttribute(new Attribute<>("z", false, Double.class));
	}

	@Override
	public Object[] getData(SetupPoint data) {
		return new Object[]{data.getId(), data.getKind(), data.getName(), data.getWorld(), data.getX(), data.getY(),
		                    data.getZ()};
	}

	@Override
	public Map<String, Object> searchCriteria(SetupPoint data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
