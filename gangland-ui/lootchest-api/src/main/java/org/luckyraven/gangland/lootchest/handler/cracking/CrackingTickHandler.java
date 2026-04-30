package org.luckyraven.gangland.lootchest.handler.cracking;

import org.luckyraven.gangland.lootchest.data.CrackingSession;
import org.luckyraven.gangland.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class CrackingTickHandler extends LootChestHandler<CrackingSession> {

	public CrackingTickHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
