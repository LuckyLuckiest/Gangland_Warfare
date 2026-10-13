package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SirenSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sirens you can hear: every {@code Cops.Siren.Interval_Seconds}, the nearest PURSUING cop of a wanted player's group
 * that is farther than {@code Min_Distance} plays the siren at his own position. Runs as a CopManager AI-tick hook.
 * @since 0.20.0
 */
public class SirenService {

	/** Plays the siren at a cop's position; production routes through Keystone's SoundEffect. */
	@FunctionalInterface
	public interface SirenSound {

		void play(Location at, SirenSettings settings);
	}

	private final Supplier<SirenSettings> settings;
	private final SirenSound              sound;
	private final java.util.function.LongSupplier clock;
	/** Per player: when the siren is next due. */
	private final Map<UUID, Long>         nextDue = new HashMap<>();

	public SirenService(Supplier<SirenSettings> settings) {
		this(settings, SirenService::keystoneSound, System::currentTimeMillis);
	}

	public SirenService(Supplier<SirenSettings> settings, SirenSound sound, java.util.function.LongSupplier clock) {
		this.settings = settings;
		this.sound    = sound;
		this.clock    = clock;
	}

	/** The AI-tick hook: {@code group} is null when the player has no chase. */
	public void tick(Player player, @Nullable CopGroup group) {
		throw new UnsupportedOperationException("TDD stub");
	}

	private static void keystoneSound(Location at, SirenSettings settings) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
