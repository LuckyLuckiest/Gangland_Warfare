package org.luckyraven.gangland.copsncrooks.npc.police;

import lombok.Getter;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.WantedTargetingManager;

/**
 * Thin facade that groups the cop subsystem's top-level beans ({@link CopManager} and {@link WantedTargetingManager})
 * for consumers that need both. Individual components are also available as beans directly.
 */
@Getter
public class CopService {

	private final CopManager             copManager;
	private final WantedTargetingManager targetingManager;

	public CopService(CopManager copManager, WantedTargetingManager targetingManager) {
		this.copManager       = copManager;
		this.targetingManager = targetingManager;
	}
}
