package org.luckyraven.gangland.data.placeholder.extension;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Placeholder parameters a runtime module resolves on the core's behalf. The core cannot name a module type
 * (e.g. {@code gang_*} and the member-touching {@code user_has-gang}/{@code user_gang-id}/{@code user_rank} family
 * all need the gang module's {@code GangManager}/{@code MemberManager}), so a module that wants to answer those
 * registers a bean implementing this contract; {@code GanglandPlaceholder} queries every contribution once its own
 * built-in prefixes (user/bank/unique-item) have missed. Modelled on {@code command.extension.CommandContribution}.
 */
public interface PlaceholderContribution {

	/**
	 * @param player    the player the placeholder is being resolved for (never {@code null} — the core only
	 *                  dispatches here for a non-null player).
	 * @param parameter the lower-cased placeholder parameter, e.g. {@code "gang_name"}.
	 * @return the resolved value, or {@code null} when this contribution doesn't own {@code parameter}.
	 */
	@Nullable
	String resolve(OfflinePlayer player, String parameter);
}
