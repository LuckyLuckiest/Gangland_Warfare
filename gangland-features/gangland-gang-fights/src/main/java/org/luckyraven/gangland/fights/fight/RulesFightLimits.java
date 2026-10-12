package org.luckyraven.gangland.fights.fight;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.stat.FightHistory;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

/** {@link FightLimits} from {@code fight_rules.yml}: bet and ranked caps, the same-address guard, the arena cooldown. */
public final class RulesFightLimits implements FightLimits {

	private final FightRules    rules;
	private final FightBetCaps  caps;
	private final FightHistory  history;
	private final FightWorld    world;
	private final LongSupplier  now;

	public RulesFightLimits(FightRules rules, FightBetCaps caps, FightHistory history, FightWorld world,
	                        LongSupplier now) {
		this.rules   = rules;
		this.caps    = caps;
		this.history = history;
		this.world   = world;
		this.now     = now;
	}

	@Override
	public @Nullable String refuse(int gangA, int gangB, double bet, boolean ranked, boolean accept) {
		if (bet <= 0) return null;

		if (rules.isBlockSameAddress() && sharedAddress(gangA, gangB)) return "Economy.Same_Address";

		BigDecimal bankA = BigDecimal.valueOf(world.bank(gangA));
		BigDecimal bankB = BigDecimal.valueOf(world.bank(gangB));
		String refusal = caps.checkBet(bet, bankA, bankB);
		if (refusal != null) return refusal;

		long t = now.getAsLong();
		refusal = caps.checkTransfer(bet, history.pairNetToday(gangA, gangB, t), history.wonToday(gangA, t),
		                             history.wonToday(gangB, t));
		if (refusal != null || !ranked) return refusal;

		return caps.checkRanked(world.gangAgeDays(accept ? gangA : gangB), history.rankedBetweenToday(gangA, gangB, t),
		                        history.millisSinceLastRanked(gangA, gangB, t));
	}

	@Override
	public long arenaCooldownMillis() {
		return Math.max(0, rules.getArenaCooldownSeconds()) * 1000L;
	}

	private boolean sharedAddress(int gangA, int gangB) {
		Set<String> addresses = new HashSet<>();
		for (UUID player : world.onlineMembers(gangA)) {
			String address = world.address(player);
			if (address != null) addresses.add(address);
		}
		for (UUID player : world.onlineMembers(gangB)) {
			String address = world.address(player);
			if (address != null && addresses.contains(address)) return true;
		}
		return false;
	}
}
