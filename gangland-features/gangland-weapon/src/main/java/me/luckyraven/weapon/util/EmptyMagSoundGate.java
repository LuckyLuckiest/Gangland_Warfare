package me.luckyraven.weapon.util;

import me.luckyraven.util.configuration.SoundConfiguration;
import me.luckyraven.util.timer.RepeatingTimer;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.dto.SoundData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Throttles the empty-magazine click sound so it plays exactly once per press cycle, regardless of fire mode.
 * <p>
 * Mirrors the watchdog pattern used by {@code WeaponInteract#engagePressHoldWatchdog}:
 * <ul>
 *   <li>{@link #play} adds the weapon UUID to the gate, plays the sound, and starts an internal
 *       release-detection watchdog. Subsequent calls while the gate is held are no-ops.</li>
 *   <li>{@link #refresh} must be called from every point in {@code WeaponInteract} that refreshes a "still
 *       pressing" flag — {@code isPressGated}, {@code handleIncendiaryAuto}, and the {@code shootFullAuto}
 *       hold-refresh branch. When the watchdog observes one full cycle without a refresh, it treats RMB as
 *       released and re-arms the gate automatically.</li>
 * </ul>
 */
public final class EmptyMagSoundGate {

	private static final Set<UUID>                GATE        = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, AtomicBoolean> PRESS_FLAGS = new ConcurrentHashMap<>();
	/**
	 * Check interval — matches the granularity used by {@code engagePressHoldWatchdog}.
	 */
	private static final long                     WATCH_TICKS = 2L;

	private EmptyMagSoundGate() {
	}

	public static void play(JavaPlugin plugin, Player player, Weapon weapon) {
		UUID uuid = weapon.getUuid();
		if (!GATE.add(uuid)) {
			// Gate already active (e.g. AUTO firing empty mag every N ticks) — refresh the watchdog
			// flag so it doesn't release the gate between fire ticks.
			refresh(uuid);
			return;
		}

		SoundData soundData = weapon.getSoundData();
		SoundConfiguration.playSounds(player, soundData.getEmptyMagCustom(), soundData.getEmptyMagDefault());

		AtomicBoolean pressing = new AtomicBoolean(true);
		PRESS_FLAGS.put(uuid, pressing);

		// Release-detection watchdog — pure flag checks and map mutations, safe to run async.
		new RepeatingTimer(plugin, WATCH_TICKS, time -> {
			AtomicBoolean flag = PRESS_FLAGS.get(uuid);
			if (flag == null) {
				time.stop();
				return;
			}
			if (!flag.get()) {
				// No refresh arrived this cycle — RMB has been released. Re-arm the gate.
				time.stop();
				PRESS_FLAGS.remove(uuid);
				GATE.remove(uuid);
				return;
			}
			flag.set(false);
		}).start(true);
	}

	/**
	 * Signals that RMB is still held for the given weapon. Call this wherever {@code WeaponInteract} refreshes a "still
	 * shooting" flag. If no gate is active for the UUID this is a no-op.
	 */
	public static void refresh(UUID weaponUuid) {
		AtomicBoolean flag = PRESS_FLAGS.get(weaponUuid);
		if (flag != null) {
			flag.set(true);
		}
	}

}
