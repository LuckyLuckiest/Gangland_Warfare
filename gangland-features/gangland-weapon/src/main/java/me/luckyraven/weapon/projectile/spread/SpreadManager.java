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

	private final Random random;
	private final Weapon weapon;

	@Getter
	private double currentSpread;
	private long   lastShotTime;

	public SpreadManager(Weapon weapon) {
		this.random = new Random();
		this.weapon = weapon;
		SpreadData data = weapon.getSpreadData();
		this.currentSpread = data != null ? data.getStart() : 0.0;
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
		SpreadData spreadData = weapon.getSpreadData();
		if (spreadData == null) return originalVector;

		checkSpreadReset(spreadData);

		double offsetX = (random.nextDouble() - 0.5) * currentSpread;
		double offsetY = (random.nextDouble() - 0.5) * currentSpread;
		double offsetZ = (random.nextDouble() - 0.5) * currentSpread;

		updateSpread(spreadData);

		return originalVector.add(new Vector(offsetX, offsetY, offsetZ)).normalize();
	}

	/**
	 * Manually resets the spread to its starting value.
	 */
	public void resetSpread() {
		SpreadData spreadData = weapon.getSpreadData();
		if (spreadData == null) return;

		this.currentSpread = spreadData.getStart();
		this.lastShotTime  = System.currentTimeMillis();
	}

	/**
	 * Checks if spread should reset based on the spreadResetTime.
	 */
	private void checkSpreadReset(SpreadData spreadData) {
		long currentTime       = System.currentTimeMillis();
		long timeSinceLastShot = currentTime - lastShotTime;

		if (timeSinceLastShot >= spreadData.getResetTime()) {
			currentSpread = spreadData.getStart();
		}

		lastShotTime = currentTime;
	}

	/**
	 * Updates the spread value after a shot is fired.
	 */
	private void updateSpread(SpreadData spreadData) {
		double newSpread = currentSpread + spreadData.getChangeBase();

		if (newSpread >= spreadData.getBoundMaximum()) {
			currentSpread = spreadData.isResetOnBound() ? spreadData.getStart() : spreadData.getBoundMaximum();
		} else if (newSpread <= spreadData.getBoundMinimum()) {
			currentSpread = spreadData.isResetOnBound() ? spreadData.getStart() : spreadData.getBoundMinimum();
		} else {
			currentSpread = newSpread;
		}
	}
}
