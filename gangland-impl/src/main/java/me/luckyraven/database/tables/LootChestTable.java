package me.luckyraven.database.tables;

import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;
import me.luckyraven.loot.data.LootChestData;

import java.sql.Types;
import java.util.Map;

public class LootChestTable extends Table<LootChestData> {

	public LootChestTable() {
		super("loot_chest");

		Attribute<String>  id            = new Attribute<>("id", true, String.class);
		Attribute<String>  world         = new Attribute<>("world", false, String.class);
		Attribute<Double>  x             = new Attribute<>("x", false, Double.class);
		Attribute<Double>  y             = new Attribute<>("y", false, Double.class);
		Attribute<Double>  z             = new Attribute<>("z", false, Double.class);
		Attribute<String>  lootTableId   = new Attribute<>("loot_table_id", false, String.class);
		Attribute<String>  tierId        = new Attribute<>("tier_id", false, String.class);
		Attribute<Long>    respawnTime   = new Attribute<>("respawn_time", false, Long.class);
		Attribute<Integer> inventorySize = new Attribute<>("inventory_size", false, Integer.class);
		Attribute<String>  displayName   = new Attribute<>("display_name", false, String.class);
		Attribute<Long>    lastOpened    = new Attribute<>("last_opened", false, Long.class);
		Attribute<Boolean> isLooted      = new Attribute<>("is_looted", false, Boolean.class);

		tierId.setCanBeNull(true);
		lastOpened.setDefaultValue(0L);
		isLooted.setDefaultValue(false);
		inventorySize.setDefaultValue(27);
		respawnTime.setDefaultValue(0L);

		this.addAttribute(id);
		this.addAttribute(world);
		this.addAttribute(x);
		this.addAttribute(y);
		this.addAttribute(z);
		this.addAttribute(lootTableId);
		this.addAttribute(tierId);
		this.addAttribute(respawnTime);
		this.addAttribute(inventorySize);
		this.addAttribute(displayName);
		this.addAttribute(lastOpened);
		this.addAttribute(isLooted);
	}

	@Override
	public Object[] getData(LootChestData data) {
		return new Object[]{data.getId().toString(),
							data.getLocation().getWorld() != null ? data.getLocation().getWorld().getName() : "world",
							data.getLocation().getX(), data.getLocation().getY(), data.getLocation().getZ(),
							data.getLootTableId(), data.getTier() != null ? data.getTier().id() : null,
							data.getRespawnTime(), data.getInventorySize(), data.getDisplayName(), data.getLastOpened(),
							data.isLooted()};
	}

	@Override
	public Map<String, Object> searchCriteria(LootChestData data) {
		return createSearchCriteria("id = ?", new Object[]{data.getId().toString()}, new int[]{Types.VARCHAR},
									new int[]{0});
	}

}
