package me.luckyraven.database.tables.trader;

import me.luckyraven.copsncrooks.npc.trader.TraderData;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;

public class TraderTable extends Table<TraderData> {

	public TraderTable() {
		super("trader");

		Attribute<String> id          = new Attribute<>("id", true, String.class);
		Attribute<String> shopKey     = new Attribute<>("shop_key", false, String.class);
		Attribute<String> world       = new Attribute<>("world", false, String.class);
		Attribute<Double> x           = new Attribute<>("x", false, Double.class);
		Attribute<Double> y           = new Attribute<>("y", false, Double.class);
		Attribute<Double> z           = new Attribute<>("z", false, Double.class);
		Attribute<Double> yaw         = new Attribute<>("yaw", false, Double.class);
		Attribute<Double> pitch       = new Attribute<>("pitch", false, Double.class);
		Attribute<String> displayName = new Attribute<>("display_name", false, String.class);
		Attribute<String> traitId     = new Attribute<>("trait_id", false, String.class);

		displayName.setCanBeNull(true);
		yaw.setDefaultValue(0D);
		pitch.setDefaultValue(0D);

		this.addAttribute(id);
		this.addAttribute(shopKey);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(yaw);
		this.addAttribute(pitch);
		this.addAttribute(displayName);
		this.addAttribute(traitId);
	}

	@Override
	public Object[] getData(TraderData data) {
		return new Object[]{
				data.getId().toString(),
				data.getShopKey(),
				data.getSpawnLocation().getWorld() != null ? data.getSpawnLocation().getWorld().getName() : "world",
				data.getSpawnLocation().getX(),
				data.getSpawnLocation().getY(),
				data.getSpawnLocation().getZ(),
				(double) data.getSpawnLocation().getYaw(),
				(double) data.getSpawnLocation().getPitch(),
				data.getDisplayName(),
				data.getTraitId()
		};
	}

	@Override
	public Map<String, Object> searchCriteria(TraderData data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId().toString()},
		                            new int[]{Types.VARCHAR}, new int[]{0});
	}

}
