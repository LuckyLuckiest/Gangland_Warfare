package org.luckyraven.gangland.copsncrooks.npc.police;

import lombok.CustomLog;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SirenSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.keystone.sound.SoundEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Sirens you can hear: every {@code Cops.Siren.Interval_Seconds}, the nearest PURSUING cop of a wanted player's group
 * that is farther than {@code Min_Distance} plays the siren at his own position. Runs as a CopManager AI-tick hook.
 * @since 0.20.0
 */
@CustomLog
public class SirenService {

	/** Plays the siren at a cop's position; production routes through Keystone's SoundEffect. */
	@FunctionalInterface
	public interface SirenSound {

		void play(Location at, SirenSettings settings);
	}

	private final Supplier<SirenSettings> settings;
	private final SirenSound              sound;
	private final LongSupplier            clock;
	/** Per player: when the siren is next due. */
	private final Map<UUID, Long>         nextDue = new HashMap<>();

	public SirenService(Supplier<SirenSettings> settings) {
		this(settings, SirenService::keystoneSound, System::currentTimeMillis);
	}

	public SirenService(Supplier<SirenSettings> settings, SirenSound sound, LongSupplier clock) {
		this.settings = settings;
		this.sound    = sound;
		this.clock    = clock;
	}

	/** The AI-tick hook: {@code group} is null when the player has no chase. */
	public void tick(Player player, @Nullable CopGroup group) {
		UUID          id = player.getUniqueId();
		SirenSettings s  = settings.get();
		if (s == null || !s.enabled() || group == null) {
			nextDue.remove(id);
			return;
		}

		// nearest PURSUING cop with a live entity in the player's world
		Location pursuerAt = null;
		CopNpc   pursuer   = null;
		double   best      = Double.MAX_VALUE;
		for (CopNpc cop : group.getCops()) {
			if (cop.getCurrentState() != CopState.PURSUING) continue;
			Entity entity = cop.getEntity();
			if (entity == null) continue;
			Location at = entity.getLocation();
			if (at == null || at.getWorld() == null || !at.getWorld().equals(player.getWorld())) continue;
			double d = at.distance(player.getLocation());
			if (d < best) {
				best      = d;
				pursuerAt = at;
				pursuer   = cop;
			}
		}

		// no pursuer, or one close enough to be heard without a siren: silent, cadence restarts later
		if (pursuer == null || best <= s.minDistance()) {
			nextDue.remove(id);
			return;
		}

		long now = clock.getAsLong();
		Long due = nextDue.get(id);
		if (due == null) {
			nextDue.put(id, now + s.intervalSeconds() * 1000L);
			return;
		}
		if (now < due) return;

		sound.play(pursuerAt, s);
		nextDue.put(id, due + s.intervalSeconds() * 1000L);
		String label = pursuer.getCallsign() != null
				? ChatColor.stripColor(pursuer.getCallsign())
				: pursuer.getEntity().getName();
		log.debug("SIREN {} cop={} dist={}", player.getName(), label, String.format("%.1f", best));
	}

	private static void keystoneSound(Location at, SirenSettings settings) {
		if (settings.sound() == null || settings.sound().isBlank()) return;
		new SoundEffect(SoundEffect.SoundType.VANILLA, settings.sound(), settings.volume(), settings.pitch())
				.playAtLocation(at);
	}
}
