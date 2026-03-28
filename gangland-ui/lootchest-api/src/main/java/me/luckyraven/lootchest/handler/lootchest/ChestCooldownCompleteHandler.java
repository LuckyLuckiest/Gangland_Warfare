package me.luckyraven.lootchest.handler.lootchest;

import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class ChestCooldownCompleteHandler extends LootChestHandler<LootChestData> {

	public ChestCooldownCompleteHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
