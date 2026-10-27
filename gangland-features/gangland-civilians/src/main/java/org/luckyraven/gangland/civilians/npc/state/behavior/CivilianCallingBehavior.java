package org.luckyraven.gangland.civilians.npc.state.behavior;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.state.CivilianBehavior;
import org.luckyraven.gangland.civilians.witness.WitnessCall;
import org.luckyraven.gangland.civilians.witness.WitnessRules;

import java.util.Locale;

/**
 * CALLING: the civilian stands still and (if the call says so) glows while the 911 call runs. 0.21.1 makes it visible:
 * on entering it looks at the criminal with a {@code Notice_Particle} above its head and takes out its
 * {@code Phone_Item}; after {@code Notice_Seconds} it turns away to dial; leaving CALLING (landed, stopped or dropped)
 * gives its own main hand back. The call itself is timed and landed by {@code WitnessService.tickCaller}. One instance
 * per NPC ({@code CivilianBehaviorFactory}), so the saved hand is this civilian's own.
 *
 * @since 0.20.0
 */
public class CivilianCallingBehavior implements CivilianBehavior {

	private @Nullable ItemStack savedHand;
	private boolean             holdingPhone;
	private boolean             turnedAway;

	@Override
	public void onEnter(CivilianNpc npc) {
		npc.stopNavigation();
		turnedAway = false;
		WitnessCall  call   = npc.getWitnessCall();
		LivingEntity entity = npc.getEntity();
		if (call == null || entity == null) return;

		if (call.glow()) entity.setGlowing(true);

		CivilianLookController.faceLocation(entity, call.criminal().getLocation().add(0, 1.6, 0));
		WitnessRules rules = call.rules();
		noticeParticle(entity, rules.noticeParticle());
		takeOutPhone(entity, rules.phoneItem());
	}

	@Override
	public void tick(CivilianNpc npc) {
		WitnessCall call = npc.getWitnessCall();
		if (call == null) {
			npc.transitionTo(CivilianState.IDLE);
			return;
		}

		if (!turnedAway && call.owner().now() - call.startedAtMillis() >= call.rules().noticeSeconds() * 1000L) {
			turnedAway = true;
			turnAway(npc.getEntity(), call);
		}
		call.owner().tickCaller(npc);
	}

	@Override
	public void onExit(CivilianNpc npc) {
		LivingEntity entity = npc.getEntity();
		if (entity != null) entity.setGlowing(false);
		putPhoneAway(entity);

		WitnessCall call = npc.getWitnessCall();
		if (call != null) call.owner().dropCall(npc);
	}

	/** Faces away from the criminal, hiding the phone while it dials. */
	private static void turnAway(@Nullable LivingEntity entity, WitnessCall call) {
		if (entity == null) return;

		Location here  = entity.getLocation();
		Location crook = call.criminal().getLocation();
		// Bukkit yaw: 0 = +Z, 270 = +X; the vector criminal -> caller points away from the criminal
		here.setYaw((float) Math.toDegrees(Math.atan2(-(here.getX() - crook.getX()), here.getZ() - crook.getZ())));
		entity.teleport(here);
	}

	private static void noticeParticle(LivingEntity entity, String name) {
		World world = entity.getWorld();
		if (name.isBlank() || world == null) return;

		Particle particle = XParticle.of(name.toUpperCase(Locale.ROOT)).map(XParticle::get).orElse(null);
		if (particle == null) return;

		Location at = entity.getLocation().add(0, 2.3, 0);
		if (particle.getDataType() == Particle.DustOptions.class) {
			world.spawnParticle(particle, at, 5, 0.2, 0.1, 0.2, 0, new Particle.DustOptions(Color.RED, 1.0F));
		} else if (particle.getDataType() == Void.class) {
			world.spawnParticle(particle, at, 5, 0.2, 0.1, 0.2, 0);
		} else if (!name.equals(WitnessRules.DEFAULT.noticeParticle())) {
			// ponytail: a block/item particle has no sensible data here, so the shipped default stands in for it
			noticeParticle(entity, WitnessRules.DEFAULT.noticeParticle());
		}
	}

	private void takeOutPhone(LivingEntity entity, String name) {
		EntityEquipment equipment = entity.getEquipment();
		if (name.isBlank() || equipment == null || holdingPhone) return;

		Material phone = XMaterial.matchXMaterial(name.toUpperCase(Locale.ROOT)).map(XMaterial::get).orElse(null);
		if (phone == null) return;
		savedHand    = equipment.getItemInMainHand();
		holdingPhone = true;
		equipment.setItemInMainHand(new ItemStack(phone));
	}

	private void putPhoneAway(@Nullable LivingEntity entity) {
		if (!holdingPhone) return;

		holdingPhone = false;
		EntityEquipment equipment = entity == null ? null : entity.getEquipment();
		if (equipment != null) equipment.setItemInMainHand(savedHand);
		savedHand = null;
	}
}
