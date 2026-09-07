package org.luckyraven.gangland.gang.events.level;

import lombok.Getter;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.luckyraven.gangland.gang.user.Level;

@Getter
public abstract class LevelUpEvent extends Event implements Cancellable {

	private final Level level;

	private boolean cancelled;

	public LevelUpEvent(boolean async, Level level) {
		super(async);

		this.level     = level;
		this.cancelled = false;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}
}
