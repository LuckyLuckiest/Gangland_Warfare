package org.luckyraven.gangland.copsncrooks.npc.police;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio.RadioCall;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.npc.radio.RadioSettings;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Radio responders (GL-3): a contact, a man down or a backup call pulls up to {@code Responder_Max} nearby cops that
 * are walking home, idle or chasing a civilian into the calling squad. The calls are only queued by the radio
 * listener and answered after the AI tick's cop loop, and none of it depends on a human hearing the radio.
 */
@DisplayName("CopManager - radio responders")
class CopRadioRespondersTest {

	private CopManagerFixture fx;
	private CopManager        manager;
	private Player            player;
	private CopGroup          group;

	@BeforeEach
	void setUp() {
		fx      = new CopManagerFixture();
		manager = fx.manager;
		player  = fx.player(0, 0);
		manager.onWantedStart(player, CopManagerFixture.wanted(2));
		group = manager.groupFor(player.getUniqueId());
		when(fx.radio.huntedOf(group, group.getSquad())).thenReturn(player);
	}

	@AfterEach
	void tearDown() {
		fx.close();
	}

	@Test
	@DisplayName("a returning cop 20 blocks from a contact rejoins the hunt and answers Responding")
	void nearbyReturningCopHearsContact_rejoinsPursuing() {
		CopNpc cop = fx.cop(CopState.RETURNING, 20, 0);
		join(cop);

		call();

		assertEquals(CopState.PURSUING, cop.getCurrentState());
		assertEquals(player.getUniqueId(), cop.getTargetPlayerId());
		verify(fx.radio).respond(group, group.getSquad(), cop);
	}

	@Test
	@DisplayName("a cop beyond Range stays on its way")
	void copBeyondRange_ignores() {
		CopNpc cop = fx.cop(CopState.RETURNING, 40, 0);
		join(cop);

		call();

		assertEquals(CopState.RETURNING, cop.getCurrentState());
		verify(fx.radio, never()).respond(any(), any(), any());
	}

	@Test
	@DisplayName("another group's cop chasing a civilian changes groups and drops the civilian")
	void otherGroupsCopChasingCivilian_transfersGroups_andClearsEntityTarget() {
		Player other = fx.player(200, 200);
		manager.onWantedStart(other, CopManagerFixture.wanted(1));
		CopGroup otherGroup = manager.groupFor(other.getUniqueId());
		CopNpc   cop        = fx.cop(CopState.PURSUING, 10, 0);
		otherGroup.add(cop);
		cop.setTargetEntity(mock(LivingEntity.class));

		call();

		assertSame(group, cop.getGroup());
		assertTrue(group.getCops().contains(cop));
		assertFalse(otherGroup.getCops().contains(cop));
		assertNull(cop.getTargetEntity());
		assertEquals(player.getUniqueId(), cop.getTargetPlayerId());
		assertEquals(CopState.PURSUING, cop.getCurrentState());
	}

	@Test
	@DisplayName("a cuffing or guarding cop is never pulled")
	void cuffingOrGuardingCop_neverPulled() {
		CopNpc cuffing  = fx.cop(CopState.CUFFING, 5, 0);
		CopNpc guarding = fx.cop(CopState.GUARDING, 6, 0);
		join(cuffing);
		join(guarding);

		call();

		assertEquals(CopState.CUFFING, cuffing.getCurrentState());
		assertEquals(CopState.GUARDING, guarding.getCurrentState());
		verify(fx.radio, never()).respond(any(), any(), any());
	}

	@Test
	@DisplayName("a cop hunting a player (its own wanted player or an attacker) is never pulled")
	void copHuntingAPlayer_neverPulled() {
		Player other = fx.player(200, 200);
		manager.onWantedStart(other, CopManagerFixture.wanted(1));
		CopNpc cop = fx.cop(CopState.COMBAT, 10, 0);
		manager.groupFor(other.getUniqueId()).add(cop);
		cop.setTargetPlayerId(other.getUniqueId());

		call();

		assertEquals(other.getUniqueId(), cop.getTargetPlayerId());
		verify(fx.radio, never()).respond(any(), any(), any());
	}

	@Test
	@DisplayName("responders are the nearest, capped by Responder_Max and by the room under Max_Per_Player")
	void respondersCappedByResponderMaxAndMaxPerPlayer() {
		CopNpc far    = fx.cop(CopState.RETURNING, 25, 0);
		CopNpc near   = fx.cop(CopState.RETURNING, 5, 0);
		CopNpc middle = fx.cop(CopState.RETURNING, 15, 0);
		join(far);
		join(near);
		join(middle);

		call();

		assertEquals(CopState.PURSUING, near.getCurrentState());
		assertEquals(CopState.PURSUING, middle.getCurrentState());
		assertEquals(CopState.RETURNING, far.getCurrentState(), "Responder_Max is 2");

		near.transitionTo(CopState.RETURNING);
		middle.transitionTo(CopState.RETURNING);
		when(fx.provider.getMaxCopsPerPlayer()).thenReturn(1);

		call();

		assertEquals(CopState.PURSUING, near.getCurrentState());
		assertEquals(CopState.RETURNING, middle.getCurrentState(), "room for one under Max_Per_Player");
	}

	@Test
	@DisplayName("Responder_Max 0 turns responders off")
	void responderMaxZero_noPull() {
		RadioSettings d = CopRadioSettings.withResponderMax(0);
		when(fx.provider.getRadioSettings()).thenReturn(d);
		CopNpc cop = fx.cop(CopState.RETURNING, 5, 0);
		join(cop);

		call();

		assertEquals(CopState.RETURNING, cop.getCurrentState());
	}

	@Test
	@DisplayName("nobody is sent after a suspect already restrained")
	void restrainedHunted_noPull() {
		when(fx.detainment.isRestrained(player)).thenReturn(true);
		CopNpc cop = fx.cop(CopState.RETURNING, 5, 0);
		join(cop);

		call();

		assertEquals(CopState.RETURNING, cop.getCurrentState());
	}

	@Test
	@DisplayName("the listener only queues the call; the AI tick answers it after its cop loop")
	void listenerOnlyQueues_drainRunsAfterLoop() {
		CopNpc cop = fx.cop(CopState.RETURNING, 5, 0);
		join(cop);

		fx.callSinks.get(group).accept(new RadioCall(group, group.getSquad(), new Location(fx.world, 0, 64, 0)));
		assertEquals(CopState.RETURNING, cop.getCurrentState());

		manager.aiTick(player.getUniqueId());

		assertEquals(CopState.PURSUING, cop.getCurrentState());
	}

	@Test
	@DisplayName("an attacker squad's call sends responders after the attacker, in combat")
	void attackerCall_respondersFightTheAttacker() {
		Player   attacker = fx.player(3, 3);
		var      squad    = group.attackerSquad(attacker.getUniqueId(), attacker.getLocation());
		when(fx.radio.huntedOf(group, squad)).thenReturn(attacker);
		CopNpc cop = fx.cop(CopState.RETURNING, 5, 0);
		join(cop);

		fx.callSinks.get(group).accept(new RadioCall(group, squad, new Location(fx.world, 0, 64, 0)));
		manager.drainRadioCalls();

		assertEquals(CopState.COMBAT, cop.getCurrentState());
		assertEquals(attacker.getUniqueId(), cop.getTargetPlayerId());
		assertTrue(cop.isCombatForced());
	}

	/** Adds {@code cop} to the calling group; a returning cop has already left the squad (ReturningBehavior.onEnter). */
	private void join(CopNpc cop) {
		group.add(cop);
		if (cop.getCurrentState() == CopState.RETURNING) group.getSquad().remove(cop);
	}

	private void call() {
		fx.callSinks.get(group).accept(new RadioCall(group, group.getSquad(), new Location(fx.world, 0, 64, 0)));
		manager.drainRadioCalls();
	}

	/** Cop radio defaults with a different {@code Responder_Max}. */
	private static final class CopRadioSettings {

		static RadioSettings withResponderMax(int max) {
			RadioSettings d = org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider.COP_RADIO_DEFAULTS;
			return new RadioSettings(d.enabled(), d.range(), d.targetRange(), d.squadGapMs(), d.playerGapMs(),
			                         d.ackDelayTicks(), max, Map.copyOf(d.cooldownMs()), Set.copyOf(d.priority()),
			                         d.soundName(), d.volume(), d.pitch());
		}
	}
}
