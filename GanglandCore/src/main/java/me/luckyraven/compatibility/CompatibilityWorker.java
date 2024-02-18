package me.luckyraven.compatibility;

import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.projectile.recoil.RecoilCompatibility;

public class CompatibilityWorker implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public CompatibilityWorker(Gangland gangland) {
		Compatibility compatibility = gangland.getInitializer()
											  .getCompatibilitySetup()
											  .getCompatibleVersion(Compatibility.class,
																	"me.luckyraven.compatibility.version");

		if (compatibility == null) {
			this.recoilCompatibility = null;
			return;
		}

		RecoilCompatibility recoilCompatibility = compatibility.getRecoilCompatibility();

		if (recoilCompatibility == null) {
			gangland.getLogger().info("You are running an unsupported version... Will try to use the default way.");
			recoilCompatibility = new RecoilCompatibility();
			recoilCompatibility.setGangland(gangland);
		}

		this.recoilCompatibility = recoilCompatibility;
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}
}
