package org.luckyraven.gangland.fights.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.Fight;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.fights.fight.FightMessages;
import org.luckyraven.gangland.fights.fight.FightState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lookups shared by the fight guard listeners. */
final class Fighters {

	private static final long MESSAGE_GAP_MILLIS = 2000L;

	private static final Map<UUID, Long> LAST_MESSAGE = new HashMap<>();

	private Fighters() {
	}

	/** The fight has its fighters standing in the arena: countdown, running or the result pause. */
	static boolean inArena(Fight fight) {
		FightState state = fight.state();
		return state == FightState.COUNTDOWN || state == FightState.RUNNING || state == FightState.RESULT;
	}

	/** The fight the player is still fighting in (in the arena, not eliminated), else null. */
	static @Nullable Fight active(FightManager manager, UUID player) {
		Fight fight = manager.fightOf(player).orElse(null);
		return fight != null && inArena(fight) && !fight.eliminated(player) ? fight : null;
	}

	/** The arena holding the location while a fight uses it, else null. */
	static @Nullable Arena busyArenaAt(FightManager manager, ArenaRegistry arenas, @Nullable Location at) {
		Arena arena = arenas.at(at).orElse(null);
		if (arena == null) return null;

		Fight fight = manager.fightInArena(arena.name()).orElse(null);
		return fight != null && inArena(fight) ? arena : null;
	}

	/** Teleports through {@link ExpectedTeleports}; false when the world is not loaded. */
	static boolean teleport(Player player, ArenaPoint to) {
		World world = Bukkit.getWorld(to.world());
		if (world == null) return false;

		Location target = new Location(world, to.x(), to.y(), to.z(), to.yaw(), to.pitch());
		return ExpectedTeleports.run(player.getUniqueId(), () -> player.teleport(target));
	}

	static void tell(Player player, FightMessages messages, String key, Object... pairs) {
		player.sendMessage(messages.text(key, pairs));
	}

	/** Like {@link #tell} but at most once per two seconds per player, for events that fire every tick. */
	static void tellThrottled(Player player, FightMessages messages, String key, Object... pairs) {
		long now = System.currentTimeMillis();
		Long last = LAST_MESSAGE.get(player.getUniqueId());
		if (last != null && now - last < MESSAGE_GAP_MILLIS) return;

		LAST_MESSAGE.put(player.getUniqueId(), now);
		tell(player, messages, key, pairs);
	}
}
