package me.luckyraven.compatibility;

import me.luckyraven.feature.weapon.projectile.recoil.RecoilCompatibility;
import org.bukkit.plugin.java.JavaPlugin;

public class CompatibilityWorker implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public CompatibilityWorker(JavaPlugin plugin) {
		Compatibility compatibility = CompatibilitySetup.getCompatibleVersion(Compatibility.class,
																			  "me.luckyraven.compatibility.version");

		if (compatibility == null) {
			this.recoilCompatibility = null;
			return;
		}

		RecoilCompatibility recoilCompatibility = compatibility.getRecoilCompatibility();

		if (recoilCompatibility == null) {
			plugin.getLogger().info("You are running an unsupported version... Will try to use the default way.");
			recoilCompatibility = new RecoilCompatibility();
		}

		this.recoilCompatibility = recoilCompatibility;
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}
}
