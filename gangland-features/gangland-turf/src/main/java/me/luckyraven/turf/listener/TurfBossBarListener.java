package me.luckyraven.turf.listener;

import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.gang.contract.UserLookupContract;
import me.luckyraven.gang.user.User;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.contract.TurfSoundContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.data.TurfRuntimeState;
import me.luckyraven.turf.events.*;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.state.TurfState;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a per-viewer BossBar while a turf is CONTESTING — red for defenders, green for attackers, white for
 * bystanders. Progress is synced against {@link TurfRuntimeState#getCaptureProgress()} on a 1-Hz scheduler so the bar
 * fills smoothly instead of stepping at the milestone events. Every upward tick plays a subtle tick sound so attackers
 * get audio feedback alongside the moving bar.
 *
 * <p>Three categories of viewer see the bar at any given moment:
 * <ul>
 *   <li>Anyone <b>physically inside</b> the contested turf (bar appears on enter, removed on exit).</li>
 *   <li>Every online <b>member of the challenger gang</b> — even when they are on the other side of the server,
 *       so a gang can coordinate multiple captures. Multiple simultaneous captures produce multiple bars that stack
 *       natively via Bukkit's {@link BossBar} API.</li>
 *   <li>Every online <b>member of the defender gang</b> (the owning gang whose turf is being contested), so they
 *       know their territory is under attack and can rush back regardless of where they are.</li>
 * </ul>
 *
 * <p>Bars are torn down on capture completion, capture cancel, and player disconnect. Exit only tears down for
 * bystanders — members of the challenger or defender gang keep the bar so they stay in the loop from anywhere.
 * Joining mid-capture rebuilds the in-flight bars for any turf where the joiner's gang is the challenger or
 * defender.
 */
@ListenerHandler
public final class TurfBossBarListener implements Listener {

	private final TurfManager         turfs;
	private final GangLookupContract  gangs;
	private final UserLookupContract  users;
	private final TurfMessageContract messages;
	private final TurfSoundContract   sounds;

	private final Map<Integer, Map<UUID, BossBar>> barsByTurf       = new HashMap<>();
	private final Map<Integer, Double>             lastProgressSent = new HashMap<>();

	public TurfBossBarListener(JavaPlugin plugin,
	                           TurfManager turfs,
	                           GangLookupContract gangs,
	                           UserLookupContract users,
	                           TurfMessageContract messages,
	                           TurfSoundContract sounds) {
		this.turfs    = turfs;
		this.gangs    = gangs;
		this.users    = users;
		this.messages = messages;
		this.sounds   = sounds;

		Bukkit.getScheduler().runTaskTimer(plugin, this::refreshProgress, 20L, 20L);
	}

	@EventHandler
	public void onEnter(TurfEnterEvent event) {
		Turf             turf  = event.getTurf();
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (state == null || state.getState() != TurfState.CONTESTING) {
			return;
		}
		showFor(event.getPlayer(), turf, state);
	}

	@EventHandler
	public void onExit(TurfExitEvent event) {
		int              turfId = event.getTurf().getId();
		TurfRuntimeState state  = turfs.getRuntimeState(turfId);
		// Keep the bar visible for anyone with a stake in the contest — challenger gang wants to track
		// their own capture from anywhere, defender gang wants to know their turf is under attack so
		// they can rush back. Only bystanders (no gang / different gang) lose the bar on exit.
		if (state != null && state.getState() == TurfState.CONTESTING) {
			if (state.getChallengerGangId() != null
			    && isMemberOf(event.getPlayer(), state.getChallengerGangId())) {
				return;
			}
			Integer defenderId = event.getTurf().getOwnerGangId();
			if (defenderId != null && isMemberOf(event.getPlayer(), defenderId)) {
				return;
			}
		}
		hideFor(event.getPlayer(), turfId);
	}

	@EventHandler
	public void onCaptureStart(TurfCaptureStartEvent event) {
		Turf             turf  = event.getTurf();
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (state == null) {
			return;
		}
		int     challengerGangId = event.getChallengerGang().getId();
		Integer defenderGangId   = turf.getOwnerGangId();
		for (Player online : Bukkit.getOnlinePlayers()) {
			Turf    at           = turfs.findAt(online.getLocation());
			boolean inside       = at != null && at.getId() == turf.getId();
			boolean isChallenger = isMemberOf(online, challengerGangId);
			boolean isDefender   = defenderGangId != null && isMemberOf(online, defenderGangId);
			if (inside || isChallenger || isDefender) {
				showFor(online, turf, state);
			}
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player       viewer = event.getPlayer();
		User<Player> user   = users.findByPlayer(viewer);
		if (user == null || !user.hasGang()) {
			return;
		}
		int viewerGangId = user.getGangId();
		// Rebuild bars for any in-flight captures the joiner's gang is involved in — either as the attacker
		// running a capture they'd want to watch, or as the defender whose turf is under attack and who needs
		// to know their territory is being contested the moment they log in.
		for (Turf turf : turfs.getAll()) {
			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			if (state == null || state.getState() != TurfState.CONTESTING) {
				continue;
			}
			Integer challengerId = state.getChallengerGangId();
			Integer defenderId   = turf.getOwnerGangId();
			boolean isChallenger = challengerId != null && challengerId == viewerGangId;
			boolean isDefender   = defenderId != null && defenderId == viewerGangId;
			if (isChallenger || isDefender) {
				showFor(viewer, turf, state);
			}
		}
	}

	private boolean isMemberOf(Player player, int gangId) {
		User<Player> user = users.findByPlayer(player);
		return user != null && user.hasGang() && user.getGangId() == gangId;
	}

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		clearTurf(event.getTurf().getId());
	}

	@EventHandler
	public void onFailed(TurfCaptureFailedEvent event) {
		clearTurf(event.getTurf().getId());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID uuid = event.getPlayer().getUniqueId();
		for (Map<UUID, BossBar> perViewer : barsByTurf.values()) {
			BossBar bar = perViewer.remove(uuid);
			if (bar != null) {
				bar.removeAll();
			}
		}
	}

	private void showFor(Player viewer, Turf turf, TurfRuntimeState state) {
		Map<UUID, BossBar> perViewer = barsByTurf.computeIfAbsent(turf.getId(), k -> new HashMap<>());
		if (perViewer.containsKey(viewer.getUniqueId())) {
			return;
		}

		Gang     challenger = state.getChallengerGangId() == null ? null : gangs.findById(state.getChallengerGangId());
		BarColor color      = resolveColor(viewer, turf.getOwnerGangId(), state.getChallengerGangId());
		String title = messages.format("TURF_BOSSBAR_TITLE",
		                               "turf", turf.getDisplayName(),
		                               "gang", GangDisplayNameResolver.resolve(challenger));

		BossBar bar = Bukkit.createBossBar(ChatUtil.color(title), color, BarStyle.SOLID);
		bar.setProgress(Math.clamp(state.getCaptureProgress() / 100.0, 0.0, 1.0));
		bar.addPlayer(viewer);
		perViewer.put(viewer.getUniqueId(), bar);
	}

	private void hideFor(Player viewer, int turfId) {
		Map<UUID, BossBar> perViewer = barsByTurf.get(turfId);
		if (perViewer == null) {
			return;
		}
		BossBar bar = perViewer.remove(viewer.getUniqueId());
		if (bar != null) {
			bar.removeAll();
		}
		if (perViewer.isEmpty()) {
			barsByTurf.remove(turfId);
		}
	}

	private void clearTurf(int turfId) {
		lastProgressSent.remove(turfId);
		Map<UUID, BossBar> perViewer = barsByTurf.remove(turfId);
		if (perViewer == null) {
			return;
		}
		for (BossBar bar : perViewer.values()) {
			bar.removeAll();
		}
	}

	private void refreshProgress() {
		for (Map.Entry<Integer, Map<UUID, BossBar>> entry : barsByTurf.entrySet()) {
			int              turfId = entry.getKey();
			TurfRuntimeState state  = turfs.getRuntimeState(turfId);
			if (state == null) {
				continue;
			}
			double  progress = Math.clamp(state.getCaptureProgress() / 100.0, 0.0, 1.0);
			double  previous = lastProgressSent.getOrDefault(turfId, 0.0);
			boolean advanced = progress > previous + 0.0005;

			for (BossBar bar : entry.getValue().values()) {
				bar.setProgress(progress);
				if (advanced) {
					for (Player listener : bar.getPlayers()) {
						sounds.playCaptureTick(listener);
					}
				}
			}
			lastProgressSent.put(turfId, progress);
		}
	}

	private BarColor resolveColor(Player viewer, Integer defenderGangId, Integer challengerGangId) {
		User<Player> user = users.findByPlayer(viewer);
		if (user == null || !user.hasGang()) {
			return BarColor.WHITE;
		}
		int viewerGangId = user.getGangId();
		if (defenderGangId != null && viewerGangId == defenderGangId) {
			return BarColor.RED;
		}
		if (challengerGangId != null && viewerGangId == challengerGangId) {
			return BarColor.GREEN;
		}
		return BarColor.WHITE;
	}
}
