package me.luckyraven.lootchest.handler.lootchest;

import me.luckyraven.lootchest.data.LootChestSession;
import me.luckyraven.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class SessionCompleteHandler extends LootChestHandler<LootChestSession> {

	public SessionCompleteHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
