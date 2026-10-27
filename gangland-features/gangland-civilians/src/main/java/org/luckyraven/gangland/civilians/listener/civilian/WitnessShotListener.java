package org.luckyraven.gangland.civilians.listener.civilian;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.bartizan.api.event.WeaponShootEvent;
import org.luckyraven.bartizan.api.weapon.ThrowableWeapon;
import org.luckyraven.bartizan.api.weapon.Weapon;
import org.luckyraven.bartizan.api.weapon.dto.ThrowableData;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * A player's gunshot is noise: his crimes in the next {@code Witness.Loud_Shot_Seconds} are loud, so civilians in range
 * turn to face them before the view cone applies (0.21.1). Only the categories in {@code Witness.Loud_Weapon_Types}
 * (GUN, THROWABLE) count: a melee swing is silent, as it is to the cops' {@code Shot_Noise}. A throwable is heard when
 * it blows up: the window is stretched by its {@code Fuse_Time}, so a grenade kill is loud however the fuse is set.
 *
 * <p>{@code condition = "isBartizanAvailable"}: the handler's parameter is Bartizan's {@link WeaponShootEvent}, so
 * scanning this class on a Bartizan-less server would throw {@code NoClassDefFoundError}.
 *
 * @since 0.21.1
 */
@ListenerHandler(condition = "isBartizanAvailable")
public class WitnessShotListener implements Listener {

	private final WitnessService witnesses;

	public WitnessShotListener(WitnessService witnesses) {
		this.witnesses = witnesses;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWeaponShoot(WeaponShootEvent event) {
		if (!(event.getShooter() instanceof Player shooter) || NpcSupport.isNpc(shooter)) return;

		Weapon weapon = event.getWeapon();
		witnesses.heardShot(shooter, weapon.getCategory().name(), fuseMillis(weapon));
	}

	/** Milliseconds from the throw to the blast (Fuse_Time ticks); 0 for anything that is not a throwable. */
	private static long fuseMillis(Weapon weapon) {
		ThrowableData data = weapon instanceof ThrowableWeapon throwable ? throwable.getThrowableData() : null;
		return data == null ? 0L : Math.max(0, data.getFuseTime()) * 50L;
	}
}
