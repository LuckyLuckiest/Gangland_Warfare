package me.luckyraven.loot.handler.lootchest;

import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.loot.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class ChestCooldownTickHandler extends LootChestHandler<LootChestData> {

	public ChestCooldownTickHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
