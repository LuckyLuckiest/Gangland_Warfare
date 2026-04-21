package me.luckyraven.copsncrooks.npc.trader;

import lombok.CustomLog;
import me.luckyraven.copsncrooks.npc.trader.config.TraderSettings;
import me.luckyraven.copsncrooks.npc.trader.mood.MoodService;
import me.luckyraven.copsncrooks.npc.trader.respawn.TraderRespawnService;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitDefinition;
import me.luckyraven.copsncrooks.npc.trader.trait.TraderTraitRegistry;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.autowire.bean.BeanLifecycle;
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

import java.util.*;
import java.util.function.Consumer;

@CustomLog
public final class TraderManager implements BeanLifecycle {

	private static final long HEAD_TRACK_PERIOD     = 2L;
	private static final long POSITION_RESET_PERIOD = 20L;

	private final JavaPlugin              plugin;
	private final IRepository<TraderData> repository;
	private final TraderSettings          settings;
	private final TraderTraitRegistry     traitRegistry;
	private final MoodService             moodService;
	private final TraderRespawnService    respawnService;

	private final Map<UUID, TraderNpc> byId = new HashMap<>();

	private BukkitTask headTrackTask;
	private BukkitTask positionResetTask;

	public TraderManager(JavaPlugin plugin,
	                     IRepository<TraderData> repository,
	                     TraderSettings settings,
	                     TraderTraitRegistry traitRegistry,
	                     MoodService moodService,
	                     TraderRespawnService respawnService) {
		this.plugin         = plugin;
		this.repository     = repository;
		this.settings       = settings;
		this.traitRegistry  = traitRegistry;
		this.moodService    = moodService;
		this.respawnService = respawnService;

		this.repository.setDataSupplier(this::snapshotData);
	}

	public Collection<TraderData> snapshotData() {
		List<TraderData> snapshot = new ArrayList<>(byId.size());
		for (TraderNpc npc : byId.values()) snapshot.add(npc.getData());
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

	public TraderNpc spawn(TraderData data) {
		TraderTraitDefinition trait = resolveTrait(data);
		if (trait == null) {
			log.warn("Cannot spawn trader {} — no trait registered (id={}, fallback={})",
			         data.getId(), data.getTraitId(), settings.getFallbackTraitId());
			return null;
		}

		TraderNpc existing = byId.get(data.getId());
		if (existing != null && existing.isAlive()) return existing;

		TraderNpc npc = TraderNpc.spawn(data, trait.profile());
		byId.put(data.getId(), npc);
		return npc;
	}

	public void create(TraderData data) {
		repository.save(data);
		spawn(data);
	}

	public void remove(UUID traderId) {
		TraderNpc npc = byId.remove(traderId);
		if (npc != null) npc.destroy();

		moodService.clearTrader(traderId);

		repository.loadAll()
				.stream()
				.filter(d -> d.getId().equals(traderId))
				.findFirst()
				.ifPresent(repository::delete);
	}

	public boolean retargetShop(UUID traderId, String newShopKey) {
		TraderNpc npc = byId.get(traderId);
		if (npc == null) return false;
		npc.getData().setShopKey(newShopKey);
		repository.save(npc.getData());
		moodService.clearTrader(traderId);
		return true;
	}

	public boolean retargetTrait(UUID traderId, String newTraitId) {
		TraderNpc npc = byId.get(traderId);
		if (npc == null) return false;
		npc.getData().setTraitId(newTraitId);
		repository.save(npc.getData());
		moodService.clearTrader(traderId);
		return true;
	}

	public boolean rename(UUID traderId, String newDisplayName) {
		TraderNpc npc = byId.get(traderId);
		if (npc == null) return false;
		npc.getData().setDisplayName(newDisplayName);
		if (npc.getNpc() != null) {
			npc.getNpc().setName(newDisplayName != null ? newDisplayName : "Trader");
		}
		repository.save(npc.getData());
		return true;
	}

	public void onTraderKilled(UUID traderId) {
		TraderNpc npc = byId.remove(traderId);
		if (npc == null) return;

		TraderData data = npc.getData();
		npc.destroy();
		moodService.clearTrader(traderId);

		respawnService.schedule(data, this::spawn);
	}

	@Nullable
	public TraderNpc getByEntity(Entity entity) {
		if (entity == null) return null;

		NPC citizensNpc = CitizensAPI.getNPCRegistry().getNPC(entity);
		if (citizensNpc == null) return null;
		if (!citizensNpc.data().has(TraderNpc.METADATA_TRADER_ID)) return null;

		try {
			UUID traderId = UUID.fromString(citizensNpc.data().get(TraderNpc.METADATA_TRADER_ID));
			return byId.get(traderId);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Nullable
	public TraderNpc get(UUID traderId) {
		return byId.get(traderId);
	}

	@Nullable
	public TraderNpc findTargetedTrader(Player player, double maxDistance) {
		Location eye       = player.getEyeLocation();
		Vector   direction = eye.getDirection();

		RayTraceResult result = player.getWorld().rayTraceEntities(eye, direction, maxDistance,
		                                                           entity -> !entity.equals(player));
		if (result == null || result.getHitEntity() == null) return null;
		return getByEntity(result.getHitEntity());
	}

	@Nullable
	public TraderTraitDefinition resolveTrait(TraderData data) {
		TraderTraitDefinition trait = traitRegistry.get(data.getTraitId());
		if (trait != null) return trait;

		TraderTraitDefinition fallback = traitRegistry.get(settings.getFallbackTraitId());
		if (fallback != null) {
			log.warn("Trader {} references unknown trait '{}'; using fallback '{}'",
			         data.getId(), data.getTraitId(), settings.getFallbackTraitId());
			return fallback;
		}

		return null;
	}

	public void forEach(Consumer<TraderNpc> consumer) {
		byId.values().forEach(consumer);
	}

	private void spawnAllFromRepository() {
		int spawned = 0;
		for (TraderData data : repository.loadAll()) {
			if (spawn(data) != null) spawned++;
		}
		log.debug("Spawned {} trader(s) from repository", spawned);
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

		for (TraderNpc trader : byId.values()) {
			if (!trader.isAlive()) continue;

			Entity entity = trader.getNpc().getEntity();
			if (entity == null) continue;

			Player closest = findClosestPlayer(entity.getLocation(), radiusSquared);
			// Citizens' faceLocation uses the NPC's body (feet) as reference, not its eyes — adding any
			// Y offset here makes the NPC angle upward. Target the player's feet so same-level
			// interactions produce a level gaze.
			if (closest != null) trader.faceLocation(closest.getLocation());
		}
	}

	private void tickPositionReset() {
		for (TraderNpc trader : byId.values()) {
			if (trader.isAlive()) trader.resetPosition();
		}
	}

	private void despawnAll() {
		for (TraderNpc trader : byId.values()) {
			try {
				trader.destroy();
			} catch (Exception e) {
				log.warn("Failed to despawn trader {}: {}", trader.getData().getId(), e.getMessage());
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
