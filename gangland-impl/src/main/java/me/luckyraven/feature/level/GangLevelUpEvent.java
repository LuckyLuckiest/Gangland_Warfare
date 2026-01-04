package me.luckyraven.feature.level;

import lombok.Getter;
import me.luckyraven.data.account.gang.Gang;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class GangLevelUpEvent extends LevelUpEvent implements Cancellable {

	private static final HandlerList handler = new HandlerList();

	@Getter
	private final Gang gang;

	private boolean cancelled;

	public GangLevelUpEvent(Gang gang, Level level) {
		super(level);

		this.gang = gang;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	@Override
	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancelled = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handler;
	}

}
