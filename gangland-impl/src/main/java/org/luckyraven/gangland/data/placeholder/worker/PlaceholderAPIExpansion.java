package org.luckyraven.gangland.data.placeholder.worker;

import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.Gangland;

@RequiredArgsConstructor
public class PlaceholderAPIExpansion extends PlaceholderExpansion {

	private final Gangland            gangland;
	private final String              prefix;
	private final GanglandPlaceholder placeholder;

	@Override
	public @NotNull String getIdentifier() {
		return prefix;
	}

	@Override
	public @NotNull String getAuthor() {
		return gangland.getDescription().getAuthors().get(0);
	}

	@Override
	public @NotNull String getVersion() {
		return gangland.getDescription().getVersion();
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
		return placeholder.onRequest(player, params);
	}

}
