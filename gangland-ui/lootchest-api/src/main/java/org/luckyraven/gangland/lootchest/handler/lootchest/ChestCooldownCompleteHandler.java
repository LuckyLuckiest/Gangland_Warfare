package org.luckyraven.gangland.lootchest.handler.lootchest;

import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class ChestCooldownCompleteHandler extends LootChestHandler<LootChestData> {

	public ChestCooldownCompleteHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
