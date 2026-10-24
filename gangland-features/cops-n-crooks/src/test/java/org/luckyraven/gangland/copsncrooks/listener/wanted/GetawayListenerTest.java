package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService.Reason;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService.Result;
import org.luckyraven.gangland.copsncrooks.wanted.hud.HudFixtures;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** P-GET-2 at the event level: pay at LOW with the arc alive, print the line at MONITOR, never read the report settings. */
@DisplayName("GetawayListener")
class GetawayListenerTest {

	@TempDir
	Path tempDir;

	private final UUID playerId = UUID.randomUUID();

	private ChaseArcs       arcs;
	private GetawayService  service;
	private Player          player;
	private Wanted          wanted;
	private GetawayListener listener;

	@BeforeAll
	static void prime() throws ReflectiveOperationException {
		HudFixtures.primeMoneySymbol();
	}

	@BeforeEach
	void setUp() throws IOException {
		arcs    = new ChaseArcs(() -> 1_000_000L);
		service = mock(GetawayService.class);
		player  = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Steve");
		wanted = new Wanted(null, 1, 5);

		WantedMessages messages = HudFixtures.messages(tempDir);
		listener = new GetawayListener(service, arcs, messages);
	}

	private void result(int granted, Reason reason) {
		when(service.award(any(), any(), any())).thenReturn(new Result(granted, reason));
	}

	private WantedEndEvent end(WantedCause cause) {
		return new WantedEndEvent(player, wanted, cause);
	}

	private List<String> sent() {
		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(player, atLeastOnce()).sendMessage(lines.capture());
		return lines.getAllValues().stream().map(l -> l.replaceAll("§.", "")).toList();
	}

	@Test
	@DisplayName("the award handler is LOW and the send handler is MONITOR, both @EventHandler")
	void priorities() throws NoSuchMethodException {
		EventHandler award = GetawayListener.class.getMethod("onEndAward", WantedEndEvent.class)
		                                          .getAnnotation(EventHandler.class);
		EventHandler send = GetawayListener.class.getMethod("onEndSend", WantedEndEvent.class)
		                                         .getAnnotation(EventHandler.class);

		assertEquals(EventPriority.LOW, award.priority());
		assertEquals(EventPriority.MONITOR, send.priority());
	}

	@Test
	@DisplayName("LOW awards with the live arc; MONITOR prints 'Getaway: +75 XP'")
	void lineAfterReport() {
		arcs.start(playerId, WantedCause.CRIME, 3);
		ChaseArc arc = arcs.arc(playerId);
		result(75, Reason.PAID);

		listener.onEndAward(end(WantedCause.EVASION));
		verify(service).award(player, arc, WantedCause.EVASION);
		verify(player, never()).sendMessage(anyString());

		listener.onEndSend(end(WantedCause.EVASION));
		assertEquals(List.of("Getaway: +75 XP"), sent());
	}

	@Test
	@DisplayName("the line is sent once: a second MONITOR sends nothing")
	void lineSentOnce() {
		arcs.start(playerId, WantedCause.CRIME, 3);
		result(75, Reason.PAID);

		listener.onEndAward(end(WantedCause.EVASION));
		listener.onEndSend(end(WantedCause.EVASION));
		listener.onEndSend(end(WantedCause.EVASION));

		assertEquals(1, sent().size());
	}

	@Test
	@DisplayName("CAPPED prints the cap line")
	void cappedLine() {
		arcs.start(playerId, WantedCause.CRIME, 3);
		result(0, Reason.CAPPED);

		listener.onEndAward(end(WantedCause.EVASION));
		listener.onEndSend(end(WantedCause.EVASION));

		assertEquals(List.of("Getaway: daily XP cap reached"), sent());
	}

	@Test
	@DisplayName("COOLDOWN, LOW_PEAK, NO_SEARCH, DISABLED and NOT_EVASION print nothing")
	void noLineForQuietReasons() {
		arcs.start(playerId, WantedCause.CRIME, 3);

		for (Reason reason : List.of(Reason.COOLDOWN, Reason.LOW_PEAK, Reason.NO_SEARCH, Reason.DISABLED,
		                             Reason.NOT_EVASION)) {
			result(0, reason);
			listener.onEndAward(end(WantedCause.EVASION));
			listener.onEndSend(end(WantedCause.EVASION));
		}

		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the line stands alone: the listener takes no config, so the report being off cannot hide it")
	void reportOff_lineStillSent() {
		arcs.start(playerId, WantedCause.CRIME, 1);
		result(25, Reason.PAID);

		listener.onEndAward(end(WantedCause.EVASION));
		listener.onEndSend(end(WantedCause.EVASION));

		assertEquals(List.of("Getaway: +25 XP"), sent());
		for (Constructor<?> c : GetawayListener.class.getConstructors()) {
			for (Class<?> type : c.getParameterTypes()) {
				assertFalse(type.getSimpleName().contains("Config"), type.getName());
			}
		}
	}

	@Test
	@DisplayName("an end with no arc never calls the service and prints nothing")
	void noArc_noop() {
		listener.onEndAward(end(WantedCause.EVASION));
		listener.onEndSend(end(WantedCause.EVASION));

		verify(service, never()).award(any(), any(), any());
		verify(player, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the end cause is passed to the service as is")
	void causeForwarded() {
		arcs.start(playerId, WantedCause.CRIME, 3);
		ChaseArc arc = arcs.arc(playerId);
		result(0, Reason.NOT_EVASION);

		listener.onEndAward(end(WantedCause.ARREST));

		verify(service).award(player, arc, WantedCause.ARREST);
	}

	@Test
	@DisplayName("quitting between LOW and MONITOR drops the pending line")
	void quitDropsPending() {
		arcs.start(playerId, WantedCause.CRIME, 3);
		result(75, Reason.PAID);

		listener.onEndAward(end(WantedCause.EVASION));
		listener.onQuit(new PlayerQuitEvent(player, "bye"));
		listener.onEndSend(end(WantedCause.EVASION));

		verify(player, never()).sendMessage(anyString());
	}
}
