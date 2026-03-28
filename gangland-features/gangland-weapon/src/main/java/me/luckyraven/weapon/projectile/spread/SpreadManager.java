package me.luckyraven.weapon.projectile.spread;

import lombok.Getter;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.SpreadData;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * Manages weapon spread mechanics including spread accumulation, reset, and bounds.
 */
public class SpreadManager {

	private final Random     random;
	private final SpreadData spreadData;

	@Getter
	private double currentSpread;
	private long   lastShotTime;

	public SpreadManager(Weapon weapon) {
		this.random        = new Random();
		this.spreadData    = weapon.getSpreadData();
		this.currentSpread = spreadData.getStart();
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
		this.currentSpread = spreadData.getStart();
		this.lastShotTime  = System.currentTimeMillis();
	}

	/**
	 * Checks if spread should reset based on the spreadResetTime.
	 */
	private void checkSpreadReset() {
		long currentTime       = System.currentTimeMillis();
		long timeSinceLastShot = currentTime - lastShotTime;

		// If enough time has passed, reset spread to starting value
		if (timeSinceLastShot >= spreadData.getResetTime()) {
			currentSpread = spreadData.getStart();
		}

		lastShotTime = currentTime;
	}

	/**
	 * Updates the spread value after a shot is fired.
	 */
	private void updateSpread() {
		double newSpread = currentSpread + spreadData.getChangeBase();

		// Check if spread exceeds bounds
		if (newSpread >= spreadData.getBoundMaximum()) {
			currentSpread = spreadData.isResetOnBound() ? spreadData.getStart() : spreadData.getBoundMaximum();
		} else if (newSpread <= spreadData.getBoundMinimum()) {
			currentSpread = spreadData.isResetOnBound() ? spreadData.getStart() : spreadData.getBoundMinimum();
		} else {
			currentSpread = newSpread;
		}
	}
}
