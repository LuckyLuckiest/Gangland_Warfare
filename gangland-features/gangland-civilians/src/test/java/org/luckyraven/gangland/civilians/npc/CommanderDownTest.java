package org.luckyraven.gangland.civilians.npc;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.config.CivilianAIBehaviorConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianDropConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianSettings;
import org.luckyraven.gangland.civilians.npc.config.CivilianTypeConfig;
import org.luckyraven.gangland.civilians.npc.config.CivilianWearableConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansConfig;
import org.luckyraven.gangland.civilians.npc.config.CiviliansLoader;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpcFactory;
import org.luckyraven.gangland.civilians.npc.spawn.CivilianSpawnManager;
import org.luckyraven.gangland.civilians.npc.state.behavior.CivilianCombatBehavior;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.npc.NpcRole;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcSquad;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Commander down - the squad holds for 5 seconds and one guard shouts")
class CommanderDownTest {

	private static final NpcRole COMMANDER = new NpcRole("Commander", "Commander", NpcFanPlacement.ANY, null, null,
	                                                     1.0, 10, null, 1.0, 0, null, 0, 0, false, true);

	private final AtomicLong now = new AtomicLong(1_000_000L);

	private CivilianMessages civilianMessages;
	private CivilianService  service;
	private SquadHold        hold;
	private World            world;
	private Player           listenerPlayer;

	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		field.set(null, "$");
	}

	@BeforeEach
	void setUp() {
		civilianMessages = mock(CivilianMessages.class);
		when(civilianMessages.lines(any())).thenReturn(List.of());
		when(civilianMessages.lines("Format")).thenReturn(List.of("%line%"));
		when(civilianMessages.lines("Commander_Down")).thenReturn(List.of("The boss is down!"));
		service = new CivilianService(mock(JavaPlugin.class), mock(CiviliansLoader.class), mock(NpcMarkManager.class),
		                              mock(CivilianSettings.class), mock(CivilianNpcFactory.class),
		                              mock(CivilianSpawnManager.class), mock(CivilianNpcRegistry.class),
		                              civilianMessages);
		service.civiliansConfig = new CiviliansConfig(List.of(), List.of(), Map.of(), Map.of(), true, 20,
		                                              CiviliansConfig.DEFAULT_SHOUTS);
		hold                    = new SquadHold(now::get);
		service.squadHold       = hold;

		world          = mock(World.class);
		listenerPlayer = mock(Player.class);
		when(listenerPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
		when(listenerPlayer.getWorld()).thenReturn(world);
		when(listenerPlayer.getLocation()).thenReturn(new Location(world, 1, 64, 1));
		when(world.getPlayers()).thenReturn(List.of(listenerPlayer));
	}

	@Test
	@DisplayName("the commander dies: the squad holds exactly 5 s with one Commander_Down shout; a plain death starts none")
	void commanderDies_squadHoldsFiveSeconds_oneShout() {
		NpcSquad squad = new NpcSquad();
		squad.setListener(service.squadListener);
		CivilianNpc commander = civilian(COMMANDER);
		CivilianNpc guard     = civilian(NpcRole.NONE);
		CivilianNpc rifleman  = civilian(NpcRole.NONE);
		squad.add(commander);
		squad.add(guard);
		squad.add(rifleman);

		squad.memberDown(rifleman); // a plain guard going down starts no hold
		assertFalse(hold.holds(squad));
		verify(listenerPlayer, never()).sendMessage(any(String.class));

		squad.memberDown(commander);

		assertTrue(hold.holds(squad));
		now.addAndGet(4_999);
		assertTrue(hold.holds(squad), "still holding just before 5 s");
		now.addAndGet(1);
		assertFalse(hold.holds(squad), "released at 5 s");
		verify(listenerPlayer, times(1)).sendMessage(any(String.class));
	}

	@Test
	@DisplayName("a holding member does not pursue; it pursues again once the hold is over")
	void holdingMember_doesNotAdvanceUntilReleased() {
		NpcSquad squad = new NpcSquad();
		hold.start(squad, SquadHold.COMMANDER_DOWN_MS);

		UUID         targetId = UUID.randomUUID();
		LivingEntity target   = mock(LivingEntity.class);
		when(target.getUniqueId()).thenReturn(targetId);
		when(target.getLocation()).thenReturn(new Location(world, 5, 64, 5));
		squad.reportSighting(new Location(world, 5, 64, 5));

		CivilianNpc member = civilian(NpcRole.NONE);
		when(member.getTargetEntity()).thenReturn(target);
		when(member.getSquad()).thenReturn(squad);
		when(member.getSquadTargetId()).thenReturn(targetId);
		when(member.getSquadHold()).thenReturn(hold);

		CivilianCombatBehavior behavior = new CivilianCombatBehavior();
		behavior.tick(member);
		now.addAndGet(4_000);
		behavior.tick(member);
		verify(member, never()).pursue(any(), any(), anyDouble());

		now.addAndGet(1_000);
		behavior.tick(member);
		verify(member, atLeastOnce()).pursue(any(), any(), anyDouble());
	}

	private CivilianNpc civilian(NpcRole role) {
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		CivilianAIBehaviorConfig ai = new CivilianAIBehaviorConfig(false, 0, false, 0, true, 4.0, 12.0, 20,
		                                                           NpcDifficulty.NORMAL, 16.0, 20);
		CivilianTypeConfig type = new CivilianTypeConfig("turf_defender", "Guard", EntityType.PLAYER, 25.0, true,
		                                                 new CivilianWearableConfig("", "", "", ""), List.of(),
		                                                 List.of(), List.of(), new CivilianDropConfig(List.of(), 0.0),
		                                                 ai, "turf_defender");
		CivilianNpc npc = mock(CivilianNpc.class);
		when(npc.getEntity()).thenReturn(entity);
		when(npc.getTypeConfig()).thenReturn(type);
		when(npc.getRole()).thenReturn(role);
		return npc;
	}
}
