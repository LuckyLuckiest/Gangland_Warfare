package org.luckyraven.gangland.copsncrooks.listener.police;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.detainment.ArrestOrigin;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.transit.TransitService;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.integration.detainment.DetainmentSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CuffLockRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.util.ChatUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 0.21.0 busted lane: {@link BustedListener} cancels a lethal hit and cuffs instead of letting the core downed flow
 * start. Ordering contract (not testable here, the module cannot see gangland-impl): the core
 * {@code CustomPlayerDeathListener.onEntityDamage} is HIGHEST + ignoreCancelled, so it skips the cancelled hit, and
 * {@code PlayerDeathListener} bills only on {@link PlayerDownedEvent}, which is never fired for a bust.
 */
@DisplayName("BustedListener - lethal hit next to a hunting cop is an arrest")
class BustedListenerTest {

	private static final double RADIUS = 3.0;

	private final UUID victimId = UUID.randomUUID();

	private BukkitStatics            bukkit;
	private MockedStatic<NpcSupport> npcSupport;
	private MockedStatic<ChatUtil>   chat;
	private CopManager               copManager;
	private CopLoader                copLoader;
	private DetainmentService        detainment;
	private DetainmentSettings       settings;
	private CuffLockRegistry         locks;
	private WantedClearContract      wanted;
	private WantedMessages           messages;
	private Player                   player;
	private List<Entity>             nearby;
	private BustedListener           listener;
	private boolean                  savedRespawn;

	@BeforeEach
	void setUp() throws ReflectiveOperationException {
		bukkit     = BukkitStatics.install();
		npcSupport = mockStatic(NpcSupport.class);
		npcSupport.when(() -> NpcSupport.isNpc(any())).thenReturn(false);
		chat = mockStatic(ChatUtil.class);

		copManager = mock(CopManager.class);
		copLoader  = mock(CopLoader.class);
		CopConfigProvider provider = mock(CopConfigProvider.class);
		when(provider.getMaxCuffAttempts()).thenReturn(4);
		when(copLoader.getLoadedProvider()).thenReturn(provider);
		detainment = mock(DetainmentService.class);
		settings   = mock(DetainmentSettings.class);
		when(settings.isBustedEnabled()).thenReturn(true);
		locks  = new CuffLockRegistry();
		wanted = mock(WantedClearContract.class);
		when(wanted.getWantedLevel(victimId)).thenReturn(2);
		messages = mock(WantedMessages.class);
		when(messages.format(eq(WantedMessages.Key.BUSTED_TITLE), anyMap())).thenReturn("T");
		when(messages.format(eq(WantedMessages.Key.BUSTED_SUBTITLE), anyMap())).thenReturn("S");

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(victimId);
		when(player.getHealth()).thenReturn(4.0);
		nearby = new ArrayList<>();
		when(player.getNearbyEntities(6.0, 6.0, 6.0)).thenReturn(nearby);

		DownedPlayerRegistry.remove(victimId);
		savedRespawn = Settings.isRespawnEnabled();
		respawn(true);

		listener = new BustedListener(copManager, copLoader, detainment, settings, locks, wanted, messages);
	}

	@AfterEach
	void tearDown() throws ReflectiveOperationException {
		respawn(savedRespawn);
		DownedPlayerRegistry.remove(victimId);
		chat.close();
		npcSupport.close();
		bukkit.close();
	}

	private static void respawn(boolean value) throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("respawnEnabled");
		field.setAccessible(true);
		field.setBoolean(null, value);
	}

	/** A cop {@code distance} blocks away, registered as nearby and findable; {@code groupTarget} null = groupless. */
	private CopNpc cop(CopState state, double distance, UUID groupTarget) {
		CopNpc        cop  = mock(CopNpc.class);
		Entity        body = mock(Entity.class);
		NPC           npc  = mock(NPC.class);
		CopTierConfig tier = mock(CopTierConfig.class);
		when(npc.getUniqueId()).thenReturn(UUID.randomUUID());
		when(cop.getNpc()).thenReturn(npc);
		when(cop.isValid()).thenReturn(true);
		when(cop.getCurrentState()).thenReturn(state);
		when(cop.getGroup()).thenReturn(groupTarget == null ? null : new CopGroup(groupTarget));
		when(cop.distanceTo(player)).thenReturn(distance);
		when(tier.cuffRadius()).thenReturn(RADIUS);
		when(cop.getTierConfig()).thenReturn(tier);
		when(copManager.findCopByEntity(body)).thenReturn(cop);
		nearby.add(body);
		return cop;
	}

	/** A PURSUING cop of the victim's own squad. */
	private CopNpc hunter(double distance) {
		return cop(CopState.PURSUING, distance, victimId);
	}

	private EntityDamageEvent hit(double damage, DamageCause cause) {
		EntityDamageEvent event = mock(EntityDamageEvent.class);
		when(event.getEntity()).thenReturn(player);
		when(event.getFinalDamage()).thenReturn(damage);
		when(event.getCause()).thenReturn(cause);
		return event;
	}

	private List<Event> firedEvents() {
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		try {
			verify(bukkit.pluginManager(), atLeastOnce()).callEvent(captor.capture());
		} catch (AssertionError none) {
			return List.of();
		}
		return new ArrayList<>(captor.getAllValues());
	}

	private CuffedEvent firedCuffedEvent() {
		return (CuffedEvent) firedEvents().stream().filter(CuffedEvent.class::isInstance).findFirst().orElseThrow();
	}

	private void assertNotBusted(EntityDamageEvent event) {
		listener.onDamage(event);
		verify(event, never()).setCancelled(true);
		assertTrue(firedEvents().stream().noneMatch(CuffedEvent.class::isInstance));
	}

	@Test
	@DisplayName("lethal hit with a hunting cop in its Cuff_Radius: cancelled, CuffedEvent BUSTED_DOWNED, cop guards")
	void lethalHitCopInRadius_cancelsAndCuffsBustedDowned() {
		CopNpc            cop   = hunter(2.0);
		EntityDamageEvent event = hit(9.0, DamageCause.ENTITY_ATTACK);

		listener.onDamage(event);

		verify(event).setCancelled(true);
		CuffedEvent cuffed = firedCuffedEvent();
		assertEquals(ArrestOrigin.BUSTED_DOWNED, cuffed.getOrigin());
		assertEquals(player, cuffed.getTarget());
		assertEquals(cop, cuffed.getCop());
		assertEquals(RADIUS, cuffed.getCuffRadius());
		assertEquals(4, cuffed.getMaxAttempts());
		verify(cop).setGuardedPlayerId(victimId);
		verify(cop).transitionTo(CopState.GUARDING);
		assertEquals(cop.getNpc().getUniqueId(), locks.getOwner(victimId));
	}

	@Test
	@DisplayName("COMBAT cops bust too (allowlist is PURSUING and COMBAT)")
	void combatCop_busts() {
		cop(CopState.COMBAT, 1.0, victimId);
		EntityDamageEvent event = hit(9.0, DamageCause.ENTITY_ATTACK);

		listener.onDamage(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("exactly at the cop's Cuff_Radius still busts")
	void radiusBoundary_inside() {
		hunter(RADIUS);
		EntityDamageEvent event = hit(9.0, DamageCause.ENTITY_ATTACK);

		listener.onDamage(event);

		verify(event).setCancelled(true);
	}

	@Test
	@DisplayName("just past the cop's Cuff_Radius does not bust")
	void radiusBoundary_outside() {
		hunter(RADIUS + 0.01);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("health 20, damage 5: not lethal, ignored")
	void nonLethalHit_ignored() {
		when(player.getHealth()).thenReturn(20.0);
		hunter(1.0);
		assertNotBusted(hit(5.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("a hit leaving exactly 0 health is lethal")
	void exactKillIsLethal() {
		hunter(1.0);
		EntityDamageEvent exact = hit(4.0, DamageCause.ENTITY_ATTACK);

		listener.onDamage(exact);

		verify(exact).setCancelled(true);
	}

	@Test
	@DisplayName("a hit leaving a sliver of health is not lethal")
	void sliverLeft_notLethal() {
		hunter(1.0);
		assertNotBusted(hit(3.99, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("cop at 6 blocks with Cuff_Radius 3: nothing, the downed flow continues")
	void noCopInRadius_downedFlowContinues() {
		hunter(6.0);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("no cop nearby at all (also the Citizens-absent case, no cop NPCs exist): nothing")
	void noCopsAtAll_ignored() {
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("zero stars: ignored")
	void zeroStars_ignored() {
		hunter(1.0);
		when(wanted.getWantedLevel(victimId)).thenReturn(0);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("already handcuffed or jailed: ignored, the 0.19 death-commit path stays")
	void alreadyRestrained_ignored() {
		hunter(1.0);
		when(detainment.isRestrained(player)).thenReturn(true);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("already downed: ignored (core absorbs the hit)")
	void alreadyDowned_ignored() {
		hunter(1.0);
		DownedPlayerRegistry.add(victimId);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("Respawn (downed mode) disabled: ignored, vanilla death as 0.19")
	void respawnDisabled_ignored() throws ReflectiveOperationException {
		hunter(1.0);
		respawn(false);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("Detainment.Busted.Enabled false: ignored")
	void disabledSwitch_ignored() {
		hunter(1.0);
		when(settings.isBustedEnabled()).thenReturn(false);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("an NPC victim (a Citizens PLAYER-type NPC): ignored")
	void npcVictim_ignored() {
		hunter(1.0);
		npcSupport.when(() -> NpcSupport.isNpc(player)).thenReturn(true);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("a non-player victim: ignored")
	void nonPlayerVictim_ignored() {
		EntityDamageEvent event = mock(EntityDamageEvent.class);
		when(event.getEntity()).thenReturn(mock(Entity.class));

		listener.onDamage(event);

		verify(event, never()).setCancelled(true);
	}

	@Test
	@DisplayName("an invalid (dying or despawned) cop in range never busts")
	void deadCopIgnored() {
		CopNpc cop = hunter(1.0);
		when(cop.isValid()).thenReturn(false);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("VOID damage: lethal, cop in range, still not a bust (falling out of the world, /kill on 1.16-1.20.2)")
	void voidDamage_notBusted() {
		hunter(1.0);
		assertNotBusted(hit(9.0, DamageCause.VOID));
	}

	@Test
	@DisplayName("/kill: VOID with Float.MAX_VALUE damage, and SUICIDE: not a bust")
	void killCommand_notBusted() {
		hunter(1.0);
		assertNotBusted(hit(Float.MAX_VALUE, DamageCause.VOID));
		assertNotBusted(hit(9.0, DamageCause.SUICIDE));
	}

	@Test
	@DisplayName("KILL is skipped by name (the constant only exists on 1.20.3+; the compile API is 1.16.5)")
	void killCause_skippedByName() {
		hunter(1.0);
		EntityDamageEvent event = mock(EntityDamageEvent.class);
		DamageCause       kill  = mock(DamageCause.class);
		when(kill.name()).thenReturn("KILL");
		when(event.getEntity()).thenReturn(player);
		when(event.getFinalDamage()).thenReturn(9.0);
		when(event.getCause()).thenReturn(kill);
		assertNotBusted(event);
	}

	@Test
	@DisplayName("a groupless cop in range never busts")
	void grouplessCopInRange_notBusted() {
		cop(CopState.PURSUING, 1.0, null);
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("a cop of the suspect's own squad that is POSTED, IDLE, GUARDING or CUFFING never busts")
	void copNotHunting_notBusted() {
		for (CopState state : new CopState[]{CopState.POSTED, CopState.IDLE, CopState.GUARDING, CopState.CUFFING}) {
			nearby.clear();
			cop(state, 1.0, victimId);
			assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
		}
	}

	@Test
	@DisplayName("a PURSUING cop whose squad hunts another player never busts")
	void otherSuspectsCop_notBusted() {
		cop(CopState.PURSUING, 1.0, UUID.randomUUID());
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
	}

	@Test
	@DisplayName("0.20 merge gate: PATROL, WARNING and OVERWATCH cops (looked up by name) never bust")
	void postMergeStates_neverBust() {
		for (String name : new String[]{"PATROL", "WARNING", "OVERWATCH"}) {
			CopState state;
			try {
				state = CopState.valueOf(name);
			} catch (IllegalArgumentException absentBeforeMerge) {
				continue; // ponytail: the states arrive with the 0.20 merge; the post-merge gate runs this for real
			}
			nearby.clear();
			cop(state, 1.0, victimId);
			assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
		}
	}

	@Test
	@DisplayName("the nearest eligible cop guards; an ineligible nearer cop is skipped")
	void nearestEligibleCopGuards() {
		CopNpc far    = hunter(2.5);
		CopNpc near   = hunter(1.0);
		CopNpc nearer = cop(CopState.IDLE, 0.5, victimId);

		listener.onDamage(hit(9.0, DamageCause.ENTITY_ATTACK));

		verify(near).transitionTo(CopState.GUARDING);
		verify(far, never()).transitionTo(CopState.GUARDING);
		verify(nearer, never()).transitionTo(CopState.GUARDING);
		assertEquals(near.getNpc().getUniqueId(), locks.getOwner(victimId));
	}

	@Test
	@DisplayName("a cuff lock another cop already holds is force-released and handed to the busting cop")
	void existingLockForceReleased() {
		locks.tryAcquire(victimId, UUID.randomUUID());
		CopNpc cop = hunter(1.0);

		listener.onDamage(hit(9.0, DamageCause.ENTITY_ATTACK));

		assertEquals(cop.getNpc().getUniqueId(), locks.getOwner(victimId));
	}

	@Test
	@DisplayName("a bust restarts the squad's cuff-failure count like a normal cuff (P-BUST-5 parity)")
	void bust_resetsSquadCuffFailures() {
		CopNpc cop = hunter(1.0);
		cop.getGroup().recordCuffFailure(victimId, 3);
		cop.getGroup().recordCuffFailure(victimId, 3);

		listener.onDamage(hit(9.0, DamageCause.ENTITY_ATTACK));

		// count restarted: two more failures must not reach max 3
		assertFalse(cop.getGroup().recordCuffFailure(victimId, 3));
		assertFalse(cop.getGroup().recordCuffFailure(victimId, 3));
	}

	@Test
	@DisplayName("a missing loaded cop provider falls back to 3 cuff attempts on the event")
	void providerMissing_defaultsAttempts() {
		when(copLoader.getLoadedProvider()).thenReturn(null);
		hunter(1.0);

		listener.onDamage(hit(9.0, DamageCause.ENTITY_ATTACK));

		assertEquals(3, firedCuffedEvent().getMaxAttempts());
	}

	@Test
	@DisplayName("BUSTED title and subtitle go to the victim through WantedMessages")
	void bustTitleShown() {
		hunter(1.0);

		listener.onDamage(hit(9.0, DamageCause.ENTITY_ATTACK));

		chat.verify(() -> ChatUtil.sendTitle(player, "T", "S"));
	}

	@Test
	@DisplayName("no title and no event for a hit that is not a bust")
	void noTitleWhenNotBusted() {
		assertNotBusted(hit(9.0, DamageCause.ENTITY_ATTACK));
		chat.verifyNoInteractions();
	}

	@Test
	@DisplayName("main thread only: no scheduler use; the search bound is the named 6.0 constant")
	void noScheduler_namedBound() {
		hunter(1.0);

		listener.onDamage(hit(9.0, DamageCause.ENTITY_ATTACK));

		verifyNoInteractions(bukkit.scheduler());
		verify(player).getNearbyEntities(BustedListener.SEARCH_BOUND, BustedListener.SEARCH_BOUND,
		                                 BustedListener.SEARCH_BOUND);
		assertEquals(6.0, BustedListener.SEARCH_BOUND);
	}

	@Test
	@DisplayName("ordering contract: @ListenerHandler class, EntityDamageEvent at HIGH with ignoreCancelled")
	void ordering_annotations() throws NoSuchMethodException {
		assertTrue(BustedListener.class.isAnnotationPresent(ListenerHandler.class));
		EventHandler handler = BustedListener.class.getMethod("onDamage", EntityDamageEvent.class)
		                                           .getAnnotation(EventHandler.class);
		assertEquals(EventPriority.HIGH, handler.priority());
		assertTrue(handler.ignoreCancelled());
	}

	@Test
	@DisplayName("one charge: no PlayerDownedEvent (no bill); the real CuffingListener cuffs BUSTED_DOWNED and starts transit")
	void exactlyOneCharge_noBill() {
		TransitService  transit = mock(TransitService.class);
		CuffingListener cuffing = new CuffingListener(detainment, mock(DetainmentMessageContract.class), transit);
		doAnswer(call -> {
			if (call.getArgument(0) instanceof CuffedEvent cuffed) {
				cuffing.onPlayerSuccessfulCuffing(cuffed);
			}
			return null;
		}).when(bukkit.pluginManager()).callEvent(any());
		hunter(1.0);
		List<String> titles = new ArrayList<>();
		chat.when(() -> ChatUtil.sendTitle(any(), any(), any())).thenAnswer(c -> {
			titles.add(c.getArgument(1));
			return null;
		});

		listener.onDamage(hit(9.0, DamageCause.ENTITY_ATTACK));

		assertEquals("T", titles.get(titles.size() - 1), "BUSTED must be the last title, after CuffingListener's");
		assertFalse(firedEvents().stream().anyMatch(PlayerDownedEvent.class::isInstance));
		verify(detainment).handcuff(player, ArrestOrigin.BUSTED_DOWNED);
		verify(transit).schedule(player);
	}

}
