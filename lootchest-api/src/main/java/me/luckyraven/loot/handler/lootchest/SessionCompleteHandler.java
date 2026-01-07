package me.luckyraven.loot.handler.lootchest;

import me.luckyraven.loot.data.LootChestSession;
import me.luckyraven.loot.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class SessionCompleteHandler extends LootChestHandler<LootChestSession> {

	public SessionCompleteHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
