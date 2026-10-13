package org.luckyraven.gangland.gadget.car.theft;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.gadget.car.access.CarAccessPolicy;
import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;

import java.util.UUID;
import java.util.function.Function;

/**
 * Lockpick hotwire of a car the player may not use (P-CRIME-1). A single repeating task ticks every active hotwire
 * each 10 ticks on the main thread; success grants the thief the car for the session, marks it stolen and commits
 * {@code Car_Theft}. Ownership never transfers (docket T-73).
 */
public class HotwireService {

	public HotwireService(JavaPlugin plugin, CarAccessPolicy policy, CrimeService crimes, GadgetPhysicsConfig config,
	                      HotwireMessages messages, Function<ItemStack, String> itemKeyReader) {
	}

	/**
	 * @return {@code Hotwire.Enable}
	 */
	public boolean isEnabled() {
		return false; // TDD stub
	}

	/**
	 * @return true when the stack's unique item key equals {@code Hotwire.Lockpick_Item}
	 */
	public boolean isLockpick(@Nullable ItemStack stack) {
		return false; // TDD stub
	}

	/**
	 * Fires {@code CarTheftAttemptEvent}; when not cancelled starts the timer.
	 *
	 * @return true when a hotwire is now running
	 */
	public boolean start(Player thief, Entity car, @Nullable UUID owner, String carType) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/**
	 * One 10-tick step over every active hotwire.
	 */
	public void tick() {
		throw new UnsupportedOperationException("TDD stub");
	}

	public boolean isHotwiring(UUID thief) {
		return false; // TDD stub
	}

}
