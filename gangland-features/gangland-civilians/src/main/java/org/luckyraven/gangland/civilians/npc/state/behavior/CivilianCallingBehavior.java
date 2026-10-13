package org.luckyraven.gangland.civilians.npc.state.behavior;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.witness.WitnessCall;
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
		npc.stopNavigation();
		WitnessCall  call   = npc.getWitnessCall();
		LivingEntity entity = npc.getEntity();
		if (call == null || entity == null) return;

		if (call.glow()) entity.setGlowing(true);

		Location here  = entity.getLocation();
		Location crook = call.criminal().getLocation();
		// Bukkit yaw: 0 = +Z, 270 = +X; the vector criminal -> caller points away from the criminal
		here.setYaw((float) Math.toDegrees(Math.atan2(-(here.getX() - crook.getX()), here.getZ() - crook.getZ())));
		entity.teleport(here);
	}

	@Override
	public void tick(CivilianNpc npc) {
		WitnessCall call = npc.getWitnessCall();
		if (call == null) {
			npc.transitionTo(CivilianState.IDLE);
			return;
		}
		call.owner().tickCaller(npc);
	}

	@Override
	public void onExit(CivilianNpc npc) {
		LivingEntity entity = npc.getEntity();
		if (entity != null) entity.setGlowing(false);

		WitnessCall call = npc.getWitnessCall();
		if (call != null) call.owner().dropCall(npc);
	}
}
