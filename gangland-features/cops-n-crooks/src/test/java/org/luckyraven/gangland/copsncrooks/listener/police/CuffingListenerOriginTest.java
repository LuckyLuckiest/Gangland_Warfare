package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.transit.TransitService;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 0.21.0 fnd-origin: {@link CuffedEvent} carries the arrest origin (the four-argument constructor keeps compiling and
 * means CUFFED) and {@link CuffingListener} hands it to {@link DetainmentService#handcuff(Player, ArrestOrigin)}.
 */
@DisplayName("CuffedEvent / CuffingListener - the arrest origin")
class CuffingListenerOriginTest {

	private DetainmentService service;
	private TransitService    transit;
	private CuffingListener   listener;
	private Player            target;
	private CopNpc            cop;

	@BeforeEach
	void setUp() {
		service  = mock(DetainmentService.class);
		transit  = mock(TransitService.class);
		listener = new CuffingListener(service, mock(DetainmentMessageContract.class, inv -> inv.getMethod().getReturnType() == String.class ? "x" : null), transit);
		target   = mock(Player.class);
		when(target.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000dd"));
		cop = mock(CopNpc.class);
	}

	@Test
	@DisplayName("the four-argument constructor means CUFFED")
	void fourArgCtor_defaultsCuffed() {
		assertEquals(ArrestOrigin.CUFFED, new CuffedEvent(cop, target, 2.0, 3).getOrigin());
	}

	@Test
	@DisplayName("the five-argument constructor carries the origin and the old fields")
	void fiveArgCtor_carriesOrigin() {
		CuffedEvent event = new CuffedEvent(cop, target, 2.5, 4, ArrestOrigin.SURRENDERED);

		assertEquals(ArrestOrigin.SURRENDERED, event.getOrigin());
		assertEquals(2.5, event.getCuffRadius());
		assertEquals(4, event.getMaxAttempts());
		assertEquals(target, event.getTarget());
	}

	@Test
	@DisplayName("a null origin passed to the five-argument constructor reads CUFFED")
	void fiveArgCtor_nullOrigin_defaultsCuffed() {
		assertEquals(ArrestOrigin.CUFFED, new CuffedEvent(cop, target, 2.0, 3, null).getOrigin());
	}

	@Test
	@DisplayName("the listener cuffs with the event's origin and schedules the transit")
	void passesEventOrigin() {
		listener.onPlayerSuccessfulCuffing(new CuffedEvent(cop, target, 2.0, 3, ArrestOrigin.BUSTED_DOWNED));

		verify(service).handcuff(target, ArrestOrigin.BUSTED_DOWNED);
		verify(transit).schedule(target);
	}

	@Test
	@DisplayName("a four-argument event is cuffed with CUFFED")
	void legacyEvent_cuffsWithCuffed() {
		listener.onPlayerSuccessfulCuffing(new CuffedEvent(cop, target, 2.0, 3));

		verify(service).handcuff(target, ArrestOrigin.CUFFED);
	}

	@Test
	@DisplayName("a dead target is not cuffed, whatever the origin")
	void deadTarget_isNotCuffed() {
		when(target.isDead()).thenReturn(true);

		listener.onPlayerSuccessfulCuffing(new CuffedEvent(cop, target, 2.0, 3, ArrestOrigin.SURRENDERED));

		verify(service, never()).handcuff(any(Player.class), any(ArrestOrigin.class));
	}
}
