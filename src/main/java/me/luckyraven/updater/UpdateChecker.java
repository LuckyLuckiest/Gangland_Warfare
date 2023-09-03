package me.luckyraven.updater;

import me.luckyraven.Gangland;
import org.bukkit.Bukkit;

public class UpdateChecker {

	private final Gangland gangland;

	public UpdateChecker(Gangland gangland) {
		this.gangland = gangland;
	}

	public void getLatestVersion() {
		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {

		});
	}

	public void downloadLatestVersion() {
		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {

		});
	}

}
