package org.luckyraven.gangland.civilians.npc.state.behavior;

import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.state.CivilianBehavior;

/**
 * CALLING: the civilian stands still, faces away from the criminal and (if the call says so) glows while the 911 call
 * runs. The call itself is timed and landed by {@code WitnessService.tickCaller}.
 *
 * @since 0.20.0
 */
public class CivilianCallingBehavior implements CivilianBehavior {

	@Override
	public void onEnter(CivilianNpc npc) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void tick(CivilianNpc npc) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public void onExit(CivilianNpc npc) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
