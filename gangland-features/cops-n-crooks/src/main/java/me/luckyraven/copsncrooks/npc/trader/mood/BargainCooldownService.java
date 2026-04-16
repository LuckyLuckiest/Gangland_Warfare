package me.luckyraven.copsncrooks.npc.trader.mood;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-item bargain-round budget with time-based regeneration.
 *
 * <p>Keyed by (traderId, playerId, itemType). When the player uses a bargain round on a specific item, the used count
 * goes up and a timestamp is stamped. Over time, rounds regenerate linearly — after the full configured cooldown, all
 * rounds are restored. Partial regeneration applies after a fraction of the cooldown: after {@code 1/maxRounds} of the
 * cooldown one round is back, after {@code 2/maxRounds} two are back, etc.
 *
 * <p>Used by both the buy-side {@code BargainView} and the sell-side {@code SellBargainView}. The propose-price
 * evaluator does not consume rounds — only an actual submitted bargain counts.
 */
@RequiredArgsConstructor
public final class BargainCooldownService {

	private final TraderSettings settings;

	private final Map<Key, State> state = new HashMap<>();

	/**
	 * How many bargain rounds the player can still use on this item right now, taking cooldown regeneration into
	 * account. The returned value is always clamped to {@code [0, maxRounds]}.
	 */
	public int availableRounds(UUID traderId, UUID playerId, Material itemType, int maxRounds) {
		State s = state.get(new Key(traderId, playerId, itemType));
		if (s == null) return maxRounds;

		int used = currentUsed(s, maxRounds);
		return Math.max(0, maxRounds - used);
	}

	/**
	 * Apply regenerated rounds based on elapsed time (so debt drops first) and return the current {@code roundsUsed}
	 * debt for this item. Used by views that need to pass {@code roundsUsed} to {@code MoodService.evaluateBargain}.
	 */
	public int roundsUsed(UUID traderId, UUID playerId, Material itemType, int maxRounds) {
		State s = state.get(new Key(traderId, playerId, itemType));
		if (s == null) return 0;

		return currentUsed(s, maxRounds);
	}

	/**
	 * Record that a bargain round was just spent on this item. Applies any pending regeneration before incrementing so
	 * the cooldown timer is measured from the latest attempt.
	 */
	public void recordBargain(UUID traderId, UUID playerId, Material itemType, int maxRounds) {
		Key  k   = new Key(traderId, playerId, itemType);
		long now = System.currentTimeMillis();

		State s              = state.computeIfAbsent(k, ignored -> new State(0, now));
		int   normalizedUsed = currentUsed(s, maxRounds);
		s.roundsUsed = Math.min(maxRounds, normalizedUsed + 1);
		s.lastUsedMs = now;
	}

	public void clearTrader(UUID traderId) {
		state.keySet().removeIf(k -> k.traderId.equals(traderId));
	}

	private int currentUsed(State s, int maxRounds) {
		if (s.roundsUsed == 0) return 0;

		long cooldownMs = Math.max(1, settings.getBargainCooldownSeconds() * 1000L);
		long elapsed    = System.currentTimeMillis() - s.lastUsedMs;
		if (elapsed <= 0) return s.roundsUsed;

		double fraction = Math.min(1.0, elapsed / (double) cooldownMs);
		int    regen    = (int) Math.floor(fraction * maxRounds);
		return Math.max(0, s.roundsUsed - regen);
	}

	private record Key(UUID traderId, UUID playerId, Material itemType) { }

	private static final class State {
		int  roundsUsed;
		long lastUsedMs;

		State(int roundsUsed, long lastUsedMs) {
			this.roundsUsed = roundsUsed;
			this.lastUsedMs = lastUsedMs;
		}
	}

}
