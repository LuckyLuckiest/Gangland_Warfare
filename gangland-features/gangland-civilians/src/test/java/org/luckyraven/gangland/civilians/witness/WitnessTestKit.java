package org.luckyraven.gangland.civilians.witness;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.crime.CrimeService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixture of the 0.20.0 witness tests: a fake clock (milliseconds, starts at 0), injectable sight and aim, a
 * mocked criminal standing at (0, 64, 0) and mocked civilians that remember their {@link CivilianState} and
 * {@link WitnessCall}. Real {@link WitnessService} on top; every outward effect is recorded in a list.
 */
public final class WitnessTestKit {

	public static final WitnessRules RULES = new WitnessRules(true, 24.0, 8, true, 16.0, 12.0, true, 10);

	public long now = 0L;

	public WitnessRules rules = RULES;

	/** Civilians that currently have the criminal in line of sight. */
	public final Set<CivilianNpc> sighted = new HashSet<>();

	/** Whether the criminal is aiming a weapon at the caller. */
	public boolean aiming;

	public final List<Event>          events  = new ArrayList<>();
	public final List<String>         debug   = new ArrayList<>();
	public final List<String>         notices = new ArrayList<>();
	public final List<CivilianNpc>    all     = new ArrayList<>();

	public final World            world     = mock(World.class);
	public final Player           criminal  = mock(Player.class);
	public final UUID             criminalId = UUID.randomUUID();
	public final Location         at        = new Location(world, 0, 64, 0);
	public final CivilianService  civilians = mock(CivilianService.class);
	public final CrimeService     crimes    = mock(CrimeService.class);
	public final WitnessService   service;

	public WitnessTestKit() {
		when(criminal.getUniqueId()).thenReturn(criminalId);
		when(criminal.getName()).thenReturn("Bob");
		when(criminal.isOnline()).thenReturn(true);
		when(criminal.getWorld()).thenReturn(world);
		when(criminal.getLocation()).thenAnswer(invocation -> at.clone());
		when(civilians.getActiveNpcs()).thenAnswer(invocation -> List.copyOf(all));

		WitnessService.Hooks hooks = new WitnessService.Hooks(() -> now, (npc, who, range) -> sighted.contains(npc),
		                                                     (who, caller, range, angle) -> aiming, events::add,
		                                                     (who, text) -> notices.add(who.getName() + "|" + text),
		                                                     debug::add);
		service = new WitnessService(civilians, crimes, () -> rules, () -> "WITNESS-CALLING", hooks);
	}

	/** A civilian standing {@code distance} blocks east of the crime, idle, seeing the criminal when {@code sees}. */
	public CivilianNpc civilian(double distance, boolean sees) {
		CivilianNpc    npc    = mock(CivilianNpc.class);
		LivingEntity   entity = mock(LivingEntity.class);
		UUID           id     = UUID.randomUUID();
		AtomicReference<CivilianState> state = new AtomicReference<>(CivilianState.IDLE);
		AtomicReference<WitnessCall>   call  = new AtomicReference<>();

		when(entity.getUniqueId()).thenReturn(id);
		when(entity.getWorld()).thenReturn(world);
		when(entity.getLocation()).thenAnswer(invocation -> new Location(world, distance, 64, 0));
		when(npc.getEntity()).thenReturn(entity);
		when(npc.isValid()).thenReturn(true);
		when(npc.getCurrentState()).thenAnswer(invocation -> state.get());
		doAnswer(invocation -> {
			state.set(invocation.getArgument(0));
			return null;
		}).when(npc).transitionTo(any(CivilianState.class));
		when(npc.getWitnessCall()).thenAnswer(invocation -> call.get());
		doAnswer(invocation -> {
			call.set(invocation.getArgument(0));
			return null;
		}).when(npc).setWitnessCall(any());

		all.add(npc);
		if (sees) sighted.add(npc);
		return npc;
	}

	public void advanceSeconds(double seconds) {
		now += (long) (seconds * 1000);
	}

	public int witness(String crimeId) {
		return service.witness(criminal, crimeId, at);
	}

	public long countEvents(Class<? extends Event> type) {
		return events.stream().filter(type::isInstance).count();
	}

	public boolean debugContains(String fragment) {
		return debug.stream().anyMatch(line -> line.contains(fragment));
	}
}
