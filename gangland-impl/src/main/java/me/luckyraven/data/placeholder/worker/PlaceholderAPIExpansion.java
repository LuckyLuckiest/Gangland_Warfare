package me.luckyraven.data.placeholder.worker;

import lombok.RequiredArgsConstructor;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.luckyraven.Gangland;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor
public class PlaceholderAPIExpansion extends PlaceholderExpansion {

	private final Gangland gangland;
	private final String   prefix;

	@Override
	public @NotNull String getIdentifier() {
		return prefix;
	}

	@Override
	public @NotNull String getAuthor() {
		return gangland.getDescription().getAuthors().getFirst();
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
		return gangland.getInitializer().getPlaceholder().onRequest(player, params);
	}

}
