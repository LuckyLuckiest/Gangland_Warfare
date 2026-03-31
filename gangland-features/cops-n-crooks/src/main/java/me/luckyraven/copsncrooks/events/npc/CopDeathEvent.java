package me.luckyraven.copsncrooks.events.npc;

import lombok.Getter;
import me.luckyraven.copsncrooks.npc.police.npc.CopNpc;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a cop NPC dies. Carries the NPC and the killer (if any).
 */
@Getter
public class CopDeathEvent extends NpcEvent {
	private static final HandlerList HANDLERS = new HandlerList();

	@Nullable
	private final Player killer;

	public CopDeathEvent(CopNpc npc, @Nullable Player killer) {
		super(npc);
		this.killer = killer;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
