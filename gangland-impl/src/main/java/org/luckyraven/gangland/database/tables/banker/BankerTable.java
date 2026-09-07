package org.luckyraven.gangland.database.tables.banker;

import org.luckyraven.gangland.copsncrooks.npc.banker.BankerData;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class BankerTable extends Table<BankerData> {

	public BankerTable() {
		super("banker");

		Attribute<String> id          = new Attribute<>("id", true, String.class);
		Attribute<String> world       = new Attribute<>("world", false, String.class);
		Attribute<Double> x           = new Attribute<>("x", false, Double.class);
		Attribute<Double> y           = new Attribute<>("y", false, Double.class);
		Attribute<Double> z           = new Attribute<>("z", false, Double.class);
		Attribute<Double> yaw         = new Attribute<>("yaw", false, Double.class);
		Attribute<Double> pitch       = new Attribute<>("pitch", false, Double.class);
		Attribute<String> displayName = new Attribute<>("display_name", false, String.class);

		displayName.setCanBeNull(true);
		yaw.setDefaultValue(0D);
		pitch.setDefaultValue(0D);

		this.addAttribute(id);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(pitch);
		this.addAttribute(displayName);
	}

	@Override
	public Object[] getData(BankerData data) {
		return new Object[]{
				data.getId().toString(),
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
	public Map<String, Object> searchCriteria(BankerData data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId().toString()},
		                            new int[]{Types.VARCHAR}, new int[]{0});
	}

}
