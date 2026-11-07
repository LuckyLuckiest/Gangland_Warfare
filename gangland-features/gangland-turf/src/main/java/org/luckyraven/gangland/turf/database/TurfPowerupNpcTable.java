package org.luckyraven.gangland.turf.database;

import org.bukkit.Location;
import org.bukkit.World;
import org.luckyraven.gangland.turf.npc.TurfPowerupData;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class TurfPowerupNpcTable extends Table<TurfPowerupData> {

	public TurfPowerupNpcTable() {
		super("turf_powerup_npc");

		Attribute<Integer> turfId      = new Attribute<>("turf_id", true, Integer.class);
		Attribute<String>  world       = new Attribute<>("world", false, String.class);
		Attribute<Double>  x           = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y           = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z           = new Attribute<>("z", false, Double.class);
		Attribute<Double>  yaw         = new Attribute<>("yaw", false, Double.class);
		Attribute<Double>  pitch       = new Attribute<>("pitch", false, Double.class);
		Attribute<String>  displayName = new Attribute<>("display_name", false, String.class);

		displayName.setCanBeNull(true);
		yaw.setDefaultValue(0D);
		pitch.setDefaultValue(0D);

		this.addAttribute(turfId);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(pitch);
		this.addAttribute(displayName);
	}

	@Override
	public Object[] getData(TurfPowerupData data) {
		Location location = data.getSpawnLocation();
		World    world    = location.getWorld();
		return new Object[]{
				data.getTurfId(),
				world != null ? world.getName() : "world",
				location.getX(),
				location.getY(),
				location.getZ(),
				(double) location.getYaw(),
				(double) location.getPitch(),
				data.getDisplayName()
		};
	}

	@Override
	public Map<String, Object> searchCriteria(TurfPowerupData data) {
		return createSearchCriteria("turf_id = ?", new Object[]{data.getTurfId()},
		                            new int[]{Types.INTEGER}, new int[]{0});
	}
}
