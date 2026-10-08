package org.luckyraven.gangland.copsncrooks.npc.police.state.behavior;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.CopFieldCare;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopBehavior;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.npc.FieldCareSettings;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcCoverStatus;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A hurt cop's own behaviour tick (its retreat) and {@link CopFieldCare} (later in the same AI tick, as in
 * {@code CopManager.aiTick}) in both orders: a patient that needs cover is never held crouching in the open by its
 * medic, whether the walk to cover or the treatment came first.
 */
@DisplayName("Retreat vs field care - a patient on its way to cover is never held in the open")
class RetreatFieldCareOrderTest {

	private static final CopRole MEDIC       = new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, 8.0, 12.0, 1.0,
	                                                       null, 0, null, 1.0, 0, null, 0, 60, true, false);
	private static final double  CUFF_RADIUS = 3.0;
	private static final double  ALERT_RANGE = 40.0;
	private static final double  RADIUS      = RetreatSettings.DEFAULT.radius();

	private final AtomicLong   now  = new AtomicLong(1_000);
	private final List<CopNpc> cops = new ArrayList<>();

	private BukkitStatics              bukkit;
	private Player                     player;
	private NpcSquad                   squad;
	private CopGroup                   group;
	private CopFieldCare               care;
	private Map<CopState, CopBehavior> behaviors;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		UUID playerId = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.isValid()).thenReturn(true);
		bukkit.statics().when(() -> Bukkit.getPlayer(playerId)).thenReturn(player);

		squad = new NpcSquad();
		group = mock(CopGroup.class);
		when(group.getCops()).thenReturn(cops);
		when(group.getSquad()).thenReturn(squad);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getFieldCareSettings()).thenReturn(FieldCareSettings.DEFAULT);
		when(provider.getAiTickRate()).thenReturn(10);
		CopRadio radio = mock(CopRadio.class);
		when(radio.speakerName(any())).thenAnswer(inv -> CopRadio.callsign(inv.getArgument(0)));
		when(radio.memberName(any())).thenAnswer(inv -> CopRadio.callsign(inv.getArgument(0)));
		care = new CopFieldCare(() -> provider, radio, now::get);

		DetainmentService detainment = mock(DetainmentService.class);
		behaviors = Map.of(CopState.PURSUING,
		                   new PursuingBehavior(CUFF_RADIUS, ALERT_RANGE, 80.0, 120, detainment, new CuffLockRegistry(),
		                                        RetreatSettings.DEFAULT, now::get),
		                   CopState.COMBAT,
		                   new CombatBehavior(4.0, ALERT_RANGE, detainment, RetreatSettings.DEFAULT, now::get));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a shooter walking to cover hops PURSUING -> COMBAT in cuff range with its medic in range: it walks on, the channel starts in cover")
	void pursuingToCombatHop_keepsTheWalkToCover() {
		CopNpc patient = shooter(5.0, CopState.PURSUING); // below the 30% retreat line
		medicInRange();
		when(patient.takeCover(player, RADIUS, squad)).thenReturn(NpcCoverStatus.MOVING);

		aiTick(patient); // walking to cover; the medic reaches it and waits
		assertFalse(patient.isUnderCare());

		when(patient.distanceTo((LivingEntity) player)).thenReturn(CUFF_RADIUS - 1); // the suspect closes in
		aiTick(patient); // PURSUING -> COMBAT, before this tick's retreat
		assertEquals(CopState.COMBAT, patient.getCurrentState());
		assertFalse(patient.isUnderCare(), "still on its way to cover: never held in the open");

		aiTick(patient); // COMBAT takes the walk over
		assertFalse(patient.isUnderCare());

		when(patient.takeCover(player, RADIUS, squad)).thenReturn(NpcCoverStatus.ARRIVED);
		aiTick(patient);
		assertTrue(patient.isUnderCare(), "in cover: the channel starts");
	}

	@Test
	@DisplayName("a patient under care shot below its retreat line stands up and walks to cover; in cover it is treated again")
	void underCare_shotBelowRetreatLine_walksToCover() {
		CopNpc patient = shooter(9.0, CopState.COMBAT); // hurt (45%), above the 30% retreat line
		medicInRange();
		aiTick(patient);
		assertTrue(patient.isUnderCare());

		when(patient.getEntity().getHealth()).thenReturn(5.0);
		when(patient.takeCover(player, RADIUS, squad)).thenReturn(NpcCoverStatus.MOVING);
		aiTick(patient);
		assertFalse(patient.isUnderCare(), "it breaks off to cover rather than crouch in the open");
		assertTrue(patient.isMovingToCover());

		when(patient.takeCover(player, RADIUS, squad)).thenReturn(NpcCoverStatus.ARRIVED);
		aiTick(patient);
		aiTick(patient);
		assertTrue(patient.isUnderCare(), "in cover it stays under care");
	}

	@Test
	@DisplayName("a band shooter under care in PURSUING shot below its retreat line stands up and walks to cover, unpaused")
	void pursuingUnderCare_shotBelowRetreatLine_walksToCover() {
		CopNpc patient = shooter(9.0, CopState.PURSUING);
		medicInRange();
		aiTick(patient);
		assertTrue(patient.isUnderCare());

		when(patient.getEntity().getHealth()).thenReturn(5.0);
		when(patient.takeCover(player, RADIUS, squad)).thenReturn(NpcCoverStatus.MOVING);
		clearInvocations(patient);
		aiTick(patient);
		assertEquals(CopState.PURSUING, patient.getCurrentState());
		assertFalse(patient.isUnderCare(), "it breaks off to cover rather than crouch in the open");
		assertTrue(patient.isMovingToCover());
		verify(patient, never()).pauseNavigation();
	}

	@Test
	@DisplayName("a patient under care whose group falls back (its Commander went down) walks to cover too")
	void underCare_groupFallsBack_walksToCover() {
		CopNpc patient = shooter(9.0, CopState.COMBAT);
		medicInRange();
		aiTick(patient);
		assertTrue(patient.isUnderCare());

		when(group.isFallingBack(anyLong())).thenReturn(true);
		when(patient.takeCover(player, RADIUS, squad)).thenReturn(NpcCoverStatus.MOVING);
		aiTick(patient);
		assertFalse(patient.isUnderCare());
	}

	/** One {@code CopManager.aiTick} for the group: the patient's behaviour, then field care. */
	private void aiTick(CopNpc patient) {
		behaviors.get(patient.getCurrentState()).tick(patient);
		care.tick(group);
	}

	/** A ranged, resisting-suspect cop of {@link #group} after {@link #player}, 10 blocks off, in sight. */
	private CopNpc shooter(double health, CopState state) {
		CopNpc cop      = cop(health, state, null);
		UUID   playerId = player.getUniqueId();
		when(cop.isRangedAttacker()).thenReturn(true);
		when(cop.isCombatForced()).thenReturn(true);
		when(cop.getTierConfig()).thenReturn(mock(CopTierConfig.class));
		when(cop.getTargetPlayerId()).thenReturn(playerId);
		when(cop.squadFor(player)).thenReturn(squad);
		when(cop.distanceTo((LivingEntity) player)).thenReturn(10.0);
		when(cop.hasLineOfSight(player)).thenReturn(true);
		when(cop.hasLineOfSight((LivingEntity) player)).thenReturn(true);
		return cop;
	}

	private void medicInRange() {
		CopNpc medic = cop(20.0, CopState.COMBAT, MEDIC);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(2.0);
	}

	/** A cop whose state, patient, under-care and moving-to-cover slots behave like CopNpc's fields. */
	private CopNpc cop(double health, CopState initial, CopRole role) {
		CopNpc       cop  = mock(CopNpc.class);
		LivingEntity body = mock(LivingEntity.class);
		when(body.getHealth()).thenReturn(health);
		when(body.getMaxHealth()).thenReturn(20.0);
		when(body.getLocation()).thenReturn(new Location(null, 0, 64, 0));
		when(cop.getEntity()).thenReturn(body);
		when(cop.isValid()).thenReturn(true);
		when(cop.getRole()).thenReturn(role);
		when(cop.getGroup()).thenReturn(group);
		when(cop.getNpc()).thenReturn(mock(NPC.class));

		AtomicReference<CopState> state = new AtomicReference<>(initial);
		when(cop.getCurrentState()).thenAnswer(i -> state.get());
		doAnswer(i -> { // CopNpc.transitionTo between the fighting states: old onExit, switch, new onEnter
			behaviors.get(state.get()).onExit(cop);
			state.set(i.getArgument(0));
			behaviors.get(state.get()).onEnter(cop);
			return null;
		}).when(cop).transitionTo(any());

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
		AtomicBoolean moving = new AtomicBoolean();
		doAnswer(i -> {
			moving.set(i.getArgument(0));
			return null;
		}).when(cop).setMovingToCover(anyBoolean());
		when(cop.isMovingToCover()).thenAnswer(i -> moving.get());

		cops.add(cop);
		return cop;
	}
}
