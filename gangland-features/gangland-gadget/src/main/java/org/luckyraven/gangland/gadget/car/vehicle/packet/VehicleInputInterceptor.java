package org.luckyraven.gangland.gadget.car.vehicle.packet;

import org.luckyraven.gangland.gadget.car.vehicle.VehicleMovementTask;
import org.luckyraven.gangland.gadget.car.vehicle.VehicleSession;
import org.luckyraven.keystone.nms.input.PlayerInputInterceptor;

/**
 * Writes the client's WASD state from every {@code ServerboundPlayerInputPacket} into the active
 * {@link VehicleSession}. Injected into the player's Netty pipeline at {@code "packet_handler"} on car spawn and
 * removed on despawn.
 *
 * <p>Runs on the Netty IO thread; the session's input fields are {@code volatile} so {@link VehicleMovementTask} can
 * read them from the main thread. All NMS reflection lives in {@link PlayerInputInterceptor}.
 */
public final class VehicleInputInterceptor extends PlayerInputInterceptor<VehicleSession> {

	public static final String HANDLER_NAME = "gangland_vehicle_input";

	public VehicleInputInterceptor(VehicleSession session) {
		super(session);
	}

	@Override
	protected void apply(VehicleSession session, PlayerInput input) {
		session.setInput(input.forward(), input.backward(), input.left(), input.right());
	}
}
