package me.luckyraven.compatibility;

import com.viaversion.viaversion.api.ViaAPI;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CompatibilityWorker implements Compatibility {

	private static final Logger logger = LogManager.getLogger(CompatibilityWorker.class.getSimpleName());

	private final RecoilCompatibility recoilCompatibility;

	public CompatibilityWorker(ViaAPI<?> viaAPI, CompatibilitySetup compatibilitySetup) {
		RecoilCompatibility recoilCompatibility = null;

		try {
			Compatibility compatibility = compatibilitySetup.getCompatibleVersion(Compatibility.class,
																				  VersionSetup.getCompatibilityFolder());

			if (compatibility != null) recoilCompatibility = compatibility.getRecoilCompatibility();

			if (recoilCompatibility == null) {
				logger.info("You are running an unsupported version... Will try to use the default way.");

				recoilCompatibility = new RecoilCompatibility();

				recoilCompatibility.setViaAPI(viaAPI);
			}

		} catch (Exception exception) {
			logger.warn("There was a problem loading Compatibility class... " + exception.getMessage(), exception);
		}

		this.recoilCompatibility = recoilCompatibility;
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

}
