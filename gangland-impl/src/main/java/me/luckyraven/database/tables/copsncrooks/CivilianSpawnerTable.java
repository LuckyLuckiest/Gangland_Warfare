package me.luckyraven.database.tables.copsncrooks;

import me.luckyraven.copsncrooks.civilian.spawn.CivilianSpawner;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;
import org.bukkit.Location;

import java.sql.Types;
import java.util.Map;
import java.util.Objects;

public class CivilianSpawnerTable extends Table<CivilianSpawner> {

	public CivilianSpawnerTable() {
		super("civilian_spawner");

		Attribute<Integer> id     = new Attribute<>("id", true, Integer.class);
		Attribute<String>  typeId = new Attribute<>("type_id", false, String.class);
		Attribute<String>  world  = new Attribute<>("world", false, String.class);
		Attribute<Double>  x      = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y      = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z      = new Attribute<>("z", false, Double.class);
		Attribute<Float>   yaw    = new Attribute<>("yaw", false, Float.class);
		Attribute<Float>   pitch  = new Attribute<>("pitch", false, Float.class);

		this.addAttribute(id);
		this.addAttribute(typeId);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(pitch);
	}

	@Override
	public Object[] getData(CivilianSpawner data) {
		Location location = data.getLocation();

		return new Object[]{data.getId(), data.getTypeId(), Objects.requireNonNull(location.getWorld()).getName(),
		                    location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch()};
	}

	@Override
	public Map<String, Object> searchCriteria(CivilianSpawner data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
