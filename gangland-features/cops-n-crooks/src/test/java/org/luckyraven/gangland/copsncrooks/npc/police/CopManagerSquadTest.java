package org.luckyraven.gangland.copsncrooks.npc.police;

import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.mockito.InOrder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase H11 (spec 4.9): the cops' squad learns where the wanted player is from the crime scene at wanted start, and
 * again whenever he hits one of the group's cops - never from an unrelated attacker. Phase H12 (GL-3): every group
 * speaks on the police radio, spawns at its tier's formation arc, announces escalations, takes (and gives back)
 * backup, and turns on a suspect who resists.
 */
@DisplayName("CopManager - squads, radio, backup and escalation")
class CopManagerSquadTest {

	private static final CopRole MEDIC = new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, 8.0, 12.0, 1.0, null,
	                                                 0, null, 1.0, 0, null, 0, 60, true, false);
	private static final CopRole COMMANDER = new CopRole("Commander", "Commander", NpcFanPlacement.ANY, null, null,
	                                                     1.0, null, 10, null, 1.0, 0, null, 0, 60, false, true);
	private static final CopRole POINTMAN  = new CopRole("Pointman", "Pointman", NpcFanPlacement.ANY, null, null,
	                                                     1.0, null, 5, null, 1.0, 0, null, 0, 60, false, false);
	private static final CopRole ASSAULT   = new CopRole("Assault", "Assault", NpcFanPlacement.ANY, null, null,
	                                                     1.0, null, 0, null, 1.0, 0, null, 0, 60, false, false);

	private CopManagerFixture fx;
	private CopManager        manager;
	private Player            player;
	private UUID              playerId;
	private Wanted            wanted;

	@BeforeEach
	void setUp() {
		fx       = new CopManagerFixture();
		manager  = fx.manager;
		player   = fx.player(10, 10);
		playerId = player.getUniqueId();
		wanted   = CopManagerFixture.wanted(2);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	/** Flipped 0.21.1 (owner ruling R4): the crime scene is a tip, no cop saw him there. */
	@Test
	@DisplayName("a wanted start tips the new group's squad off to the crime scene: a last-known spot, not a sighting")
	void wantedStart_reportsCrimeScene() {
		manager.onWantedStart(player, wanted);

		NpcSquad squad = manager.groupFor(playerId).getSquad();
		assertFalse(squad.hasFreshSighting(), "no cop saw him: the squad searches the spot, it does not chase him live");
		assertEquals(new Location(fx.world, 10, 64, 10), squad.lastKnownLocation());
		assertTrue(manager.groupFor(playerId).tippedOffWithin(fx.clock[0], 0L));
	}

	@Test
	@DisplayName("a hit by the target from where the cop cannot see him is a tip; one the cop can see is a sighting (R4)")
	void copAttacked_withoutLineOfSight_isATip_withItASighting() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		group.add(cop);
		Location hidden = new Location(fx.world, 40, 70, 40);
		when(player.getLocation()).thenReturn(hidden);
		when(cop.hasLineOfSight(player)).thenReturn(false);

		manager.onCopAttackedAlert(cop, player);

		assertEquals(hidden, group.getSquad().lastKnownLocation());
		assertFalse(group.getSquad().hasFreshSighting(), "a shot from cover gives his spot away, not his live position");

		when(cop.hasLineOfSight(player)).thenReturn(true);
		manager.onCopAttackedAlert(cop, player);
		assertTrue(group.getSquad().hasFreshSighting());
	}

	@Test
	@DisplayName("the group's target hitting a cop gives his position away; an unrelated attacker does not")
	void copAttacked_reportsOnlyTheGroupTarget() {
		manager.onWantedStart(player, wanted);
		CopNpc cop = mock(CopNpc.class);
		manager.groupFor(playerId).add(cop);

		Player stranger = fx.player(90, 90);
		manager.onCopAttackedAlert(cop, stranger);

		assertEquals(new Location(fx.world, 10, 64, 10), manager.groupFor(playerId).getSquad().lastKnownLocation());

		when(player.getLocation()).thenReturn(new Location(fx.world, 40, 70, 40));
		manager.onCopAttackedAlert(cop, player);

		assertEquals(new Location(fx.world, 40, 70, 40), manager.groupFor(playerId).getSquad().lastKnownLocation());
	}

	@Test
	@DisplayName("a new wanted start sends the group's returning cops back to pursuit; other states are untouched")
	void wantedStart_sendsReturningCopsBackToPursuit() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);

		CopNpc returningCop = fx.cop(CopState.RETURNING, 0, 0);
		group.add(returningCop);
		CopNpc pursuingCop = fx.cop(CopState.PURSUING, 0, 0);
		group.add(pursuingCop);

		manager.onWantedStart(player, wanted);

		assertEquals(CopState.PURSUING, returningCop.getCurrentState());
		assertEquals(playerId, returningCop.getTargetPlayerId());
		verify(pursuingCop, never()).setTargetPlayerId(any());
		verify(pursuingCop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("a wanted start leaves a returning cop beyond Pursuit.Max_Distance walking home (T-131, cause 5)")
	void wantedStart_farReturningCop_staysReturning() {
		manager.onWantedStart(player, wanted);
		CopNpc far = fx.cop(CopState.RETURNING, 500, 500);
		manager.groupFor(playerId).add(far);

		manager.onWantedStart(player, wanted);

		assertEquals(CopState.RETURNING, far.getCurrentState(), "it would leash again on its next tick");
		verify(far, never()).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("a new group's squad speaks on the police radio")
	void newGroup_squadHasRadioListener() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = mock(CopNpc.class);
		group.add(cop);

		group.getSquad().memberDown(cop);

		assertNotNull(fx.listeners.get(group));
		verify(fx.listeners.get(group)).onSignal(eq(group.getSquad()), any(NpcSquadSignal.class), eq(cop), any());
	}

	@Test
	@DisplayName("dispatch announces a new hunt once; a repeated start with a fresh trail stays quiet")
	void wantedStart_dispatchesOnceForANewHunt() {
		manager.onWantedStart(player, wanted);
		manager.onWantedStart(player, wanted);

		verify(fx.radio, times(1)).dispatch(manager.groupFor(playerId), player, "Dispatch_Wanted", 2, "SWAT");
	}

	@Test
	@DisplayName("the spawn task gives the group squad its tier's formation arc and fills the wanted-level count")
	void spawnTask_setsTierFormationArc() {
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);

		CopGroup group = manager.groupFor(playerId);
		assertEquals(270.0, group.getSquad().getFormationArc());
		assertEquals(2, group.getCops().size());
		assertEquals("SWAT", group.getTierName());
	}

	/** Flipped 0.21.1 (T-131/T-149, owner ruling R3): an out-of-view stranded walker is released, not kept. */
	@Test
	@DisplayName("RETURNING cops beyond the pursuit range are released out of view and refilled; nearby ones count")
	void spawnTask_ignoresStrandedReturningCops() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   east  = fx.cop(CopState.RETURNING, 500, 500);
		CopNpc   west  = fx.cop(CopState.RETURNING, 500, -500);
		group.add(east);
		group.add(west);

		manager.spawnTick(playerId, wanted);

		assertEquals(2, group.getCops().size(), "two fresh cops; the stranded returners are gone, never above the target");
		assertFalse(group.getCops().contains(east));
		assertFalse(group.getCops().contains(west));
		verify(east).destroy(any());
		verify(west).destroy(any());

		CopGroup near = manager.groupFor(playerId);
		near.getCops().clear();
		near.add(fx.cop(CopState.RETURNING, 10, 10));
		near.add(fx.cop(CopState.RETURNING, 12, 10));
		manager.spawnTick(playerId, wanted);
		assertEquals(2, near.getCops().size(), "nearby returners still count");
	}

	/** r2 (R4): a leash release is no news of him - the squad keeps its own lead, his unseen live spot never leaks in. */
	@Test
	@DisplayName("a released leashed walker leaves the squad's lead and tip-off clock alone; a watched one stays (R3/R4)")
	void strandedReleased_keepsTheSquadsLead_watchedStays() {
		manager.onWantedStart(player, wanted);
		CopGroup group   = manager.groupFor(playerId);
		CopNpc   watched = fx.cop(CopState.RETURNING, 500, 500);
		CopNpc   alone   = fx.cop(CopState.RETURNING, -500, 500);
		watched.setTargetPlayerId(playerId);
		alone.setTargetPlayerId(playerId);
		group.add(watched);
		group.add(alone);
		Player bystander = observer(500, 470, false);
		when(fx.world.getPlayers()).thenReturn(List.of(bystander));
		Location lead  = group.getSquad().lastKnownLocation(); // the crime-scene tip
		Location ranOn = new Location(fx.world, 60, 64, 10);
		when(player.getLocation()).thenReturn(ranOn); // he ran on, unseen
		fx.clock[0] += 5_000L;

		manager.spawnTick(playerId, wanted);

		assertFalse(group.getCops().contains(alone), "out of every player's view: removed");
		verify(alone).destroy(any());
		assertTrue(group.getCops().contains(watched), "a bystander is looking at it: it stays, uncounted (T-130)");
		assertEquals(3, group.getCops().size(), "two fresh cops plus the watched walker");
		assertEquals(lead, group.getSquad().lastKnownLocation(), "nobody saw him run on: the squad keeps its own lead");
		assertFalse(group.getSquad().hasFreshSighting());
		assertFalse(group.tippedOffWithin(fx.clock[0], 0L), "a release is no tip-off: the evasion clock runs");
	}

	@Test
	@DisplayName("r2 pin: a leash release re-tips nothing, so the search circle keeps widening from the squad's own stamp")
	void strandedReleased_searchCircleKeepsWidening() {
		manager.onWantedStart(player, wanted);
		CopGroup group  = manager.groupFor(playerId);
		CopNpc   walker = fx.cop(CopState.RETURNING, -500, 500);
		walker.setTargetPlayerId(playerId);
		group.add(walker);
		// 40 s of searching the crime scene: the circle is already wide
		long stamp = CopManagerFixture.ageSearch(group.getSquad(), 40_000L);
		fx.clock[0] += 5_000L;

		manager.spawnTick(playerId, wanted);

		assertFalse(group.getCops().contains(walker), "released");
		assertEquals(stamp, CopManagerFixture.searchStamp(group.getSquad()),
		             "no reportTip, not even of the last-known spot: re-tipping would shrink the circle back to 6 blocks");
	}

	@Test
	@DisplayName("T-435: a new episode at a lower level sends the re-engaged cops over its target home, never the Commander")
	void newLowerEpisode_reengagedCopsOverTheTarget_areSentHome() {
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		when(fx.provider.getSquadComposition(3)).thenReturn(List.of(COMMANDER, POINTMAN, ASSAULT, ASSAULT));
		when(fx.provider.getSquadComposition(1)).thenReturn(List.of(COMMANDER, POINTMAN));
		when(fx.spawner.spawnNearPlayer(any(), anyInt(), any(), any())).thenAnswer(inv -> {
			CopRole role = inv.getArgument(3);
			// the Commander walked farthest (60 blocks off him), the rest 14: nearest first alone would leave him out
			CopNpc cop = role == COMMANDER ? fx.cop(CopState.IDLE, 10, 70) : fx.cop(CopState.IDLE, 0, 0);
			when(cop.getRole()).thenReturn(role);
			return cop;
		});
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		assertEquals(4, group.getCops().size());

		// cleared: the four walk home, still within Pursuit.Max_Distance of him
		manager.onWantedEnd(player, WantedCause.UNKNOWN);
		for (CopNpc cop : group.getCops()) cop.transitionTo(CopState.RETURNING);
		// a new episode at one star calls back only its two of the four in range
		manager.onWantedStart(player, one);
		manager.spawnTick(playerId, one);

		List<CopNpc> engaged = group.getCops().stream().filter(c -> c.getCurrentState() != CopState.RETURNING).toList();
		assertEquals(2, engaged.size(), "one star fields two cops, not the four of the last episode");
		assertTrue(engaged.stream().anyMatch(c -> c.getRole() == COMMANDER), "the Commander stays (T-146)");
	}

	@Test
	@DisplayName("T-435: a level drop sends the live cops over the new target home, not only the queued ones")
	void levelDrop_liveCopsOverTheTarget_areSentHome() {
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		for (CopNpc cop : group.getCops()) cop.transitionTo(CopState.PURSUING);

		manager.spawnTick(playerId, one);

		assertEquals(2, group.getCops().stream().filter(c -> c.getCurrentState() == CopState.PURSUING).count());
	}

	@Test
	@DisplayName("T-510: a level drop with the surplus Marksman and Defender POSTED (perimeter) sends one home")
	void levelDrop_surplusPosted_isSentHome() {
		CopRole defender = role("Defender");
		CopRole marksman = role("Marksman");
		squadComposition(COMMANDER, POINTMAN, defender, marksman);
		Wanted three = CopManagerFixture.wanted(3);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(2)).thenReturn(3);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup     group = manager.groupFor(playerId);
		List<CopNpc> cops  = List.copyOf(group.getCops()); // Commander, Pointman, Defender, Marksman
		assertEquals(4, cops.size());
		cops.get(0).transitionTo(CopState.PURSUING);
		cops.get(1).transitionTo(CopState.PURSUING);
		cops.get(2).transitionTo(CopState.POSTED);
		cops.get(3).transitionTo(CopState.POSTED);

		manager.spawnTick(playerId, CopManagerFixture.wanted(2));

		assertEquals(1, countIn(group, CopState.RETURNING), "two stars field three: the posted Marksman goes home");
		assertEquals(CopState.RETURNING, cops.get(3).getCurrentState());
		assertEquals(CopState.POSTED, cops.get(2).getCurrentState(), "the base squad's Defender keeps his post");
		assertEquals(0, group.getPendingRelease());
	}

	@Test
	@DisplayName("T-510: a COMBAT surplus cop is still never sent home on a level drop; the release waits")
	void levelDrop_surplusInCombat_staysAndTheReleaseWaits() {
		squadComposition(COMMANDER, POINTMAN, role("Defender"), role("Marksman"));
		Wanted three = CopManagerFixture.wanted(3);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(2)).thenReturn(3);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup     group = manager.groupFor(playerId);
		List<CopNpc> cops  = List.copyOf(group.getCops());
		for (CopNpc cop : cops) cop.transitionTo(CopState.PURSUING);
		cops.get(3).transitionTo(CopState.COMBAT);

		manager.spawnTick(playerId, CopManagerFixture.wanted(2));

		assertEquals(0, countIn(group, CopState.RETURNING));
		assertEquals(1, group.getPendingRelease());
	}

	@Test
	@DisplayName("T-435: a new episode at a lower level calls back only its target's worth of walkers, none flip home")
	void newLowerEpisode_callsBackOnlyItsTarget() {
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		manager.onWantedEnd(player, WantedCause.UNKNOWN);
		for (CopNpc cop : group.getCops()) cop.transitionTo(CopState.RETURNING);

		manager.onWantedStart(player, one);

		assertEquals(2, countIn(group, CopState.PURSUING), "the walkers over one star's two keep walking");
	}

	@Test
	@DisplayName("T-436: after a level drop, a rise calls the cops sent home back before any new one spawns")
	void levelDropThenRise_sentHomeCopsComeBack() {
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		manager.spawnTick(playerId, one);
		assertEquals(2, countIn(group, CopState.RETURNING));

		manager.onWantedLevelChange(player, three, 1, 3);
		manager.spawnTick(playerId, three);

		assertEquals(4, countIn(group, CopState.PURSUING), "the two walking home in range rejoin the hunt");
		verify(fx.spawner, times(4)).spawnNearPlayer(any(), anyInt(), any(), any());
	}

	@Test
	@DisplayName("T-436: a casualty after a level drop is refilled by the nearest cop sent home")
	void levelDropThenCasualty_sentHomeCopRefills() {
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		manager.spawnTick(playerId, one);
		CopNpc fallen = group.getCops().stream().filter(c -> c.getCurrentState() == CopState.PURSUING)
		                     .findFirst().orElseThrow();
		when(fallen.isMarkedForRemoval()).thenReturn(true);

		manager.spawnTick(playerId, one);

		assertEquals(2, countIn(group, CopState.PURSUING), "one star still fields two cops");
		verify(fx.spawner, times(4)).spawnNearPlayer(any(), anyInt(), any(), any());
	}

	@Test
	@DisplayName("T-187: the post-escape search keeps every walker it called back, none is sent home at its one star")
	void escapeSearch_keepsEveryCalledBackCop() {
		Wanted three = CopManagerFixture.wanted(3);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		manager.setEscapePredicate(cause -> cause == WantedCause.EVASION);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		for (CopNpc cop : group.getCops()) cop.transitionTo(CopState.RETURNING);
		manager.onWantedEnd(player, WantedCause.EVASION);
		when(fx.targeting.isSearching(playerId)).thenReturn(true);

		manager.spawnTick(playerId, CopManagerFixture.wanted(0));

		assertEquals(4, countIn(group, CopState.PURSUING), "the cops walking home come back to the search");
	}

	@Test
	@DisplayName("T-436 (D1): a cop sent home, called back, then rotated out by its AI walks home, never called back again")
	void sentHomeCalledBackThenRotated_isNotCalledBackAgain() {
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		manager.spawnTick(playerId, one);
		List<CopNpc> sent = group.getCops().stream().filter(c -> c.getCurrentState() == CopState.RETURNING).toList();
		assertEquals(2, sent.size());
		manager.spawnTick(playerId, three); // the rise calls both back
		assertEquals(4, countIn(group, CopState.PURSUING));

		// before the next spawn run the AI rotates one called-back cop out of the chase (stuck, nobody sees him)
		CopNpc rotated = sent.get(0);
		rotated.transitionTo(CopState.RETURNING);
		manager.spawnTick(playerId, three);

		assertEquals(CopState.RETURNING, rotated.getCurrentState(), "a rotated-out cop is never called back (D1)");
	}

	@Test
	@DisplayName("D1 control: a cop never sent home and rotated out of the chase keeps walking home")
	void neverSentHomeRotated_staysReturning() {
		Wanted three = CopManagerFixture.wanted(3);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group   = manager.groupFor(playerId);
		CopNpc   rotated = group.getCops().get(0);
		rotated.transitionTo(CopState.RETURNING);

		manager.spawnTick(playerId, three);

		assertEquals(CopState.RETURNING, rotated.getCurrentState());
	}

	@Test
	@DisplayName("0.21.1: on the post-escape search a walker near the last-known spot is no stranded cop, though he hides 120 off")
	void search_walkerNearTheLastKnownSpot_isNeitherReleasedNorRefilled() {
		manager.setEscapePredicate(cause -> cause == WantedCause.EVASION);
		manager.onWantedStart(player, wanted);
		manager.spawnTick(playerId, wanted);
		CopGroup group = manager.groupFor(playerId);
		manager.onWantedEnd(player, WantedCause.EVASION);
		when(fx.targeting.isSearching(playerId)).thenReturn(true);
		// the squad knew him at the crime scene (10, 10); one searcher walks home from (0, 0), 14 blocks from it
		CopNpc walker = group.getCops().get(0);
		walker.transitionTo(CopState.RETURNING);
		when(player.getLocation()).thenReturn(new Location(fx.world, 130, 64, 10));
		when(player.getEyeLocation()).thenReturn(new Location(fx.world, 130, 65.62, 10, 0f, 0f));

		manager.spawnTick(playerId, CopManagerFixture.wanted(0));

		assertTrue(group.getCops().contains(walker), "measured to the search's goal, the walker is in range");
		verify(fx.spawner, times(2)).spawnNearPlayer(any(), anyInt(), any(), any());
	}

	private static long countIn(CopGroup group, CopState state) {
		return group.getCops().stream().filter(c -> c.getCurrentState() == state).count();
	}

	@Test
	@DisplayName("an NPC facing a leashed walker does not keep it: only real players watch (B13; Keystone's check counts NPCs)")
	void strandedWatchedOnlyByNpcs_released() {
		manager.onWantedStart(player, wanted);
		CopGroup group  = manager.groupFor(playerId);
		CopNpc   walker = fx.cop(CopState.RETURNING, 500, 500);
		walker.setTargetPlayerId(playerId);
		group.add(walker);
		// a Citizens PLAYER NPC (a cop, a civilian, the walker ahead in the file) faces it; the spawner's
		// isVisibleToOtherPlayers iterates world.getPlayers() without an NPC filter and says yes
		Player npc = observer(500, 470, true);
		when(fx.world.getPlayers()).thenReturn(List.of(npc));
		when(fx.spawner.isVisibleToOtherPlayers(any(), any())).thenReturn(true);
		fx.clock[0] += 5_000L;

		manager.spawnTick(playerId, wanted);

		assertFalse(group.getCops().contains(walker), "no real player sees it: removed");
		verify(walker).destroy(any());
	}

	/** A player at (x, 64, z) looking along +Z with a clear line, a Citizens NPC when {@code npc}. */
	private Player observer(double x, double z, boolean npc) {
		Player other = mock(Player.class);
		when(other.getEyeLocation()).thenReturn(new Location(fx.world, x, 65.62, z, 0f, 0f));
		when(other.hasLineOfSight(any())).thenReturn(true);
		when(other.hasMetadata("NPC")).thenReturn(npc);
		return other;
	}

	@Test
	@DisplayName("each new cop gets the composition's next role; a stranded RETURNING cop does not hold its role")
	void spawnTask_fillsCompositionRoles_strandedRoleRefilled() {
		CopRole pointman = role("Pointman");
		CopRole assault  = role("Assault");
		when(fx.provider.getSquadComposition(2)).thenReturn(List.of(pointman, assault));
		manager.onWantedStart(player, wanted);
		CopGroup group    = manager.groupFor(playerId);
		CopNpc   stranded = fx.cop(CopState.RETURNING, 500, 500);
		when(stranded.getRole()).thenReturn(pointman);
		group.add(stranded);

		manager.spawnTick(playerId, wanted);

		verify(fx.spawner).spawnNearPlayer(eq(player), eq(3), any(), eq(pointman));
		verify(fx.spawner).spawnNearPlayer(eq(player), eq(3), any(), eq(assault));
	}

	@Test
	@DisplayName("with roles off (no composition) cops spawn with no role")
	void spawnTask_noComposition_noRole() {
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);

		verify(fx.spawner, times(2)).spawnNearPlayer(eq(player), eq(3), any(), isNull());
	}

	@Test
	@DisplayName("a tier rise is announced once by dispatch; the first spawn and a steady tier are not")
	void tierRise_dispatchesEscalateOnce() {
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, wanted);
		fx.tier[0] = 4;
		manager.spawnTick(playerId, wanted);
		manager.spawnTick(playerId, wanted);

		verify(fx.radio, times(1)).dispatch(any(), eq(player), eq("Escalate"), anyInt(), anyString());
	}

	@Test
	@DisplayName("granted backup adds its extra cop, still capped by Max_Per_Player")
	void backupActive_addsExtraCop_cappedByMaxPerPlayer() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		manager.spawnTick(playerId, wanted);
		assertEquals(2, group.getCops().size());

		assertTrue(group.requestBackup(fx.clock[0], fx.provider.getBackupSettings()));
		manager.spawnTick(playerId, wanted);
		assertEquals(3, group.getCops().size());

		when(fx.provider.getMaxCopsPerPlayer()).thenReturn(3);
		when(fx.provider.getBackupSettings()).thenReturn(new BackupSettings(true, 5, 30_000, 60_000));
		manager.spawnTick(playerId, wanted);
		assertEquals(3, group.getCops().size());
	}

	@Test
	@DisplayName("when backup runs out the newest free cop walks home; fighting or cuffing cops never do, and it retries")
	void backupExpired_surplusCopReturns_onlyWhenNotEngaged() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.requestBackup(fx.clock[0], fx.provider.getBackupSettings());
		manager.spawnTick(playerId, wanted);
		List<CopNpc> cops = List.copyOf(group.getCops());
		assertEquals(3, cops.size());

		cops.get(0).transitionTo(CopState.COMBAT);
		cops.get(1).transitionTo(CopState.CUFFING);
		cops.get(2).transitionTo(CopState.COMBAT);
		fx.clock[0] += 31_000;
		manager.spawnTick(playerId, wanted);

		assertEquals(1, group.getPendingRelease(), "no free cop yet: the release waits");
		for (CopNpc cop : cops) assertFalse(cop.getCurrentState() == CopState.RETURNING);

		cops.get(1).transitionTo(CopState.PURSUING);
		cops.get(2).transitionTo(CopState.PURSUING);
		fx.clock[0] += 1_000;
		manager.spawnTick(playerId, wanted);

		assertEquals(CopState.RETURNING, cops.get(2).getCurrentState(), "newest free cop goes first");
		assertEquals(CopState.PURSUING, cops.get(1).getCurrentState());
		assertEquals(CopState.COMBAT, cops.get(0).getCurrentState());
		assertEquals(0, group.getPendingRelease());
		assertEquals(3, group.getCops().size(), "it despawns on its own, not replaced meanwhile");
	}

	@Test
	@DisplayName("when backup runs out the backup's extra goes home, not a Commander replacement spawned mid-backup (T-146)")
	void backupExpired_commanderReplacementStays() {
		CopNpc[] squad = replacedMidBackup(COMMANDER, ASSAULT);

		assertEquals(CopState.PURSUING, squad[2].getCurrentState(), "the new Commander stays");
		assertEquals(CopState.RETURNING, squad[1].getCurrentState(), "the backup's Assault goes");
		assertEquals(CopState.PURSUING, squad[0].getCurrentState());
	}

	@Test
	@DisplayName("when backup runs out a Pointman replacement spawned mid-backup stays (it leads at L1/L2); the extra Assault goes")
	void backupExpired_pointmanReplacementStays() {
		CopNpc[] squad = replacedMidBackup(POINTMAN, ASSAULT);

		assertEquals(CopState.PURSUING, squad[2].getCurrentState(), "the new Pointman stays");
		assertEquals(CopState.RETURNING, squad[1].getCurrentState(), "the backup's Assault goes");
	}

	@Test
	@DisplayName("a composition ending in Commander: the backup's extra Commander goes home, not the base Assault")
	void backupExpired_extraCommanderGoes() {
		squadComposition(ASSAULT, COMMANDER);
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.requestBackup(fx.clock[0], fx.provider.getBackupSettings());
		manager.spawnTick(playerId, wanted);
		List<CopNpc> cops = List.copyOf(group.getCops()); // Assault, Commander, Commander (backup)

		fx.clock[0] += 31_000;
		manager.spawnTick(playerId, wanted);

		assertEquals(CopState.RETURNING, cops.get(2).getCurrentState(), "the backup's Commander goes");
		assertEquals(CopState.PURSUING, cops.get(1).getCurrentState());
		assertEquals(CopState.PURSUING, cops.get(0).getCurrentState(), "the base Assault stays");
		assertEquals(0, group.getPendingRelease());
	}

	@Test
	@DisplayName("the wanted level dropping to a Commander-less composition mid-backup never sends the last Commander home (T-146)")
	void backupExpired_levelDropped_lastCommanderStays() {
		squadComposition(COMMANDER, POINTMAN, role("Defender"), role("Marksman"), ASSAULT);
		when(fx.provider.getSquadComposition(2)).thenReturn(List.of(POINTMAN, ASSAULT, ASSAULT));
		when(fx.spawner.getTargetCopCount(3)).thenReturn(5);
		when(fx.spawner.getTargetCopCount(2)).thenReturn(3);
		Wanted level3 = CopManagerFixture.wanted(3);
		manager.onWantedStart(player, level3);
		CopGroup group = manager.groupFor(playerId);
		group.requestBackup(fx.clock[0], fx.provider.getBackupSettings());
		manager.spawnTick(playerId, level3);
		group.detach(group.getCops().get(0)); // the Commander is killed mid-backup
		manager.spawnTick(playerId, level3);
		CopNpc replacement = group.getCops().get(5);
		assertSame(COMMANDER, replacement.getRole());

		fx.clock[0] += 31_000;
		manager.spawnTick(playerId, CopManagerFixture.wanted(2)); // the level fell to 2 before the backup ran out

		assertEquals(CopState.PURSUING, replacement.getCurrentState(), "the squad's only Commander stays");
		// flipped 0.21.1 (T-435): the level drop sends home the live cops over its target too, not only the backup's
		// extra; the Commander is the one cop left over the target of 3
		assertEquals(2, group.getCops().stream().filter(c -> c.getCurrentState() == CopState.RETURNING).count());
		assertEquals(4, group.getCops().stream().filter(c -> c.getCurrentState() != CopState.RETURNING).count());
	}

	/**
	 * Base squad {@code lead, extra} plus one backup {@code extra}; {@code lead} is killed mid-backup and replaced, then
	 * the backup runs out. Returns the surviving base {@code extra}, the backup {@code extra} and the replacement.
	 */
	private CopNpc[] replacedMidBackup(CopRole lead, CopRole extra) {
		squadComposition(lead, extra);
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.requestBackup(fx.clock[0], fx.provider.getBackupSettings());
		manager.spawnTick(playerId, wanted);
		List<CopNpc> cops = List.copyOf(group.getCops());
		assertEquals(List.of(lead, extra, extra), cops.stream().map(CopNpc::getRole).toList());

		group.detach(cops.get(0)); // killed mid-backup
		manager.spawnTick(playerId, wanted);
		CopNpc replacement = group.getCops().get(2);
		assertSame(lead, replacement.getRole());

		fx.clock[0] += 31_000;
		manager.spawnTick(playerId, wanted);
		assertEquals(0, group.getPendingRelease());
		return new CopNpc[] {cops.get(1), cops.get(2), replacement};
	}

	/** Squads fill {@code composition} ({@code Squad_Composition}, copsncrooks/cop_roles.yml); each spawn gets the role it was asked for. */
	private void squadComposition(CopRole... composition) {
		when(fx.provider.getSquadComposition(anyInt())).thenReturn(List.of(composition));
		when(fx.spawner.spawnNearPlayer(any(), anyInt(), any(), any())).thenAnswer(inv -> {
			CopNpc cop = fx.cop(CopState.IDLE, 0, 0);
			when(cop.getRole()).thenReturn(inv.getArgument(3));
			return cop;
		});
	}

	@Test
	@DisplayName("a resisting suspect: Resisting is said once, the group's cops fight, and new spawns come combat-forced")
	void resistingEscalation_newSpawnsCombatForced_resistingSaidOnce() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		cop.setTargetPlayerId(playerId);
		group.add(cop);

		group.escalate(playerId);
		manager.aiTick(playerId);
		manager.aiTick(playerId);

		verify(fx.radio, times(1)).sayFromLeader(group, "Resisting");
		assertEquals(CopState.COMBAT, cop.getCurrentState());
		assertTrue(cop.isCombatForced());

		manager.spawnTick(playerId, wanted);
		CopNpc spawned = group.getCops().get(1);
		assertTrue(spawned.isCombatForced());
	}

	@Test
	@DisplayName("each AI tick runs field care on the group: a cop at half health limps and radios Hit")
	void aiTick_runsFieldCare() {
		manager.onWantedStart(player, wanted);
		CopGroup     group = manager.groupFor(playerId);
		CopNpc       cop   = fx.cop(CopState.PURSUING, 0, 0);
		org.bukkit.entity.LivingEntity body = cop.getEntity();
		when(body.getHealth()).thenReturn(10.0);
		when(body.getMaxHealth()).thenReturn(20.0);
		group.add(cop);

		manager.aiTick(playerId);

		verify(cop).applySpeed(0.7);
		verify(fx.radio).sayAs(group, cop, "Hit", java.util.Map.of("health", "50"));
	}

	@Test
	@DisplayName("the wanted player hitting one of his pursuers is resisting too")
	void groupTargetHitsCop_escalates() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		group.add(cop);

		manager.onCopAttackedAlert(cop, player);

		assertTrue(group.isCombatAlert());
		assertTrue(group.pollResisting());
	}

	@Test
	@DisplayName("Keystone 1.15.4: a hit the cop sees marks it eyes-on, so its squad chases while it still sees him")
	void groupTargetHitsCopInSight_marksTheCopEyesOn() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		group.add(cop);
		when(cop.hasLineOfSight(player)).thenReturn(true);
		when(cop.canSee(eq(player), anyDouble())).thenReturn(true);

		manager.onCopAttackedAlert(cop, player);

		assertTrue(org.luckyraven.keystone.npc.SquadProbe.eyesOn(group.getSquad(), player));
		assertTrue(group.getSquad().hasFreshSighting(), "still a sighting for the squad clock");
	}

	@Test
	@DisplayName("0.21.0 P-STAND-3: the group target hitting a cop through the alert path ends the cease-fire")
	void groupTargetHitsCop_endsCeaseFire() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		group.add(cop);
		group.setCeaseFire(true);

		manager.onCopAttackedAlert(cop, player);

		assertFalse(group.isCeaseFire());
	}

	@Test
	@DisplayName("wanted end: the leader stands the group down and the resisting alert is forgotten")
	void wantedEnd_saysStandDown() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 0, 0));
		group.escalate(playerId);

		manager.onWantedEnd(player);

		verify(fx.radio).sayFromLeader(group, "Stand_Down");
		assertFalse(group.isCombatAlert());
	}

	@Test
	@DisplayName("T-433: the Stand_Down marks the squad stood down (its queued orders go quiet); the next wanted start clears it")
	void wantedEnd_marksStoodDown_untilTheNextStart() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 0, 0));

		manager.onWantedEnd(player);
		assertTrue(group.isStoodDown());

		manager.onWantedStart(player, wanted);
		assertFalse(group.isStoodDown());
	}

	@Test
	@DisplayName("T-433 review 2: he hits a stood-down cop: the squad is back in a fight, its contact and backup calls heard")
	void stoodDownSquad_hitByHim_backOnDuty() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.RETURNING, 0, 0);
		group.add(cop);
		manager.onWantedEnd(player);
		assertTrue(group.isStoodDown());

		manager.onCopAttackedAlert(cop, player);

		assertFalse(group.isStoodDown(), "a squad he fights is on duty: its signals reach the radio again");
	}

	@Test
	@DisplayName("0.21.1: on the post-escape search the spawner's leash anchor is the squad's last-known spot, not him")
	void searchSpawnAnchor_isTheLastKnownSpot() {
		@SuppressWarnings("unchecked")
		org.mockito.ArgumentCaptor<java.util.function.Function<Player, Location>> anchor =
				org.mockito.ArgumentCaptor.forClass(java.util.function.Function.class);
		verify(fx.spawner).setLeashAnchor(anchor.capture());

		fx.manager.setEscapePredicate(cause -> cause == WantedCause.EVASION);
		fx.manager.onWantedStart(player, wanted);
		fx.manager.spawnTick(playerId, wanted);
		CopGroup group = fx.manager.groupFor(playerId);
		group.getSquad().reportSighting(new Location(fx.world, 10, 64, 10));
		when(player.getLocation()).thenReturn(new Location(fx.world, 100, 64, 10));
		assertEquals(100.0, anchor.getValue().apply(player).getX(), "while wanted: him");

		fx.manager.onWantedEnd(player, WantedCause.EVASION);
		when(fx.targeting.isSearching(playerId)).thenReturn(true);
		assertEquals(10.0, anchor.getValue().apply(player).getX(), "on the search: the squad's last-known spot");
	}

	@Test
	@DisplayName("one wanted clear (WantedEndEvent then the level change to 0) announces Stand_Down once")
	void wantedEnd_calledTwice_saysStandDownOnce() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 0, 0));
		group.escalate(playerId);

		manager.onWantedEnd(player);
		manager.onWantedEnd(player);

		verify(fx.radio, times(1)).sayFromLeader(group, "Stand_Down");
	}

	@Test
	@DisplayName("an escape wanted end keeps the squad and does not stand the cops down (0.16.1 T-187)")
	void escapeWantedEnd_keepsGroup_andSkipsStandDown() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 0, 0));

		manager.onWantedEnd(player, WantedCause.EVASION);

		verify(fx.radio, never()).sayFromLeader(group, "Stand_Down");
		assertSame(group, manager.groupFor(playerId));
		assertFalse(group.isEmpty());
	}

	@Test
	@DisplayName("a death wanted end despawns the squad and forgets the cop attacker (0.16.1 T-187)")
	void deathWantedEnd_despawnsSquad_andClearsAttackerLock() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		group.add(cop);
		manager.onCopAttacked(cop, player);

		manager.onWantedEnd(player, WantedCause.DEATH);

		assertNull(manager.groupFor(playerId));
		verify(cop).destroy(any());
		assertFalse(manager.isCopAttacker(playerId));
	}

	@Test
	@DisplayName("an arrest wanted end despawns the squad and forgets the cop attacker (0.16.1 T-187)")
	void arrestWantedEnd_despawnsSquad_andClearsCopAttacker() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		group.add(cop);
		manager.onCopAttacked(cop, player);

		manager.onWantedEnd(player, WantedCause.ARREST);

		assertNull(manager.groupFor(playerId));
		assertFalse(manager.isCopAttacker(playerId));
	}

	@Test
	@DisplayName("an ordinary wanted end (bribe) keeps the squad and only clears its targets: the cops walk home")
	void bribeWantedEnd_keepsSquad_andClearsOnlyItsTargets() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		CopNpc   cop   = fx.cop(CopState.PURSUING, 0, 0);
		cop.setTargetPlayerId(playerId);
		group.add(cop);

		manager.onWantedEnd(player, WantedCause.BRIBE);

		assertSame(group, manager.groupFor(playerId));
		verify(cop, never()).destroy(any());
		verify(cop).setTargetPlayerId(null);
	}

	@Test
	@DisplayName("T-371/T-370: a non-death wanted end (bribe) zeroes the surviving group's level")
	void bribeWantedEnd_zeroesGroupLevel() {
		manager.onWantedStart(player, wanted);
		manager.spawnTick(playerId, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 0, 0));
		assertEquals(2, group.getLevel());

		manager.onWantedEnd(player, WantedCause.BRIBE);

		assertEquals(0, group.getLevel());
	}

	@Test
	@DisplayName("with Post_Escape off an escape is the 0.16.0 stand-down: Stand_Down once, the cops walk home")
	void evasionWantedEnd_withEscapeOff_standsDown_andWalksHome() {
		manager.setEscapePredicate(cause -> false);
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 0, 0));

		manager.onWantedEnd(player, WantedCause.EVASION);

		verify(fx.radio).sayFromLeader(group, "Stand_Down");
		assertSame(group, manager.groupFor(playerId));
	}

	@Test
	@DisplayName("a cop mid-cuff drops the cuff when the player escapes: a searched player is never cuffed")
	void escapeWantedEnd_cuffingCop_goesBackToPursuit() {
		manager.onWantedStart(player, wanted);
		CopNpc cop = fx.cop(CopState.CUFFING, 0, 0);
		manager.groupFor(playerId).add(cop);

		manager.onWantedEnd(player, WantedCause.EVASION);

		verify(cop).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("an escape pulls the squad's cops that were walking home back into the search (0.16.1 T-187)")
	void escapeWantedEnd_returningCop_isPulledBackIntoTheSearch() {
		manager.onWantedStart(player, wanted);
		CopNpc returningCop = fx.cop(CopState.RETURNING, 0, 0);
		manager.groupFor(playerId).add(returningCop);

		manager.onWantedEnd(player, WantedCause.EVASION);

		assertEquals(CopState.PURSUING, returningCop.getCurrentState());
		assertEquals(playerId, returningCop.getTargetPlayerId());
	}

	@Test
	@DisplayName("a cop's hit that kills the player ends the squad mid-tick: no ConcurrentModificationException (0.16.1 T-187)")
	void killingHit_endsSquadMidTick_withoutConcurrentModification() {
		manager.onWantedStart(player, wanted);
		CopNpc killer = fx.cop(CopState.PURSUING, 0, 0);
		CopNpc second = fx.cop(CopState.PURSUING, 0, 0);
		manager.groupFor(playerId).add(killer);
		manager.groupFor(playerId).add(second);
		doAnswer(invocation -> {
			manager.onWantedEnd(player, WantedCause.DEATH);
			return null;
		}).when(killer).tick(any());

		assertDoesNotThrow(() -> manager.aiTick(playerId));

		verify(second, never()).tick(any());
		assertNull(manager.groupFor(playerId));
	}

	@Test
	@DisplayName("a searched player at zero stars keeps a lowest-tier squad spawning (0.16.1 T-187)")
	void searchedSpawnTick_atZeroStars_spawnsTwoCopsAtTierOne() {
		manager.onWantedStart(player, wanted);
		when(fx.targeting.isSearching(playerId)).thenReturn(true);

		manager.spawnTick(playerId, CopManagerFixture.wanted(0));

		verify(fx.spawner).getTargetCopCount(1);
		verify(fx.spawner, times(2)).spawnNearPlayer(eq(player), anyInt(), any(), isNull());
	}

	@Test
	@DisplayName("at zero stars and not searched, the spawn task stops and spawns nothing")
	void spawnTick_atZeroStars_notSearched_spawnsNothing() {
		manager.onWantedStart(player, wanted);

		manager.spawnTick(playerId, CopManagerFixture.wanted(0));

		verify(fx.spawner, never()).spawnNearPlayer(any(), anyInt(), any(), any());
	}

	@Test
	@DisplayName("a cop in COMBAT on a searched player goes back to the chase and does not strike him")
	void searchedTarget_inCombat_goesBackToPursuit() {
		manager.onWantedStart(player, wanted);
		CopNpc cop = fx.cop(CopState.COMBAT, 0, 0);
		cop.setTargetPlayerId(playerId);
		manager.groupFor(playerId).add(cop);
		when(fx.targeting.isSearching(playerId)).thenReturn(true);

		manager.aiTick(playerId);

		verify(cop).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("the search running out lifts the cop-attacker lock: nobody who hit a cop stays hunted")
	void searchGaveUp_clearsCopAttackerLock() {
		manager.onWantedStart(player, wanted);
		CopNpc cop = fx.cop(CopState.PURSUING, 0, 0);
		manager.groupFor(playerId).add(cop);
		manager.onCopAttacked(cop, player);

		manager.searchGaveUp(playerId);

		assertFalse(manager.isCopAttacker(playerId));
	}

	@Test
	@DisplayName("shutdown ends field care before the cops are destroyed: the medic is freed, the patient stands up")
	void shutdown_endsFieldCare_beforeDestroy() {
		manager.onWantedStart(player, wanted);
		CopGroup group   = manager.groupFor(playerId);
		CopNpc   patient = careCop(group, 10.0, null);
		CopNpc   medic   = careCop(group, 20.0, MEDIC);
		manager.aiTick(playerId);
		assertSame(patient, medic.getPatient());
		assertTrue(patient.isUnderCare());

		manager.shutdown();

		InOrder medicOrder   = inOrder(medic);
		InOrder patientOrder = inOrder(patient);
		medicOrder.verify(medic).setPatient(null);
		medicOrder.verify(medic).destroy(any());
		patientOrder.verify(patient).setUnderCare(false);
		patientOrder.verify(patient).destroy(any());
	}

	@Test
	@DisplayName("a group the AI tick drops (empty, player no longer wanted) ends its field care: the medic is freed, the patient stands up")
	void emptyGroupDropped_endsFieldCare() {
		manager.onWantedStart(player, wanted);
		CopGroup group   = manager.groupFor(playerId);
		CopNpc   patient = careCop(group, 10.0, null);
		CopNpc   medic   = careCop(group, 20.0, MEDIC);
		manager.aiTick(playerId);
		assertSame(patient, medic.getPatient());

		group.getCops().clear();
		when(fx.targeting.isWanted(playerId)).thenReturn(false);
		manager.aiTick(playerId);

		assertNull(manager.groupFor(playerId));
		assertNull(medic.getPatient());
		assertFalse(patient.isUnderCare());
	}

	@Test
	@DisplayName("an AI-tick hook runs once per aiTick with the player and his group")
	void aiTickHook_runsOncePerAiTick_withTheGroup() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 0, 0));
		List<Object[]> calls = new java.util.ArrayList<>();
		manager.addAiTickHook((who, g) -> calls.add(new Object[]{who, g}));

		manager.aiTick(playerId);

		assertEquals(1, calls.size());
		assertSame(player, calls.get(0)[0]);
		assertSame(group, calls.get(0)[1]);
	}

	@Test
	@DisplayName("the hook also runs for a wanted player whose group has no cops, before the early return")
	void aiTickHook_runsForAnEmptyGroup_beforeTheEarlyReturn() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		assertTrue(group.isEmpty());
		List<CopGroup> seen = new java.util.ArrayList<>();
		manager.addAiTickHook((who, g) -> seen.add(g));

		manager.aiTick(playerId);

		assertEquals(1, seen.size());
		assertSame(group, seen.get(0));
	}

	@Test
	@DisplayName("with no group at all the hook gets a null group")
	void aiTickHook_runsWithNullWhenNoGroupExists() {
		List<Object> seen = new java.util.ArrayList<>();
		boolean[]    ran  = {false};
		manager.addAiTickHook((who, g) -> {
			ran[0] = true;
			seen.add(g);
		});

		manager.aiTick(playerId);

		assertTrue(ran[0]);
		assertNull(seen.get(0));
	}

	@Test
	@DisplayName("a reload tears the groups down and tells the AI-tick hooks the chase has no group (evasion turns OFF)")
	void preClear_runsTheAiTickHooksWithNoGroup() {
		manager.onWantedStart(player, wanted);
		manager.groupFor(playerId).add(fx.cop(CopState.PURSUING, 0, 0));
		List<Object[]> calls = new java.util.ArrayList<>();
		manager.addAiTickHook((who, g) -> calls.add(new Object[]{who, g}));

		manager.onPreClear();

		assertEquals(1, calls.size());
		assertSame(player, calls.get(0)[0]);
		assertNull(calls.get(0)[1], "the group is gone");
		assertNull(manager.groupOf(playerId));
	}

	@Test
	@DisplayName("a hook that throws is skipped: later hooks and the rest of the tick still run")
	void throwingAiTickHook_doesNotStopTheTick() {
		manager.onWantedStart(player, wanted);
		manager.groupFor(playerId).add(fx.cop(CopState.PURSUING, 0, 0));
		int[] later = {0};
		manager.addAiTickHook((who, g) -> {
			throw new IllegalStateException("boom");
		});
		manager.addAiTickHook((who, g) -> later[0]++);

		manager.aiTick(playerId);

		assertEquals(1, later[0]);
	}

	@Test
	@DisplayName("a cop-attacked hook receives the cop and the attacker")
	void copAttackedHook_receivesTheCopAndTheAttacker() {
		manager.onWantedStart(player, wanted);
		CopNpc cop = fx.cop(CopState.PURSUING, 0, 0);
		manager.groupFor(playerId).add(cop);
		List<Object[]> calls = new java.util.ArrayList<>();
		manager.addCopAttackedHook((c, attacker) -> calls.add(new Object[]{c, attacker}));

		manager.onCopAttackedAlert(cop, player);

		assertEquals(1, calls.size());
		assertSame(cop, calls.get(0)[0]);
		assertSame(player, calls.get(0)[1]);
	}

	@Test
	@DisplayName("groupOf returns the group hunting the player, null for anyone else")
	void groupOf_returnsTheHuntedGroup() {
		manager.onWantedStart(player, wanted);

		assertSame(manager.groupFor(playerId), manager.groupOf(playerId));
		assertNotNull(manager.groupOf(playerId));
		assertNull(manager.groupOf(UUID.randomUUID()));
	}

	/** A cop of {@code group} chasing the player, next to the others, at {@code health} of 20, with stateful care slots. */
	private CopNpc careCop(CopGroup group, double health, CopRole role) {
		CopNpc       cop  = fx.cop(CopState.PURSUING, 0, 0);
		cop.setTargetPlayerId(playerId);
		LivingEntity body = cop.getEntity();
		when(body.getHealth()).thenReturn(health);
		when(body.getMaxHealth()).thenReturn(20.0);
		when(cop.getRole()).thenReturn(role);
		when(cop.distanceTo(any(LivingEntity.class))).thenReturn(1.0);
		AtomicReference<CopNpc> patient = new AtomicReference<>();
		doAnswer(i -> {
			patient.set(i.getArgument(0));
			return null;
		}).when(cop).setPatient(any());
		when(cop.getPatient()).thenAnswer(i -> patient.get());
		AtomicBoolean underCare = new AtomicBoolean();
		doAnswer(i -> {
			underCare.set(i.getArgument(0));
			return null;
		}).when(cop).setUnderCare(anyBoolean());
		when(cop.isUnderCare()).thenAnswer(i -> underCare.get());
		group.add(cop);
		return cop;
	}

	private static CopRole role(String name) {
		return new CopRole(name, name, NpcFanPlacement.ANY, null, null, 1.0, null, 0, null, 1.0, 0, null, 0, 60, false,
		                   false);
	}

	private CopGroup regroupingGroup(double copX, double copZ) {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, copX, copZ));
		group.add(fx.cop(CopState.PURSUING, copX, copZ));
		group.startRegroup(fx.clock[0], org.luckyraven.gangland.copsncrooks.npc.police.config.RegroupSettings.DEFAULT);
		return group;
	}

	@Test
	@DisplayName("regroup_endsWhenArrivedCopsReachTheTarget_andRadiosPushOnce")
	void regroup_endsWhenArrivedCopsReachTheTarget_andRadiosPushOnce() {
		CopGroup group = regroupingGroup(12, 10);

		manager.spawnTick(playerId, wanted);
		manager.spawnTick(playerId, wanted);

		assertFalse(group.isRegrouping());
		assertFalse(group.isFallingBack(fx.clock[0]));
		verify(fx.radio, times(1)).sayFromLeaderLater(eq(group), eq("Regroup_Push"), eq(1), any());
	}

	@Test
	@DisplayName("regroup_endsAtTheFallBackTimeout")
	void regroup_endsAtTheFallBackTimeout() {
		CopGroup group = regroupingGroup(100, 100);
		manager.spawnTick(playerId, wanted);
		assertTrue(group.isRegrouping());

		fx.clock[0] += 15_000;
		manager.spawnTick(playerId, wanted);

		assertFalse(group.isRegrouping());
		verify(fx.radio).sayFromLeaderLater(eq(group), eq("Regroup_Push"), eq(1), any());
	}

	@Test
	@DisplayName("with Backup.Enabled false the regroup ends without the Regroup_Push line announcing backup")
	void regroup_backupOff_pushesWithoutRegroupPush() {
		when(fx.provider.getBackupSettings()).thenReturn(new BackupSettings(false, 1, 30_000, 60_000));
		CopGroup group = regroupingGroup(100, 100);

		fx.clock[0] += 15_000;
		manager.spawnTick(playerId, wanted);

		assertFalse(group.isRegrouping());
		verify(fx.radio, never()).sayFromLeaderLater(any(), eq("Regroup_Push"), anyInt(), any());
	}

	@Test
	@DisplayName("regroup_holdsWhileOnlyOneOfTwoCopsHasArrived")
	void regroup_holdsWhileOnlyOneOfTwoCopsHasArrived() {
		manager.onWantedStart(player, wanted);
		CopGroup group = manager.groupFor(playerId);
		group.add(fx.cop(CopState.PURSUING, 12, 10));     // within Arrival_Radius 24
		group.add(fx.cop(CopState.PURSUING, 100, 100));   // still far
		group.startRegroup(fx.clock[0], org.luckyraven.gangland.copsncrooks.npc.police.config.RegroupSettings.DEFAULT);

		manager.spawnTick(playerId, wanted);

		assertTrue(group.isRegrouping(), "one arrived cop is not the target count of two");
		verify(fx.radio, never()).sayFromLeaderLater(any(), eq("Regroup_Push"), anyInt(), any());
	}

	@Test
	@DisplayName("regroup_holdsWhileReinforcementsAreStillFar")
	void regroup_holdsWhileReinforcementsAreStillFar() {
		CopGroup group = regroupingGroup(100, 100);

		fx.clock[0] += 14_000;
		manager.spawnTick(playerId, wanted);

		assertTrue(group.isRegrouping());
		assertTrue(group.isFallingBack(fx.clock[0]));
		verify(fx.radio, never()).sayFromLeaderLater(any(), eq("Regroup_Push"), anyInt(), any());
	}

	@Test
	@DisplayName("T-371: a chase that ends without a death (wanted level 0, not searching) zeroes the group's level")
	void chaseEndedByDecay_zeroesGroupLevel() {
		manager.onWantedStart(player, wanted);
		manager.spawnTick(playerId, wanted);
		assertEquals(2, manager.groupFor(playerId).getLevel());

		manager.spawnTick(playerId, CopManagerFixture.wanted(0));

		assertEquals(0, manager.groupFor(playerId).getLevel());
	}

	@Test
	@DisplayName("T-187: a release left over from a drop (cops still fighting) never fires in the post-escape search")
	void escapeAfterADrop_leftoverReleaseNeverFiresInTheSearch() {
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		manager.setEscapePredicate(cause -> cause == WantedCause.EVASION);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		// every cop is in a fight, so the drop to one star finds nobody free to send home (flipped 0.21.1, T-510: a
		// POSTED cop is free to go now, so posts no longer hold the release back)
		for (CopNpc cop : group.getCops()) cop.transitionTo(CopState.COMBAT);
		manager.spawnTick(playerId, one);
		assertEquals(2, group.getPendingRelease(), "the drop's release waits for free cops");

		// the last star goes: escape, the fights end, and the search keeps all four
		manager.onWantedEnd(player, WantedCause.EVASION);
		for (CopNpc cop : group.getCops()) cop.transitionTo(CopState.PURSUING);
		when(fx.targeting.isSearching(playerId)).thenReturn(true);
		manager.spawnTick(playerId, CopManagerFixture.wanted(0));

		assertEquals(4, group.getCops().stream().filter(c -> c.getCurrentState() == CopState.PURSUING).count());
	}

	@Test
	@DisplayName("T-435: a re-spot at one star after a stepwise escape fields one star's target, however the stars went")
	void stepwiseEscape_reSpottedAtOneStar_fieldsItsTarget() {
		Wanted three = CopManagerFixture.wanted(3);
		Wanted two   = CopManagerFixture.wanted(2);
		Wanted one   = CopManagerFixture.wanted(1);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(2)).thenReturn(3);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		manager.setEscapePredicate(cause -> cause == WantedCause.EVASION);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		// Drop_Mode ONE_STAR: three, two, one, then the search, which calls every walker back
		manager.spawnTick(playerId, two);
		manager.spawnTick(playerId, one);
		manager.onWantedEnd(player, WantedCause.EVASION);
		when(fx.targeting.isSearching(playerId)).thenReturn(true);
		manager.spawnTick(playerId, CopManagerFixture.wanted(0));
		assertEquals(4, group.getCops().stream().filter(c -> c.getCurrentState() == CopState.PURSUING).count());

		// re-spotted: the search ends in a one-star wanted start
		when(fx.targeting.isSearching(playerId)).thenReturn(false);
		manager.onWantedLevelChange(player, one, 0, 1);
		manager.spawnTick(playerId, one);
		manager.spawnTick(playerId, one);

		assertEquals(2, group.getCops().stream().filter(c -> c.getCurrentState() == CopState.PURSUING).count());
	}

	@Test
	@DisplayName("T-187: a backup that runs out inside the post-escape search sends none of the searchers home")
	void backupExpiryInsideTheSearch_sendsNobodyHome() {
		Wanted three = CopManagerFixture.wanted(3);
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		manager.setEscapePredicate(cause -> cause == WantedCause.EVASION);
		manager.onWantedStart(player, three);
		CopGroup group = manager.groupFor(playerId);
		assertTrue(group.requestBackup(fx.clock[0], fx.provider.getBackupSettings()));
		manager.spawnTick(playerId, three);
		assertEquals(5, countIn(group, CopState.PURSUING));

		manager.onWantedEnd(player, WantedCause.EVASION);
		when(fx.targeting.isSearching(playerId)).thenReturn(true);
		manager.spawnTick(playerId, CopManagerFixture.wanted(0));
		fx.clock[0] += 31_000L; // the backup runs out mid-search
		manager.spawnTick(playerId, CopManagerFixture.wanted(0));

		assertEquals(5, countIn(group, CopState.PURSUING), "the search keeps every cop it called back");
	}

	/** Three stars field Commander, Pointman and two Assaults; one star a Pointman and an Assault, no Commander. */
	private void oneStarWithoutACommander() {
		when(fx.spawner.getTargetCopCount(3)).thenReturn(4);
		when(fx.spawner.getTargetCopCount(1)).thenReturn(2);
		when(fx.provider.getSquadComposition(3)).thenReturn(List.of(COMMANDER, POINTMAN, ASSAULT, ASSAULT));
		when(fx.provider.getSquadComposition(1)).thenReturn(List.of(POINTMAN, ASSAULT));
		when(fx.spawner.spawnNearPlayer(any(), anyInt(), any(), any())).thenAnswer(inv -> {
			CopNpc cop = fx.cop(CopState.IDLE, 0, 0);
			when(cop.getRole()).thenReturn(inv.getArgument(3));
			return cop;
		});
	}

	/** A radio responder (an Assault) joins the group and takes up the chase. */
	private CopNpc responderJoins(CopGroup group) {
		CopNpc responder = fx.cop(CopState.IDLE, 0, 0);
		when(responder.getRole()).thenReturn(ASSAULT);
		group.add(responder);
		responder.setTargetPlayerId(playerId);
		responder.transitionTo(CopState.PURSUING);
		return responder;
	}

	@Test
	@DisplayName("T-435: a drop to a level without a Commander keeps the last one over the target, and owes no release")
	void dropToALevelWithoutACommander_aRadioResponderStays() {
		oneStarWithoutACommander();
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		manager.spawnTick(playerId, one);
		manager.spawnTick(playerId, one);
		assertEquals(0, group.getPendingRelease(), "the protected Commander is no release still owed");

		CopNpc responder = responderJoins(group);
		manager.spawnTick(playerId, one);

		assertEquals(CopState.PURSUING, responder.getCurrentState(), "a radio responder stays");
	}

	@Test
	@DisplayName("T-435: two Commanders over a level without one: one goes home, the last stays, and no release is left owed")
	void twoCommandersOverALevelWithoutOne_oneGoesAndARadioResponderStays() {
		oneStarWithoutACommander();
		when(fx.provider.getSquadComposition(3)).thenReturn(List.of(COMMANDER, COMMANDER, POINTMAN, ASSAULT));
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		// one run: the responder joins straight after the drop's release
		manager.spawnTick(playerId, one);
		assertEquals(1, group.getCops().stream().filter(c -> c.getRole() == COMMANDER)
		                     .filter(c -> c.getCurrentState() == CopState.PURSUING).count());
		assertEquals(0, group.getPendingRelease(), "the last Commander is no release still owed");

		CopNpc responder = responderJoins(group);
		manager.spawnTick(playerId, one);

		assertEquals(CopState.PURSUING, responder.getCurrentState(), "a radio responder stays");
	}

	@Test
	@DisplayName("T-435: two Commander roles over a level without one share one release pool: a radio responder stays")
	void twoCommanderRolesOverALevelWithoutOne_aRadioResponderStays() {
		CopRole sergeant = new CopRole("Sergeant", "Sergeant", NpcFanPlacement.ANY, null, null, 1.0, null, 8, null, 1.0,
		                               0, null, 0, 60, false, true);
		oneStarWithoutACommander();
		when(fx.provider.getSquadComposition(3)).thenReturn(List.of(COMMANDER, sergeant, POINTMAN, ASSAULT));
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		// one run: the responder joins straight after the drop's release
		manager.spawnTick(playerId, one);
		assertEquals(1, group.getCops().stream().filter(c -> c.getRole().commander())
		                     .filter(c -> c.getCurrentState() == CopState.PURSUING).count());
		assertEquals(0, group.getPendingRelease(), "the last Commander is no release still owed");

		CopNpc responder = responderJoins(group);
		manager.spawnTick(playerId, one);

		assertEquals(CopState.PURSUING, responder.getCurrentState(), "a radio responder stays");
	}

	@Test
	@DisplayName("T-435: a re-spot that calls the last Commander back past the target leaves a radio responder alone")
	void reSpotAfterTheCommanderWalked_aRadioResponderStays() {
		oneStarWithoutACommander();
		Wanted three = CopManagerFixture.wanted(3);
		Wanted one   = CopManagerFixture.wanted(1);
		manager.setEscapePredicate(cause -> cause == WantedCause.EVASION);
		manager.onWantedStart(player, three);
		manager.spawnTick(playerId, three);
		CopGroup group = manager.groupFor(playerId);
		manager.onWantedEnd(player, WantedCause.EVASION);
		when(fx.targeting.isSearching(playerId)).thenReturn(true);
		manager.spawnTick(playerId, CopManagerFixture.wanted(0));
		CopNpc commander = group.getCops().stream().filter(c -> c.getRole() == COMMANDER).findFirst().orElseThrow();
		commander.transitionTo(CopState.RETURNING); // the AI rotated him out mid-search
		manager.spawnTick(playerId, CopManagerFixture.wanted(0));

		when(fx.targeting.isSearching(playerId)).thenReturn(false);
		manager.onWantedLevelChange(player, one, 0, 1);
		manager.spawnTick(playerId, one);
		manager.spawnTick(playerId, one);
		assertEquals(CopState.PURSUING, commander.getCurrentState(), "the last Commander came back (T-146)");

		CopNpc responder = responderJoins(group);
		manager.spawnTick(playerId, one);

		assertEquals(CopState.PURSUING, responder.getCurrentState(), "a radio responder stays");
	}
}
