package org.luckyraven.gangland.lootchest.listener;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.gangland.lootchest.config.CrackingAlarmSettings;
import org.luckyraven.gangland.lootchest.config.LootChestSettingsProvider;
import org.luckyraven.gangland.lootchest.data.CrackingSession;
import org.luckyraven.gangland.lootchest.data.LootChestData;
import org.luckyraven.gangland.lootchest.data.LootTier;
import org.luckyraven.gangland.lootchest.events.cracking.LootChestCrackingEndEvent;
import org.luckyraven.gangland.lootchest.events.cracking.LootChestCrackingFailureEvent;
import org.luckyraven.gangland.lootchest.events.cracking.LootChestCrackingSuccessEvent;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P-CRIME-3 (0.20.0): succeeding or failing to crack a {@code police_armory} or {@code military_vault} chest rings an
 * alarm at the chest and commits {@code Safe_Cracking} (once per cracking session); other tiers, a disabled alarm and
 * cancelled events do nothing.
 */
@DisplayName("CrackingAlarmListener")
class CrackingAlarmListenerTest {

	private final World                     world    = mock(World.class);
	private final Player                    player   = mock(Player.class);
	private final CrimeService              crimes   = mock(CrimeService.class);
	private final LootChestSettingsProvider settings = mock(LootChestSettingsProvider.class);
	private final LootChestData             chest    = mock(LootChestData.class);
	private final CrackingSession           session  = mock(CrackingSession.class);

	private Location              chestAt;
	private int                   rings;
	private Location              ringAt;
	private String                ringSound;
	private CrackingAlarmListener listener;

	@BeforeEach
	void setUp() {
		chestAt = new Location(world, 100, 64, 100);

		when(settings.getCrackingAlarm()).thenReturn(
				new CrackingAlarmSettings(true, Set.of("police_armory", "military_vault"), "BLOCK_BELL_USE"));
		when(session.getPlayer()).thenReturn(player);
		when(session.getChestData()).thenReturn(chest);
		when(session.getSessionId()).thenReturn(UUID.randomUUID());
		tier("police_armory");
		when(chest.getLocation()).thenReturn(chestAt);

		listener = new CrackingAlarmListener(settings, crimes, (at, sound) -> {
			rings++;
			ringAt    = at;
			ringSound = sound;
		});
	}

	private void tier(String id) {
		when(session.getTier()).thenReturn(new LootTier(id, id, 1, LootTier.UnlockRequirement.NONE));
	}

	private void success() {
		listener.onSuccess(new LootChestCrackingSuccessEvent(chest, session));
	}

	private void failure() {
		listener.onFailure(new LootChestCrackingFailureEvent(chest, session));
	}

	private void verifyNoCrime() {
		verify(crimes, never()).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("cracking a police_armory chest commits an unseen Safe_Cracking with one witness and rings the bell at the chest")
	void alarmTierSuccessCommitsSafeCracking() {
		success();

		verify(crimes, times(1)).commit(player, Crimes.SAFE_CRACKING, chestAt, false, 1);
		assertEquals(1, rings);
		assertEquals(chestAt, ringAt);
		assertEquals("BLOCK_BELL_USE", ringSound);
	}

	@Test
	@DisplayName("failing to crack an alarm-tier chest rings and commits too")
	void failureAlsoAlarms() {
		failure();

		verify(crimes).commit(player, Crimes.SAFE_CRACKING, chestAt, false, 1);
		assertEquals(1, rings);
	}

	@Test
	@DisplayName("military_vault is an alarm tier; the match ignores case")
	void militaryVaultAndCase() {
		tier("MILITARY_VAULT");

		success();

		verify(crimes).commit(player, Crimes.SAFE_CRACKING, chestAt, false, 1);
	}

	@Test
	@DisplayName("a tier that is not listed (common) does nothing")
	void otherTierIgnored() {
		tier("common");

		success();
		failure();

		verifyNoCrime();
		assertEquals(0, rings);
	}

	@Test
	@DisplayName("a session without a tier does nothing")
	void nullTierIgnored() {
		when(session.getTier()).thenReturn(null);

		success();

		verifyNoCrime();
	}

	@Test
	@DisplayName("failure then success in the same session commit and ring once")
	void secondEventSameSessionIgnored() {
		failure();
		success();

		verify(crimes, times(1)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
		assertEquals(1, rings);
	}

	@Test
	@DisplayName("a different session rings again")
	void otherSessionRings() {
		success();
		when(session.getSessionId()).thenReturn(UUID.randomUUID());

		success();

		verify(crimes, times(2)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("the end event forgets the session so the alarm set does not grow")
	void endEventForgetsSession() {
		success();
		listener.onEnd(new LootChestCrackingEndEvent(chest, session));

		success();

		verify(crimes, times(2)).commit(any(), anyString(), any(), anyBoolean(), anyInt());
	}

	@Test
	@DisplayName("Cracking_Alarm.Enable false does nothing")
	void disabledDoesNothing() {
		when(settings.getCrackingAlarm()).thenReturn(
				new CrackingAlarmSettings(false, Set.of("police_armory"), "BLOCK_BELL_USE"));

		success();

		verifyNoCrime();
		assertEquals(0, rings);
	}

	@Test
	@DisplayName("a chest with no world still commits the crime but plays no sound")
	void nullWorldSkipsSoundOnly() {
		Location noWorld = new Location(null, 1, 2, 3);
		when(chest.getLocation()).thenReturn(noWorld);

		success();

		verify(crimes).commit(player, Crimes.SAFE_CRACKING, noWorld, false, 1);
		assertEquals(0, rings);
	}

	@Test
	@DisplayName("a chest with no location commits nothing and does not throw")
	void nullLocationIgnored() {
		when(chest.getLocation()).thenReturn(null);

		success();

		verifyNoCrime();
	}

	@Test
	@DisplayName("the success and failure handlers skip cancelled events and run at MONITOR")
	void handlersSkipCancelledAtMonitor() throws Exception {
		EventHandler success = CrackingAlarmListener.class.getMethod("onSuccess", LootChestCrackingSuccessEvent.class)
		                                                  .getAnnotation(EventHandler.class);
		EventHandler failure = CrackingAlarmListener.class.getMethod("onFailure", LootChestCrackingFailureEvent.class)
		                                                  .getAnnotation(EventHandler.class);

		assertTrue(success.ignoreCancelled());
		assertTrue(failure.ignoreCancelled());
		assertEquals(EventPriority.MONITOR, success.priority());
		assertEquals(EventPriority.MONITOR, failure.priority());
	}
}
