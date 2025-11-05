package me.luckyraven.data.placeholder;

import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PlaceholderRequest {

	@Nullable
	public String onRequest(OfflinePlayer player, @NotNull String parameter) {
		return null;
	}

}
