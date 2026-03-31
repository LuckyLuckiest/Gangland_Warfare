package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.entity.npc.NpcBehavior;
import me.luckyraven.copsncrooks.police.npc.CopNpc;

/**
 * Behavior contract for a single cop AI state.
 * <p>
 * The active target player is stored on the {@link CopNpc} via {@link CopNpc#getTargetPlayerId()} before each tick, so
 * behaviors retrieve it from the NPC rather than through a separate parameter.
 */
public interface CopBehavior extends NpcBehavior<CopNpc> {
}
