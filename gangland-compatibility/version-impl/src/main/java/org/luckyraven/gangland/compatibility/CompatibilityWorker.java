package org.luckyraven.gangland.compatibility;

import com.viaversion.viaversion.api.ViaAPI;
import lombok.CustomLog;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;

@CustomLog
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

		} catch (Throwable exception) {
			log.warn("There was a problem loading Compatibility class... {}", exception.getMessage(), exception);
		}

		this.recoilCompatibility = recoilCompatibility;
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

}
