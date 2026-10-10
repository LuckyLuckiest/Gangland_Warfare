package org.luckyraven.gangland.turf.listener;

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
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.contract.TurfSoundContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.*;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders one per-viewer BossBar while a turf is CONTESTING, owned or unclaimed alike (one bar since 0.17.0; the
 * stacked "Unclaimed Territory" claim bar is gone). The title is "%gang% is capturing %turf%" with the attackers
 * against the defenders, and "+N guards" appended to the defenders while guards hold the bar. The colour is red for
 * defenders, green for the capturing gang, white for bystanders. Progress syncs against
 * {@link TurfRuntimeState#getCaptureProgress()} on a 1-Hz scheduler so the bar fills smoothly between milestone
 * events. Every upward tick plays the subtle tick sound so attackers get audio feedback alongside the moving bar.
 *
 * <p>Three categories of viewer see the bars at any given moment:
 * <ul>
 *   <li>Anyone <b>physically inside</b> the contested turf (bars appear on enter, removed on bystander exit).</li>
 *   <li>Every online <b>member of the challenger gang</b> — even when they are on the other side of the server,
 *       so a gang can coordinate multiple captures. Multiple simultaneous captures stack natively via Bukkit's
 *       {@link BossBar} API.</li>
 *   <li>Every online <b>member of the defender gang</b> (the owning gang whose turf is being contested), so they
 *       know their territory is under attack and can rush back regardless of where they are.</li>
 * </ul>
 *
 * <p>Bars are torn down on capture completion, capture cancel, and player disconnect. Exit only tears down for
 * bystanders — members of the challenger or defender gang keep the bars so they stay in the loop from anywhere.
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
	private final CaptureSettings     settings;
	private final CaptureEligibility  eligibility;
	private final TurfMessages        text;

	private final Map<Integer, Map<UUID, BossBar>> barsByTurf   = new HashMap<>();
	private final Map<Integer, Double>             lastProgress = new HashMap<>();

	public TurfBossBarListener(JavaPlugin plugin,
	                           TurfManager turfs,
	                           GangLookupContract gangs,
	                           UserLookupContract users,
	                           TurfMessageContract messages,
	                           TurfSoundContract sounds,
	                           CaptureSettings settings,
	                           CaptureEligibility eligibility,
	                           TurfMessages text) {
		this.turfs       = turfs;
		this.gangs       = gangs;
		this.users       = users;
		this.messages    = messages;
		this.sounds      = sounds;
		this.settings    = settings;
		this.eligibility = eligibility;
		this.text        = text;

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
		// Keep the bars visible for anyone with a stake in the contest — challenger gang wants to track
		// their own capture from anywhere, defender gang wants to know their turf is under attack so
		// they can rush back. Only bystanders (no gang / different gang) lose the bars on exit.
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
		// CaptureService re-fires this event when the challenger changes. Tear the old bars down first so
		// the gang label and per-viewer colour rebuild against the current challenger.
		clearTurf(turf.getId());
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

	private boolean isMemberOf(Player player, int gangId) {
		User<Player> user = users.findByPlayer(player);
		return user != null && user.hasGang() && user.getGangId() == gangId;
	}

	private void showFor(Player viewer, Turf turf, TurfRuntimeState state) {
		Map<UUID, BossBar> perViewer = barsByTurf.computeIfAbsent(turf.getId(), k -> new HashMap<>());
		if (perViewer.containsKey(viewer.getUniqueId())) {
			return;
		}

		BarColor color = resolveColor(viewer, turf.getOwnerGangId(), state.getChallengerGangId());
		BossBar  bar   = Bukkit.createBossBar(bossTitle(turf, state), color, BarStyle.SOLID);
		bar.setProgress(progress(state));
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
		lastProgress.remove(turfId);
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
			Turf turf = turfs.get(turfId);
			if (turf == null) {
				continue;
			}

			double  progress = progress(state);
			boolean advanced = progress > lastProgress.getOrDefault(turfId, 0.0) + 0.0005;
			String  title    = bossTitle(turf, state);

			for (BossBar bar : entry.getValue().values()) {
				bar.setProgress(progress);
				if (!title.equals(bar.getTitle())) {
					bar.setTitle(title);
				}
				if (advanced) {
					for (Player listener : bar.getPlayers()) {
						sounds.playCaptureTick(listener);
					}
				}
			}
			lastProgress.put(turfId, progress);
		}
	}

	private static double progress(TurfRuntimeState state) {
		return Math.max(0.0, Math.min(state.getCaptureProgress() / 100.0, 1.0));
	}

	/**
	 * The boss bar title at this moment, the same for owned and unclaimed turfs. Attackers = the challenger's count
	 * inside. Defenders = the owner and allies inside (owned) or every other gang inside (unclaimed), with the guards
	 * holding the bar appended. The time is the time left at the pace {@link BossBarText#pace} gives (attackers minus
	 * defenders minus guards), clamped to the minimum capture time.
	 */
	String bossTitle(Turf turf, TurfRuntimeState state) {
		Integer               challengerId = state.getChallengerGangId();
		Map<Integer, Integer> inside       = state.getChallengersInside();
		int                   attackers    = challengerId == null ? 0 : inside.getOrDefault(challengerId, 0);
		int                   defenders    = turf.isUnclaimed()
		                                     ? BossBarText.unclaimedDefenders(inside, challengerId)
		                                     : state.getDefendersInside();
		int                   guards       = state.getGuardsCounted();

		int    pace        = BossBarText.pace(attackers, defenders, guards);
		long   secondsLeft = BossBarText.secondsLeft(state.getCaptureProgress(), pace,
		                                             settings.getCaptureDurationSeconds(),
		                                             eligibility.rules().minCaptureSeconds());
		String time        = secondsLeft < 0
		                     ? text.text("Info.BossBar_Stalled", "stalled")
		                     : messages.formatDuration(secondsLeft);
		Gang   challenger  = challengerId == null ? null : gangs.findById(challengerId);

		return BossBarText.title(text, GangDisplayNameResolver.resolve(challenger), turf.getDisplayName(),
		                         attackers, BossBarText.defendersLabel(text, defenders, guards), time);
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
