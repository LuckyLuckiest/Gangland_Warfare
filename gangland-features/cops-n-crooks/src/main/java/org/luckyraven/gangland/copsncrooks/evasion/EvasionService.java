package org.luckyraven.gangland.copsncrooks.evasion;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.events.evasion.EvasionStateChangeEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Line-of-sight evasion and search zone (0.12 F2): escaping becomes a skill. While a wanted player's cop squad has a
 * fresh sighting, stars are solid; once nobody has seen the player for {@code Lost_Sight_Seconds}, a search zone opens
 * on the last known position and an {@link EvasionClock} counts toward dropping a star (faster outside the zone).
 * <p>
 * Evasion drives a chase only while {@link #handles(Player) handling} it: evasion enabled, the player tracked (wanted)
 * and a cop group that has had at least one cop ({@link CopGroup#isStaffed()}). For every other wanted player the old
 * repeating decay ({@code WantedExecutor}) stays the fallback; {@code EvasionListener} cancels that timer's
 * {@code WantedEvent} only for handled players.
 * <p>
 * One sync task ticks every tracked player once a second. It starts on the first {@link #track(Player)} and stops when
 * nobody is tracked or the bean is cleared. A tick is skipped (clock paused, state kept) while the player is offline,
 * dead, restrained or downed.
 */
@CustomLog
public class EvasionService implements BeanLifecycle {

	private static final long   TICK_PERIOD_TICKS = 20L;
	private static final double TICK_SECONDS      = TICK_PERIOD_TICKS / 20D;

	private final JavaPlugin          plugin;
	private final CopManager          copManager;
	private final UserManager<Player> users;
	private final DetainmentService   detainment;
	private final WantedSettings      wantedSettings;
	private final Map<UUID, Tracked>  tracked;

	private BukkitTask task;

	public EvasionService(JavaPlugin plugin, CopManager copManager, UserManager<Player> users,
	                      DetainmentService detainment, WantedSettings wantedSettings) {
		this.plugin         = plugin;
		this.copManager     = copManager;
		this.users          = users;
		this.detainment     = detainment;
		this.wantedSettings = wantedSettings;
		this.tracked        = new ConcurrentHashMap<>();
	}

	/**
	 * Starts watching a player who just became wanted.
	 *
	 * @param player the wanted player
	 */
	public void track(Player player) {
		tracked.computeIfAbsent(player.getUniqueId(), id -> new Tracked());
		startTask();
	}

	/**
	 * Stops watching a player (chase over or quit). Fires an {@link EvasionStateChangeEvent} to NONE if evasion was
	 * driving the chase.
	 *
	 * @param playerId the player UUID
	 */
	public void untrack(UUID playerId) {
		Tracked entry = tracked.remove(playerId);
		if (tracked.isEmpty()) stopTask();
		if (entry == null) return;

		EvasionState oldState = entry.clock.getState();
		entry.clock.reset();
		entry.snapshot = EvasionSnapshot.none();

		Player player = Bukkit.getPlayer(playerId);
		if (player != null) fireChange(player, oldState, EvasionSnapshot.none());
	}

	/**
	 * Whether evasion drives this player's chase: evasion enabled, the player tracked and a cop group that has had at
	 * least one cop. Read-only and thread-safe: {@code WantedEvent} is fired off the main thread.
	 *
	 * @param owner the wanted player, may be null
	 *
	 * @return true when evasion replaces the old decay timer for this player
	 */
	public boolean handles(@Nullable Player owner) {
		if (owner == null || !Settings.isWantedEvasionEnabled()) return false;

		UUID playerId = owner.getUniqueId();
		return tracked.containsKey(playerId) && isStaffed(copManager.getGroup(playerId));
	}

	/**
	 * The player's current evasion state; NONE when untracked or not handled.
	 *
	 * @param playerId the player UUID
	 *
	 * @return the state
	 */
	public EvasionState getState(UUID playerId) {
		return getSnapshot(playerId).state();
	}

	/**
	 * The player's evasion state as of the last tick, for the HUD.
	 *
	 * @param playerId the player UUID
	 *
	 * @return the snapshot, {@link EvasionSnapshot#none()} when untracked or not handled
	 */
	public EvasionSnapshot getSnapshot(UUID playerId) {
		Tracked entry = tracked.get(playerId);
		return entry == null ? EvasionSnapshot.none() : entry.snapshot;
	}

	@Override
	public void onPreClear() {
		stopTask();
	}

	@Override
	public void onClear() {
		stopTask();
		tracked.clear();
	}

	@Override
	public void onShutdown() {
		stopTask();
		tracked.clear();
	}

	private void tickAll() {
		EvasionConfig config = EvasionConfig.fromSettings();

		// Copy: a drop to zero stars ends the chase and untracks the player while we iterate
		List<UUID> ids = new ArrayList<>(tracked.keySet());
		for (UUID playerId : ids) {
			try {
				tick(playerId, config);
			} catch (RuntimeException exception) {
				log.error("Evasion tick failed for {}", playerId, exception);
			}
		}

		if (tracked.isEmpty()) stopTask();
	}

	private void tick(UUID playerId, EvasionConfig config) {
		Tracked entry = tracked.get(playerId);
		if (entry == null) return;

		Player player = Bukkit.getPlayer(playerId);
		if (player == null || !player.isOnline()) return;

		User<Player> user = users.getUser(player);
		if (user == null) return;

		Wanted wanted = user.getWanted();
		if (!wanted.isWanted()) {
			untrack(playerId);
			return;
		}

		// Paused: the clock neither runs nor resets while the player cannot run
		if (player.isDead() || detainment.isRestrained(player) || DownedPlayerRegistry.isDowned(playerId)) return;

		EvasionState oldState = entry.clock.getState();
		CopGroup     group    = copManager.getGroup(playerId);

		if (!config.enabled() || !isStaffed(group)) {
			if (oldState == EvasionState.NONE) return;

			entry.clock.reset();
			entry.snapshot = EvasionSnapshot.none();
			fireChange(player, oldState, entry.snapshot);
			return;
		}

		NpcSquad squad = group.getSquad();
		int      level = wanted.getLevel();
		boolean  due   = entry.clock.tick(squad.millisSinceSighting(), squad.lastKnownLocation(), player.getLocation(),
		                                  level, config, TICK_SECONDS);

		entry.snapshot = entry.clock.snapshot(level, config);
		if (entry.clock.getState() != oldState) fireChange(player, oldState, entry.snapshot);

		if (!due) return;

		drop(user, wanted, level, config);

		// Zero stars ends the chase, and WantedEndEvent already untracked the player
		Tracked current = tracked.get(playerId);
		if (current != entry) return;

		entry.clock.afterDrop();
		entry.snapshot = entry.clock.snapshot(wanted.getLevel(), config);
	}

	private void drop(User<Player> user, Wanted wanted, int level, EvasionConfig config) {
		int newLevel = config.dropMode() == EvasionDropMode.ALL_STARS ? 0 : Math.max(0, level - 1);

		wanted.setLevel(newLevel);
		if (wanted.getLevel() >= level) return; // a listener cancelled the change

		if (!wanted.isWanted()) wanted.stopTimer();

		int    current = wanted.getLevel();
		String message = wantedSettings.getWantedDecreasedMessageTemplate()
		                               .replace("%level%", String.valueOf(current))
		                               .replace("%stars%", Wanted.buildStars(current, wanted.getMaxLevel()));

		user.sendMessage(message);
	}

	private void fireChange(Player player, EvasionState oldState, EvasionSnapshot snapshot) {
		if (oldState == snapshot.state()) return;

		Bukkit.getPluginManager().callEvent(new EvasionStateChangeEvent(player, oldState, snapshot.state(), snapshot));
	}

	private void startTask() {
		if (task != null) return;

		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
	}

	private void stopTask() {
		if (task == null) return;

		task.cancel();
		task = null;
	}

	private static boolean isStaffed(@Nullable CopGroup group) {
		return group != null && group.isStaffed();
	}

	/**
	 * One tracked player: the clock is touched on the main thread only; the snapshot is published for readers on
	 * any thread.
	 */
	private static final class Tracked {

		private final    EvasionClock    clock    = new EvasionClock();
		private volatile EvasionSnapshot snapshot = EvasionSnapshot.none();
	}
}
