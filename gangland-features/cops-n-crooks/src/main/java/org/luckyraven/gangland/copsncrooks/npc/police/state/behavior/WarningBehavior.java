package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;

/** The {@code WARNING} state (0.20.0 lane patrol): faces the warned player and holds until the warning is decided. Never attacks. Without services (the bare factory path, tests) it does nothing. */
public class WarningBehavior implements CopBehavior {

	private final @Nullable PatrolServices services;

	public WarningBehavior() {
		this(null);
	}

	public WarningBehavior(@Nullable PatrolServices services) {
		this.services = services;
	}

	/** The services this behaviour runs on; {@code null} when unwired. */
	public @Nullable PatrolServices services() {
		return null;
	}

	@Override
	public void tick(CopNpc cop) {
	}

	@Override
	public void onEnter(CopNpc cop) {
	}

	@Override
	public void onExit(CopNpc cop) {
	}
}
