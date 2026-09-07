package org.luckyraven.gangland.copsncrooks.events.npc;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Event;
import org.luckyraven.gangland.copsncrooks.npc.AbstractNpc;

/**
 * Base class for all gangland NPC-related Bukkit events. Concrete subclasses must provide their own
 * {@code HandlerList}.
 */
@Getter
@RequiredArgsConstructor
public abstract class NpcEvent extends Event {

	private final AbstractNpc npc;
}
