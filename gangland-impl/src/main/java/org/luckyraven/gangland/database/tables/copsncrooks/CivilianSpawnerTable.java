package org.luckyraven.gangland.database.tables.copsncrooks;

import org.bukkit.Location;
import org.luckyraven.gangland.copsncrooks.npc.civilian.spawn.CivilianSpawner;
import org.luckyraven.gangland.persistence.database.component.Attribute;
import org.luckyraven.gangland.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;
import java.util.Objects;

public class CivilianSpawnerTable extends Table<CivilianSpawner> {

	public CivilianSpawnerTable() {
		super("civilian_spawner");

		Attribute<Integer> id      = new Attribute<>("id", true, Integer.class);
		Attribute<String>  typeId  = new Attribute<>("type_id", false, String.class);
		Attribute<String>  groupId = new Attribute<>("group_id", false, String.class);
		Attribute<String>  world   = new Attribute<>("world", false, String.class);
		Attribute<Double>  x       = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y       = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z       = new Attribute<>("z", false, Double.class);
		Attribute<Float>   yaw     = new Attribute<>("yaw", false, Float.class);
		Attribute<Float>   pitch   = new Attribute<>("pitch", false, Float.class);

		typeId.setCanBeNull(true);
		groupId.setCanBeNull(true);

		this.addAttribute(id);
		this.addAttribute(typeId);
		this.addAttribute(groupId);
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

		return new Object[]{data.getId(), data.getTypeId(), data.getGroupId(),
		                    Objects.requireNonNull(location.getWorld()).getName(), location.getX(), location.getY(),
		                    location.getZ(), location.getYaw(), location.getPitch()};
	}

	@Override
	public Map<String, Object> searchCriteria(CivilianSpawner data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId()}, new int[]{Types.INTEGER}, new int[]{0});
	}
}
