package org.luckyraven.gangland.gadget.car.vehicle.entity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Minecart;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.car.Car;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins gi=49 (P0): {@code World.spawn(Location, Class, org.bukkit.util.Consumer)} was removed on Paper 1.20.2+
 * (replaced by a {@code java.util.function.Consumer} overload with a different descriptor), so
 * {@link MinecartVehicle#spawn}'s 3-arg Consumer-lambda call threw {@code NoSuchMethodError} there. The 2-arg
 * {@code World.spawn(Location, Class)} overload is stable across 1.16.5-1.21.x (javap-verified) and must be used
 * instead, with the minecart configured directly on the returned instance.
 */
class MinecartVehicleSpawnTest {

	@Test
	void spawn_usesTwoArgWorldSpawn_notConsumerOverload() {
		World    world    = mock(World.class);
		Location location = mock(Location.class);
		when(location.getWorld()).thenReturn(world);
		when(location.clone()).thenReturn(location);

		Minecart minecart = mock(Minecart.class);
		when(world.spawn(location, Minecart.class)).thenReturn(minecart);

		Car              car     = Car.builder().carId("test_car").build();
		MinecartVehicle  vehicle = new MinecartVehicle(car);

		vehicle.spawn(location);

		verify(world).spawn(location, Minecart.class);
		verify(world, never()).spawn(any(Location.class), eq(Minecart.class), any(org.bukkit.util.Consumer.class));
		verify(minecart).setMaxSpeed(10.0);
		verify(minecart).setSlowWhenEmpty(false);
	}
}
