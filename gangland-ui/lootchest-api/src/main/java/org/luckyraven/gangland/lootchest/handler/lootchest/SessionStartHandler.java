package org.luckyraven.gangland.lootchest.handler.lootchest;

import org.luckyraven.gangland.lootchest.data.LootChestSession;
import org.luckyraven.gangland.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class SessionStartHandler extends LootChestHandler<LootChestSession> {

	public SessionStartHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
