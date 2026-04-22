package me.luckyraven.turf.contract;

import org.bukkit.command.CommandSender;

/**
 * Routes turf-facing user strings through gangland-impl's {@code Messages} enum. Keys are referenced by name; the impl
 * verifies each key exists at bean-init time.
 */
public interface TurfMessageContract {

	String format(String key, Object... replacements);

	void send(CommandSender recipient, String key, Object... replacements);

	void broadcast(String key, Object... replacements);
}
