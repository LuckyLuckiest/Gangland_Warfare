package me.luckyraven.compatibility;

import me.luckyraven.Gangland;
import me.luckyraven.feature.weapon.projectile.recoil.RecoilCompatibility;

import java.util.logging.Level;

public class CompatibilityWorker implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public CompatibilityWorker(Gangland gangland) {
		RecoilCompatibility recoilCompatibility = null;

		try {
			Compatibility compatibility = gangland.getInitializer()
												  .getCompatibilitySetup()
												  .getCompatibleVersion(Compatibility.class,
																		VersionSetup.getCompatibilityFolder());

			if (compatibility != null) recoilCompatibility = compatibility.getRecoilCompatibility();

			if (recoilCompatibility == null) {
				gangland.getLogger().info("You are running an unsupported version... Will try to use the default way.");

				recoilCompatibility = new RecoilCompatibility();

				recoilCompatibility.setGangland(gangland);
			}

		} catch (Exception exception) {
			gangland.getLogger()
					.log(Level.WARNING, "There was a problem loading Compatibility class... " + exception.getMessage(),
						 exception);
		}

		this.recoilCompatibility = recoilCompatibility;
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

}
