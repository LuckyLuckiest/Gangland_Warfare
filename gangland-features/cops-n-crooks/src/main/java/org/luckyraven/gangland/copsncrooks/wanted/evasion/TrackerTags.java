package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.TrackerSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;

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
public final class TrackerTags {

	/** What the player is told; the module config binds it to {@code WantedMessages} and the action bar. */
	public interface Notifier {

		void tagged(Player player);

		void hud(Player player, int secondsLeft);
	}

	public TrackerTags(Supplier<TrackerSettings> settings, ToIntFunction<Player> level, CopRadio radio, LongSupplier clock,
	                   Notifier notifier, NamespacedKey key) {
	}

	/** One AI tick of {@code player}'s chase; {@code group} is null when no squad hunts him. */
	public void tick(Player player, @Nullable CopGroup group) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Drops the per-player seen-time and ping state (wanted end, quit). Tags on entities just expire. */
	public void forget(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
