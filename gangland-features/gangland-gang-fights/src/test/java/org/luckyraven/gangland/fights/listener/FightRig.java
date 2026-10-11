package org.luckyraven.gangland.fights.listener;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.fights.FightRules;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.gangland.fights.arena.ArenaBox;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.EscrowResult;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightClock;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.gangland.fights.fight.FightSettings;
import org.luckyraven.gangland.fights.fight.FightSettlement;
import org.luckyraven.gangland.fights.fight.FightWorld;
import org.luckyraven.gangland.fights.fight.PlayerSnapshot;
import org.luckyraven.gangland.gang.combat.CombatTag;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A real {@link FightManager} over mocked collaborators, driven to a 1 v 1 fight in the arena "pit" so the listener
 * tests exercise the real state machine. Vipers (gang 1, player {@link #a}) against Rats (gang 2, player {@link #b}).
 */
final class FightRig {

	static final int VIPERS = 1;
	static final int RATS   = 2;

	static final ArenaBox BOX = ArenaBox.of("world", 0, 0, 0, 20, 20, 20);

	final ArenaPoint spawnA = new ArenaPoint("world", 2, 1, 2, 0, 0);
	final ArenaPoint spawnB = new ArenaPoint("world", 18, 1, 18, 0, 0);
	final ArenaPoint exit   = new ArenaPoint("world", 50, 1, 50, 0, 0);
	final Arena      arena  = new Arena("pit", BOX, List.of(spawnA), List.of(spawnB), exit, true);

	final World  world  = mock(World.class);
	final Server server = mock(Server.class);

	final Player a = player();
	final Player b = player();

	final FightWorld      fightWorld = mock(FightWorld.class);
	final FightSettlement settlement = mock(FightSettlement.class);
	final ArenaRegistry   arenas     = mock(ArenaRegistry.class);
	final FightRules      rules      = mock(FightRules.class);
	final FightMessages   messages   = mock(FightMessages.class);
	final CombatTag       combatTag  = new CombatTag();
	final List<Runnable>  tasks      = new ArrayList<>();
	final FightManager    manager;

	FightRig() {
		org.mockito.Mockito.doReturn(List.of()).when(server).getOnlinePlayers();
		when(world.getName()).thenReturn("world");
		when(messages.text(anyString(), any(Object[].class))).thenAnswer(i -> i.getArgument(0));
		when(rules.getMaxLeaveWarnings()).thenReturn(3);
		when(rules.isProtectBlocks()).thenReturn(true);
		when(rules.getCommandBlockMode()).thenReturn("ALLOWLIST");
		when(rules.getAllowedCommands()).thenReturn(List.of("glw gang fight", "msg"));
		when(rules.getBlockedCommands()).thenReturn(List.of("tp"));

		when(arenas.freeArena(any())).thenReturn(Optional.of(arena));
		when(arenas.byName("pit")).thenReturn(Optional.of(arena));
		when(arenas.at(any())).thenAnswer(i -> {
			Location at = i.getArgument(0);
			return at != null && at.getWorld() != null
			       && BOX.contains(at.getWorld().getName(), at.getX(), at.getY(), at.getZ())
			       ? Optional.of(arena) : Optional.empty();
		});

		when(fightWorld.gangOf(a.getUniqueId())).thenReturn(Optional.of(VIPERS));
		when(fightWorld.gangOf(b.getUniqueId())).thenReturn(Optional.of(RATS));
		when(fightWorld.gangByName("Rats")).thenReturn(Optional.of(RATS));
		when(fightWorld.gangByName("Vipers")).thenReturn(Optional.of(VIPERS));
		when(fightWorld.gangName(VIPERS)).thenReturn("Vipers");
		when(fightWorld.gangName(RATS)).thenReturn("Rats");
		when(fightWorld.mayFight(any())).thenReturn(true);
		when(fightWorld.mayWithdraw(any())).thenReturn(true);
		when(fightWorld.bank(anyInt())).thenReturn(100000D);
		when(fightWorld.gangAgeDays(anyInt())).thenReturn(30L);
		when(fightWorld.online(any())).thenReturn(true);
		when(fightWorld.teleport(any(), any())).thenReturn(true);
		when(fightWorld.snapshot(any())).thenAnswer(i -> new PlayerSnapshot(i.getArgument(0), exit, 20, 20));
		when(fightWorld.officers(anyInt())).thenReturn(List.of());
		when(fightWorld.onlineMembers(anyInt())).thenReturn(List.of());
		when(settlement.escrow(any())).thenReturn(EscrowResult.success());

		FightClock clock = new FightClock() {
			@Override
			public Instant now() {
				return Instant.EPOCH;
			}

			@Override
			public void schedule(Runnable task, Duration delay) {
				tasks.add(task);
			}
		};
		FightSettings settings = new FightSettings(60, 60, 10, 10, 5, 1, 5, true, 60, "MOST_ALIVE", 0, 100000, 50,
		                                           1000, 24, 7);
		manager = new FightManager(clock, settlement, combatTag, arenas, fightWorld, () -> settings);
	}

	Player player() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getHealth()).thenReturn(20D);
		when(player.getServer()).thenReturn(server);
		return player;
	}

	Location at(double x, double y, double z) {
		return new Location(world, x, y, z);
	}

	/** Proposed, accepted and both fighters enlisted: the countdown is on. */
	Fight countdown(double bet) {
		manager.propose(a.getUniqueId(), "Rats", 1, bet);
		manager.accept(b.getUniqueId(), "Vipers");
		manager.enlist(a.getUniqueId());
		manager.enlist(b.getUniqueId());
		return manager.fightOf(a.getUniqueId()).orElseThrow();
	}

	/** The countdown has run out: the fight is RUNNING. */
	Fight running(double bet) {
		Fight fight = countdown(bet);
		tasks.get(tasks.size() - 1).run();
		return fight;
	}
}
