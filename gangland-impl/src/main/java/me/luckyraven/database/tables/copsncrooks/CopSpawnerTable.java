package me.luckyraven.database.tables.copsncrooks;

import me.luckyraven.copsncrooks.police.spawn.CopSpawner;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;
import org.bukkit.Location;

import java.sql.Types;
import java.util.Map;
import java.util.Objects;

public class CopSpawnerTable extends Table<CopSpawner> {

	public CopSpawnerTable() {
		super("cop_spawner");

		Attribute<Integer> id    = new Attribute<>("id", true, Integer.class);
		Attribute<String>  world = new Attribute<>("world", false, String.class);
		Attribute<Double>  x     = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y     = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z     = new Attribute<>("z", false, Double.class);
		Attribute<Float>   yaw   = new Attribute<>("yaw", false, Float.class);
		Attribute<Float>   pitch = new Attribute<>("pitch", false, Float.class);

		this.addAttribute(id);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(pitch);
	}

	@Override
	public Object[] getData(CopSpawner data) {
		Location location = data.getLocation();

		return new Object[]{data.getId(), Objects.requireNonNull(location.getWorld()).getName(), location.getX(),
							location.getY(), location.getZ(), location.getYaw(), location.getPitch()};
	}

	@Override
	public Map<String, Object> searchCriteria(CopSpawner data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
