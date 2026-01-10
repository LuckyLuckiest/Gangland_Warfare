package me.luckyraven.compatibility;

import com.viaversion.viaversion.api.ViaAPI;
import me.luckyraven.compatibility.pathfinding.DefaultPathfindingHandler;
import me.luckyraven.compatibility.pathfinding.PathfindingHandler;
import me.luckyraven.compatibility.recoil.RecoilCompatibility;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CompatibilityWorker implements Compatibility {

	private static final Logger logger = LogManager.getLogger(CompatibilityWorker.class.getSimpleName());

	private final RecoilCompatibility recoilCompatibility;
	private final PathfindingHandler  pathfindingHandler;

	public CompatibilityWorker(ViaAPI<?> viaAPI, CompatibilitySetup compatibilitySetup) {
		RecoilCompatibility recoilCompatibility = null;
		PathfindingHandler  pathfindingHandler  = null;

		try {
			Compatibility compatibility = compatibilitySetup.getCompatibleVersion(Compatibility.class,
																				  VersionSetup.getCompatibilityFolder());

			if (compatibility != null) {
				recoilCompatibility = compatibility.getRecoilCompatibility();
				pathfindingHandler  = compatibility.getPathfindingHandler();
			}

			if (recoilCompatibility == null) {
				logger.info("Using default recoil (limited functionality).");

				recoilCompatibility = new RecoilCompatibility();
				recoilCompatibility.setViaAPI(viaAPI);
			}

			if (pathfindingHandler == null) {
				logger.info("Using default pathfinding handler (limited functionality).");

				pathfindingHandler = new DefaultPathfindingHandler();
			}

		} catch (Exception exception) {
			logger.warn("There was a problem loading Compatibility class... {}", exception.getMessage(), exception);
		}

		this.recoilCompatibility = recoilCompatibility;
		this.pathfindingHandler  = pathfindingHandler != null ? pathfindingHandler : new DefaultPathfindingHandler();
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

	@Override
	public PathfindingHandler getPathfindingHandler() {
		return pathfindingHandler;
	}

}
