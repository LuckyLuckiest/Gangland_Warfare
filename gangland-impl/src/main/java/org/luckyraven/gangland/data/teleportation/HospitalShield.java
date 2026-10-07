package org.luckyraven.gangland.data.teleportation;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The few seconds of damage immunity a hospital respawn grants ({@code User.Death.Hospital.Shield_Seconds}, 0 = off).
 * Memory only: a restart or a quit ends every shield. Expiry is read on demand, there is no timer. The damage rules
 * live in {@code HospitalShieldListener}.
 */
@CustomLog
public final class HospitalShield {

	private final Map<UUID, Long> until = new ConcurrentHashMap<>();
	private final LongSupplier    clock;

	public HospitalShield() {
		this(System::currentTimeMillis);
	}

	// package-private: test seam
	HospitalShield(LongSupplier clock) {
		this.clock = clock;
	}

	/** Shields {@code player} for the configured seconds (read now, so a reload applies); 0 or less grants nothing. */
	public void grant(Player player) {
		int seconds = Settings.getHospitalShieldSeconds();
		if (seconds <= 0) return;

		until.put(player.getUniqueId(), clock.getAsLong() + seconds * 1000L);
		player.sendMessage(Messages.DEATH_HOSPITAL_SHIELD.toString().replace("%seconds%", String.valueOf(seconds)));
		log.debug("SHIELD {} seconds={}", player.getName(), seconds);
	}

	public boolean isShielded(UUID id) {
		Long end = until.get(id);
		if (end == null) return false;

		if (clock.getAsLong() < end) return true;

		until.remove(id);
		return false;
	}

	public void end(UUID id) {
		until.remove(id);
	}

}
