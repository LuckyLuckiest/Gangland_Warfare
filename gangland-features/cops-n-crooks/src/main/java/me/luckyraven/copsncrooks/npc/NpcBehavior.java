package me.luckyraven.copsncrooks.npc;

/**
 * Generic contract for a single NPC AI state behavior.
 *
 * @param <T> the concrete NPC type this behavior operates on
 */
public interface NpcBehavior<T extends AbstractNpc> {

	/**
	 * Executes one AI tick while this behavior is active.
	 */
	void tick(T npc);

	/**
	 * Called when the NPC transitions into this state.
	 */
	void onEnter(T npc);

	/**
	 * Called when the NPC transitions out of this state.
	 */
	void onExit(T npc);
}
