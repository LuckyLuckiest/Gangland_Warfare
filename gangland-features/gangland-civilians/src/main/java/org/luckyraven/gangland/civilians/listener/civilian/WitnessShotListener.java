package org.luckyraven.gangland.civilians.listener.civilian;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.bartizan.api.event.WeaponShootEvent;
import org.luckyraven.gangland.civilians.witness.WitnessService;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * A player's gunshot is noise: his crimes in the next {@code Witness.Loud_Shot_Seconds} are loud, so civilians in range
 * turn to face them before the view cone applies (0.21.1).
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
		if (event.getShooter() instanceof Player shooter && !NpcSupport.isNpc(shooter)) witnesses.heardShot(shooter);
	}
}
