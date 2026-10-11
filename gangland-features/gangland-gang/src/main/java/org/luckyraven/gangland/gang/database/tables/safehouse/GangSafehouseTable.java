package org.luckyraven.gangland.gang.database.tables.safehouse;

import org.luckyraven.gangland.gang.database.tables.gang.GangTable;
import org.luckyraven.gangland.gang.safehouse.Safehouse;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

/**
 * {@code gang_safehouse}: one row per saved gang teleport spot. The key is the pair {@code (gang_id, name)}, so two
 * gangs may both own a safehouse called {@code base}, while one gang cannot own the same name twice.
 */
public class GangSafehouseTable extends Table<Safehouse> {

	public GangSafehouseTable(GangTable gangTable) {
		super("gang_safehouse");

		Attribute<Integer> gangId = new Attribute<>("gang_id", true, Integer.class);
		Attribute<String>  name   = new Attribute<>("name", true, String.class);
		Attribute<String>  world  = new Attribute<>("world", false, String.class);
		Attribute<Double>  x      = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y      = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z      = new Attribute<>("z", false, Double.class);
		Attribute<Float>   yaw    = new Attribute<>("yaw", false, Float.class);
		Attribute<Float>   pitch  = new Attribute<>("pitch", false, Float.class);

		gangId.setForeignKey(gangTable.get("id"), gangTable);

		this.addAttribute(gangId);
		this.addAttribute(name);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(pitch);
	}

	@Override
	public Object[] getData(Safehouse data) {
		return new Object[]{data.gangId(), data.name(), data.world(), data.x(), data.y(), data.z(), data.yaw(),
		                    data.pitch()};
	}

	@Override
	public Map<String, Object> searchCriteria(Safehouse data) {
		return createSearchCriteria("gang_id = ? AND name = ?", new Object[]{data.gangId(), data.name()},
		                            new int[]{Types.INTEGER, Types.VARCHAR}, new int[]{0, 1});
	}
}
