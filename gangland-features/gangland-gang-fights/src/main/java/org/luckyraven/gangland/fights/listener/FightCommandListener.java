package org.luckyraven.gangland.fights.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

@ListenerHandler
public class FightCommandListener implements Listener {

	public FightCommandListener(FightManager manager, FightRules rules, FightMessages messages) {
	}

	@EventHandler
	public void onCommand(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
	}

	String canonical(String token) {
		return token;
	}
}
