package org.luckyraven.gangland.lootchest.handler.cracking;

import org.luckyraven.gangland.lootchest.data.CrackingSession;
import org.luckyraven.gangland.lootchest.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class CrackingSuccessHandler extends LootChestHandler<CrackingSession> {

	public CrackingSuccessHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}
