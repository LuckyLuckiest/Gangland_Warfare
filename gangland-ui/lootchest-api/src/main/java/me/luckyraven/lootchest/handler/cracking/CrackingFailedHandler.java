package me.luckyraven.lootchest.handler.cracking;

import me.luckyraven.lootchest.data.CrackingSession;
import me.luckyraven.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class CrackingFailedHandler extends LootChestHandler<CrackingSession> {

	public CrackingFailedHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
