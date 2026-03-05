package me.luckyraven.copsncrooks.events.police;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class CuffedEvent extends Event {

	private static final HandlerList handler = new HandlerList();

	private final CopNpc cop;
	private final Player target;
	private final double cuffRadius;
	private final int    maxAttempts;

	public CuffedEvent(CopNpc cop, Player target, double cuffRadius, int maxAttempts) {
		this.cop         = cop;
		this.target      = target;
		this.cuffRadius  = cuffRadius;
		this.maxAttempts = maxAttempts;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handler;
	}

}