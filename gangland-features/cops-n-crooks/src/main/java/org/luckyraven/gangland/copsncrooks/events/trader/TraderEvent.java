package org.luckyraven.gangland.copsncrooks.events.trader;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.luckyraven.gangland.copsncrooks.npc.trader.TraderNpc;

@Getter
public abstract class TraderEvent extends Event {

	protected final Player    player;
	protected final TraderNpc trader;

	protected TraderEvent(Player player, TraderNpc trader) {
		this.player = player;
		this.trader = trader;
	}

}
