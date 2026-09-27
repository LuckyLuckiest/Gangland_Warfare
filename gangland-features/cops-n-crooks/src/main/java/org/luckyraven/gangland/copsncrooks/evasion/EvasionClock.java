package org.luckyraven.gangland.copsncrooks.evasion;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Per-player evasion state machine (0.12 F2). Pure: it reads {@link Location} coordinates and nothing else from Bukkit,
 * so it is unit-testable without a server.
 * <ul>
 *     <li><b>SEEN</b> — the squad's last sighting is younger than {@code Lost_Sight_Seconds}: the clock is reset and
 *     the player's position is remembered as the fallback zone center.</li>
 *     <li><b>SEARCHING</b> — the zone is a circle on the squad's last known position (the remembered position when
 *     the squad has none) with radius {@code Search_Radius[level - 1]}. The clock counts toward
 *     {@code Seconds_To_Drop[level - 1]}, at {@code Outside_Zone_Speed} while the player is outside the zone
 *     (horizontal distance; another world counts as outside).</li>
 * </ul>
 * The owner calls {@link #afterDrop()} once it has taken the star(s), which restarts the clock at the new level.
 */
public class EvasionClock {

	@Getter
	private EvasionState state;
	@Getter
	private double       elapsedSeconds;
	private Location     lastSeen;
	private Location     zoneCenter;
	private double       zoneRadius;

	public EvasionClock() {
		this.state = EvasionState.NONE;
	}

	/**
	 * Advances the clock by one step.
	 *
	 * @param millisSinceSighting time since the squad last saw the player ({@code Long.MAX_VALUE} if never)
	 * @param lastKnown           the squad's last known position of the player, if any
	 * @param playerLocation      where the player is now
	 * @param level               the current wanted level
	 * @param config              the evasion settings
	 * @param deltaSeconds        real seconds since the previous step
	 *
	 * @return {@code true} when the clock ran out and a star drop is due
	 */
	public boolean tick(long millisSinceSighting, @Nullable Location lastKnown, Location playerLocation, int level,
	                    EvasionConfig config, double deltaSeconds) {
		if (millisSinceSighting < config.lostSightSeconds() * 1000L) {
			state          = EvasionState.SEEN;
			elapsedSeconds = 0D;
			lastSeen       = playerLocation.clone();
			zoneCenter     = null;
			zoneRadius     = 0D;
			return false;
		}

		// A squad that never recorded a position falls back to the last place the player was seen, then to where the
		// search started, so the zone never follows the player around.
		if (lastKnown == null && lastSeen == null) lastSeen = playerLocation.clone();
		Location center = lastKnown != null ? lastKnown : lastSeen;

		state      = EvasionState.SEARCHING;
		zoneCenter = center.clone();
		zoneRadius = config.radiusFor(level);

		boolean outside = isOutside(zoneCenter, zoneRadius, playerLocation);
		elapsedSeconds += Math.max(0D, deltaSeconds) * (outside ? config.outsideZoneSpeed() : 1D);

		return elapsedSeconds >= config.secondsToDropFor(level);
	}

	/**
	 * Restarts the clock after a drop. The state stays SEARCHING, so the next step searches at the new level.
	 */
	public void afterDrop() {
		elapsedSeconds = 0D;
	}

	/**
	 * Forgets everything: evasion no longer drives this player.
	 */
	public void reset() {
		state          = EvasionState.NONE;
		elapsedSeconds = 0D;
		lastSeen       = null;
		zoneCenter     = null;
		zoneRadius     = 0D;
	}

	/**
	 * A read-only picture of the clock at the given level.
	 *
	 * @param level  the current wanted level
	 * @param config the evasion settings
	 *
	 * @return the snapshot, {@link EvasionSnapshot#none()} while the state is NONE
	 */
	public EvasionSnapshot snapshot(int level, EvasionConfig config) {
		if (state == EvasionState.NONE) return EvasionSnapshot.none();

		double total = config.secondsToDropFor(level);
		if (state != EvasionState.SEARCHING || zoneCenter == null) {
			return new EvasionSnapshot(state, level, total, total, null, 0D);
		}

		double remaining = Math.max(0D, total - elapsedSeconds);
		return new EvasionSnapshot(state, level, remaining, total, zoneCenter.clone(), zoneRadius);
	}

	/**
	 * Whether {@code player} stands outside the circle, measured horizontally. Another world is always outside.
	 */
	static boolean isOutside(Location center, double radius, Location player) {
		World centerWorld = center.getWorld();
		World playerWorld = player.getWorld();
		if (!Objects.equals(centerWorld, playerWorld)) return true;

		double dx = player.getX() - center.getX();
		double dz = player.getZ() - center.getZ();
		return dx * dx + dz * dz > radius * radius;
	}
}
