package me.luckyraven.loot.handler.lootchest;

import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.loot.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class ChestCooldownCompleteHandler extends LootChestHandler<LootChestData> {

	public ChestCooldownCompleteHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
