package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
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
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio.RadioCall;
import org.luckyraven.gangland.npc.radio.RadioSettings;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.NpcSquadSignal;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
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
