package me.luckyraven.copsncrooks.events.npc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.AbstractNpc;
import org.bukkit.event.Event;

/**
 * Base class for all gangland NPC-related Bukkit events. Concrete subclasses must provide their own
 * {@code HandlerList}.
 */
@Getter
@RequiredArgsConstructor
public abstract class NpcEvent extends Event {

	private final AbstractNpc npc;
}
