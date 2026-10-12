package org.luckyraven.gangland.turf.capture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.RecordingTurfSoundContract;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.*;

import static org.mockito.Mockito.*;

/**
 * Lane W2-R2 test harness: a {@link CaptureService} wired with the 0.17.0 capture rules, a fake clock and mock players
 * and gangs. Package-private because it drives the package-private {@code tick(cache, now)}. Owner gangs report
 * {@code getLastMemberOnlineAt() == 0}, so the window says attackable before and after W2-R1 lands.
 */
final class W2R2Harness {

	static final long T0 = 100_000_000L;

	final TurfManager                turfManager = new TurfManager(new InMemoryTurfRepository());
	final FakeGangLookup             gangs       = new FakeGangLookup();
	final FakeUserLookup             users       = new FakeUserLookup();
	final Map<Integer, Integer>      guards      = new HashMap<>();
	final Map<Integer, Integer>      cap         = new HashMap<>();
	final Map<Integer, Integer>      owned       = new HashMap<>();
	final Map<UUID, Player>          players     = new HashMap<>();
	final Map<UUID, Turf>            cache       = new HashMap<>();
	final Map<Integer, List<Member>> members     = new HashMap<>();
	final CaptureRules               rules;
	final CaptureService             service;

	/** Events fired by the last {@link #tick}. */
	List<Event> lastEvents = List.of();
	Turf        turf;

	W2R2Harness(int durationSeconds, CaptureRules rules) {
		this.rules = rules;
		CaptureSettings settings = CaptureFixtures.settings(durationSeconds, 15, 15, 10, new int[0], 90, 90);
		CaptureEligibility eligibility = new CaptureEligibility(gangs, settings, () -> this.rules, id -> 0L,
		                                                        id -> cap.getOrDefault(id, 99),
		                                                        id -> owned.getOrDefault(id, 0));
		this.service = new CaptureService(turfManager, gangs, users, settings, new RecordingTurfSoundContract(),
		                                  new TurfOwnership(turfManager), eligibility, () -> this.rules,
		                                  id -> guards.getOrDefault(id, 0));
	}

	/** Rules with the 0.17.0 window (OWNERS_OFFLINE, zero wake-up/min age) and the given knobs. */
	static CaptureRules rules(int presenceSeconds, int minCaptureSeconds, int maxContestMinutes, int guardWeightCap) {
		return new CaptureRules(CaptureRules.Window.OWNERS_OFFLINE, 0, 0, presenceSeconds, minCaptureSeconds,
		                        maxContestMinutes, guardWeightCap);
	}

	Gang gang(int id) {
		Gang gang = CaptureFixtures.gang(id);
		gangs.register(gang);
		members.put(id, new ArrayList<>());
		when(gang.getMembers()).thenReturn(members.get(id));
		return gang;
	}

	Turf ownedBy(Integer ownerId) {
		turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), ownerId, BigDecimal.TEN, 0L, 0L);
		turfManager.create(turf);
		return turf;
	}

	Turf unclaimed() {
		return ownedBy(null);
	}

	TurfRuntimeState state() {
		return turfManager.getRuntimeState(turf.getId());
	}

	/** A living player of {@code gangId} standing inside the turf. */
	Player join(int gangId) {
		Player player = CaptureFixtures.player();
		users.register(player, CaptureFixtures.userInGang(gangId));
		players.put(player.getUniqueId(), player);
		cache.put(player.getUniqueId(), turf);
		return player;
	}

	void leave(Player player) {
		cache.remove(player.getUniqueId());
	}

	void setDead(Player player, boolean dead) {
		when(player.isDead()).thenReturn(dead);
	}

	/** Records a member of {@code gang} who joined at {@code joinDate}, for the frozen-sides check. */
	Player addMember(Gang gang, Player player, long joinDate) {
		UUID   uuid   = player.getUniqueId();
		Member member = mock(Member.class);
		when(member.getUuid()).thenReturn(uuid);
		when(member.getGangJoinDateLong()).thenReturn(joinDate);
		members.get(gang.getId()).add(member);
		return player;
	}

	/** One capture tick at {@code now}; {@link #lastEvents} holds what it fired. */
	void tick(long now) {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			for (Map.Entry<UUID, Player> entry : players.entrySet()) {
				UUID   id     = entry.getKey();
				Player player = entry.getValue();
				bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
			}
			service.tick(cache, now);
			ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
			verify(bukkit.pluginManager(), atLeast(0)).callEvent(captor.capture());
			lastEvents = List.copyOf(captor.getAllValues());
		}
	}

	List<TurfCaptureFailedEvent.Reason> failedReasons() {
		List<TurfCaptureFailedEvent.Reason> reasons = new ArrayList<>();
		for (Event event : lastEvents) {
			if (event instanceof TurfCaptureFailedEvent failed) {
				reasons.add(failed.getReason());
			}
		}
		return reasons;
	}
}
