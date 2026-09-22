package org.luckyraven.gangland.lootchest.handler.lootchest;

import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class ChestCooldownTickHandler extends LootChestHandler<LootChestData> {

	public ChestCooldownTickHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
