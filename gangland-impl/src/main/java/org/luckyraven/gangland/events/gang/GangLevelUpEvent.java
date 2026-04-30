package org.luckyraven.gangland.events.gang;

import lombok.Getter;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.events.level.LevelUpEvent;
import org.luckyraven.gangland.gang.user.Level;

public class GangLevelUpEvent extends LevelUpEvent {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final Gang gang;

	public GangLevelUpEvent(boolean async, Gang gang, Level level) {
		super(async, level);

		this.gang = gang;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handler;
	}

}
