package org.luckyraven.gangland.data.detainment;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.gang.wanted.Wanted;

import java.util.UUID;

public final class GanglandWantedClearContract implements WantedClearContract {

	private final UserManager<Player> userManager;

	public GanglandWantedClearContract(UserManager<Player> userManager) {
		this.userManager = userManager;
	}

	@Override
	public int getWantedLevel(UUID playerId) {
		User<Player> user = lookup(playerId);
		if (user == null) return 0;
		Wanted wanted = user.getWanted();
		return wanted == null ? 0 : wanted.getLevel();
	}

	@Override
	public void clearWanted(UUID playerId) {
		User<Player> user = lookup(playerId);
		if (user == null) return;
		Wanted wanted = user.getWanted();
		if (wanted == null) return;
		wanted.setLevel(0);
	}

	private User<Player> lookup(UUID playerId) {
		if (playerId == null) return null;
		Player player = Bukkit.getPlayer(playerId);
		if (player == null) return null;
		return userManager.getUser(player);
	}
}
