package org.luckyraven.gangland.copsncrooks.database;

import org.bukkit.Location;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawner;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

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
		// appended last in 0.16 (the schema diff adds the column to an older table); null = no station
		Attribute<Integer> station = new Attribute<>("station_id", false, Integer.class);

		station.setCanBeNull(true);

		this.addAttribute(id);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(pitch);
		this.addAttribute(station);
	}

	@Override
	public Object[] getData(CopSpawner data) {
		Location location = data.getLocation();

		return new Object[]{data.getId(), Objects.requireNonNull(location.getWorld()).getName(), location.getX(),
		                    location.getY(), location.getZ(), location.getYaw(), location.getPitch(), data.getStationId()};
	}

	@Override
	public Map<String, Object> searchCriteria(CopSpawner data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
