package me.luckyraven.copsncrooks.events.npc;

import lombok.Getter;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a civilian NPC dies. Carries the NPC, the killer (if any), and the configured experience reward. Listeners
 * in gangland-impl use this to apply level rewards via {@code UserManager}.
 */
@Getter
public class CivilianDeathEvent extends NpcEvent {
	private static final HandlerList HANDLERS = new HandlerList();

	private final CivilianNpc civilianNpc;
	@Nullable
	private final Player      killer;
	private final double      experience;

	public CivilianDeathEvent(CivilianNpc npc, @Nullable Player killer, double experience) {
		super(npc);
		this.civilianNpc = npc;
		this.killer      = killer;
		this.experience  = experience;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}
}
