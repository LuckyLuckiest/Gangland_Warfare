package org.luckyraven.gangland.copsncrooks.events.police;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

@Getter
@Setter
public class DuringCuffingEvent extends Event {

	private static final HandlerList handler = new HandlerList();

	private final CopNpc cop;
	private final Player target;
	private final double cuffRadius;
	private final int    maxAttempts;
	private final long   maxCuffingCooldown;
	private final long   currentCuffingCooldown;

	public DuringCuffingEvent(CopNpc cop, Player target, double cuffRadius, int maxAttempts, long maxCuffingCooldown,
	                          long currentCuffingCooldown) {
		this.cop                    = cop;
		this.target                 = target;
		this.cuffRadius             = cuffRadius;
		this.maxAttempts            = maxAttempts;
		this.maxCuffingCooldown     = maxCuffingCooldown;
		this.currentCuffingCooldown = currentCuffingCooldown;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handler;
	}

}