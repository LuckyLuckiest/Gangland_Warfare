package me.luckyraven.loot.handler.cracking;

import me.luckyraven.loot.data.CrackingSession;
import me.luckyraven.loot.handler.LootChestHandler;

import java.util.concurrent.CopyOnWriteArrayList;

public class CrackingStartHandler extends LootChestHandler<CrackingSession> {

	public CrackingStartHandler() {
		super(new CopyOnWriteArrayList<>());
	}

}