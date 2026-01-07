package me.luckyraven.loot.handler.lootchest;

import me.luckyraven.loot.data.LootChestSession;
import me.luckyraven.loot.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class SessionStartHandler extends LootChestHandler<LootChestSession> {

	public SessionStartHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
