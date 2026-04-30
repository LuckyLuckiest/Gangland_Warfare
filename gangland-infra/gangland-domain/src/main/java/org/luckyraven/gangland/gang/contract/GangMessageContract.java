package org.luckyraven.gangland.gang.contract;

import org.bukkit.command.CommandSender;

/**
 * Routes gang / user / rank / member user-facing strings through the {@code Messages} enum without importing it from
 * domain code. The implementation in gangland-impl looks up each key and applies the plugin's standard prefix +
 * placeholder substitution.
 *
 * <p>Keys are defined as constants on this interface so the gang module
 * has a compile-time contract for what it can broadcast. The impl verifies every key is present in {@code Messages} at
 * bean-init time.
 */
public interface GangMessageContract {

	/**
	 * Resolve a message by its key, substituting {@code %placeholder%} tokens with the given replacements (alternating
	 * key/value pairs).
	 */
	String format(String key, Object... replacements);

	/**
	 * Send a resolved message to the given recipient, applying the plugin's standard chat prefix.
	 */
	void send(CommandSender recipient, String key, Object... replacements);
}
