package org.luckyraven.gangland.copsncrooks.npc.banker;

import lombok.CustomLog;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.banker.config.BankerSettings;
import org.luckyraven.gangland.core.bean.BeanLifecycle;
import org.luckyraven.gangland.persistence.repository.IRepository;

import java.util.*;
import java.util.function.Consumer;

@CustomLog
public final class BankerManager implements BeanLifecycle {

	private static final long HEAD_TRACK_PERIOD     = 2L;
	private static final long POSITION_RESET_PERIOD = 20L;

	private final JavaPlugin              plugin;
	private final IRepository<BankerData> repository;
	private final BankerSettings          settings;

	private final Map<UUID, BankerNpc> byId = new HashMap<>();

	private BukkitTask headTrackTask;
	private BukkitTask positionResetTask;

	public BankerManager(JavaPlugin plugin, IRepository<BankerData> repository, BankerSettings settings) {
		this.plugin     = plugin;
		this.repository = repository;
		this.settings   = settings;

		this.repository.setDataSupplier(this::snapshotData);
	}

	public Collection<BankerData> snapshotData() {
		List<BankerData> snapshot = new ArrayList<>(byId.size());
		for (BankerNpc npc : byId.values()) snapshot.add(npc.getData());
		return snapshot;
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		Bukkit.getScheduler().runTaskLater(plugin, this::spawnAllFromRepository, 40L);
		startTasks();
	}

	@Override
	public void onPreClear() {
		stopTasks();
		despawnAll();
	}

	@Override
	public void onClear() {
		byId.clear();
	}

	@Override
	public void onShutdown() {
		stopTasks();
		despawnAll();
		byId.clear();
	}

	public BankerNpc spawn(BankerData data) {
		BankerNpc existing = byId.get(data.getId());
		if (existing != null && existing.isAlive()) return existing;

		BankerNpc npc = BankerNpc.spawn(data, settings);
		byId.put(data.getId(), npc);
		return npc;
	}

	public void create(BankerData data) {
		repository.save(data);
		spawn(data);
	}

	public void remove(UUID bankerId) {
		BankerNpc npc = byId.remove(bankerId);
		if (npc != null) npc.destroy();

		repository.loadAll()
				.stream()
				.filter(d -> d.getId().equals(bankerId))
				.findFirst()
				.ifPresent(repository::delete);
	}

	public boolean rename(UUID bankerId, String newDisplayName) {
		BankerNpc npc = byId.get(bankerId);
		if (npc == null) return false;
		npc.getData().setDisplayName(newDisplayName);
		if (npc.getNpc() != null) {
			npc.getNpc().setName(newDisplayName != null ? newDisplayName : "Banker");
		}
		repository.save(npc.getData());
		return true;
	}

	@Nullable
	public BankerNpc getByEntity(Entity entity) {
		if (entity == null) return null;

		NPC citizensNpc = CitizensAPI.getNPCRegistry().getNPC(entity);
		if (citizensNpc == null) return null;
		if (!citizensNpc.data().has(BankerNpc.METADATA_BANKER_ID)) return null;

		try {
			UUID bankerId = UUID.fromString(citizensNpc.data().get(BankerNpc.METADATA_BANKER_ID));
			return byId.get(bankerId);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Nullable
	public BankerNpc get(UUID bankerId) {
		return byId.get(bankerId);
	}

	@Nullable
	public BankerNpc findTargetedBanker(Player player, double maxDistance) {
		Location eye       = player.getEyeLocation();
		Vector   direction = eye.getDirection();

		RayTraceResult result = player.getWorld().rayTraceEntities(eye, direction, maxDistance,
		                                                           entity -> !entity.equals(player));
		if (result == null || result.getHitEntity() == null) return null;
		return getByEntity(result.getHitEntity());
	}

	public void forEach(Consumer<BankerNpc> consumer) {
		byId.values().forEach(consumer);
	}

	private void spawnAllFromRepository() {
		int spawned = 0;
		for (BankerData data : repository.loadAll()) {
			if (spawn(data) != null) spawned++;
		}
		log.debug("Spawned {} banker(s) from repository", spawned);
	}

	private void startTasks() {
		headTrackTask     = Bukkit.getScheduler().runTaskTimer(plugin, this::tickHeadTrack,
		                                                       HEAD_TRACK_PERIOD, HEAD_TRACK_PERIOD);
		positionResetTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPositionReset,
		                                                       POSITION_RESET_PERIOD, POSITION_RESET_PERIOD);
	}

	private void stopTasks() {
		if (headTrackTask != null) {
			headTrackTask.cancel();
			headTrackTask = null;
		}
		if (positionResetTask != null) {
			positionResetTask.cancel();
			positionResetTask = null;
		}
	}

	private void tickHeadTrack() {
		double radius        = settings.getHeadTrackRadius();
		double radiusSquared = radius * radius;

		for (BankerNpc banker : byId.values()) {
			if (!banker.isAlive()) continue;

			Entity entity = banker.getNpc().getEntity();
			if (entity == null) continue;

			Player closest = findClosestPlayer(entity.getLocation(), radiusSquared);
			if (closest != null) banker.faceLocation(closest.getLocation());
		}
	}

	private void tickPositionReset() {
		for (BankerNpc banker : byId.values()) {
			if (banker.isAlive()) banker.resetPosition();
		}
	}

	private void despawnAll() {
		for (BankerNpc banker : byId.values()) {
			try {
				banker.destroy();
			} catch (Exception e) {
				log.warn("Failed to despawn banker {}: {}", banker.getData().getId(), e.getMessage());
			}
		}
	}

	@Nullable
	private Player findClosestPlayer(Location location, double radiusSquared) {
		Player closest     = null;
		double closestDist = radiusSquared;

		for (Player player : location.getWorld().getPlayers()) {
			double dist = player.getLocation().distanceSquared(location);
			if (dist < closestDist) {
				closest     = player;
				closestDist = dist;
			}
		}
		return closest;
	}

}
