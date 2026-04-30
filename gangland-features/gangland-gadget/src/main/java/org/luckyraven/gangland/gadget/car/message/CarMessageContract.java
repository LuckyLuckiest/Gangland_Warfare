package org.luckyraven.gangland.gadget.car.message;

/**
 * Car-scoped message contract. Covers the chat and action-bar strings emitted by the car placement + mount + refuel
 * listeners. Integrations (gangland-impl) implement this interface by routing each call to their preferred
 * {@code Messages} enum entry so the gadget module stays decoupled from the Messages enum.
 */
public interface CarMessageContract {

	String noPermission();

	String alreadyDriving();

	String fuelCanEmpty();

	String fuelTankFull();

	String refuelFailed();

}
