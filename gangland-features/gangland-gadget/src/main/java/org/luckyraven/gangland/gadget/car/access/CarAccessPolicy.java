package org.luckyraven.gangland.gadget.car.access;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Decides who may mount, refuel or pick up a placed car.
 *
 * <p>The placer's UUID was already written to the entity's {@code car_placer} PDC key and to the
 * {@code parked_car.placer_uuid} column — nothing ever read it back. {@code gangland.cars.<id>} was only checked
 * when the car item was placed, so once a car stood in the world any player could drive it away, refuel it or
 * shift-left-click it into their own inventory.
 *
 * <p>The rule:
 * <ul>
 *   <li>a car with no recorded placer stays open to everyone — cars placed before this fix have no owner and must
 *       not become unusable;</li>
 *   <li>the placer always has access;</li>
 *   <li>gang mates of the placer have access, so a shared gang car keeps working;</li>
 *   <li>{@link #BYPASS_PERMISSION} is the staff override.</li>
 * </ul>
 *
 * <p>Observation #6 (gadgets-cars-fuel-jetpack.md), docket GD-06.
 */
public final class CarAccessPolicy {

	/** Staff override: use any placed car regardless of who placed it. */
	public static final String BYPASS_PERMISSION = "gangland.cars.bypass";

	private final CarGangContract gangs;

	public CarAccessPolicy(CarGangContract gangs) {
		this.gangs = gangs;
	}

	/**
	 * @param player the player reaching for the car
	 * @param placer the UUID recorded when the car was placed; {@code null} for a car placed before GD-06
	 *
	 * @return {@code true} when the player may mount, refuel or pick the car up
	 */
	public boolean canUse(@Nullable Player player, @Nullable UUID placer) {
		if (player == null) return false;
		if (placer == null) return true;

		UUID playerUuid = player.getUniqueId();

		if (placer.equals(playerUuid)) return true;
		if (player.hasPermission(BYPASS_PERMISSION)) return true;

		return gangs != null && gangs.sharesGang(playerUuid, placer);
	}

	/**
	 * @return {@code true} when the player may not use the car but it has a recorded owner, i.e. a lockpick may try it
	 */
	public boolean canHotwire(@Nullable Player player, @Nullable UUID placer) {
		return false; // TDD stub
	}

	/**
	 * Mounting and driving: {@link #canUse} or a hotwire grant on this car. Refuel and pickup keep {@link #canUse}.
	 */
	public boolean canMount(@Nullable Player player, @Nullable UUID placer, UUID carEntityId) {
		return false; // TDD stub
	}

	/**
	 * Lets the thief mount and drive the car until restart or {@link #revoke}. Ownership never changes.
	 */
	public void grant(UUID carEntityId, UUID thief) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * Drops a hotwire grant (the car was picked up).
	 */
	public void revoke(UUID carEntityId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * @return the thief holding a hotwire grant on this car, or {@code null}
	 */
	@Nullable
	public UUID grantedTo(UUID carEntityId) {
		return null; // TDD stub
	}

}
