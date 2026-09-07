package org.luckyraven.gangland.database.tables.copsncrooks;

import org.bukkit.Location;
import org.luckyraven.gangland.copsncrooks.jail.Jail;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;
import java.util.Objects;

public class JailTable extends Table<Jail> {

	public JailTable() {
		super("jail");

		Attribute<Integer> id          = new Attribute<>("id", true, Integer.class);
		Attribute<String>  world       = new Attribute<>("world", false, String.class);
		Attribute<Double>  x           = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y           = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z           = new Attribute<>("z", false, Double.class);
		Attribute<Integer> maxCapacity = new Attribute<>("max_capacity", false, Integer.class);

		this.addAttribute(id);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(maxCapacity);
	}

	@Override
	public Object[] getData(Jail data) {
		Location location = data.getLocation();

		return new Object[]{data.getId(), Objects.requireNonNull(location.getWorld()).getName(), location.getX(),
		                    location.getY(), location.getZ(), data.getMaxCapacity()};
	}

	@Override
	public Map<String, Object> searchCriteria(Jail data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
