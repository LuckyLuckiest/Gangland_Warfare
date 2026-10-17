package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.TrackerSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Tracker tags (0.20.0): a ranged cop of the squad who sees a wanted player ride a vehicle for {@code Tag_Seconds} in
 * total tags it (cops-owned PDC key on the vehicle entity, value = expiry epoch ms); while he rides a tagged vehicle the
 * squad gets a sighting every {@code Ping_Seconds} until the tag expires or he swaps vehicles. Fed once per cop AI tick
 * through {@code CopManager.addAiTickHook}; main thread only.
 *
 * @since 0.20.0
 */
@CustomLog
public final class TrackerTags {

	/** What the player is told; the module config binds it to {@code WantedMessages} and the action bar. */
	public interface Notifier {

		void tagged(Player player);

		void hud(Player player, int secondsLeft);
	}

	private static final long MAX_DELTA_MS = 1000L;

	private final Supplier<TrackerSettings> settings;
	private final ToIntFunction<Player>     level;
	private final CopRadio                  radio;
	private final LongSupplier              clock;
	private final Notifier                  notifier;
	private final NamespacedKey             key;
	private final Map<UUID, State>          states = new HashMap<>();

	/** Per-player chase state; all fields main-thread only. */
	private static final class State {

		long lastTick = -1L;
		int  vehicle  = Integer.MIN_VALUE;
		long seenMs;
		long nextPing = -1L;
	}

	public TrackerTags(Supplier<TrackerSettings> settings, ToIntFunction<Player> level, CopRadio radio, LongSupplier clock,
	                   Notifier notifier, NamespacedKey key) {
		this.settings = settings;
		this.level    = level;
		this.radio    = radio;
		this.clock    = clock;
		this.notifier = notifier;
		this.key      = key;
	}

	/** One AI tick of {@code player}'s chase; {@code group} is null when no squad hunts him. */
	public void tick(Player player, @Nullable CopGroup group) {
		TrackerSettings s = settings.get();
		if (!s.enabled() || group == null) {
			return;
		}

		State state = states.computeIfAbsent(player.getUniqueId(), id -> new State());
		long  now   = clock.getAsLong();
		long  delta = state.lastTick < 0 ? 0 : Math.min(MAX_DELTA_MS, Math.max(0, now - state.lastTick));
		state.lastTick = now;

		Entity vehicle = player.getVehicle();
		if (vehicle == null) {
			state.vehicle  = Integer.MIN_VALUE;
			state.seenMs   = 0;
			state.nextPing = -1L;
			return;
		}
		if (vehicle.getEntityId() != state.vehicle) {
			state.vehicle  = vehicle.getEntityId();
			delta          = 0;
			state.seenMs   = 0;
			state.nextPing = -1L;
		}
		if (level.applyAsInt(player) < s.minLevel()) {
			return;
		}

		PersistentDataContainer pdc    = vehicle.getPersistentDataContainer();
		Long                    expiry = pdc.get(key, PersistentDataType.LONG);
		if (expiry == null) {
			state.nextPing = -1L;
			trySeeing(player, group, s, state, vehicle, pdc, now, delta);
			return;
		}
		if (now >= expiry) {
			pdc.remove(key);
			state.nextPing = -1L;
			log.debug("TRACKER {} state=expired vehicle={}", player.getName(), vehicle.getEntityId());
			return;
		}

		notifier.hud(player, (int) Math.ceil((expiry - now) / 1000.0));
		if (s.pingSeconds() <= 0) {
			return;
		}
		if (state.nextPing < 0) {
			state.nextPing = now + s.pingSeconds() * 1000L;
		} else if (now >= state.nextPing) {
			state.nextPing = now + s.pingSeconds() * 1000L;
			Location at = vehicle.getLocation();
			group.getSquad().reportSighting(at);
			radio.sayFromLeader(group, "Tracker_Ping", Map.of("place", Objects.toString(radio.placeOf(at), "")));
			log.debug("TRACKER {} state=ping vehicle={}", player.getName(), vehicle.getEntityId());
		}
	}

	private void trySeeing(Player player, CopGroup group, TrackerSettings s, State state, Entity vehicle,
	                       PersistentDataContainer pdc, long now, long delta) {
		boolean seen = false;
		for (CopNpc cop : group.getCops()) {
			CopRole role = cop.getRole();
			if (role != null && role.rangedBand(null) != null && cop.canSee(player, s.tagRange())) {
				seen = true;
				break;
			}
		}
		if (!seen) {
			return;
		}
		state.seenMs += delta;
		if (state.seenMs < s.tagSeconds() * 1000L) {
			return;
		}
		pdc.set(key, PersistentDataType.LONG, now + s.expireSeconds() * 1000L);
		state.seenMs = 0;
		radio.sayFromLeader(group, "Tracker_Tagged");
		notifier.tagged(player);
		log.debug("TRACKER {} state=tagged vehicle={}", player.getName(), vehicle.getEntityId());
	}

	/** Drops the per-player seen-time and ping state (wanted end, quit). Tags on entities just expire. */
	public void forget(UUID playerId) {
		states.remove(playerId);
	}
}
