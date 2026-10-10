package org.luckyraven.gangland.turf.map;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.config.TurfMapSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfGeometry;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.map.MapPrefsService.HudMode;
import org.luckyraven.gangland.turf.task.TurfLocationTracker;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.util.Collection;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Sends the turf line to every online player on each Hud.Interval_Seconds tick: the inside line while the player
 * stands in a turf, and (only with {@code /glw map hud on}) the nearest-turf line outside one. A line is sent on every
 * tick, even when its text did not change, so it comes back as soon as a priority-10 line (car, jetpack, gun) stops.
 */
@CustomLog
public final class TurfHudTask implements BeanLifecycle {

	/** Sends one action-bar line at a priority; production calls ActionBarManager.sendBackground. */
	@FunctionalInterface
	public interface LineSender {

		void send(Player player, String line, int priority);
	}

	private final TurfLocationTracker             tracker;
	private final TurfManager                     turfs;
	private final GangLookupContract              gangs;
	private final UserLookupContract              users;
	private final CaptureEligibility              eligibility;
	private final TurfMessages                    text;
	private final TurfMapSettings                 settings;
	private final MapPrefsService                 prefs;
	private final LineSender                      sender;
	private final Supplier<Collection<? extends Player>> players;
	private final LongSupplier                    clock;

	private BukkitTask task;

	public TurfHudTask(TurfLocationTracker tracker,
	                   TurfManager turfs,
	                   GangLookupContract gangs,
	                   UserLookupContract users,
	                   CaptureEligibility eligibility,
	                   TurfMessages text,
	                   TurfMapSettings settings,
	                   MapPrefsService prefs,
	                   LineSender sender,
	                   Supplier<Collection<? extends Player>> players,
	                   LongSupplier clock) {
		this.tracker     = tracker;
		this.turfs       = turfs;
		this.gangs       = gangs;
		this.users       = users;
		this.eligibility = eligibility;
		this.text        = text;
		this.settings    = settings;
		this.prefs       = prefs;
		this.sender      = sender;
		this.players     = players;
		this.clock       = clock;
	}

	/** Starts the repeating task; the period is Hud.Interval_Seconds. */
	public void start(JavaPlugin plugin) {
		if (task != null) {
			return;
		}
		long period = settings.getHudIntervalSeconds() * 20L;
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
	}

	@Override
	public void onShutdown() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	void tick() {
		if (!settings.isHudEnabled()) {
			return;
		}
		long now = clock.getAsLong();
		for (Player player : players.get()) {
			HudMode mode = prefs.hudMode(player.getUniqueId());
			if (mode == HudMode.OFF) {
				continue;
			}
			String line = lineFor(player, mode, now);
			if (line != null) {
				sender.send(player, line, settings.getHudPriority());
			}
		}
	}

	private @Nullable String lineFor(Player player, HudMode mode, long now) {
		Turf current = tracker.getPlayerTurfCache().get(player.getUniqueId());
		if (current != null) {
			return insideLine(player, current, now);
		}
		if (mode != HudMode.ON) {
			return null;
		}
		World world = player.getWorld();
		if (world == null) {
			return null;
		}
		Location at = player.getLocation();
		return TurfHudLine.nearest(text, TurfGeometry.nearest(turfs.getTurfsInWorld(world.getName()),
		                                                      at.getBlockX(), at.getBlockZ()));
	}

	private String insideLine(Player player, Turf turf, long now) {
		Integer gangId = viewerGangId(player);
		TurfRelation relation = TurfRelation.of(turf, gangId, gangs);
		String ownerName = turf.isUnclaimed() ? "" : GangDisplayNameResolver.resolve(
				gangs.findById(turf.getOwnerGangId()));

		TurfRuntimeState state       = turfs.getRuntimeState(turf.getId());
		Integer          challengerId = state == null ? null : state.getChallengerGangId();
		String challenger = challengerId == null ? null : GangDisplayNameResolver.resolve(gangs.findById(challengerId));

		return TurfHudLine.inside(text, turf, relation, gangId == null, ownerName, state, challenger,
		                          eligibility.evaluate(turf, now), now);
	}

	private @Nullable Integer viewerGangId(Player player) {
		User<Player> user = users.findByPlayer(player);
		return user != null && user.hasGang() ? user.getGangId() : null;
	}
}
