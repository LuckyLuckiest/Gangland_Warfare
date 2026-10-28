package org.luckyraven.gangland.copsncrooks.events.npc;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.npc.event.NpcEvent;

/**
 * Fired when a cop NPC dies. Carries the NPC and the killer (if any).
 * <p>
 * 0.21.2 (T-545): a death nobody saw is {@link #isSilent() silent}: the city does not know of it yet, so a casualty
 * or a cop-down count must ignore it. The same death is fired again, not silent, when dispatch notices it
 * (Officer_Missing or a 911 call), with {@link #getAt()} the spot he fell, his body gone by then.
 */
@Getter
public class CopDeathEvent extends NpcEvent {
	private static final HandlerList HANDLERS = new HandlerList();

	@Nullable
	private final Player   killer;
	private final boolean  silent;
	/** Where he fell; {@code null} when unknown. */
	@Nullable
	private final Location at;

	public CopDeathEvent(CopNpc npc, @Nullable Player killer) {
		this(npc, killer, false, null);
	}

	public CopDeathEvent(CopNpc npc, @Nullable Player killer, boolean silent, @Nullable Location at) {
		super(npc);
		this.killer = killer;
		this.silent = silent;
		this.at     = at;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
