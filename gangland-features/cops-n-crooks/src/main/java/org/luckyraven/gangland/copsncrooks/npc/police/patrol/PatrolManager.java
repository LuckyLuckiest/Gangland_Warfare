package org.luckyraven.gangland.copsncrooks.npc.police.patrol;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.npc.police.spawn.CopSpawnManager;
import org.luckyraven.gangland.copsncrooks.station.StationRegistry;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.entity.NpcMarkManager;

import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The beat cops (0.20.0 lane patrol): groupless tier cops spawned at a station anchor while a player is near, driven
 * by their own repeating main-thread task, despawned when nobody is near (and on shutdown/reload). They belong to no
 * {@code CopGroup}; {@code CopManager.addCopResolver(this::beatCopOf)} makes the cop listeners treat them as cops.
 * Spawned only through {@link CopSpawnManager#spawnAtLocation} (the factory sets the POLICE mark and SHOULD_SAVE false).
 */
public class PatrolManager implements BeanLifecycle {

	/**
	 * @param players the players that count as "near"
	 * @param isNpc Citizens NPCs never count
	 * @param citizensAvailable whether Citizens is present (absent: no beats)
	 * @param clock milliseconds
	 */
	public PatrolManager(JavaPlugin plugin, Supplier<PatrolSettings> settings, StationRegistry stations,
	                     CopSpawnManager spawner, NpcMarkManager markManager, CopPerception perception,
	                     Supplier<Collection<? extends Player>> players, Predicate<Entity> isNpc,
	                     BooleanSupplier citizensAvailable, LongSupplier clock) {
	}

	/** Spawn / keep / despawn the beat cops of every station (called every 20 ticks). */
	public void manage() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** One AI tick for every live beat cop (called every 10 ticks): resolves each cop's target and runs its behaviour. */
	public void aiTick() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Every live beat cop, all stations. */
	public List<CopNpc> beatCops() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The beat cop whose body is {@code entity}, or {@code null}: the {@code CopManager.addCopResolver} function. */
	public @Nullable CopNpc beatCopOf(@Nullable Entity entity) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Whether some live beat cop within {@code range} of {@code player} acquires them (darkness and disguise apply). */
	public boolean beatCopSees(Player player, double range) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Despawns every beat cop through {@code markManager} and forgets them. */
	public void despawnAll() {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Starts the repeating main-thread task. */
	@Override
	public void onInitialize(boolean firstLoad) {
	}

	/** Stops the task and despawns every beat cop (never leave Citizens NPCs behind). */
	@Override
	public void onClear() {
	}

	@Override
	public void onShutdown() {
	}
}
