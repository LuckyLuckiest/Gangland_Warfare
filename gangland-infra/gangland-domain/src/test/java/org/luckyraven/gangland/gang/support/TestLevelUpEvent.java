package org.luckyraven.gangland.gang.support;

import org.bukkit.event.HandlerList;
import org.luckyraven.gangland.gang.events.level.LevelUpEvent;
import org.luckyraven.gangland.gang.user.Level;

/**
 * Minimal concrete {@link LevelUpEvent} for domain-module tests. {@code LevelUpEvent} is abstract only because it
 * extends Bukkit's {@link org.bukkit.event.Event}, which requires a {@code HandlerList}; production code supplies
 * this via {@code UserLevelUpEvent}/{@code GangLevelUpEvent} in gangland-impl, which is out of this module's reach.
 */
public final class TestLevelUpEvent extends LevelUpEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	public TestLevelUpEvent(Level level) {
		super(false, level);
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public HandlerList getHandlers() {
		return HANDLERS;
	}

}
