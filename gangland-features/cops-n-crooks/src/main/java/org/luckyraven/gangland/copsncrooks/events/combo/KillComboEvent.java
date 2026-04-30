package org.luckyraven.gangland.copsncrooks.events.combo;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.luckyraven.gangland.copsncrooks.combo.KillComboTracker;

@Getter
public class KillComboEvent extends Event {

	private static final HandlerList handler = new HandlerList();

	private final Player           player;
	private final KillComboTracker tracker;

	public KillComboEvent(Player player, KillComboTracker tracker) {
		this.player  = player;
		this.tracker = tracker;
	}

	public static HandlerList getHandlerList() {
		return handler;
	}

	public int getNormalKillCount() {
		return tracker.getNormalKillCount();
	}

	public int getPointKillCount() {
		return tracker.getPointKillCount();
	}

	public long getRemainingTime() {
		return tracker.getRemainingTime();
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handler;
	}

}
