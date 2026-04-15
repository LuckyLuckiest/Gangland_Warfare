package me.luckyraven.copsncrooks.events.trader;

import lombok.Getter;
import me.luckyraven.copsncrooks.npc.trader.TraderNpc;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

@Getter
public abstract class TraderEvent extends Event {

	protected final Player    player;
	protected final TraderNpc trader;

	protected TraderEvent(Player player, TraderNpc trader) {
		this.player = player;
		this.trader = trader;
	}

}
