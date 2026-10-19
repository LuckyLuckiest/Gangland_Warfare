package org.luckyraven.gangland.copsncrooks.wanted.getaway;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.copsncrooks.wanted.config.GetawaySettings;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.keystone.npc.NpcSupport;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Getaway XP (0.21.0 wave 2): an evasion end pays XP by the earned peak, half of it to the wanted crew. Cooldown and
 * daily tallies are in memory. Main thread only.
 *
 * @since 0.21.0
 */
@CustomLog
public class GetawayService {

	/** What {@link #award} did. */
	public record Result(int granted, Reason reason) { }

	public enum Reason {
		PAID, CAPPED, LOW_PEAK, NO_SEARCH, COOLDOWN, DISABLED, NOT_EVASION
	}

	private final ChaseArcs arcs;
	private final ChaseConfigLoader config;
	private final GangMembership gangs;
	private final DetainmentService detainment;
	private final WantedMessages messages;
	private final Supplier<Collection<? extends Player>> online;
	private final BiConsumer<Player, Integer> grantXp;
	private final ZoneId zone;

	private final Map<UUID, Long> lastQualifiedAt = new HashMap<>();
	private final Map<UUID, long[]> dayTally = new HashMap<>();

	public GetawayService(ChaseArcs arcs, ChaseConfigLoader config, GangMembership gangs, DetainmentService detainment,
	                      WantedMessages messages, Supplier<Collection<? extends Player>> online,
	                      BiConsumer<Player, Integer> grantXp, ZoneId zone) {
		this.arcs = arcs;
		this.config = config;
		this.gangs = gangs;
		this.detainment = detainment;
		this.messages = messages;
		this.online = online;
		this.grantXp = grantXp;
		this.zone = zone;
	}

	public Result award(Player escaper, ChaseArc arc, WantedCause cause) {
		GetawaySettings settings = config.getGetaway();
		if (!settings.enabled()) {
			return new Result(0, Reason.DISABLED);
		}
		if (cause != WantedCause.EVASION) {
			return new Result(0, Reason.NOT_EVASION);
		}
		int base = settings.experienceFor(arc.earnedPeak());
		if (base <= 0) {
			return new Result(0, Reason.LOW_PEAK);
		}
		if (!arc.sighted()) {
			return new Result(0, Reason.NO_SEARCH);
		}
		UUID id  = escaper.getUniqueId();
		long now = arcs.now();
		Long last = lastQualifiedAt.get(id);
		if (last != null && now - last < settings.cooldownSeconds() * 1000L) {
			return new Result(0, Reason.COOLDOWN);
		}
		// stamped before the clip: a capped getaway still starts the cooldown
		lastQualifiedAt.put(id, now);

		int granted = clip(id, base, settings, now);
		Result result;
		if (granted > 0) {
			grantXp.accept(escaper, granted);
			result = new Result(granted, Reason.PAID);
		} else {
			result = new Result(0, Reason.CAPPED);
		}
		shares(escaper, base / 2, settings, now);
		return result;
	}

	private void shares(Player escaper, int half, GetawaySettings settings, long now) {
		UUID id = escaper.getUniqueId();
		for (Player p : online.get()) {
			UUID pId = p.getUniqueId();
			if (pId.equals(id) || NpcSupport.isNpc(p) || !gangs.alliedOrSame(id, pId) || detainment.isRestrained(p)
			    || DownedPlayerRegistry.isDowned(pId)) {
				continue;
			}
			ChaseArc mateArc = arcs.arc(pId);
			if (mateArc == null || mateArc.earned() < 1) {
				continue;
			}
			int amount = clip(pId, half, settings, now);
			if (amount > 0) {
				grantXp.accept(p, amount);
				p.sendMessage(messages.format(WantedMessages.Key.GETAWAY_SHARE,
				                              Map.of("player", escaper.getName(), "xp", String.valueOf(amount))));
				log.debug("GETAWAY_SHARE {} -> {} xp={}", escaper.getName(), p.getName(), amount);
			}
		}
	}

	private int clip(UUID id, int amount, GetawaySettings settings, long now) {
		long day = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).toEpochDay();
		long[] tally = dayTally.get(id);
		if (tally == null || tally[0] != day) {
			tally = new long[]{day, 0};
			dayTally.put(id, tally);
		}
		int granted = settings.dailyCap() <= 0 ? amount : (int) Math.max(0, Math.min(amount, settings.dailyCap() - tally[1]));
		tally[1] += granted;
		return granted;
	}
}
