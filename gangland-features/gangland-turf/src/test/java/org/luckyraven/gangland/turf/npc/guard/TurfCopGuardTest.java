package org.luckyraven.gangland.turf.npc.guard;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.npc.TurfPowerupManager;
import org.luckyraven.gangland.turf.npc.defender.TurfDefenderDeployer;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Red tests for T-189 (0.16.1): turf defenders and the Quartermaster answer cops that hit a player standing on the
 * turf owned by the player's gang (or an allied gang when {@code Include_Allies} is on). Pins the hand-off, the
 * range gate, the release rules and the static {@link TurfCopGuard#protects} helper.
 */
@DisplayName("TurfCopGuard — turf defenders answer cops that hit protected players (T-189)")
class TurfCopGuardTest {

	private static final World WORLD = mock(World.class);

	private TurfManager              turfs;
	private UserLookupContract       users;
	private GangMembership           membership;
	private TurfPowerupManager       powerups;
	private TurfDefenderDeployer     defenders;
	private Turf                     turf1;
	private Turf                     turf2;

	@BeforeEach
	void setUp() {
		turfs      = mock(TurfManager.class);
		users      = mock(UserLookupContract.class);
		membership = mock(GangMembership.class);
		powerups   = mock(TurfPowerupManager.class);
		defenders  = mock(TurfDefenderDeployer.class);

		turf1 = turf(1, 1);
		turf2 = turf(2, 3);
	}

	// ── owner / allied / challenger hand-off ─────────────────────────────────

	@Test
	@DisplayName("owner member on the turf: in-radius defenders and the Quartermaster get the cop; out-of-radius do not")
	void ownerMemberOnTurf_inRadiusNpcsTargetCop_outOfRadiusDoNot() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 1);

		CivilianNpc quartermaster = npcAt(5, true);
		CivilianNpc near          = npcAt(10, true);
		CivilianNpc far           = npcAt(100, true);
		wirePowerupsAndDefenders(1, List.of(quartermaster), List.of(near, far));

		guard(enabledConfig()).onCopHitPlayer(cop, victim);

		verify(quartermaster).addEntityTargetToFront(cop);
		verify(near).addEntityTargetToFront(cop);
		verify(far, never()).addEntityTargetToFront(cop);
	}

	@Test
	@DisplayName("allied member is protected when Include_Allies is true")
	void alliedMember_protectedWhenIncludeAlliesTrue() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 2);
		when(membership.gangsAllied(2, 1)).thenReturn(true);

		CivilianNpc defender = npcAt(5, true);
		wirePowerupsAndDefenders(1, List.of(), List.of(defender));

		guard(new CopGuardConfig(true, 32.0, true)).onCopHitPlayer(cop, victim);

		verify(defender).addEntityTargetToFront(cop);
	}

	@Test
	@DisplayName("allied member is NOT protected when Include_Allies is false")
	void alliedMember_notProtectedWhenIncludeAlliesFalse() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 2);
		when(membership.gangsAllied(2, 1)).thenReturn(true);

		CivilianNpc defender = npcAt(5, true);
		wirePowerupsAndDefenders(1, List.of(), List.of(defender));

		guard(new CopGuardConfig(true, 32.0, false)).onCopHitPlayer(cop, victim);

		verify(defender, never()).addEntityTargetToFront(cop);
	}

	@Test
	@DisplayName("challenger-gang member on the turf: no hand-off")
	void challengerMember_noHandOff() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 3);

		CivilianNpc defender = npcAt(5, true);
		wirePowerupsAndDefenders(1, List.of(), List.of(defender));

		guard(enabledConfig()).onCopHitPlayer(cop, victim);

		verify(defender, never()).addEntityTargetToFront(cop);
	}

	@Test
	@DisplayName("victim standing on no turf: no hand-off")
	void victimOffTurf_noHandOff() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		when(turfs.findAt(spot)).thenReturn(null);

		CivilianNpc defender = npcAt(5, true);
		wirePowerupsAndDefenders(1, List.of(), List.of(defender));

		guard(enabledConfig()).onCopHitPlayer(cop, victim);

		verify(defender, never()).addEntityTargetToFront(cop);
	}

	@Test
	@DisplayName("victim on a different turf: the first turf's defenders are not summoned")
	void victimOnAnotherTurf_firstTurfDefendersIgnored() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		// victim is a member of gang 1 (owner of turf 1) but stands on turf 2, owned by gang 3
		when(turfs.findAt(spot)).thenReturn(turf2);
		User<Player> member = user(1);
		when(users.findByPlayer(victim)).thenReturn(member);

		CivilianNpc turf1Defender = npcAt(5, true);
		wirePowerupsAndDefenders(1, List.of(), List.of(turf1Defender));

		guard(enabledConfig()).onCopHitPlayer(cop, victim);

		verify(turf1Defender, never()).addEntityTargetToFront(cop);
	}

	@Test
	@DisplayName("enabled=false: no hand-off even for an owner member on the turf")
	void disabled_noHandOff() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 1);

		CivilianNpc defender = npcAt(5, true);
		wirePowerupsAndDefenders(1, List.of(), List.of(defender));

		guard(new CopGuardConfig(false, 32.0, true)).onCopHitPlayer(cop, victim);

		verify(defender, never()).addEntityTargetToFront(cop);
	}

	@Test
	@DisplayName("a non-hostile NPC on the turf is skipped")
	void nonHostileNpc_skipped() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 1);

		CivilianNpc passive = npcAt(5, false);
		wirePowerupsAndDefenders(1, List.of(), List.of(passive));

		guard(enabledConfig()).onCopHitPlayer(cop, victim);

		verify(passive, never()).addEntityTargetToFront(cop);
	}

	@Test
	@DisplayName("cop hitting a protected victim on a second turf is released from the first turf")
	void copMovesToSecondTurf_releasedFromFirst() {
		LivingEntity cop = living(0);

		Location firstSpot  = loc(0);
		Player   firstVict  = victim(firstSpot, UUID.randomUUID());
		whenVictimOn(firstVict, firstSpot, turf1, 1);

		Location secondSpot = loc(50);
		Player   secondVict = victim(secondSpot, UUID.randomUUID());
		whenVictimOn(secondVict, secondSpot, turf2, 3);

		CivilianNpc quartermaster1 = npcAt(5, true);
		when(powerups.civilianNpcsOf(1)).thenReturn(List.of(quartermaster1));
		when(defenders.liveDefenders(1)).thenReturn(List.of());

		CivilianNpc quartermaster2 = npcAt(55, true);
		when(powerups.civilianNpcsOf(2)).thenReturn(List.of(quartermaster2));
		when(defenders.liveDefenders(2)).thenReturn(List.of());

		CopGuardConfig config = new CopGuardConfig(true, 100.0, true);
		TurfCopGuard   guard  = guard(config);
		guard.onCopHitPlayer(cop, firstVict);
		guard.onCopHitPlayer(cop, secondVict);

		verify(quartermaster1).removeEntityTarget(cop);
		verify(quartermaster2).addEntityTargetToFront(cop);
	}

	// ── tick: release and range rules ────────────────────────────────────────

	@Test
	@DisplayName("tick: a dead cop drops the engagement")
	void tick_deadCop_dropsEngagement() throws Exception {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 1);

		CivilianNpc defender = npcAt(5, true);
		wirePowerupsAndDefenders(1, List.of(), List.of(defender));

		TurfCopGuard guard = guard(enabledConfig());
		guard.onCopHitPlayer(cop, victim);
		assertEquals(1, engagementCount(guard), "precondition: the hit opens one engagement");

		when(cop.isDead()).thenReturn(true);
		guard.tick();

		assertEquals(0, engagementCount(guard));
	}

	@Test
	@DisplayName("tick: the victim leaving the turf releases every NPC and drops the engagement")
	void tick_victimLeavesTurf_releasesAll() throws Exception {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 1);

		CivilianNpc quartermaster = npcAt(5, true);
		CivilianNpc defender      = npcAt(10, true);
		wirePowerupsAndDefenders(1, List.of(quartermaster), List.of(defender));

		TurfCopGuard guard = guard(enabledConfig());
		guard.onCopHitPlayer(cop, victim);

		Location away = loc(500);
		when(victim.getLocation()).thenReturn(away);
		when(turfs.findAt(away)).thenReturn(null);
		guard.tick();

		verify(quartermaster).removeEntityTarget(cop);
		verify(defender).removeEntityTarget(cop);
		assertEquals(0, engagementCount(guard));
	}

	@Test
	@DisplayName("tick: a cop beyond the radius is removed from that NPC only, the others keep it")
	void tick_copBeyondRadius_removedFromThatNpcOnly() throws Exception {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 1);

		CivilianNpc far  = npcAt(100, true);  // 100 blocks from the cop, radius 32
		CivilianNpc near = npcAt(10, true);   // 10 blocks from the cop
		wirePowerupsAndDefenders(1, List.of(), List.of(far, near));

		TurfCopGuard guard = guard(enabledConfig());
		guard.onCopHitPlayer(cop, victim);
		guard.tick();

		verify(far).removeEntityTarget(cop);
		verify(near, never()).removeEntityTarget(cop);
	}

	@Test
	@DisplayName("tick: IDLE only when the NPC has neither an entity target nor a player target left")
	void tick_idleOnlyWhenNoTargetsLeft() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 1);

		CivilianNpc emptied    = npcAt(100, true);            // out of range, nothing left -> IDLE
		CivilianNpc stillTarget = npcAt(100, true);           // out of range, but a player target remains -> stays
		when(emptied.removeEntityTarget(cop)).thenReturn(true);
		when(stillTarget.removeEntityTarget(cop)).thenReturn(true);
		when(stillTarget.getTargetPlayerId()).thenReturn(UUID.randomUUID());
		wirePowerupsAndDefenders(1, List.of(), List.of(emptied, stillTarget));

		TurfCopGuard guard = guard(enabledConfig());
		guard.onCopHitPlayer(cop, victim);
		guard.tick();

		verify(emptied).transitionTo(CivilianState.IDLE);
		verify(stillTarget, never()).transitionTo(CivilianState.IDLE);
	}

	@Test
	@DisplayName("tick: an NPC that never held the cop keeps its WANDERING state through a release")
	void tick_unengagedNpc_keepsState() {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       victim = victim(spot, UUID.randomUUID());
		whenVictimOn(victim, spot, turf1, 1);

		CivilianNpc near     = npcAt(5, true);      // engaged at the hit
		CivilianNpc wanderer = npcAt(100, true);    // out of range, never held the cop
		when(wanderer.getCurrentState()).thenReturn(CivilianState.WANDERING);
		wirePowerupsAndDefenders(1, List.of(), List.of(near, wanderer));

		TurfCopGuard guard = guard(enabledConfig());
		guard.onCopHitPlayer(cop, victim);
		guard.tick();

		verify(wanderer, never()).transitionTo(any());
	}

	@Test
	@DisplayName("a cop that then hits a non-member is released from the turf it was defending")
	void copHitsNonMemberAfterProtectedMember_released() throws Exception {
		LivingEntity cop    = living(0);
		Location     spot   = loc(0);
		Player       member = victim(spot, UUID.randomUUID());
		whenVictimOn(member, spot, turf1, 1);

		Location nonMemberSpot = loc(2);
		Player   nonMember     = victim(nonMemberSpot, UUID.randomUUID());
		when(turfs.findAt(nonMemberSpot)).thenReturn(turf1);
		User<Player> challenger = user(3);                         // challenger gang, not protected
		when(users.findByPlayer(nonMember)).thenReturn(challenger);

		CivilianNpc quartermaster = npcAt(5, true);
		wirePowerupsAndDefenders(1, List.of(quartermaster), List.of());

		TurfCopGuard guard = guard(enabledConfig());
		guard.onCopHitPlayer(cop, member);
		verify(quartermaster).addEntityTargetToFront(cop);

		guard.onCopHitPlayer(cop, nonMember);

		verify(quartermaster).removeEntityTarget(cop);
		assertEquals(0, engagementCount(guard));
	}

	// ── static protects() helper ─────────────────────────────────────────────

	@Test
	@DisplayName("protects: same gang is protected")
	void protects_sameGang_true() {
		assertTrue(TurfCopGuard.protects(1, 1, false, (a, b) -> false));
	}

	@Test
	@DisplayName("protects: allied gang protected only when includeAllies is on")
	void protects_allyByFlag() {
		assertTrue(TurfCopGuard.protects(2, 1, true, (a, b) -> true));
		assertFalse(TurfCopGuard.protects(2, 1, false, (a, b) -> true));
	}

	@Test
	@DisplayName("protects: an unclaimed owner (-1) protects nobody")
	void protects_unclaimedOwner_false() {
		assertFalse(TurfCopGuard.protects(-1, -1, true, (a, b) -> true));
	}

	@Test
	@DisplayName("protects: a non-member victim (-1) is never protected")
	void protects_nonMember_false() {
		assertFalse(TurfCopGuard.protects(-1, 1, true, (a, b) -> true));
	}

	// ── fixtures ──────────────────────────────────────────────────────────────

	private TurfCopGuard guard(CopGuardConfig config) {
		return new TurfCopGuard(mock(JavaPlugin.class), turfs, users, membership, powerups, defenders, config);
	}

	private static CopGuardConfig enabledConfig() {
		return new CopGuardConfig(true, 32.0, true);
	}

	private static Location loc(double x) {
		return new Location(WORLD, x, 64, 0);
	}

	private static LivingEntity living(double x) {
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getWorld()).thenReturn(WORLD);
		when(entity.getLocation()).thenReturn(loc(x));
		when(entity.isValid()).thenReturn(true);
		when(entity.isDead()).thenReturn(false);
		return entity;
	}

	private static Player victim(Location at, UUID id) {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getWorld()).thenReturn(WORLD);
		when(player.getLocation()).thenReturn(at);
		when(player.isOnline()).thenReturn(true);
		when(player.isDead()).thenReturn(false);
		return player;
	}

	/** Places the victim on {@code turf} at {@code at} and makes them a member of {@code gangId}. */
	private void whenVictimOn(Player victim, Location at, Turf turf, int gangId) {
		User<Player> member = user(gangId);
		when(turfs.findAt(at)).thenReturn(turf);
		when(users.findByPlayer(victim)).thenReturn(member);
	}

	@SuppressWarnings("unchecked")
	private static User<Player> user(int gangId) {
		User<Player> user = mock(User.class);
		when(user.getGangId()).thenReturn(gangId);
		when(user.hasGang()).thenReturn(true);
		return user;
	}

	private static Turf turf(int id, int ownerGangId) {
		Turf turf = mock(Turf.class);
		when(turf.getId()).thenReturn(id);
		when(turf.getOwnerGangId()).thenReturn(ownerGangId);
		when(turf.isUnclaimed()).thenReturn(false);
		return turf;
	}

	private static CivilianNpc npcAt(double x, boolean hostile) {
		CivilianNpc npc  = mock(CivilianNpc.class);
		LivingEntity body = living(x);
		when(npc.isValid()).thenReturn(true);
		when(npc.isHostile()).thenReturn(hostile);
		when(npc.getEntity()).thenReturn(body);

		CivilianAIBehaviorConfig ai = mock(CivilianAIBehaviorConfig.class);
		when(ai.combatEnabled()).thenReturn(true);
		CivilianTypeConfig typeConfig = mock(CivilianTypeConfig.class);
		when(typeConfig.ai()).thenReturn(ai);
		when(npc.getTypeConfig()).thenReturn(typeConfig);
		return npc;
	}

	private void wirePowerupsAndDefenders(int turfId, List<CivilianNpc> quartermasters, List<CivilianNpc> live) {
		when(powerups.civilianNpcsOf(turfId)).thenReturn(quartermasters);
		when(defenders.liveDefenders(turfId)).thenReturn(live);
	}

	/** Reads the private engagement map size (house pattern: private seam read through reflection). */
	private static int engagementCount(TurfCopGuard guard) throws Exception {
		Field field = TurfCopGuard.class.getDeclaredField("engagements");
		field.setAccessible(true);
		return ((Map<?, ?>) field.get(guard)).size();
	}
}
