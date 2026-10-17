package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;

/**
 * A Marksman perched in the {@code OVERWATCH} state (0.20.0, {@code OverwatchController}): each AI tick he only keeps
 * his perch and watches. He never chases, never attacks and never cuffs; the controller ends the post when the stars
 * drop, the time runs out or the squad is gone, and {@code onExit} releases it.
 */
public class OverwatchBehavior implements CopBehavior {

	@Override
	public void tick(CopNpc cop) {
		cop.tickPost();
	}

	@Override
	public void onEnter(CopNpc cop) {
	}

	@Override
	public void onExit(CopNpc cop) {
		cop.releasePost();
	}
}
