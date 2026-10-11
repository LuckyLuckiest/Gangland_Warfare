package org.luckyraven.gangland.listener.gang.stats;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.downed.PlayerUndownedEvent;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangWorldPolicy;
import org.luckyraven.gangland.gang.combat.DamageLedger;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Counts gang kills, deaths and assists (GP-20). A kill counts only between two different, non-allied gangs, when the
 * victim joined his gang at least {@code Stats.Min_Victim_Member_Age_Hours} ago, outside {@code Worlds.Disabled}, not
 * again for the same killer and victim inside {@code Stats.Repeat_Kill_Cooldown_Seconds}, and not past
 * {@code Stats.Max_Kills_Per_Victim_Per_Day} for the same pair. The killer is the last player in the {@link DamageLedger}
 * (the shooter of a one-shot kill when the ledger saw nothing); the ledger's other damagers inside the assist window get
 * the assists. Core kill counters (/glw stats) are separate by design.
 */
@ListenerHandler
public final class GangStatsListener implements Listener {

	private static final long DEDUP_MS = 500L;
	private static final long DAY_MS   = 24L * 60 * 60 * 1000;

	private final MemberManager   memberManager;
	private final GangManager     gangManager;
	private final DamageLedger    ledger;
	private final GangWorldPolicy worldPolicy;
	private final GangRules       rules;

	/** Test seam; production reads the system clock. */
	LongSupplier clock = System::currentTimeMillis;

	private final Map<UUID, Long>           recentVictims = new HashMap<>();
	private final Map<UUID, Long>           downedCounted = new HashMap<>();
	private final Map<String, Long>         lastKill      = new HashMap<>();
	private final Map<String, Deque<Long>>  dayKills      = new HashMap<>();

	public GangStatsListener(MemberManager memberManager, GangManager gangManager, DamageLedger ledger,
	                         GangWorldPolicy worldPolicy, GangRules rules) {
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.ledger        = ledger;
		this.worldPolicy   = worldPolicy;
		this.rules         = rules;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(PlayerDeathEvent event) {
		Player victim = event.getEntity();
		long   now    = clock.getAsLong();

		// a downed player who then really dies was counted at the down
		if (downedCounted.remove(victim.getUniqueId()) != null) return;

		count(victim, now);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDowned(PlayerDownedEvent event) {
		Player victim = event.getPlayer();
		long   now    = clock.getAsLong();

		if (count(victim, now)) downedCounted.put(victim.getUniqueId(), now);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onUndowned(PlayerUndownedEvent event) {
		downedCounted.remove(event.getPlayer().getUniqueId());
	}

	/** @return whether a kill was credited */
	private boolean count(Player victim, long now) {
		UUID victimId = victim.getUniqueId();

		Long last = recentVictims.get(victimId);
		if (last != null && now - last < DEDUP_MS) return false;
		recentVictims.values().removeIf(at -> now - at > DEDUP_MS);
		recentVictims.put(victimId, now);

		if (!worldPolicy.enabledIn(victim.getWorld())) return false;

		UUID killerId = ledger.lastDamager(victimId).orElseGet(() -> {
			Player shooter = victim.getKiller();
			return shooter == null ? null : shooter.getUniqueId();
		});
		if (killerId == null || killerId.equals(victimId)) return false;

		Member killer = memberManager.getMember(killerId);
		Member dead   = memberManager.getMember(victimId);
		if (!fair(killer, dead)) return false;

		long minAge = rules.getStatsMinVictimMemberAgeHours() * 60L * 60 * 1000;
		if (now - dead.getGangJoinDateLong() < minAge) return false;

		String pair = killerId + ">" + victimId;
		Long   again = lastKill.get(pair);
		if (again != null && now - again < rules.getStatsRepeatKillCooldownSeconds() * 1000L) return false;

		Deque<Long> today = dayKills.computeIfAbsent(pair, key -> new ArrayDeque<>());
		while (!today.isEmpty() && now - today.peekFirst() > DAY_MS) {
			today.pollFirst();
		}
		if (today.size() >= rules.getStatsMaxKillsPerVictimPerDay()) return false;

		today.addLast(now);
		lastKill.put(pair, now);
		killer.setKills(killer.getKills() + 1);
		dead.setDeaths(dead.getDeaths() + 1);

		Duration window = Duration.ofSeconds(rules.getStatsAssistWindowSeconds());
		for (UUID helperId : ledger.assistants(victimId, window)) {
			if (helperId.equals(killerId) || helperId.equals(victimId)) continue;

			Member helper = memberManager.getMember(helperId);
			if (fair(helper, dead)) helper.setAssists(helper.getAssists() + 1);
		}
		return true;
	}

	/** Both in a gang, different gangs, not allied. */
	private boolean fair(@Nullable Member attacker, @Nullable Member victim) {
		if (attacker == null || victim == null || !attacker.hasGang() || !victim.hasGang()) return false;
		if (attacker.getGangId() == victim.getGangId()) return false;

		Gang first  = gangManager.getGang(attacker.getGangId());
		Gang second = gangManager.getGang(victim.getGangId());
		return first != null && second != null && !first.isAlly(second);
	}
}
