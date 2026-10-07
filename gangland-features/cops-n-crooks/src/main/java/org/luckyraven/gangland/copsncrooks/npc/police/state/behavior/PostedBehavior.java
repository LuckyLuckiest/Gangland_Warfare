package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;

/**
 * A cop holding a perimeter post (0.16.0, {@code PerimeterController}): each AI tick it only keeps its post. No leash
 * give-up and no cuffing; the controller ends the post, and a suspect who attacks forces the cop into COMBAT
 * ({@code CopManager.fightResisting}), which leaves this state and so releases the post.
 */
public class PostedBehavior implements CopBehavior {

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
