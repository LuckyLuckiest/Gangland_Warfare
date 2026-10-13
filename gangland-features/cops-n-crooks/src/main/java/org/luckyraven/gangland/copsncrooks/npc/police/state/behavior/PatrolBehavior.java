package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.patrol.PatrolServices;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;

/** The {@code PATROL} state (0.20.0 lane patrol): walks a beat around the anchor, reports wanted players to their squad and starts a warning for a brandished weapon. Never attacks. Without services (the bare factory path, tests) it does nothing. */
public class PatrolBehavior implements CopBehavior {

	private final @Nullable PatrolServices services;

	public PatrolBehavior() {
		this(null);
	}

	public PatrolBehavior(@Nullable PatrolServices services) {
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
