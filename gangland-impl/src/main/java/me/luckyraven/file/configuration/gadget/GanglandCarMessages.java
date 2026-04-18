package me.luckyraven.file.configuration.gadget;

import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gadget.car.message.CarMessageContract;

/**
 * Default {@link CarMessageContract} implementation. Routes every call through a {@link Messages} enum key so the
 * gadget car listeners stay decoupled from the Messages enum.
 */
public final class GanglandCarMessages implements CarMessageContract {

	@Override
	public String noPermission() {
		return Messages.CAR_NO_PERMISSION.toString();
	}

	@Override
	public String alreadyDriving() {
		return Messages.CAR_ALREADY_DRIVING.toString();
	}

	@Override
	public String fuelCanEmpty() {
		return Messages.CAR_FUEL_CAN_EMPTY.toString();
	}

	@Override
	public String fuelTankFull() {
		return Messages.CAR_FUEL_TANK_FULL.toString();
	}

	@Override
	public String refuelFailed() {
		return Messages.CAR_REFUEL_FAILED.toString();
	}

}
