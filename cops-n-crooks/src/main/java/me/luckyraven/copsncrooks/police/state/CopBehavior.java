package me.luckyraven.copsncrooks.police.state;

import me.luckyraven.copsncrooks.police.npc.CopNpc;
import org.bukkit.entity.Player;

/**
 * Defines behavior for a single cop AI state.
 */
public interface CopBehavior {

	/**
	 * Executes a single AI tick for the cop in this state.
	 *
	 * @param cop the cop NPC
	 * @param target the current target player, or null if none
	 */
	void tick(CopNpc cop, Player target);

	/**
	 * Called when the cop enters this state.
	 *
	 * @param cop the cop NPC
	 */
	void onEnter(CopNpc cop);

	/**
	 * Called when the cop exits this state.
	 *
	 * @param cop the cop NPC
	 */
	void onExit(CopNpc cop);
}