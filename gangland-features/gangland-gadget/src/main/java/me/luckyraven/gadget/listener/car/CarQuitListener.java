package me.luckyraven.gadget.listener.car;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.autowire.AutowireTarget;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.gadget.car.CarService;
import me.luckyraven.gadget.car.vehicle.VehicleSession;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles a player quitting while mounted in an active car. Synchronously ejects the passenger and parks the vehicle
 * before Spigot's {@code PlayerList.remove} reaches {@code ServerPlayer.save()} — otherwise Minecraft would serialize
 * the minecart into the player's {@code RootVehicle} NBT tag and spawn a rogue duplicate on rejoin.
 * <p>
 * The tick-level fallback in {@code VehicleMovementTask.checkGuards} still catches offline drivers, but only after the
 * save has run; this listener shortcuts that race.
 */
@ListenerHandler
@RequiredArgsConstructor
@AutowireTarget({CarService.class})
public class CarQuitListener implements Listener {

	private final CarService carService;

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player         player  = event.getPlayer();
		VehicleSession session = carService.getVehicleRegistry().getByPlayer(player.getUniqueId());
		if (session == null) return;

		Entity live = session.getEntity().getBukkitEntity();
		if (live != null && !live.isDead()) {
			live.eject();
		}

		carService.parkCar(session.getEntity().getEntityUUID());
	}
}
