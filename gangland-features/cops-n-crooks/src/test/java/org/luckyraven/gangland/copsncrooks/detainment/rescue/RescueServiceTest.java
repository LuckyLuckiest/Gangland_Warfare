package org.luckyraven.gangland.copsncrooks.detainment.rescue;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseReason;
import org.luckyraven.gangland.copsncrooks.detainment.settings.RescueSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.keystone.util.ActionBarManager;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * P-RESC-1..5: a crew mate holding right-click on the guarding cop frees a handcuffed player. Time is the injected
 * clock; no sleeps.
 */
@DisplayName("RescueService")
@SuppressWarnings("unchecked")
class RescueServiceTest {

	private final UUID rescuerId  = UUID.randomUUID();
	private final UUID prisonerId = UUID.randomUUID();

	private final AtomicReference<RescueSettings> settings = new AtomicReference<>(new RescueSettings(true, 3, 1));
	private final long[]                          now      = {0L};

	private CopManager                     copManager;
	private DetainmentService              detainment;
	private CuffLockRegistry               locks;
	private ReleasePipeline                pipeline;
	private GangMembership                 gangs;
	private WantedStars                    stars;
	private UserManager<Player>            users;
	private WantedMessages                 messages;
	private Player                         rescuer;
	private Player                         prisoner;
	private User<Player>                   rescuerUser;
	private User<Player>                   prisonerUser;
	private CopNpc                         cop;
	private Entity                         copEntity;
	private RescueService                  service;
	private MockedStatic<ActionBarManager> bar;

	@BeforeEach
	void setUp() {
		copManager   = mock(CopManager.class);
		detainment   = mock(DetainmentService.class);
		locks        = mock(CuffLockRegistry.class);
		pipeline     = mock(ReleasePipeline.class);
		gangs        = mock(GangMembership.class);
		stars        = mock(WantedStars.class);
		users        = mock(UserManager.class);
		messages     = mock(WantedMessages.class);
		rescuer      = mock(Player.class);
		prisoner     = mock(Player.class);
		rescuerUser  = mock(User.class);
		prisonerUser = mock(User.class);
		cop          = mock(CopNpc.class);
		copEntity    = mock(Entity.class);

		when(rescuer.getUniqueId()).thenReturn(rescuerId);
		when(rescuer.getName()).thenReturn("Rex");
		when(prisoner.getUniqueId()).thenReturn(prisonerId);
		when(prisoner.getName()).thenReturn("Alex");
		when(copManager.findCopByEntity(copEntity)).thenReturn(cop);
		when(cop.isValid()).thenReturn(true);
		when(cop.getCurrentState()).thenReturn(CopState.GUARDING);
		when(cop.getGuardedPlayerId()).thenReturn(prisonerId);
		when(detainment.isHandcuffed(prisoner)).thenReturn(true);
		when(detainment.isRestrained(rescuer)).thenReturn(false);
		when(gangs.alliedOrSame(rescuerId, prisonerId)).thenReturn(true);
		when(users.getUser(rescuer)).thenReturn(rescuerUser);
		when(users.getUser(prisoner)).thenReturn(prisonerUser);
		when(messages.format(any(), any())).thenReturn("msg");

		bar = mockStatic(ActionBarManager.class);

		service = newService(id -> id.equals(prisonerId) ? prisoner : null);
	}

	@AfterEach
	void tearDown() {
		bar.close();
	}

	private RescueService newService(java.util.function.Function<UUID, Player> online) {
		return new RescueService(settings::get, copManager, detainment, locks, pipeline, gangs, stars, users, messages,
		                         mock(JavaPlugin.class), online, () -> now[0]);
	}

	private void clickAt(long ms) {
		now[0] = ms;
		service.onClick(rescuer, copEntity);
	}

	private void clickEvery200(long fromMs, long toMs) {
		for (long t = fromMs; t <= toMs; t += 200) clickAt(t);
	}

	@Test
	@DisplayName("holding 3 s with clicks every 200 ms releases the prisoner as RESCUED exactly once")
	void crewHold3s_frees() {
		clickEvery200(0, 3000);

		verify(pipeline, times(1)).release(prisoner, ReleaseReason.RESCUED);
	}

	@Test
	@DisplayName("2.8 s of holding frees nobody")
	void holdShorterThanThreeSeconds_doesNotFree() {
		clickEvery200(0, 2800);

		verify(pipeline, never()).release(any(), any());
	}

	@Test
	@DisplayName("Hold_Seconds comes from the live settings")
	void holdSecondsFromSettings() {
		settings.set(new RescueSettings(true, 1, 1));

		clickEvery200(0, 1000);

		verify(pipeline).release(prisoner, ReleaseReason.RESCUED);
	}

	@Test
	@DisplayName("a gap over 1 s resets the hold: new hold at 2500 frees at 5500, not before")
	void gapOverOneSecond_resets() {
		clickEvery200(0, 1000);
		clickEvery200(2500, 5300);

		verify(pipeline, never()).release(any(), any());

		clickAt(5500);

		verify(pipeline, times(1)).release(prisoner, ReleaseReason.RESCUED);
	}

	@Test
	@DisplayName("a gap of exactly 1 s still counts as holding")
	void gapOfExactlyOneSecond_continues() {
		clickAt(0);
		clickAt(1000);
		clickAt(2000);
		clickAt(3000);

		verify(pipeline).release(prisoner, ReleaseReason.RESCUED);
	}

	@Test
	@DisplayName("clicking on after the rescue does not release twice")
	void clicksAfterFree_doNotReleaseAgain() {
		clickEvery200(0, 3000);
		clickAt(3200);
		clickAt(3400);

		verify(pipeline, times(1)).release(prisoner, ReleaseReason.RESCUED);
	}

	@Test
	@DisplayName("both the rescuer and the prisoner gain Rescue.Stars through WantedStars with cause CRIME")
	void bothGainStars() {
		settings.set(new RescueSettings(true, 3, 2));

		clickEvery200(0, 3000);

		verify(stars).raise(rescuerUser, 2, WantedCause.CRIME);
		verify(stars).raise(prisonerUser, 2, WantedCause.CRIME);
	}

	@Test
	@DisplayName("Stars 0 raises nobody but still frees")
	void zeroStars_raiseNobody() {
		settings.set(new RescueSettings(true, 3, 0));

		clickEvery200(0, 3000);

		verify(stars, never()).raise(any(), anyInt(), any());
		verify(pipeline).release(prisoner, ReleaseReason.RESCUED);
	}

	@Test
	@DisplayName("the cuff lock is force-released before the release, and the guard leaves guard duty")
	void lockForceReleased_andGuardRelieved() {
		clickEvery200(0, 3000);

		InOrder order = inOrder(locks, pipeline);
		order.verify(locks).forceRelease(prisonerId);
		order.verify(pipeline).release(prisoner, ReleaseReason.RESCUED);
		verify(cop).setGuardedPlayerId(null);
		verify(cop).transitionTo(CopState.PURSUING);
	}

	@Test
	@DisplayName("a guard that already left GUARDING is not pushed into PURSUING")
	void guardNoLongerGuardingAtTheEnd_isLeftAlone() {
		clickEvery200(0, 2800);
		when(cop.getCurrentState()).thenReturn(CopState.COMBAT);
		clickAt(3000);

		verify(cop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("a guard that leaves GUARDING during the release is not pushed into PURSUING (free() re-check)")
	void guardLeavesGuardingDuringRelease_isLeftAlone() {
		java.util.concurrent.atomic.AtomicBoolean released = new java.util.concurrent.atomic.AtomicBoolean();
		when(cop.getCurrentState()).thenAnswer(i -> released.get() ? CopState.RETURNING : CopState.GUARDING);
		org.mockito.Mockito.doAnswer(i -> {
			released.set(true);
			return null;
		}).when(pipeline).release(any(), any());

		clickEvery200(0, 3000);

		verify(pipeline).release(prisoner, ReleaseReason.RESCUED);
		verify(cop, never()).transitionTo(any());
	}

	@Test
	@DisplayName("a user the manager does not know is skipped for stars; the other side still gains and the rescue completes")
	void unknownUser_skippedForStars() {
		when(users.getUser(rescuer)).thenReturn(null);

		clickEvery200(0, 3000);

		verify(pipeline).release(prisoner, ReleaseReason.RESCUED);
		verify(stars).raise(prisonerUser, 1, WantedCause.CRIME);
		verify(stars, times(1)).raise(any(), anyInt(), any());
	}

	@Test
	@DisplayName("the rescuer sees RESCUE_FREED and the prisoner RESCUE_FREED_BY")
	void messagesBoth() {
		clickEvery200(0, 3000);

		verify(messages).format(WantedMessages.Key.RESCUE_FREED, Map.of("player", "Alex"));
		verify(messages).format(WantedMessages.Key.RESCUE_FREED_BY, Map.of("player", "Rex"));
		verify(rescuer).sendMessage("msg");
		verify(prisoner).sendMessage("msg");
	}

	@Test
	@DisplayName("the action bar counts down in whole seconds, rounded up")
	void progressActionBar_countsDownCeil() {
		clickAt(0);
		clickAt(200);
		clickAt(1000);
		clickAt(2000);

		verify(messages, times(2)).format(WantedMessages.Key.RESCUE_PROGRESS, Map.of("player", "Alex", "seconds", "3"));
		verify(messages).format(WantedMessages.Key.RESCUE_PROGRESS, Map.of("player", "Alex", "seconds", "2"));
		verify(messages).format(WantedMessages.Key.RESCUE_PROGRESS, Map.of("player", "Alex", "seconds", "1"));
		bar.verify(() -> ActionBarManager.send(any(JavaPlugin.class), eq(rescuer), eq("msg"), anyLong()), times(4));
	}

	@Test
	@DisplayName("a stranger (not same gang, not allied) is ignored: no hold, no message")
	void nonCrew_ignored() {
		when(gangs.alliedOrSame(rescuerId, prisonerId)).thenReturn(false);

		clickEvery200(0, 3000);

		verify(pipeline, never()).release(any(), any());
		verify(messages, never()).format(any(), any());
		verify(rescuer, never()).sendMessage(any(String.class));
		bar.verifyNoInteractions();
	}

	@Test
	@DisplayName("the prisoner clicking his own guard is left to the bribe listener")
	void prisonerClickingOwnGuard_ignored() {
		when(detainment.isRestrained(prisoner)).thenReturn(true);
		when(gangs.alliedOrSame(prisonerId, prisonerId)).thenReturn(true);

		for (long t = 0; t <= 3000; t += 200) {
			now[0] = t;
			service.onClick(prisoner, copEntity);
		}

		verify(pipeline, never()).release(any(), any());
		bar.verifyNoInteractions();
	}

	@Test
	@DisplayName("a JAILED prisoner (not handcuffed) cannot be rescued")
	void prisonerJailed_ignored() {
		when(detainment.isHandcuffed(prisoner)).thenReturn(false);

		clickEvery200(0, 3000);

		verify(pipeline, never()).release(any(), any());
		bar.verifyNoInteractions();
	}

	@Test
	@DisplayName("an offline prisoner is ignored")
	void prisonerOffline_ignored() {
		service = newService(id -> null);

		clickEvery200(0, 3000);

		verify(pipeline, never()).release(any(), any());
	}

	@Test
	@DisplayName("a cop that is not guarding is ignored")
	void copNotGuarding_ignored() {
		when(cop.getCurrentState()).thenReturn(CopState.PURSUING);

		clickEvery200(0, 3000);

		verify(pipeline, never()).release(any(), any());
		bar.verifyNoInteractions();
	}

	@Test
	@DisplayName("a guard with no guarded player is ignored")
	void guardWithoutPrisoner_ignored() {
		when(cop.getGuardedPlayerId()).thenReturn(null);

		clickEvery200(0, 3000);

		verify(pipeline, never()).release(any(), any());
	}

	@Test
	@DisplayName("an invalid (dead or despawned) cop is ignored")
	void invalidCop_ignored() {
		when(cop.isValid()).thenReturn(false);

		clickEvery200(0, 3000);

		verify(pipeline, never()).release(any(), any());
	}

	@Test
	@DisplayName("an entity that is no cop is ignored")
	void notACop_ignored() {
		when(copManager.findCopByEntity(copEntity)).thenReturn(null);

		clickEvery200(0, 3000);

		verifyNoInteractions(pipeline, detainment);
	}

	@Test
	@DisplayName("a rescuer who is himself handcuffed or jailed is ignored")
	void restrainedRescuer_ignored() {
		when(detainment.isRestrained(rescuer)).thenReturn(true);

		clickEvery200(0, 3000);

		verify(pipeline, never()).release(any(), any());
		bar.verifyNoInteractions();
	}

	@Test
	@DisplayName("Rescue.Enabled false is a no-op")
	void disabled_noop() {
		settings.set(new RescueSettings(false, 3, 1));

		clickEvery200(0, 3000);

		verifyNoInteractions(copManager, pipeline, locks);
		bar.verifyNoInteractions();
	}

	@Test
	@DisplayName("the prisoner leaving HANDCUFFED mid-hold drops the hold; a later hold starts from scratch")
	void prisonerLeavesHandcuffed_dropsTheHold() {
		clickEvery200(0, 1000);
		when(detainment.isHandcuffed(prisoner)).thenReturn(false);
		clickAt(1200);
		when(detainment.isHandcuffed(prisoner)).thenReturn(true);
		clickEvery200(1400, 4200);

		verify(pipeline, never()).release(any(), any());

		clickAt(4400);

		verify(pipeline, times(1)).release(prisoner, ReleaseReason.RESCUED);
	}

	@Test
	@DisplayName("the guard dying mid-hold drops the hold")
	void guardDies_dropsTheHold() {
		clickEvery200(0, 1000);
		when(cop.isValid()).thenReturn(false);
		clickAt(1200);
		when(cop.isValid()).thenReturn(true);
		clickEvery200(1400, 4200);

		verify(pipeline, never()).release(any(), any());
	}

	@Test
	@DisplayName("switching to a cop guarding someone else starts a new hold")
	void switchingGuards_restartsTheHold() {
		CopNpc otherCop    = mock(CopNpc.class);
		Entity otherEntity = mock(Entity.class);
		UUID   otherId     = UUID.randomUUID();
		Player other       = mock(Player.class);
		when(other.getUniqueId()).thenReturn(otherId);
		when(other.getName()).thenReturn("Bo");
		when(copManager.findCopByEntity(otherEntity)).thenReturn(otherCop);
		when(otherCop.isValid()).thenReturn(true);
		when(otherCop.getCurrentState()).thenReturn(CopState.GUARDING);
		when(otherCop.getGuardedPlayerId()).thenReturn(otherId);
		when(detainment.isHandcuffed(other)).thenReturn(true);
		when(gangs.alliedOrSame(rescuerId, otherId)).thenReturn(true);
		service = newService(id -> id.equals(prisonerId) ? prisoner : id.equals(otherId) ? other : null);

		clickEvery200(0, 2000);
		now[0] = 2200;
		service.onClick(rescuer, otherEntity);
		clickAt(3000);

		verify(pipeline, never()).release(any(), any());
	}
}
