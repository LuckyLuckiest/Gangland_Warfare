package me.luckyraven.lootchest.handler.lootchest;

import me.luckyraven.lootchest.data.LootChestSession;
import me.luckyraven.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class SessionStartHandler extends LootChestHandler<LootChestSession> {

	public SessionStartHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
