package me.luckyraven.feature.weapon.projectile.spread;

import lombok.Getter;
import me.luckyraven.feature.weapon.Weapon;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Manages weapon spread mechanics including spread accumulation, reset, and bounds.
 */
public class SpreadManager {

	private final Weapon weapon;
	private final Random random;

	@Getter
	private double currentSpread;
	private long   lastShotTime;

	public SpreadManager(Weapon weapon) {
		this.weapon        = weapon;
		this.random        = new Random();
		this.currentSpread = weapon.getSpreadStart();
		this.lastShotTime  = System.currentTimeMillis();
	}

	/**
	 * Applies spread to the given direction vector.
	 *
	 * @param originalVector The original direction vector
	 *
	 * @return The vector with spread applied
	 */
	public Vector applySpread(Vector originalVector) {
		// Check if spread should reset based on time
		checkSpreadReset();

		// Apply the current spread factor
		double offsetX = (random.nextDouble() - 0.5) * currentSpread;
		double offsetY = (random.nextDouble() - 0.5) * currentSpread;
		double offsetZ = (random.nextDouble() - 0.5) * currentSpread;

		// Update spread for next shot
		updateSpread();

		return originalVector.add(new Vector(offsetX, offsetY, offsetZ)).normalize();
	}

	/**
	 * Manually resets the spread to its starting value.
	 */
	public void resetSpread() {
		this.currentSpread = weapon.getSpreadStart();
		this.lastShotTime  = System.currentTimeMillis();
	}

	/**
	 * Checks if spread should reset based on the spreadResetTime.
	 */
	private void checkSpreadReset() {
		long currentTime       = System.currentTimeMillis();
		long timeSinceLastShot = currentTime - lastShotTime;

		// If enough time has passed, reset spread to starting value
		if (timeSinceLastShot >= weapon.getSpreadResetTime()) {
			currentSpread = weapon.getSpreadStart();
		}

		lastShotTime = currentTime;
	}

	/**
	 * Updates the spread value after a shot is fired.
	 */
	private void updateSpread() {
		double newSpread = currentSpread + weapon.getSpreadChangeBase();

		// Check if spread exceeds bounds
		if (newSpread >= weapon.getSpreadBoundMaximum()) {
			currentSpread = weapon.isSpreadResetOnBound() ? weapon.getSpreadStart() : weapon.getSpreadBoundMaximum();
		} else if (newSpread <= weapon.getSpreadBoundMinimum()) {
			currentSpread = weapon.isSpreadResetOnBound() ? weapon.getSpreadStart() : weapon.getSpreadBoundMinimum();
		} else {
			currentSpread = newSpread;
		}
	}
}
