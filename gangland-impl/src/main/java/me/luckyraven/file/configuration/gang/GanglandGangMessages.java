package me.luckyraven.file.configuration.gang;

import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.contract.GangMessageContract;
import org.bukkit.command.CommandSender;

/**
 * Routes {@link GangMessageContract} calls through the impl-side {@link Messages} enum. The key must match a
 * {@code Messages} enum constant — a typo throws {@link IllegalArgumentException} via {@link Messages#valueOf}.
 *
 * <p>{@code replacements} follow the pair convention used everywhere else in the plugin: alternating
 * {@code (placeholder, value, placeholder, value, ...)}. Each {@code placeholder} is wrapped in {@code %...%} before
 * being substituted into the resolved string.
 */
public final class GanglandGangMessages implements GangMessageContract {

	@Override
	public String format(String key, Object... replacements) {
		String resolved = Messages.valueOf(key).toString();
		if (replacements == null || replacements.length < 2) {
			return resolved;
		}
		for (int i = 0; i + 1 < replacements.length; i += 2) {
			String placeholder = "%" + replacements[i] + "%";
			String value       = String.valueOf(replacements[i + 1]);
			resolved = resolved.replace(placeholder, value);
		}
		return resolved;
	}

	@Override
	public void send(CommandSender recipient, String key, Object... replacements) {
		if (recipient == null) {
			return;
		}
		recipient.sendMessage(format(key, replacements));
	}
}
