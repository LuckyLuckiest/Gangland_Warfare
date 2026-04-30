package org.luckyraven.gangland.lootchest.handler.lootchest;

import org.luckyraven.gangland.lootchest.data.LootChestSession;
import org.luckyraven.gangland.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class SessionCompleteHandler extends LootChestHandler<LootChestSession> {

	public SessionCompleteHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
