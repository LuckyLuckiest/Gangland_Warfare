package me.luckyraven.compatibility;

import com.viaversion.viaversion.api.ViaAPI;
import lombok.extern.log4j.Log4j2;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;

@Log4j2
public class CompatibilityWorker implements Compatibility {

	private final RecoilCompatibility recoilCompatibility;

	public CompatibilityWorker(ViaAPI<?> viaAPI, CompatibilitySetup compatibilitySetup) {
		RecoilCompatibility recoilCompatibility = null;

		try {
			Compatibility compatibility = compatibilitySetup.getCompatibleVersion(Compatibility.class,
																				  VersionSetup.getCompatibilityFolder());

			if (compatibility != null) {
				recoilCompatibility = compatibility.getRecoilCompatibility();
			}

			if (recoilCompatibility == null) {
				log.info("Using default recoil (limited functionality).");

				recoilCompatibility = new RecoilCompatibility();
				recoilCompatibility.setViaAPI(viaAPI);
			}

		} catch (Exception exception) {
			log.warn("There was a problem loading Compatibility class... {}", exception.getMessage(), exception);
		}

		this.recoilCompatibility = recoilCompatibility;
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

}
