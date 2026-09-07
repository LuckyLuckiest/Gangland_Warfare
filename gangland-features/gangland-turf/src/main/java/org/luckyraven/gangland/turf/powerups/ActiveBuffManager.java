package org.luckyraven.gangland.turf.powerups;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory cache of {@link ActiveTurfBuff} entries grouped by turf id, plus a 1-Hz scheduler that prunes expired buffs
 * (and deletes them from the repository so a restart doesn't see them again). All writes go through here so panel buys,
 * prune, and panel reads see a single consistent state.
 *
 * <p>Effect aggregation:
 * <ul>
 *   <li>{@link EffectType#INCOME_MULTIPLIER} — multiplied (stacking 1.25 × 1.25 = 1.5625).</li>
 *   <li>{@link EffectType#GARRISON_DISCOUNT} — multiplied (stacking 0.8 × 0.8 = 0.64).</li>
 *   <li>{@link EffectType#CAPTURE_DEFENSE_BONUS} — summed (additive, e.g. 1.0 + 1.0 = 2.0 phantom defenders).</li>
 * </ul>
 */
@CustomLog
public final class ActiveBuffManager {

	private final JavaPlugin                         plugin;
	private final ActiveBuffRepositoryContract       repository;
	private final Map<Integer, List<ActiveTurfBuff>> byTurf = new HashMap<>();
	private final AtomicLong                         nextId = new AtomicLong(1L);
	private       BukkitTask                         pruneTask;

	public ActiveBuffManager(JavaPlugin plugin, ActiveBuffRepositoryContract repository) {
		this.plugin     = plugin;
		this.repository = repository;
	}

	public void initialize() {
		byTurf.clear();
		long now     = System.currentTimeMillis();
		long highest = 0L;
		for (ActiveTurfBuff buff : repository.loadAll()) {
			if (buff.isExpired(now)) {
				repository.delete(buff);
				continue;
			}
			byTurf.computeIfAbsent(buff.getTurfId(), k -> new ArrayList<>()).add(buff);
			if (buff.getId() > highest) {
				highest = buff.getId();
			}
		}
		nextId.set(highest + 1);

		// Per feedback_repository_data_supplier: every AbstractRepository needs setDataSupplier wired in
		// initialize(), or autosave/shutdown throws No data supplier set.
		repository.setDataSupplier(this::snapshotAll);

		log.debug("Loaded {} active turf buff(s) across {} turf(s)",
		          byTurf.values()
						  .stream().mapToInt(List::size).sum(), byTurf.size());

		pruneTask = Bukkit.getScheduler().runTaskTimer(plugin, this::prune, 20L, 20L);
	}

	public void shutdown() {
		if (pruneTask != null) {
			pruneTask.cancel();
			pruneTask = null;
		}
	}

	/**
	 * Activate a fresh buff on the given turf. The new buff is persisted immediately (so a restart preserves the
	 * remaining duration) and added to the in-memory map.
	 */
	public ActiveTurfBuff activate(int turfId, PowerupDefinition def) {
		long now = System.currentTimeMillis();
		ActiveTurfBuff buff = new ActiveTurfBuff(
				nextId.getAndIncrement(),
				turfId,
				def.id(),
				def.effectType(),
				def.magnitude(),
				now + def.durationSeconds() * 1000L);
		byTurf.computeIfAbsent(turfId, k -> new ArrayList<>()).add(buff);
		repository.save(buff);
		return buff;
	}

	/**
	 * Aggregated effect strength for the given turf and effect type at the current instant. Multiplicative effects
	 * (income / garrison discount) start at 1.0 and multiply each active magnitude in. Additive effects (capture
	 * defense bonus) start at 0.0 and sum. No active buffs of that type → identity value (1.0 for multipliers, 0.0 for
	 * additives) so callers can blindly multiply / add without branching.
	 */
	public double effectiveMultiplier(int turfId, EffectType type) {
		List<ActiveTurfBuff> list = byTurf.get(turfId);
		if (list == null || list.isEmpty()) {
			return type == EffectType.CAPTURE_DEFENSE_BONUS ? 0.0 : 1.0;
		}
		double now    = System.currentTimeMillis();
		double result = type == EffectType.CAPTURE_DEFENSE_BONUS ? 0.0 : 1.0;
		for (ActiveTurfBuff buff : list) {
			if (buff.getEffectType() != type || buff.isExpired((long) now)) continue;
			if (type == EffectType.CAPTURE_DEFENSE_BONUS) {
				result += buff.getMagnitude();
			} else {
				result *= buff.getMagnitude();
			}
		}
		return result;
	}

	public List<ActiveTurfBuff> active(int turfId) {
		List<ActiveTurfBuff> list = byTurf.get(turfId);
		return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
	}

	private Collection<ActiveTurfBuff> snapshotAll() {
		List<ActiveTurfBuff> all = new ArrayList<>();
		for (List<ActiveTurfBuff> list : byTurf.values()) {
			all.addAll(list);
		}
		return all;
	}

	private void prune() {
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Integer, List<ActiveTurfBuff>>> entries = byTurf.entrySet().iterator();
		     entries.hasNext(); ) {
			Map.Entry<Integer, List<ActiveTurfBuff>> entry = entries.next();
			List<ActiveTurfBuff>                     list  = entry.getValue();
			for (Iterator<ActiveTurfBuff> it = list.iterator(); it.hasNext(); ) {
				ActiveTurfBuff buff = it.next();
				if (buff.isExpired(now)) {
					it.remove();
					repository.delete(buff);
				}
			}
			if (list.isEmpty()) {
				entries.remove();
			}
		}
	}
}
