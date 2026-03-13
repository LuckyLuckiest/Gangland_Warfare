package me.luckyraven.lootchest.handler.lootchest;

import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class ChestCooldownTickHandler extends LootChestHandler<LootChestData> {

	public ChestCooldownTickHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
