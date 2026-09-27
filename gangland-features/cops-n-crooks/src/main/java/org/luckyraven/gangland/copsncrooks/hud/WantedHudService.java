package org.luckyraven.gangland.copsncrooks.hud;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.copsncrooks.evasion.EvasionService;
import org.luckyraven.gangland.copsncrooks.evasion.EvasionSnapshot;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.bean.Qualifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The wanted HUD (0.12 F3): a per-player boss bar (red while SEEN, yellow with the countdown while SEARCHING),
 * flashing stars, the search-zone particle ring and the escape compass. Reads {@link EvasionService#getSnapshot(UUID)}
 * for state and drives everything from one lazy 20-tick sync task, started on the first {@link #show(Player)} and
 * stopped once no player has a bar. The whole service no-ops when {@code Hud.Enable} is {@code false}.
 */
@CustomLog
public class WantedHudService implements BeanLifecycle {

	private static final long   TASK_PERIOD_TICKS  = 20L;
	private static final long   STAR_LOST_MILLIS   = 2_000L;
	private static final int    RING_MIN_POINTS    = 16;
	private static final int    RING_MAX_POINTS    = 64;
	private static final double RING_VISIBLE_RANGE = 48D;

	private final JavaPlugin          plugin;
	private final EvasionService      evasion;
	private final UserManager<Player> users;
	private final CopLoader           copLoader;
	private final EscapeCompass       compass;

	private final Map<UUID, BossBar> bars;
	private final Map<UUID, Long>    starLostUntil;

	private BukkitTask task;
	private long       tickCount;

	public WantedHudService(JavaPlugin plugin, EvasionService evasion, @Qualifier("online") UserManager<Player> users,
	                        CopLoader copLoader) {
		this.plugin        = plugin;
		this.evasion       = evasion;
		this.users         = users;
		this.copLoader     = copLoader;
		this.compass       = new EscapeCompass();
		this.bars          = new ConcurrentHashMap<>();
		this.starLostUntil = new ConcurrentHashMap<>();
	}

	/**
	 * Creates (or refreshes) {@code player}'s boss bar. Called on every wanted start and evasion state change so the
	 * HUD reflects the new state immediately instead of waiting for the next tick.
	 *
	 * @param player the wanted player
	 */
	public void show(Player player) {
		HudConfig config = config();
		if (!config.isEnabled() || !config.isBossBar()) return;

		bars.computeIfAbsent(player.getUniqueId(), id -> createBar(player));
		startTask();
		refresh(player, config);
	}

	/**
	 * Title, subtitle and sound for a star gained (including the wanted level going from 0 to 1).
	 *
	 * @param player the wanted player
	 * @param newLevel the new wanted level
	 * @param maxLevel the wanted maximum level
	 */
	public void onStarGained(Player player, int newLevel, int maxLevel) {
		HudConfig config = config();
		if (!config.isEnabled()) return;

		show(player);
		if (config.isStarGainTitle()) {
			String title    = GanglandChatUtil.color(config.getStarGainedTitle());
			String subtitle = GanglandChatUtil.color(HudFormat.apply(config.getStarGainedSubtitle(),
			                                                        Wanted.buildStars(newLevel, maxLevel), ""));

			player.sendTitle(title, subtitle, 5, 20, 10);
		}
		player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1F, 1F);
	}

	/**
	 * Shows the green {@code STAR LOST} boss bar title for a couple of seconds before the bar returns to its normal
	 * state.
	 *
	 * @param player the wanted player
	 * @param newLevel the new (lower) wanted level
	 * @param maxLevel the wanted maximum level
	 */
	public void onStarLost(Player player, int newLevel, int maxLevel) {
		HudConfig config = config();
		if (!config.isEnabled()) return;

		starLostUntil.put(player.getUniqueId(), System.currentTimeMillis() + STAR_LOST_MILLIS);
		show(player);
	}

	/**
	 * Removes {@code player}'s boss bar and restores their compass. Called on wanted end, quit, death and downed.
	 *
	 * @param player the player
	 */
	public void hide(Player player) {
		UUID id = player.getUniqueId();

		BossBar bar = bars.remove(id);
		if (bar != null) bar.removeAll();
		starLostUntil.remove(id);
		compass.restore(player);

		if (bars.isEmpty()) stopTask();
	}

	@Override
	public void onPreClear() {
		stopTask();
	}

	@Override
	public void onClear() {
		clearAll();
	}

	@Override
	public void onShutdown() {
		clearAll();
	}

	private void clearAll() {
		stopTask();
		bars.values().forEach(BossBar::removeAll);
		bars.clear();
		starLostUntil.clear();
	}

	private void tickAll() {
		tickCount++;

		HudConfig config = config();
		if (!config.isEnabled()) {
			clearAll();
			return;
		}

		// Copy: hide() removes from `bars` while we iterate a stale/offline player out
		List<UUID> ids = new ArrayList<>(bars.keySet());
		for (UUID id : ids) {
			Player player = Bukkit.getPlayer(id);
			if (player == null || !player.isOnline()) {
				bars.remove(id);
				starLostUntil.remove(id);
				continue;
			}

			try {
				refresh(player, config);
			} catch (RuntimeException exception) {
				log.error("Wanted HUD tick failed for {}", id, exception);
			}
		}

		if (bars.isEmpty()) stopTask();
	}

	private void refresh(Player player, HudConfig config) {
		UUID    id  = player.getUniqueId();
		BossBar bar = bars.get(id);
		if (bar == null) return;

		User<Player> user = users.getUser(player);
		Wanted       wanted = user == null ? null : user.getWanted();
		if (wanted == null || !wanted.isWanted()) {
			hide(player);
			return;
		}

		int level    = wanted.getLevel();
		int maxLevel = wanted.getMaxLevel();

		Long lostUntil = starLostUntil.get(id);
		if (lostUntil != null) {
			if (lostUntil > System.currentTimeMillis()) {
				showStarLost(bar, level, maxLevel, config);
				compass.restore(player);
				return;
			}
			starLostUntil.remove(id);
		}

		EvasionSnapshot snapshot = evasion.getSnapshot(id);
		switch (snapshot.state()) {
			case SEEN -> showSeen(bar, level, maxLevel, config, player);
			case SEARCHING -> showSearching(bar, player, level, maxLevel, snapshot, config);
			case NONE -> showWanted(bar, level, maxLevel, config, player);
		}
	}

	private void showSeen(BossBar bar, int level, int maxLevel, HudConfig config, Player player) {
		bar.setColor(BarColor.RED);
		bar.setProgress(1D);
		bar.setTitle(title(config.getInSightMessage(), level, maxLevel, false, 0D));
		compass.restore(player);
	}

	private void showWanted(BossBar bar, int level, int maxLevel, HudConfig config, Player player) {
		bar.setColor(BarColor.RED);
		bar.setProgress(1D);
		bar.setTitle(title(config.getWantedMessage(), level, maxLevel, false, 0D));
		compass.restore(player);
	}

	private void showStarLost(BossBar bar, int level, int maxLevel, HudConfig config) {
		bar.setColor(BarColor.GREEN);
		bar.setProgress(1D);
		bar.setTitle(title(config.getStarLostMessage(), level, maxLevel, false, 0D));
	}

	private void showSearching(BossBar bar, Player player, int level, int maxLevel, EvasionSnapshot snapshot,
	                           HudConfig config) {
		double total     = Math.max(snapshot.totalSeconds(), 0.0001D);
		double remaining = Math.max(0D, Math.min(snapshot.remainingSeconds(), total));
		double progress  = remaining / total;

		bar.setColor(BarColor.YELLOW);
		bar.setProgress(Math.max(0D, Math.min(1D, progress)));

		boolean flashOff = (tickCount & 1L) == 0L;
		bar.setTitle(title(config.getSearchingMessage(), level, maxLevel, flashOff, remaining));

		Location center = snapshot.zoneCenter();
		if (center == null) return;

		double radius = snapshot.zoneRadius();

		if (config.isSearchZoneRing()) spawnRing(player, center, radius);
		if (config.isEscapeCompass()) {
			compass.point(player, EscapeCompass.pointOutside(center, radius, player.getLocation()));
		}
	}

	private static String title(String template, int level, int maxLevel, boolean flashOff, double remaining) {
		String stars = HudFormat.stars(level, maxLevel, flashOff);
		String time  = HudFormat.time(remaining);
		return GanglandChatUtil.color(HudFormat.apply(template, stars, time));
	}

	private void spawnRing(Player player, Location center, double radius) {
		World world = center.getWorld();
		if (world == null || !world.equals(player.getWorld())) return;

		int points = Math.min(RING_MAX_POINTS, Math.max(RING_MIN_POINTS, (int) (2 * Math.PI * radius / 4)));
		double y = player.getLocation().getY() + 1D;

		for (int i = 0; i < points; i++) {
			double angle = i * (2 * Math.PI / points);
			double x     = center.getX() + radius * Math.cos(angle);
			double z     = center.getZ() + radius * Math.sin(angle);

			Location point = new Location(world, x, y, z);
			if (point.distanceSquared(player.getLocation()) > RING_VISIBLE_RANGE * RING_VISIBLE_RANGE) continue;

			player.spawnParticle(Particle.REDSTONE, point, 1, 0D, 0D, 0D, 0D,
			                     new Particle.DustOptions(Color.RED, 1.5F));
		}
	}

	private HudConfig config() {
		HudConfig loaded = copLoader.getLoadedHudConfig();
		return loaded == null ? HudConfig.defaults() : loaded;
	}

	private static BossBar createBar(Player player) {
		BossBar bar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
		bar.addPlayer(player);
		return bar;
	}

	private void startTask() {
		if (task != null) return;

		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TASK_PERIOD_TICKS, TASK_PERIOD_TICKS);
	}

	private void stopTask() {
		if (task == null) return;

		task.cancel();
		task = null;
	}
}
