package me.luckyraven.events.gang;

import lombok.Getter;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.events.level.LevelUpEvent;
import me.luckyraven.features.level.Level;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

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
