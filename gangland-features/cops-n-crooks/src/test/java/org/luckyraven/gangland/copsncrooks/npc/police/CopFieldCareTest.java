package org.luckyraven.gangland.copsncrooks.npc.police;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.npc.FieldCareSettings;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Field care (phase H13): a hurt cop limps, bleeds and radios Hit once; a Medic-role cop of its group walks over, the
 * patient holds still and crouches while treated, a hit on the medic starts the channel over, and the heal fires
 * EntityRegainHealthEvent(CUSTOM).
 */
@DisplayName("CopFieldCare - hurt state, medic assignment, channel and heal")
class CopFieldCareTest {

	private static final CopRole MEDIC = new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, 8.0, 12.0, 1.0, null,
	                                                 0, null, 1.0, 0, null, 0, 60, true, false);

	private BukkitStatics     bukkit;
	private World             world;
	private CopConfigProvider provider;
	private CopRadio          radio;
	private AtomicLong        clock;
	private CopGroup          group;
	private NpcSquad          squad;
	private List<CopNpc>      cops;
	private CopFieldCare      care;

	@BeforeEach
	void setUp() {
		bukkit   = BukkitStatics.install();
		world    = mock(World.class);
		provider = mock(CopConfigProvider.class);
		when(provider.getFieldCareSettings()).thenReturn(FieldCareSettings.DEFAULT);
		when(provider.getAiTickRate()).thenReturn(10);
		radio = mock(CopRadio.class);
		clock = new AtomicLong(1_000);
		cops  = new ArrayList<>();
		group = mock(CopGroup.class);
		squad = mock(NpcSquad.class);
		when(group.getCops()).thenReturn(cops);
		when(group.getSquad()).thenReturn(squad);
		care = new CopFieldCare(() -> provider, radio, clock::get);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("dropping to half health: limp 0.7, bleeding every tick, one Hit line; back above: normal speed")
	void hurtEdge_limpsBleedsAndSaysHitOnce() {
		CopNpc cop = cop(1, 10.0, CopState.PURSUING, null);

		care.tick(group);
		care.tick(group);
		care.tick(group);

		verify(cop, times(1)).applySpeed(0.7);
		verify(radio, times(1)).sayAs(group, cop, "Hit", Map.of());
		verify(world, times(3)).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(),
		                                      anyDouble(), anyDouble(), anyDouble());

		when(cop.getEntity().getHealth()).thenReturn(15.0);
		care.tick(group);

		verify(cop).applySpeed(1.0);
		verify(radio, times(1)).sayAs(group, cop, "Hit", Map.of());
	}

	@Test
	@DisplayName("Field_Care switched off: a limping cop walks normally again")
	void disabled_restoresSpeed() {
		CopNpc cop = cop(1, 10.0, CopState.PURSUING, null);
		care.tick(group);

		when(provider.getFieldCareSettings()).thenReturn(new FieldCareSettings(false, 0.5, 0.7, true, 24, 2.5, 60,
		                                                                       0.5));
		care.tick(group);

		verify(cop).applySpeed(1.0);
	}

	@Test
	@DisplayName("a hurt cop gets the nearest medic: Medic_Moving and Covering_Fire (from the leader); one medic, one patient")
	void hurtCop_getsMedic_andSecondHurtCopWaits() {
		CopNpc patient = cop(1, 10.0, CopState.PURSUING, null);
		CopNpc other   = cop(2, 8.0, CopState.COMBAT, null);
		CopNpc medic   = cop(3, 20.0, CopState.PURSUING, MEDIC);
		CopNpc leader  = cop(4, 20.0, CopState.PURSUING, null);
		when(squad.leader()).thenReturn(leader);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(10.0);

		care.tick(group);

		assertSame(patient, medic.getPatient());
		verify(radio).sayAs(group, medic, "Medic_Moving", Map.of("member", CopRadio.callsign(patient)));
		verify(radio).sayAs(eq(group), eq(leader), eq("Covering_Fire"), anyMap());
		verify(medic).navigateTo(patient.getEntity().getLocation());

		care.tick(group);
		assertSame(patient, medic.getPatient());
		verify(radio, never()).sayAs(group, medic, "Medic_Moving", Map.of("member", CopRadio.callsign(other)));
	}

	@Test
	@DisplayName("no medic: a cop without the Medic role, a cuffing medic or one beyond Medic_Radius is never picked")
	void ineligibleCops_neverPicked() {
		cop(1, 10.0, CopState.PURSUING, null);
		CopNpc plain    = cop(2, 20.0, CopState.PURSUING, null);
		CopNpc cuffing  = cop(3, 20.0, CopState.CUFFING, MEDIC);
		CopNpc far      = cop(4, 20.0, CopState.COMBAT, MEDIC);
		when(plain.distanceTo(any(LivingEntity.class))).thenReturn(5.0);
		when(cuffing.distanceTo(any(LivingEntity.class))).thenReturn(5.0);
		when(far.distanceTo(any(LivingEntity.class))).thenReturn(30.0);

		care.tick(group);

		assertNull(plain.getPatient());
		assertNull(cuffing.getPatient());
		assertNull(far.getPatient());
		verify(radio, never()).sayAs(any(), any(), eq("Medic_Moving"), anyMap());
	}

	@Test
	@DisplayName("within Heal_Range: the medic stops, the patient holds still under care; Channel_Ticks later it is healed by half of max")
	void channel_healsHalfOfMax_firesRegainEvent_endsCare() {
		CopNpc patient = cop(1, 8.0, CopState.PURSUING, null);
		CopNpc medic   = cop(2, 20.0, CopState.COMBAT, MEDIC);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(2.0);

		for (int i = 0; i < 5; i++) care.tick(group); // assigned and treated from the first tick
		assertTrue(patient.isUnderCare());
		verify(medic, atLeastOnce()).pauseNavigation();
		verify(patient.getEntity(), never()).setHealth(anyDouble());

		care.tick(group); // 6th channel tick: 60 ticks at an AI tick rate of 10

		verify(patient.getEntity()).setHealth(18.0);
		ArgumentCaptor<Event> event = ArgumentCaptor.forClass(Event.class);
		verify(bukkit.pluginManager()).callEvent(event.capture());
		EntityRegainHealthEvent regain = (EntityRegainHealthEvent) event.getValue();
		assertEquals(EntityRegainHealthEvent.RegainReason.CUSTOM, regain.getRegainReason());
		assertEquals(10.0, regain.getAmount());
		verify(radio).sayAs(group, medic, "Patched_Up", Map.of("member", CopRadio.callsign(patient)));
		assertNull(medic.getPatient());
		assertFalse(patient.isUnderCare());
	}

	@Test
	@DisplayName("a cancelled regain event heals nothing, but the treatment still ends")
	void cancelledRegain_noHeal() {
		CopNpc patient = cop(1, 8.0, CopState.PURSUING, null);
		CopNpc medic   = cop(2, 20.0, CopState.COMBAT, MEDIC);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(2.0);
		doAnswer(i -> {
			((EntityRegainHealthEvent) i.getArgument(0)).setCancelled(true);
			return null;
		}).when(bukkit.pluginManager()).callEvent(any());

		for (int i = 0; i < 6; i++) care.tick(group);

		verify(patient.getEntity(), never()).setHealth(anyDouble());
		assertNull(medic.getPatient());
	}

	@Test
	@DisplayName("out of Heal_Range the medic walks to the patient, which is not held; a hit on the medic mid-channel starts it over")
	void walkThenChannel_hitResetsProgress() {
		CopNpc patient = cop(1, 8.0, CopState.PURSUING, null);
		CopNpc medic   = cop(2, 20.0, CopState.COMBAT, MEDIC);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(6.0);

		care.tick(group);
		care.tick(group);
		assertFalse(patient.isUnderCare());
		verify(medic, atLeastOnce()).navigateTo(patient.getEntity().getLocation());

		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(2.0);
		for (int i = 0; i < 4; i++) care.tick(group); // 40 of 60 ticks
		when(medic.getEntity().getHealth()).thenReturn(16.0);
		care.tick(group); // hit: back to 0

		verify(radio).sayAs(group, medic, "Medic_Pinned", Map.of("member", CopRadio.callsign(patient)));
		for (int i = 0; i < 5; i++) care.tick(group);
		verify(patient.getEntity(), never()).setHealth(anyDouble());
		care.tick(group);
		verify(patient.getEntity()).setHealth(18.0);
	}

	@Test
	@DisplayName("a patient that turns invalid, or a medic leaving the fight, ends the treatment on both sides")
	void endConditions_clearBothSides() {
		CopNpc patient = cop(1, 8.0, CopState.PURSUING, null);
		CopNpc medic   = cop(2, 20.0, CopState.COMBAT, MEDIC);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(2.0);
		care.tick(group);
		care.tick(group);
		assertTrue(patient.isUnderCare());

		when(medic.getCurrentState()).thenReturn(CopState.RETURNING);
		care.tick(group);
		assertNull(medic.getPatient());
		assertFalse(patient.isUnderCare());

		when(medic.getCurrentState()).thenReturn(CopState.COMBAT);
		care.tick(group); // re-assigned
		assertSame(patient, medic.getPatient());
		when(patient.isValid()).thenReturn(false);
		care.tick(group);
		assertNull(medic.getPatient());
	}

	@Test
	@DisplayName("a medic that never gets there gives up after GIVE_UP_MS")
	void givesUp() {
		cop(1, 8.0, CopState.PURSUING, null);
		CopNpc medic = cop(2, 20.0, CopState.COMBAT, MEDIC);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(6.0);
		care.tick(group);

		clock.addAndGet(CopFieldCare.GIVE_UP_MS);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(30.0); // out of radius: no instant re-assignment
		care.tick(group);

		assertNull(medic.getPatient());
	}

	@Test
	@DisplayName("Covering_Fire: another cop speaks when the leader is the medic; nobody when only medic and patient are left")
	void coveringFire_speakerIsNeverMedicOrPatient() {
		CopNpc patient = cop(1, 8.0, CopState.PURSUING, null);
		CopNpc medic   = cop(2, 20.0, CopState.COMBAT, MEDIC);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(6.0);
		when(squad.leader()).thenReturn(medic);

		care.tick(group);
		verify(radio, never()).sayAs(eq(group), any(), eq("Covering_Fire"), anyMap());

		CopNpc rifle = cop(3, 20.0, CopState.PURSUING, null);
		medic.setPatient(null);
		care.tick(group); // the stale treatment ends (medic dropped its patient)
		care.tick(group); // re-assigned
		verify(radio).sayAs(eq(group), eq(rifle), eq("Covering_Fire"), anyMap());
		verify(radio, never()).sayAs(eq(group), eq(patient), eq("Covering_Fire"), anyMap());
	}

	@Test
	@DisplayName("a cop left under care with no treatment (its medic died and was collected) stands up again")
	void underCareWithoutMedic_standsUp() {
		CopNpc patient = cop(1, 8.0, CopState.PURSUING, null);
		patient.setUnderCare(true);

		care.tick(group);

		assertFalse(patient.isUnderCare());
	}

	/** A cop of {@link #group} at {@code health} of 20, with working patient / under-care slots. */
	private CopNpc cop(int id, double health, CopState state, CopRole role) {
		CopNpc       cop  = mock(CopNpc.class);
		LivingEntity body = mock(LivingEntity.class);
		Location     at   = new Location(world, id, 64, 0);
		when(body.getHealth()).thenReturn(health);
		when(body.getMaxHealth()).thenReturn(20.0);
		when(body.getLocation()).thenReturn(at);
		when(body.getWorld()).thenReturn(world);
		when(cop.getEntity()).thenReturn(body);
		when(cop.isValid()).thenReturn(true);
		when(cop.getCurrentState()).thenReturn(state);
		when(cop.getRole()).thenReturn(role);
		when(cop.getGroup()).thenReturn(group);
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(id);
		when(cop.getNpc()).thenReturn(npc);

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
		}).when(cop).setUnderCare(org.mockito.ArgumentMatchers.anyBoolean());
		when(cop.isUnderCare()).thenAnswer(i -> underCare.get());

		cops.add(cop);
		return cop;
	}
}
