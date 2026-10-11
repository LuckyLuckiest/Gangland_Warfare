package org.luckyraven.gangland.fights.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

@ListenerHandler
public class FightWantedGuard implements Listener {

	public FightWantedGuard(FightManager manager) {
	}

	@EventHandler
	public void onWantedChange(org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent event) {
	}
}
