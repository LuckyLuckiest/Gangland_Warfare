package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.BackupSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.npc.radio.RadioSettings;
import org.luckyraven.gangland.npc.radio.RadioSides;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The role-aware radio lines (H13b): a Marksman's overwatch and sightings, a Defender's shield, an Assault cop's
 * flank, the Commander's orders, status check and pull-back, and the placeholders (distance, direction, health,
 * role) filling them. Each fires once per signal edge and respects its own cooldown.
 */
@DisplayName("CopRadio - role lines")
class CopRadioRolesTest {

	private static final Map<String, Long> COOLDOWNS = Map.ofEntries(
			Map.entry("Overwatch_Set", 20_000L), Map.entry("Marksman_Spotted", 8_000L),
			Map.entry("Commander_Orders", 20_000L), Map.entry("Flanking", 10_000L));

	private static final RadioSettings SETTINGS = new RadioSettings(true, 32, 64, 1500, 1000, 25, 2, COOLDOWNS,
	                                                                Set.of("Marksman_Spotted"), null, 1f, 1f);

	private BukkitStatics               bukkit;
	private World                       world;
	private List<Player>                listeners;
	private CopConfigProvider           provider;
	private long[]                      clock;
	private List<Runnable>              tasks;
	private List<Long>                  delays;
	private CopGroup                    group;
	private Player                      bystander;
	private Map<String, List<String>>   pools;
	private CopRadio                    radio;

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
		tasks  = new ArrayList<>();
		delays = new ArrayList<>();
		pools = new java.util.HashMap<>(Map.of(
				"Format", List.of("[%unit%] %line%"),
				"Compass", List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW"),
				"Overwatch_Set", List.of("OW %distance% %direction% %role%"),
				"Marksman_Spotted", List.of("SPOT %distance% %direction%"),
				"Marksman_Lost", List.of("LOST %direction%"),
				"Marksman_Reloading", List.of("MRELOAD"),
				"Shield_Up", List.of("SHIELD %role%"),
				"Flanking", List.of("FLANK %direction%"),
				"Commander_Orders", List.of("ORDERS %direction%"),
				"Pull_Back", List.of("BACK %member%")));
		pools.put("Status_Check", List.of("STATUS"));
		pools.put("Engage", List.of("Engage line"));
		pools.put("Contact", List.of("Contact line"));
		pools.put("Ack", List.of("Ack line"));
		pools.put("Flank_Left", List.of("Flank_Left line"));
		pools.put("Hit", List.of("Hit at %health%%"));
		pools.put("Medic_Moving", List.of("MOVING %distance% %eta% %member%"));
		radio = new CopRadio(() -> provider, key -> pools.getOrDefault(key, List.of()), () -> clock[0], (task, ticks) -> {
			tasks.add(task);
			delays.add(ticks);
		});

		Player suspect = player(0, 0);
		group = new CopGroup(suspect.getUniqueId());
		group.setListener(radio.listenerFor(group, call -> { }));
		bystander = listener(10, 0);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a Marksman engaging says Overwatch_Set with distance, compass direction and its role, not Engage")
	void marksmanEngage_saysOverwatch() {
		CopNpc marksman = cop(1, 4, 0, marksmanRole());
		group.add(marksman);
		Location target = new Location(world, 14, 64, 0);

		signal(NpcSquadSignal.ENGAGE, marksman, target);

		verify(bystander).sendMessage("[SWAT-1] OW 10 " + compass(marksman, target) + " Marksman");
		verify(bystander, never()).sendMessage("[SWAT-1] Engage line");
	}

	@Test
	@DisplayName("a Marksman's contact, lost contact and reload use its own lines; a role-less cop keeps the generic ones")
	void marksmanSignals_ownLines() {
		CopNpc marksman = cop(1, 4, 0, marksmanRole());
		CopNpc plain    = cop(2, 6, 0, null);
		group.add(marksman);
		group.add(plain);
		Location spot = new Location(world, 24, 64, 0);

		signal(NpcSquadSignal.CONTACT, marksman, spot);
		verify(bystander).sendMessage("[SWAT-1] SPOT 20 " + compass(marksman, spot));

		clock[0] += 5_000;
		signal(NpcSquadSignal.CONTACT_LOST, marksman, spot);
		verify(bystander).sendMessage("[SWAT-1] LOST " + compass(marksman, spot));

		clock[0] += 5_000;
		signal(NpcSquadSignal.RELOADING, marksman, marksman.getEntity().getLocation());
		verify(bystander).sendMessage("[SWAT-1] MRELOAD");

		clock[0] += 60_000;
		pools.put("Contact", List.of("Contact line"));
		signal(NpcSquadSignal.CONTACT, plain, spot);
		verify(bystander).sendMessage("[SWAT-2] Contact line");
	}

	@Test
	@DisplayName("a role whose line pool is muted falls back to the generic line")
	void mutedRoleLine_fallsBackToGeneric() {
		CopNpc marksman = cop(1, 4, 0, marksmanRole());
		group.add(marksman);
		pools.put("Overwatch_Set", List.of());

		signal(NpcSquadSignal.ENGAGE, marksman, new Location(world, 14, 64, 0));

		verify(bystander).sendMessage("[SWAT-1] Engage line");
	}

	@Test
	@DisplayName("a Defender engaging says Shield_Up; the same line repeated inside its cooldown stays silent, then returns")
	void defenderEngage_shieldUp_respectsCooldown() {
		pools.put("Shield_Up", List.of("SHIELD %role%"));
		CopNpc defender = cop(1, 4, 0, defenderRole());
		group.add(defender);
		Location target = new Location(world, 14, 64, 0);
		RadioSettings withCooldown = new RadioSettings(true, 32, 64, 1500, 1000, 25, 2, Map.of("Shield_Up", 20_000L),
		                                               Set.of(), null, 1f, 1f);
		when(provider.getRadioSettings()).thenReturn(withCooldown);

		signal(NpcSquadSignal.ENGAGE, defender, target);
		clock[0] += 5_000;
		signal(NpcSquadSignal.ENGAGE, defender, target);
		verify(bystander, times(1)).sendMessage("[SWAT-1] SHIELD Defender");

		clock[0] += 20_000;
		signal(NpcSquadSignal.ENGAGE, defender, target);
		verify(bystander, times(2)).sendMessage("[SWAT-1] SHIELD Defender");
	}

	@Test
	@DisplayName("with a Commander alive, an engage makes it order the squad once, a follow-up said past the gaps with the direction")
	void engage_commanderOrders() {
		CopNpc commander = cop(1, 4, 0, commanderRole());
		CopNpc pointman  = cop(2, 6, 0, null);
		group.add(commander);
		group.add(pointman);
		Location target = new Location(world, 14, 64, 0);

		signal(NpcSquadSignal.ENGAGE, pointman, target);
		assertEquals(1, tasks.stream().count(), "one follow-up, scheduled one ack delay out");
		assertEquals(25L, delays.get(0));
		clock[0] += 1_250;
		tasks.get(0).run();

		verify(bystander).sendMessage("[SWAT-1] ORDERS " + compass(commander, target));
	}

	@Test
	@DisplayName("without a Commander there is no order, and no follow-up is scheduled at all")
	void engage_noCommander_noOrders() {
		CopNpc pointman = cop(2, 6, 0, null);
		group.add(pointman);

		signal(NpcSquadSignal.ENGAGE, pointman, new Location(world, 14, 64, 0));

		assertTrue(tasks.isEmpty());
	}

	@Test
	@DisplayName("lost contact: the Commander checks the squad's status; a man down: it pulls the squad back, naming the casualty")
	void contactLostAndManDown_commanderSpeaks() {
		CopNpc commander = cop(1, 4, 0, commanderRole());
		CopNpc pointman  = cop(2, 6, 0, null);
		group.add(commander);
		group.add(pointman);

		signal(NpcSquadSignal.CONTACT_LOST, pointman, new Location(world, 14, 64, 0));
		clock[0] += 1_250;
		tasks.get(0).run();
		verify(bystander).sendMessage("[SWAT-1] STATUS");

		clock[0] += 30_000;
		tasks.clear();
		signal(NpcSquadSignal.MAN_DOWN, pointman, new Location(world, 6, 64, 0));
		assertEquals(1, tasks.size());
		clock[0] += 1_250;
		tasks.get(0).run();
		verify(bystander).sendMessage("[SWAT-1] BACK SWAT-2");
	}

	@Test
	@DisplayName("the Commander going down is not a pull-back: it is Commander_Down's job")
	void commanderDown_noPullBack() {
		CopNpc commander = cop(1, 4, 0, commanderRole());
		CopNpc pointman  = cop(2, 6, 0, null);
		group.add(commander);
		group.add(pointman);

		signal(NpcSquadSignal.MAN_DOWN, commander, new Location(world, 4, 64, 0));

		assertTrue(tasks.isEmpty());
	}

	@Test
	@DisplayName("an Assault cop ordered to a flank calls its move two ack delays later, after its ack")
	void assaultFlank_callsItsMove() {
		CopNpc leader  = cop(1, 4, 0, null);
		CopNpc assault = cop(2, 6, 0, assaultRole());
		group.add(leader);
		group.add(assault);
		Location post = new Location(world, 6, 64, 12);

		signal(NpcSquadSignal.FLANK_LEFT, assault, post);

		assertEquals(List.of(25L, 50L), delays, "the ack, then the Flanking line");
		for (int i = 0; i < tasks.size(); i++) {
			clock[0] += 1_250;
			tasks.get(i).run();
		}
		verify(bystander).sendMessage("[SWAT-2] Ack line");
		verify(bystander).sendMessage("[SWAT-2] FLANK " + compass(assault, post));
	}

	@Test
	@DisplayName("a flank order to a non-Assault cop gets only the ack")
	void plainFlank_noFlankingLine() {
		CopNpc leader = cop(1, 4, 0, null);
		CopNpc plain  = cop(2, 6, 0, null);
		group.add(leader);
		group.add(plain);

		signal(NpcSquadSignal.FLANK_LEFT, plain, new Location(world, 6, 64, 12));

		assertEquals(List.of(25L), delays);
	}

	@Test
	@DisplayName("sayAs extras beat the built-in speaker-to-spot values: Hit fills the health percent, Medic_Moving the patient's distance and ETA")
	void sayAs_extrasFillPlaceholders() {
		CopNpc cop = cop(3, 4, 0, null);
		group.add(cop);

		radio.sayAs(group, cop, "Hit", Map.of("health", "40"));
		verify(bystander).sendMessage("[SWAT-3] Hit at 40%");

		clock[0] += 60_000;
		radio.sayAs(group, cop, "Medic_Moving", Map.of("member", "SWAT-9", "distance", "7", "eta", "2"));
		verify(bystander).sendMessage("[SWAT-3] MOVING 7 2 SWAT-9");
	}

	@Test
	@DisplayName("a role's radio kind: the flags and block cone first, Marksman and Assault by name, a custom role generic")
	void kindOf_mapsRoles() {
		assertEquals(CopRadio.RoleKind.COMMANDER, CopRadio.kindOf(commanderRole()));
		assertEquals(CopRadio.RoleKind.MEDIC, CopRadio.kindOf(role("Medic", 0, true, false)));
		assertEquals(CopRadio.RoleKind.DEFENDER, CopRadio.kindOf(defenderRole()));
		assertEquals(CopRadio.RoleKind.MARKSMAN, CopRadio.kindOf(role("MARKSMAN", 0, false, false)));
		assertEquals(CopRadio.RoleKind.ASSAULT, CopRadio.kindOf(assaultRole()));
		assertNull(CopRadio.kindOf(role("Sniper", 0, false, false)));
		assertNull(CopRadio.kindOf(null));
	}

	private void signal(NpcSquadSignal signal, CopNpc member, Location where) {
		group.getListener().onSignal(group.getSquad(), signal, member, where);
	}

	/** The compass word the radio picks from the speaker to {@code where}, from the test's own Compass list. */
	private String compass(CopNpc from, Location where) {
		return pools.get("Compass").get(RadioSides.compass8(from.getEntity().getLocation(), where));
	}

	private static CopRole role(String name, double block, boolean medic, boolean commander) {
		return new CopRole(name, name, NpcFanPlacement.CENTER, null, null, 1.0, null, 0, null, 1.0, 0, null, block, 60,
		                   medic, commander);
	}

	private static CopRole marksmanRole() { return role("Marksman", 0, false, false); }

	private static CopRole assaultRole() { return role("Assault", 0, false, false); }

	private static CopRole defenderRole() { return role("Defender", 0.5, false, false); }

	private static CopRole commanderRole() { return role("Commander", 0, false, true); }

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

	private CopNpc cop(int id, double x, double z, CopRole role) {
		CopNpc        cop  = mock(CopNpc.class);
		CopTierConfig tier = mock(CopTierConfig.class);
		when(tier.displayName()).thenReturn("SWAT");
		when(cop.getTierConfig()).thenReturn(tier);
		NPC npc = mock(NPC.class);
		when(npc.getId()).thenReturn(id);
		when(cop.getNpc()).thenReturn(npc);
		LivingEntity body = mock(LivingEntity.class);
		when(body.getLocation()).thenReturn(new Location(world, x, 64, z));
		when(body.getWorld()).thenReturn(world);
		when(cop.getEntity()).thenReturn(body);
		when(cop.isValid()).thenReturn(true);
		when(cop.getRole()).thenReturn(role);
		return cop;
	}
}
