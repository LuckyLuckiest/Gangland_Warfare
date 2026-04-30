package org.luckyraven.gangland.copsncrooks.npc.police.state;

import org.luckyraven.gangland.copsncrooks.npc.NpcBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

/**
 * Behavior contract for a single cop AI state.
 * <p>
 * The active target player is stored on the {@link CopNpc} via {@link CopNpc#getTargetPlayerId()} before each tick, so
 * behaviors retrieve it from the NPC rather than through a separate parameter.
 */
public interface CopBehavior extends NpcBehavior<CopNpc> {
}
