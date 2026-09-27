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
import org.jetbrains.annotations.Nullable;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The wanted HUD (0.12 F3): a per-player boss bar (red while SEEN, yellow with the countdown while SEARCHING),
 * flashing stars, the search-zone particle ring and the escape compass. Reads {@link EvasionService#getSnapshot(UUID)}
 * for state and drives everything from one lazy 20-tick sync task, started on the first {@link #show(Player)} and
 * stopped once no player is tracked. The whole service no-ops when {@code Hud.Enable} is {@code false}.
 * <p>
 * {@code Hud.Boss_Bar}, {@code Hud.Search_Zone_Ring} and {@code Hud.Escape_Compass} are independent toggles: which
 * players are ticked ({@link #tracked}) is decided by whether any of them is on, while {@link #bars} only ever holds
 * an entry when {@code Boss_Bar} itself is on. {@link #refresh} and the {@code showXxx} helpers below therefore treat
 * the {@link BossBar} as possibly {@code null} and skip only the bar-specific calls when it is.
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

	private final Set<UUID>          tracked;
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
		this.tracked       = ConcurrentHashMap.newKeySet();
		this.bars          = new ConcurrentHashMap<>();
		this.starLostUntil = new ConcurrentHashMap<>();
	}

	/**
	 * Starts (or refreshes) the HUD for {@code player}: registers them for ticking and creates the boss bar when
	 * {@code Boss_Bar} is on. Called on every wanted start and evasion state change so the HUD reflects the new state
	 * immediately instead of waiting for the next tick.
	 *
	 * @param player the wanted player
	 */
	public void show(Player player) {
		HudConfig config = config();
		if (!config.isEnabled()) return;
		if (!config.isBossBar() && !config.isSearchZoneRing() && !config.isEscapeCompass()) return;

		setup(player, config);
		refresh(player, config);
	}

	/**
	 * Like {@link #show(Player)}, but renders {@code level}/{@code maxLevel} directly instead of reading
	 * {@link Wanted#getLevel()}. {@link org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent} fires (and
	 * so reaches listeners) before {@link Wanted#setLevel} applies the new level to the {@code Wanted} object itself,
	 * so {@link #onStarGained}/{@link #onStarLost} handling that event must not go through the level the live
	 * {@code Wanted} still reports — for any n -&gt; m change with n &gt; 0 that would render the stale, pre-change
	 * level for up to a tick (0.12 review).
	 *
	 * @param player the wanted player
	 * @param level the level to render
	 * @param maxLevel the wanted maximum level
	 */
	private void show(Player player, int level, int maxLevel) {
		HudConfig config = config();
		if (!config.isEnabled()) return;
		if (!config.isBossBar() && !config.isSearchZoneRing() && !config.isEscapeCompass()) return;

		setup(player, config);
		refresh(player, config, level, maxLevel);
	}

	private void setup(Player player, HudConfig config) {
		UUID id = player.getUniqueId();
		tracked.add(id);
		if (config.isBossBar()) {
			bars.computeIfAbsent(id, key -> createBar(player));
		}
		startTask();
	}

	/**
	 * Refreshes {@code player}'s HUD only if it is already being tracked; otherwise a no-op. Used for updates that
	 * should never themselves start showing the HUD to a player who isn't already seeing it (e.g. an evasion state
	 * change delivered after the player has already quit or stopped being wanted).
	 *
	 * @param player the player
	 */
	public void refreshIfShown(Player player) {
		if (!tracked.contains(player.getUniqueId())) return;

		HudConfig config = config();
		if (!config.isEnabled()) return;

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

		show(player, newLevel, maxLevel);
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
		show(player, newLevel, maxLevel);
	}

	/**
	 * Stops tracking {@code player}, removes their boss bar (if any) and restores their compass. Called on wanted
	 * end, quit, death and downed.
	 *
	 * @param player the player
	 */
	public void hide(Player player) {
		UUID id = player.getUniqueId();

		tracked.remove(id);
		BossBar bar = bars.remove(id);
		if (bar != null) bar.removeAll();
		starLostUntil.remove(id);
		compass.restore(player);

		if (tracked.isEmpty()) stopTask();
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
		// Restore compasses before dropping `tracked`: offline players are simply skipped by EscapeCompass.
		for (UUID id : tracked) {
			Player player = Bukkit.getPlayer(id);
			if (player != null) compass.restore(player);
		}
		bars.values().forEach(BossBar::removeAll);
		bars.clear();
		tracked.clear();
		starLostUntil.clear();
	}

	private void tickAll() {
		tickCount++;

		HudConfig config = config();
		if (!config.isEnabled()) {
			clearAll();
			return;
		}

		// Copy: hide() removes from `tracked` while we iterate a stale/offline player out
		List<UUID> ids = new ArrayList<>(tracked);
		for (UUID id : ids) {
			Player player = Bukkit.getPlayer(id);
			if (player == null || !player.isOnline()) {
				tracked.remove(id);
				BossBar bar = bars.remove(id);
				if (bar != null) bar.removeAll();
				starLostUntil.remove(id);
				continue;
			}

			try {
				refresh(player, config);
			} catch (RuntimeException exception) {
				log.error("Wanted HUD tick failed for {}", id, exception);
			}
		}

		if (tracked.isEmpty()) stopTask();
	}

	private void refresh(Player player, HudConfig config) {
		User<Player> user = users.getUser(player);
		Wanted       wanted = user == null ? null : user.getWanted();
		if (wanted == null || !wanted.isWanted()) {
			hide(player);
			return;
		}

		refresh(player, config, wanted.getLevel(), wanted.getMaxLevel());
	}

	private void refresh(Player player, HudConfig config, int level, int maxLevel) {
		UUID id = player.getUniqueId();

		// Only present when Boss_Bar is on; every showXxx helper below tolerates a null bar (0.12 review).
		BossBar bar = bars.get(id);

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

	private void showSeen(@Nullable BossBar bar, int level, int maxLevel, HudConfig config, Player player) {
		if (bar != null) {
			bar.setColor(BarColor.RED);
			bar.setProgress(1D);
			bar.setTitle(title(config.getInSightMessage(), level, maxLevel, false, 0D));
		}
		compass.restore(player);
	}

	private void showWanted(@Nullable BossBar bar, int level, int maxLevel, HudConfig config, Player player) {
		if (bar != null) {
			bar.setColor(BarColor.RED);
			bar.setProgress(1D);
			bar.setTitle(title(config.getWantedMessage(), level, maxLevel, false, 0D));
		}
		compass.restore(player);
	}

	private void showStarLost(@Nullable BossBar bar, int level, int maxLevel, HudConfig config) {
		if (bar == null) return;

		bar.setColor(BarColor.GREEN);
		bar.setProgress(1D);
		bar.setTitle(title(config.getStarLostMessage(), level, maxLevel, false, 0D));
	}

	private void showSearching(@Nullable BossBar bar, Player player, int level, int maxLevel,
	                           EvasionSnapshot snapshot, HudConfig config) {
		double total     = Math.max(snapshot.totalSeconds(), 0.0001D);
		double remaining = Math.max(0D, Math.min(snapshot.remainingSeconds(), total));

		if (bar != null) {
			double progress = remaining / total;

			bar.setColor(BarColor.YELLOW);
			bar.setProgress(Math.max(0D, Math.min(1D, progress)));

			boolean flashOff = (tickCount & 1L) == 0L;
			bar.setTitle(title(config.getSearchingMessage(), level, maxLevel, flashOff, remaining));
		}

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
