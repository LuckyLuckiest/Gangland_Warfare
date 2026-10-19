package org.luckyraven.gangland.copsncrooks.wanted.mostwanted;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;

/**
 * {@code %gangland_cnc_most_wanted%} and {@code %gangland_cnc_most_wanted_stars%}.
 *
 * @since 0.21.0
 */
public class MostWantedPlaceholders implements PlaceholderContribution {

	private final MostWanted mostWanted;

	public MostWantedPlaceholders(MostWanted mostWanted) {
		this.mostWanted = mostWanted;
	}

	@Override
	public @Nullable String resolve(OfflinePlayer player, String parameter) {
		return null;
	}

	@Override
	public @Nullable String resolveGlobal(String token) {
		return null;
	}
}
