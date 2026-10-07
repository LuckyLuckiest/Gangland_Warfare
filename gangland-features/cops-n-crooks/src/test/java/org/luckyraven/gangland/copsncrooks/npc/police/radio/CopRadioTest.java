package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopFieldCare;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio.RadioCall;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.npc.FieldCareSettings;
import org.luckyraven.gangland.npc.radio.RadioSettings;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The police radio (GL-3): callsigns, who a squad hunts, the calls a casualty or contact queues for responders
 * (whether or not any human hears the line), backup requests, and dispatch lines heard around the suspect.
 */
@DisplayName("CopRadio - voice, calls and backup")
class CopRadioTest {

	private static final RadioSettings SETTINGS = new RadioSettings(true, 32, 64, 1500, 1000, 25, 2,
	                                                                Map.of("Man_Down", 5000L), Set.of("Man_Down",
	                                                                "Backup", "Dispatch_Wanted"), null, 1f, 1f);

	private BukkitStatics     bukkit;
	private World             world;
	private List<Player>      listeners;
	private CopConfigProvider provider;
	private long[]            clock;
	private CopRadio          radio;
	private Player            suspect;
	private CopGroup          group;
	private List<RadioCall>   calls;

	@BeforeEach
	void setUp() {
		bukkit    = BukkitStatics.install();
		world     = mock(World.class);
		listeners = new ArrayList<>();
		when(world.getPlayers()).thenReturn(listeners);

		provider = mock(CopConfigProvider.class);
		when(provider.getRadioSettings()).thenReturn(SETTINGS);
		when(provider.getBackupSettings()).thenReturn(new BackupSettings(true, 1, 30_000, 60_000));
		clock = new long[]{10_000};
		radio = new CopRadio(() -> provider, key -> switch (key) {
			case "Format" -> List.of("[%unit%] %line%");
			case "Dispatch_Format" -> List.of("[DISPATCH] %line%");
			default -> List.of(key + " line");
		}, () -> clock[0], (task, ticks) -> { });

		suspect = player(0, 0);
		group   = new CopGroup(suspect.getUniqueId());
		calls   = new ArrayList<>();
		group.setListener(radio.listenerFor(group, calls::add));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a callsign is the tier's display name without colours, a dash, and the NPC id")
	void callsign_isStrippedTierPlusNpcId() {
		assertEquals("SWAT-17", CopRadio.callsign(cop(17, "&9&lSWAT", 5, 0)));
	}

	@Test
	@DisplayName("a cop with a badge callsign speaks under it, colours stripped")
	void callsign_usesCopsOwnCallsignStripped() {
		CopNpc cop = cop(17, "&9&lSWAT", 5, 0);
		when(cop.getCallsign()).thenReturn("&9Officer &fBob &7#1017");

		assertEquals("Officer Bob #1017", CopRadio.callsign(cop));
	}

	@Test
	@DisplayName("the radio callsign keeps the role word but not its symbol; the nameplate keeps both")
	void callsign_dropsRoleSymbol() {
		CopNpc cop = cop(17, "&9&lSWAT", 5, 0);
		when(cop.getCallsign()).thenReturn("&9Officer &c✚ Medic &fBob &7#1592");
		CopRole medic = role("Medic", "✚");
		when(cop.getRole()).thenReturn(medic);

		assertEquals("Officer Medic Bob #1592", CopRadio.callsign(cop));
		assertEquals("&9Officer &c✚ Medic &fBob &7#1592", cop.getCallsign());
	}

	@Test
	@DisplayName("only the role's own symbol is removed, never a look-alike inside the name or tier word; no stray edge space")
	void callsign_symbolRemovalIsAnchored() {
		CopNpc cop = cop(17, "&9&lSWAT", 5, 0);
		when(cop.getCallsign()).thenReturn("&9Alpha &ca Medic &fBea &7#1");
		CopRole medic = role("Medic", "a");
		when(cop.getRole()).thenReturn(medic);
		assertEquals("Alpha Medic Bea #1", CopRadio.callsign(cop));

		when(cop.getCallsign()).thenReturn("&ca Medic &fBob");
		assertEquals("Medic Bob", CopRadio.callsign(cop));
	}

	@Test
	@DisplayName("the group squad hunts the wanted player; an attacker squad hunts its attacker")
	void hunted_groupTargetOrAttacker() {
		Player   attacker = player(3, 3);
		NpcSquad attackers = group.attackerSquad(attacker.getUniqueId(), attacker.getLocation());

		assertSame(suspect, radio.huntedOf(group, group.getSquad()));
		assertSame(attacker, radio.huntedOf(group, attackers));
		assertEquals(null, radio.huntedOf(group, new NpcSquad()));
	}

	@Test
	@DisplayName("a man down queues a call and requests backup, voiced only when granted; a second inside the cooldown adds nothing")
	void manDown_requestsBackup_saysBackupOnlyWhenGranted_andQueuesCall() {
		Player bystander = listener(10, 0);
		CopNpc leader    = cop(1, "SWAT", 4, 0);
		CopNpc first     = cop(2, "SWAT", 6, 0);
		CopNpc second    = cop(3, "SWAT", 8, 0);
		group.add(leader);
		group.add(first);
		group.add(second);

		group.getSquad().memberDown(first);

		assertEquals(1, calls.size());
		verify(bystander).sendMessage("[SWAT-1] Man_Down line");
		verify(bystander).sendMessage("[SWAT-1] Backup line");
		assertEquals(1, group.backupExtra(clock[0], provider.getBackupSettings()));

		clock[0] += 6_000;
		group.getSquad().memberDown(second);

		assertEquals(2, calls.size(), "the casualty is still heard by nearby cops");
		verify(bystander, org.mockito.Mockito.times(1)).sendMessage("[SWAT-1] Backup line");
	}

	@Test
	@DisplayName("the Commander going down: the new leader radios Commander_Down instead of Leader_Down, the group falls back briefly, backup is still requested")
	void commanderDown_saysCommanderDown_groupFallsBack() {
		Player bystander = listener(10, 0);
		CopNpc commander = cop(1, "SWAT", 4, 0);
		CopNpc pointman  = cop(2, "SWAT", 6, 0);
		when(commander.getRole()).thenReturn(new CopRole("Commander", "Commander", NpcFanPlacement.ANY, null, null,
		                                                 1.0, null, 2, null, 1.0, 0, null, 0, 60, false, true));
		group.add(commander);
		group.add(pointman);

		group.getSquad().memberDown(commander);

		verify(bystander).sendMessage("[SWAT-2] Commander_Down line");
		verify(bystander, never()).sendMessage("[SWAT-2] Leader_Down line");
		verify(bystander).sendMessage("[SWAT-2] Backup line");
		assertEquals(1, calls.size());
		assertTrue(group.isFallingBack(clock[0]));
		assertFalse(group.isFallingBack(clock[0] + CopRadio.COMMANDER_FALL_BACK_MS));
	}

	@Test
	@DisplayName("a Commander that is not the squad leader (Leader_Priority 0) still radios Commander_Down and falls the group back when it dies as a man down")
	void commanderNotLeader_manDown_saysCommanderDown_groupFallsBack() {
		Player bystander = listener(10, 0);
		CopNpc leader    = cop(1, "SWAT", 4, 0);
		CopNpc commander = cop(2, "SWAT", 6, 0);
		when(commander.getRole()).thenReturn(new CopRole("Commander", "Commander", NpcFanPlacement.ANY, null, null,
		                                                 1.0, null, 0, null, 1.0, 0, null, 0, 60, false, true));
		group.add(leader);
		group.add(commander);

		group.getSquad().memberDown(commander);

		verify(bystander).sendMessage("[SWAT-1] Commander_Down line");
		verify(bystander, never()).sendMessage("[SWAT-1] Man_Down line");
		verify(bystander).sendMessage("[SWAT-1] Backup line");
		assertEquals(1, calls.size());
		assertTrue(group.isFallingBack(clock[0]));
	}

	@Test
	@DisplayName("any other leader going down is Leader_Down, and the group does not fall back")
	void plainLeaderDown_saysLeaderDown_noFallBack() {
		Player bystander = listener(10, 0);
		CopNpc leader    = cop(1, "SWAT", 4, 0);
		group.add(leader);
		group.add(cop(2, "SWAT", 6, 0));

		group.getSquad().memberDown(leader);

		verify(bystander).sendMessage("[SWAT-2] Leader_Down line");
		assertFalse(group.isFallingBack(clock[0]));
	}

	@Test
	@DisplayName("with no human in range, a contact still queues a call for the cops")
	void noHumanInRange_stillQueuesCall() {
		CopNpc   cop   = cop(1, "SWAT", 4, 0);
		Location where = new Location(world, 1, 64, 1);
		group.add(cop);

		group.getListener().onSignal(group.getSquad(), NpcSquadSignal.CONTACT, cop, where);

		assertEquals(1, calls.size());
		assertEquals(where, calls.get(0).origin());
		assertSame(group.getSquad(), calls.get(0).squad());
	}

	@Test
	@DisplayName("dispatch is heard around the suspect, not far from him")
	void dispatch_originAtTarget() {
		Player near = listener(10, 0);
		Player far  = listener(50, 0);

		assertTrue(radio.dispatch(group, suspect, "Dispatch_Wanted", 2, "SWAT"));

		verify(near).sendMessage("[DISPATCH] Dispatch_Wanted line");
		verify(far, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("dispatch with extras fills %station%, %eta% and %count% beside %level%")
	void dispatch_withExtras_fillsTheLine() {
		radio = new CopRadio(() -> provider, key -> switch (key) {
			case "Dispatch_Format" -> List.of("[DISPATCH] %line%");
			default -> List.of("%count% from %station% in %eta% at %place%, level %level%");
		}, () -> clock[0], (task, ticks) -> { });
		Player near = listener(10, 0);

		assertTrue(radio.dispatch(group, suspect, "Dispatch_En_Route", 3, "SWAT",
		                          Map.of("count", "4", "station", "Central", "eta", "12", "place", "Dock")));

		verify(near).sendMessage("[DISPATCH] 4 from Central in 12 at Dock, level 3");
	}

	@Test
	@DisplayName("a %place% no caller names reads Unknown_Place, never the raw token")
	void place_unnamed_readsUnknownPlace() {
		radio = new CopRadio(() -> provider, key -> switch (key) {
			case "Dispatch_Format" -> List.of("[DISPATCH] %line%");
			case "Unknown_Place" -> List.of("the area");
			default -> List.of("wanted in %place%");
		}, () -> clock[0], (task, ticks) -> { });
		Player near = listener(10, 0);

		assertTrue(radio.dispatch(group, suspect, "Dispatch_Wanted", 2, "SWAT"));

		verify(near).sendMessage("[DISPATCH] wanted in the area");
	}

	@Test
	@DisplayName("enRoute_rightAfterDispatchWanted_isDelivered: with the default settings the same player hears both in one tick")
	void enRoute_rightAfterDispatchWanted_isDelivered() {
		RadioSettings defaults = CopConfigProvider.COP_RADIO_DEFAULTS;
		// no click: the sound would need a live server
		when(provider.getRadioSettings()).thenReturn(new RadioSettings(true, defaults.range(), defaults.targetRange(),
		                                                               defaults.squadGapMs(), defaults.playerGapMs(),
		                                                               defaults.ackDelayTicks(), defaults.responderMax(),
		                                                               defaults.cooldownMs(), defaults.priority(), null,
		                                                               1f, 1f));
		Player near = listener(10, 0);

		assertTrue(radio.dispatch(group, suspect, "Dispatch_Wanted", 2, "SWAT"));
		assertTrue(radio.dispatch(group, suspect, "Dispatch_En_Route", 2, "SWAT", Map.of("count", "2")));

		verify(near).sendMessage("[DISPATCH] Dispatch_Wanted line");
		verify(near).sendMessage("[DISPATCH] Dispatch_En_Route line");
	}

	@Test
	@DisplayName("sayFromLeader with extras: the leader speaks the key with %direction% filled from the extras")
	void sayFromLeader_withExtras() {
		radio = new CopRadio(() -> provider, key -> switch (key) {
			case "Format" -> List.of("[%unit%] %line%");
			default -> List.of("heading %direction%");
		}, () -> clock[0], (task, ticks) -> { });
		Player bystander = listener(10, 0);
		CopNpc leader    = cop(3, "SWAT", 4, 0);
		group.add(leader);

		assertTrue(radio.sayFromLeader(group, "Handoff", Map.of("direction", "north")));

		verify(bystander).sendMessage("[SWAT-3] heading north");
	}

	@Test
	@DisplayName("compassWord returns the file's word for each of the 8 sides, empty across worlds or with no compass lines")
	void compassWord_eightSides() {
		List<String> words = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");
		radio = new CopRadio(() -> provider, key -> "Compass".equals(key) ? words : List.of(), () -> clock[0],
		                     (task, ticks) -> { });
		Location from = new Location(world, 0, 64, 0);
		Location[] targets = {new Location(world, 0, 64, -10), new Location(world, 10, 64, -10),
		                      new Location(world, 10, 64, 0), new Location(world, 10, 64, 10),
		                      new Location(world, 0, 64, 10), new Location(world, -10, 64, 10),
		                      new Location(world, -10, 64, 0), new Location(world, -10, 64, -10)};

		for (int side = 0; side < 8; side++) assertEquals(words.get(side), radio.compassWord(from, targets[side]), "side " + side);

		assertEquals("", radio.compassWord(from, new Location(mock(World.class), 0, 64, -10)));
		assertEquals("", radio.compassWord(null, from));
	}

	@Test
	@DisplayName("sayAs: the cop speaks the key itself, with its callsign and the extras filling the line")
	void sayAs_speaksFromTheCopWithExtras() {
		radio = new CopRadio(() -> provider, key -> switch (key) {
			case "Format" -> List.of("[%unit%] %line%");
			default -> List.of(key + " to %member%");
		}, () -> clock[0], (task, ticks) -> { });
		Player bystander = listener(10, 0);
		CopNpc medic     = cop(3, "SWAT", 4, 0);
		group.add(medic);

		assertTrue(radio.sayAs(group, medic, "Medic_Moving", Map.of("member", "SWAT-9")));

		verify(bystander).sendMessage("[SWAT-3] Medic_Moving to SWAT-9");
	}

	@Test
	@DisplayName("sayAs: a cop with no entity says nothing")
	void sayAs_noEntity_silent() {
		Player bystander = listener(10, 0);
		CopNpc medic     = cop(3, "SWAT", 4, 0);
		when(medic.getEntity()).thenReturn(null);

		assertFalse(radio.sayAs(group, medic, "Hit", Map.of()));

		verify(bystander, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("field care on a real radio: Hit, then Medic_Moving and Covering_Fire all reach a listener, past the squad and player gaps")
	void fieldCare_hitThenMedicLines_allHeard() {
		List<Map.Entry<Runnable, Long>> tasks = new ArrayList<>();
		radio = new CopRadio(() -> provider, key -> switch (key) {
			case "Format" -> List.of("[%unit%] %line%");
			default -> List.of(key + " line");
		}, () -> clock[0], (task, ticks) -> tasks.add(Map.entry(task, ticks)));
		when(provider.getFieldCareSettings()).thenReturn(FieldCareSettings.DEFAULT);
		when(provider.getAiTickRate()).thenReturn(10);
		Player bystander = listener(10, 0);
		CopNpc leader    = careCop(1, 20.0, null, 4, 0);
		CopNpc patient   = careCop(2, 8.0, null, 6, 0);
		CopNpc medic     = careCop(3, 20.0, new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, 8.0, 12.0, 1.0, null,
		                                                0, null, 1.0, 0, null, 0, 60, true, false), 8, 0);
		when(medic.distanceTo(any(LivingEntity.class))).thenReturn(10.0);

		new CopFieldCare(() -> provider, radio, () -> clock[0]).tick(group);
		long start = clock[0];
		tasks.sort(Map.Entry.comparingByValue());
		for (Map.Entry<Runnable, Long> task : tasks) {
			clock[0] = start + task.getValue() * 50;
			task.getKey().run();
		}

		InOrder order = inOrder(bystander);
		order.verify(bystander).sendMessage("[SWAT-2] Hit line");
		order.verify(bystander).sendMessage("[SWAT-3] Medic_Moving line");
		order.verify(bystander).sendMessage("[SWAT-1] Covering_Fire line");
		assertSame(patient, medic.getPatient());
		assertSame(leader, group.getSquad().leader());
	}

	/** A group cop for field care: health out of 20, a fighting state, a role and stateful care slots. */
	/** Rebuilds {@link #radio} and {@link #group} so scheduled follow-ups land in {@code tasks}. */
	private void captureLater(List<Runnable> tasks) {
		radio = new CopRadio(() -> provider, key -> key.equals("Format") ? List.of("[%unit%] %line%")
		                                                                  : List.of(key + " line"),
		                     () -> clock[0], (task, ticks) -> tasks.add(task));
		group = new CopGroup(suspect.getUniqueId());
		group.setListener(radio.listenerFor(group, calls::add));
	}

	private void twoDown(Player bystander, List<Runnable> tasks) {
		group.escalate(suspect.getUniqueId());
		for (int id = 1; id <= 4; id++) group.add(cop(id, "SWAT", 4 + id, 0));
		List<CopNpc> cops = group.getCops();
		group.getSquad().memberDown(cops.get(1));
		clock[0] += 5_000;
		group.getSquad().memberDown(cops.get(2));
	}

	@Test
	@DisplayName("secondManDownInside20s_radiosRegroup_andFallsBack")
	void secondManDownInside20s_radiosRegroup_andFallsBack() {
		Player         bystander = listener(10, 0);
		List<Runnable> tasks     = new ArrayList<>();
		captureLater(tasks);

		twoDown(bystander, tasks);
		tasks.forEach(Runnable::run);

		assertTrue(group.isRegrouping());
		assertTrue(group.isFallingBack(clock[0]));
		InOrder order = inOrder(bystander);
		order.verify(bystander, org.mockito.Mockito.atLeastOnce()).sendMessage("[SWAT-1] Man_Down line");
		order.verify(bystander).sendMessage("[SWAT-1] Regroup line");
	}

	@Test
	@DisplayName("a regroup grants its own backup while the backup cooldown still runs, and radios Regroup")
	void regroup_duringBackupCooldown_grantsBackup_andRadiosRegroup() {
		Player         bystander = listener(10, 0);
		List<Runnable> tasks     = new ArrayList<>();
		captureLater(tasks);
		// an earlier backup came and went 5 s ago; its 60 s cooldown still runs, so no casualty may request one
		clock[0] = 100_000;
		assertTrue(group.requestBackup(clock[0] - 35_000, provider.getBackupSettings()));
		assertEquals(0, group.backupExtra(clock[0], provider.getBackupSettings()));

		twoDown(bystander, tasks);
		tasks.forEach(Runnable::run);

		assertEquals(1, group.backupExtra(clock[0], provider.getBackupSettings()));
		verify(bystander).sendMessage("[SWAT-1] Regroup line");
	}

	@Test
	@DisplayName("with Backup.Enabled false the squad still regroups but never radios the Regroup line")
	void regroup_backupOff_regroupsSilently() {
		when(provider.getBackupSettings()).thenReturn(new BackupSettings(false, 1, 30_000, 60_000));
		Player         bystander = listener(10, 0);
		List<Runnable> tasks     = new ArrayList<>();
		captureLater(tasks);

		twoDown(bystander, tasks);
		tasks.forEach(Runnable::run);

		assertTrue(group.isRegrouping());
		verify(bystander, never()).sendMessage("[SWAT-1] Regroup line");
	}

	@Test
	@DisplayName("regroupLine_isDelivered_withAPreUpgradePriorityList")
	void regroupLine_isDelivered_withAPreUpgradePriorityList() {
		// SETTINGS' Priority set names Man_Down, Backup and Dispatch_Wanted only: no Regroup entry
		assertFalse(SETTINGS.priority().contains("Regroup"));
		Player         bystander = listener(10, 0);
		List<Runnable> tasks     = new ArrayList<>();
		captureLater(tasks);

		twoDown(bystander, tasks);
		verify(bystander, never()).sendMessage("[SWAT-1] Regroup line");
		tasks.forEach(Runnable::run);

		verify(bystander).sendMessage("[SWAT-1] Regroup line");
	}

	@Test
	@DisplayName("commanderDownDuringARegroup_keepsTheLongerFallBack")
	void commanderDownDuringARegroup_keepsTheLongerFallBack() {
		listener(10, 0);
		List<Runnable> tasks = new ArrayList<>();
		captureLater(tasks);
		group.escalate(suspect.getUniqueId());
		CopNpc commander = cop(1, "SWAT", 4, 0);
		when(commander.getRole()).thenReturn(new CopRole("Commander", "Commander", NpcFanPlacement.ANY, null, null,
		                                                 1.0, null, 2, null, 1.0, 0, null, 0, 60, false, true));
		group.add(commander);
		group.add(cop(2, "SWAT", 6, 0));
		group.startRegroup(clock[0], org.luckyraven.gangland.copsncrooks.npc.police.config.RegroupSettings.DEFAULT);
		long regroupEnd = group.getFallBackUntil();

		group.getSquad().memberDown(commander);

		assertEquals(regroupEnd, group.getFallBackUntil());
		assertTrue(regroupEnd > clock[0] + CopRadio.COMMANDER_FALL_BACK_MS);
	}

	@Test
	@DisplayName("cuffFirstSquad_radiosNoRegroup")
	void cuffFirstSquad_radiosNoRegroup() {
		Player         bystander = listener(10, 0);
		List<Runnable> tasks     = new ArrayList<>();
		captureLater(tasks);

		for (int id = 1; id <= 4; id++) group.add(cop(id, "SWAT", 4 + id, 0));
		List<CopNpc> cops = group.getCops();
		group.getSquad().memberDown(cops.get(1));
		clock[0] += 5_000;
		group.getSquad().memberDown(cops.get(2));
		tasks.forEach(Runnable::run);

		assertFalse(group.isRegrouping());
		verify(bystander, never()).sendMessage("[SWAT-1] Regroup line");
	}

	private CopNpc careCop(int id, double health, CopRole role, double x, double z) {
		CopNpc cop = cop(id, "SWAT", x, z);
		when(cop.getEntity().getHealth()).thenReturn(health);
		when(cop.getEntity().getMaxHealth()).thenReturn(20.0);
		when(cop.getCurrentState()).thenReturn(CopState.PURSUING);
		when(cop.getRole()).thenReturn(role);
		when(cop.getGroup()).thenReturn(group);
		AtomicReference<CopNpc> patient = new AtomicReference<>();
		doAnswer(i -> {
			patient.set(i.getArgument(0));
			return null;
		}).when(cop).setPatient(any());
		when(cop.getPatient()).thenAnswer(i -> patient.get());
		group.add(cop);
		return cop;
	}

	/** A real role: {@link CopRole} is a record, which Mockito cannot mock. */
	private static CopRole role(String displayName, String symbol) {
		return new CopRole(displayName, displayName, NpcFanPlacement.ANY, null, null, 1.0, 0, null, 1.0, 0, null, 0, 60,
		                   false, false, "", symbol, CopRole.Kit.EMPTY, Map.of());
	}

	/** Colouring a line reads {@code Settings.moneySymbol}, which only a loaded settings.yml sets. */
	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		if (field.get(null) == null) {
			field.set(null, "$");
		}
	}

	private Player player(double x, double z) {
		UUID   id     = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, x, 64, z));
		when(player.getName()).thenReturn("P" + x);
		bukkit.statics().when(() -> Bukkit.getPlayer(id)).thenReturn(player);
		return player;
	}

	private Player listener(double x, double z) {
		Player player = player(x, z);
		listeners.add(player);
		return player;
	}

	private CopNpc cop(int id, String tierName, double x, double z) {
		CopNpc        cop  = mock(CopNpc.class);
		CopTierConfig tier = mock(CopTierConfig.class);
		when(tier.displayName()).thenReturn(tierName);
		when(cop.getTierConfig()).thenReturn(tier);
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(id);
		when(cop.getNpc()).thenReturn(npc);
		LivingEntity body = mock(LivingEntity.class);
		when(body.getLocation()).thenReturn(new Location(world, x, 64, z));
		when(body.getWorld()).thenReturn(world);
		when(cop.getEntity()).thenReturn(body);
		when(cop.isValid()).thenReturn(true);
		return cop;
	}
}
