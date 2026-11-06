package org.luckyraven.gangland.turf.npc.defender;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.config.TurfNpcsConfigLoader;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.gangland.turf.powerups.GarrisonManager;
import org.luckyraven.gangland.turf.powerups.GuardBuffs;
import org.luckyraven.gangland.turf.powerups.PowerupRegistry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Assembles a turf's garrison and its live guard numbers for {@link TurfDefenderDeployer}. Buffs are read here on every
 * call, so one bought mid-contest applies to the next spawn and one that ends falls back on the next tick. Takes no
 * deployer, so there is no bean cycle.
 */
public final class GarrisonRoster {

	private final TurfManager          turfs;
	private final GarrisonManager      garrisons;
	private final TurfNpcsConfigLoader npcConfig;
	private final GangLookupContract   gangs;
	private final TurfMessages         text;
	private final PowerupRegistry      registry;
	private final ActiveBuffManager    buffs;

	public GarrisonRoster(TurfManager turfs, GarrisonManager garrisons, TurfNpcsConfigLoader npcConfig,
	                      GangLookupContract gangs, TurfMessages text, PowerupRegistry registry,
	                      ActiveBuffManager buffs) {
		this.turfs     = turfs;
		this.garrisons = garrisons;
		this.npcConfig = npcConfig;
		this.gangs     = gangs;
		this.text      = text;
		this.registry  = registry;
		this.buffs     = buffs;
	}

	public List<Integer> stockedTurfIds() {
		return garrisons.stockedTurfIds();
	}

	/** A fresh garrison for {@code turfId}, reading the row live; null for a gone or unclaimed turf. */
	@Nullable
	public TurfGarrison garrisonOf(int turfId) {
		Turf turf = turfs.get(turfId);
		if (turf == null || turf.isUnclaimed()) return null;

		Gang owner = gangs.findById(turf.getOwnerGangId());
		String tag = owner == null ? GuardCallsign.tag("", "")
		                           : GuardCallsign.tag(GangDisplayNameResolver.resolve(owner), owner.getName());
		TurfGarrison garrison = new TurfGarrison(turfId, turf.getRegion(), configOf(turfId), tag,
		                                         () -> garrisons.count(turfId), n -> garrisons.consume(turfId, n),
		                                         n -> tellWave(turf, n));
		// T-700: the owner paid for these guards, so a rival's bounty is a share of that price
		// (ponytail: today's price, not the historical purchase price; the stock is a bare count)
		garrison.funded(turf.getOwnerGangId(), guardPrice(turfId));
		return garrison;
	}

	/** {@code turf_npcs.yml} (TF-36: live, so a reload applies) plus Bigger Waves and Faster Reinforcements. */
	public TurfDefenderConfig configOf(int turfId) {
		TurfDefenderConfig          base   = npcConfig.getDefenderConfig();
		PowerupRegistry.GuardLimits limits = registry.guardLimits();
		double waves = GuardBuffs.up(buffs.effectiveMultiplier(turfId, EffectType.BIGGER_GUARD_WAVES),
		                             limits.maxWaves());
		double wait  = GuardBuffs.wait(buffs.effectiveMultiplier(turfId, EffectType.FASTER_REINFORCEMENTS),
		                               limits.minWait());
		return base.withWaves(GuardBuffs.scale(base.waveSize(), waves), GuardBuffs.scale(base.maxAlive(), waves),
		                      (int) Math.round(base.waveCooldownSeconds() * wait));
	}

	/** The Guard Toughness multiplier, clamped; 1.0 with none running. */
	public double guardHealth(int turfId) {
		return GuardBuffs.up(buffs.effectiveMultiplier(turfId, EffectType.GUARD_TOUGHNESS),
		                     registry.guardLimits().maxToughness());
	}

	/** The price of one guard on {@code turfId} right now; the same clamp as the Quartermaster panel's (T-275). */
	private int guardPrice(int turfId) {
		double multiplier = buffs.effectiveMultiplier(turfId, EffectType.GARRISON_DISCOUNT);
		multiplier = Double.isFinite(multiplier) ? Math.max(0.1, Math.min(1.0, multiplier)) : 1.0;
		BigDecimal price = registry.garrisonCost().multiply(BigDecimal.valueOf(multiplier))
		                           .setScale(0, RoundingMode.HALF_UP);
		return price.max(BigDecimal.ONE).intValue();
	}

	/** Tells the owner's online members that a wave took its posts. */
	private void tellWave(Turf turf, int count) {
		Integer ownerId = turf.getOwnerGangId();
		Gang    owner   = ownerId == null ? null : gangs.findById(ownerId);
		if (owner == null) return;
		String line = text.text("Garrison.Wave", "&7Guards at &f%turf% &7reinforced: &f+%count%",
		                        "turf", turf.getDisplayName(), "count", count);
		if (line == null || line.isEmpty()) return;
		for (Member member : owner.getMembers()) {
			Player player = Bukkit.getPlayer(member.getUuid());
			if (player != null) {
				player.sendMessage(line);
			}
		}
	}
}
