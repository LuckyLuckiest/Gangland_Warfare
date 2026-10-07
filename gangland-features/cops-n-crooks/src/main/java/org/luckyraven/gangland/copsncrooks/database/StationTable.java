package org.luckyraven.gangland.copsncrooks.database;

import org.luckyraven.gangland.copsncrooks.station.Station;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/** {@code cop_station}: one row per police station; the world is stored by name so an unloaded world keeps its row. */
public class StationTable extends Table<Station> {

	public StationTable() {
		super("cop_station");

		Attribute<Integer> id     = new Attribute<>("id", true, Integer.class);
		Attribute<String>  name   = new Attribute<>("name", false, String.class);
		Attribute<String>  world  = new Attribute<>("world", false, String.class);
		Attribute<Double>  x      = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y      = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z      = new Attribute<>("z", false, Double.class);
		Attribute<Float>   yaw    = new Attribute<>("yaw", false, Float.class);
		Attribute<Integer> jailId = new Attribute<>("jail_id", false, Integer.class);

		jailId.setCanBeNull(true);

		this.addAttribute(id);
		this.addAttribute(name);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(jailId);
	}

	@Override
	public Object[] getData(Station data) {
		return new Object[]{data.getId(), data.getName(), data.getWorld(), data.getX(), data.getY(), data.getZ(),
		                    data.getYaw(), data.getJailId()};
	}

	@Override
	public Map<String, Object> searchCriteria(Station data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
