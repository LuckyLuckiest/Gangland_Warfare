package org.luckyraven.gangland.file.configuration.turf;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.luckyraven.keystone.util.TimeUtil;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.util.TimeMessages;

/**
 * Routes {@link TurfMessageContract} calls through the impl-side {@link Messages} enum. The key passed in must match a
 * {@code Messages} enum constant — typos throw {@link IllegalArgumentException} via {@link Messages#valueOf}.
 *
 * <p>{@code replacements} follow the paired convention used elsewhere in the plugin:
 * {@code (placeholder, value, placeholder, value, ...)}. Each placeholder is wrapped in {@code %...%} before
 * substitution.
 */
public final class GanglandTurfMessages implements TurfMessageContract {

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

	@Override
	public void broadcast(String key, Object... replacements) {
		Bukkit.broadcastMessage(format(key, replacements));
	}

	@Override
	public String formatDuration(long seconds) {
		return TimeUtil.formatTime(seconds, true, TimeMessages.getInstance());
	}
}
