package me.luckyraven.sign.handler;

import me.luckyraven.sign.aspect.AspectResult;
import me.luckyraven.sign.model.ParsedSign;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles sign interaction execution
 */
public interface SignHandler {

	/**
	 * Handle sign interaction
	 *
	 * @param player The player interacting
	 * @param sign The parsed sign
	 *
	 * @return Results from all executed aspects
	 */
	List<AspectResult> handle(Player player, ParsedSign sign);

	/**
	 * Check if player can interact with this sign
	 */
	boolean canHandle(Player player, ParsedSign sign);

}
