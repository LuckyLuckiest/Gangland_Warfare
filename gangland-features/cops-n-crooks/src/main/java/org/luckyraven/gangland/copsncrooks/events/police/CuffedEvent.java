package org.luckyraven.gangland.copsncrooks.events.police;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

@Getter
@Setter
public class CuffedEvent extends Event {

	private static final HandlerList handler = new HandlerList();

	private final CopNpc cop;
	private final Player target;
	private final double cuffRadius;
	private final int    maxAttempts;
	/** How the arrest happened (0.21.0). */
	private final ArrestOrigin origin;

	public CuffedEvent(CopNpc cop, Player target, double cuffRadius, int maxAttempts) {
		this(cop, target, cuffRadius, maxAttempts, null); // TDD stub: the green step defaults CUFFED
	}

	public CuffedEvent(CopNpc cop, Player target, double cuffRadius, int maxAttempts, ArrestOrigin origin) {
		this.cop         = cop;
		this.target      = target;
		this.cuffRadius  = cuffRadius;
		this.maxAttempts = maxAttempts;
		this.origin      = origin;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handler;
	}

}