package me.luckyraven.database.tables.turf;

import me.luckyraven.copsncrooks.npc.turf.TurfPowerupData;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

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
		return new Object[]{
				data.getTurfId(),
				data.getSpawnLocation().getWorld() != null ? data.getSpawnLocation().getWorld().getName() : "world",
				data.getSpawnLocation().getX(),
				data.getSpawnLocation().getY(),
				data.getSpawnLocation().getZ(),
				(double) data.getSpawnLocation().getYaw(),
				(double) data.getSpawnLocation().getPitch(),
				data.getDisplayName()
		};
	}

	@Override
	public Map<String, Object> searchCriteria(TurfPowerupData data) {
		return createSearchCriteria("turf_id = ?", new Object[]{data.getTurfId()},
		                            new int[]{Types.INTEGER}, new int[]{0});
	}
}
