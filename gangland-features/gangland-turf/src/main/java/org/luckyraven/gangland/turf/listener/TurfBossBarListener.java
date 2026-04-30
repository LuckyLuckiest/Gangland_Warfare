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
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.contract.TurfSoundContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.*;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.CapturePhase;
import org.luckyraven.gangland.turf.state.TurfState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders per-viewer BossBars while a turf is CONTESTING. Owned-turf captures show a single bar (consolidate).
 * Unclaimed-turf captures show two stacked bars: a neutral "Unclaimed Territory" bar that fills 0→100 during the CLAIM
 * phase, then sticks at 100 throughout CONSOLIDATE; and a coloured "%gang% is capturing %turf%" bar that stays at 0
 * during CLAIM and fills 0→100 during CONSOLIDATE. Bar B colour is red for defenders, green for the capturing gang,
 * white for bystanders. Progress syncs against {@link TurfRuntimeState#getCaptureProgress()} on a 1-Hz scheduler so
 * bars fill smoothly between milestone events. Every upward tick on the active bar plays the subtle tick sound so
 * attackers get audio feedback alongside the moving bar.
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

	private final Map<Integer, Map<UUID, BarPair>> barsByTurf              = new HashMap<>();
	private final Map<Integer, Double>             lastClaimProgress       = new HashMap<>();
	private final Map<Integer, Double>             lastConsolidateProgress = new HashMap<>();

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
		// CaptureService re-fires this event on the Phase 1 → Phase 2 transition with a (possibly
		// different) newly-elected challenger gang. Tear the old bars down first so the gang label and
		// per-viewer colour rebuild against the current challenger instead of being stuck on the prior
		// one's identity.
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
		for (Map<UUID, BarPair> perViewer : barsByTurf.values()) {
			BarPair pair = perViewer.remove(uuid);
			if (pair != null) {
				pair.removeAll();
			}
		}
	}

	private boolean isMemberOf(Player player, int gangId) {
		User<Player> user = users.findByPlayer(player);
		return user != null && user.hasGang() && user.getGangId() == gangId;
	}

	private void showFor(Player viewer, Turf turf, TurfRuntimeState state) {
		Map<UUID, BarPair> perViewer = barsByTurf.computeIfAbsent(turf.getId(), k -> new HashMap<>());
		if (perViewer.containsKey(viewer.getUniqueId())) {
			return;
		}

		Gang     challenger = state.getChallengerGangId() == null ? null : gangs.findById(state.getChallengerGangId());
		BarColor color      = resolveColor(viewer, turf.getOwnerGangId(), state.getChallengerGangId());

		BossBar consolidateBar = Bukkit.createBossBar(buildConsolidateTitle(turf, challenger), color, BarStyle.SOLID);
		BossBar claimBar       = null;

		if (turf.isUnclaimed()) {
			claimBar = Bukkit.createBossBar(buildClaimTitle(), BarColor.WHITE, BarStyle.SOLID);
			// Claim bar fills first; consolidate bar stays at 0 until CLAIM completes.
			double claimProgress = state.getPhase() == CapturePhase.CLAIM
			                       ? Math.clamp(state.getCaptureProgress() / 100.0, 0.0, 1.0)
			                       : 1.0;
			double consolidateProgress = state.getPhase() == CapturePhase.CONSOLIDATE
			                             ? Math.clamp(state.getCaptureProgress() / 100.0, 0.0, 1.0)
			                             : 0.0;
			claimBar.setProgress(claimProgress);
			consolidateBar.setProgress(consolidateProgress);
			claimBar.addPlayer(viewer);
		} else {
			consolidateBar.setProgress(Math.clamp(state.getCaptureProgress() / 100.0, 0.0, 1.0));
		}

		consolidateBar.addPlayer(viewer);
		perViewer.put(viewer.getUniqueId(), new BarPair(claimBar, consolidateBar));
	}

	private void hideFor(Player viewer, int turfId) {
		Map<UUID, BarPair> perViewer = barsByTurf.get(turfId);
		if (perViewer == null) {
			return;
		}
		BarPair pair = perViewer.remove(viewer.getUniqueId());
		if (pair != null) {
			pair.removeAll();
		}
		if (perViewer.isEmpty()) {
			barsByTurf.remove(turfId);
		}
	}

	private void clearTurf(int turfId) {
		lastClaimProgress.remove(turfId);
		lastConsolidateProgress.remove(turfId);
		Map<UUID, BarPair> perViewer = barsByTurf.remove(turfId);
		if (perViewer == null) {
			return;
		}
		for (BarPair pair : perViewer.values()) {
			pair.removeAll();
		}
	}

	private void refreshProgress() {
		for (Map.Entry<Integer, Map<UUID, BarPair>> entry : barsByTurf.entrySet()) {
			int              turfId = entry.getKey();
			TurfRuntimeState state  = turfs.getRuntimeState(turfId);
			if (state == null) {
				continue;
			}
			Turf turf = turfs.get(turfId);
			if (turf == null) {
				continue;
			}

			double progress = Math.clamp(state.getCaptureProgress() / 100.0, 0.0, 1.0);
			double claimProgress;
			double consolidateProgress;
			if (turf.isUnclaimed()) {
				claimProgress       = state.getPhase() == CapturePhase.CLAIM ? progress : 1.0;
				consolidateProgress = state.getPhase() == CapturePhase.CONSOLIDATE ? progress : 0.0;
			} else {
				claimProgress       = 0.0; // unused; owned turfs have no claim bar
				consolidateProgress = progress;
			}

			double  prevClaim       = lastClaimProgress.getOrDefault(turfId, 0.0);
			double  prevConsolidate = lastConsolidateProgress.getOrDefault(turfId, 0.0);
			boolean claimAdvanced   = claimProgress > prevClaim + 0.0005;
			boolean consAdvanced    = consolidateProgress > prevConsolidate + 0.0005;

			for (BarPair pair : entry.getValue().values()) {
				if (pair.claim() != null) {
					pair.claim().setProgress(claimProgress);
				}
				pair.consolidate().setProgress(consolidateProgress);
				if (claimAdvanced || consAdvanced) {
					for (Player listener : pair.consolidate().getPlayers()) {
						sounds.playCaptureTick(listener);
					}
				}
			}
			lastClaimProgress.put(turfId, claimProgress);
			lastConsolidateProgress.put(turfId, consolidateProgress);
		}
	}

	private String buildConsolidateTitle(Turf turf, Gang challenger) {
		return ChatUtil.color(messages.format("TURF_BOSSBAR_TITLE",
		                                      "turf", turf.getDisplayName(),
		                                      "gang", GangDisplayNameResolver.resolve(challenger)));
	}

	private String buildClaimTitle() {
		return ChatUtil.color(messages.format("TURF_BOSSBAR_TITLE_UNCLAIMED"));
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

	/**
	 * Owned-turf captures use {@code claim == null}; unclaimed captures populate both. {@link #removeAll()} hides
	 * whatever is present, so callers don't have to null-check at teardown sites.
	 */
	private record BarPair(BossBar claim, BossBar consolidate) {

		void removeAll() {
			if (claim != null) {
				claim.removeAll();
			}
			consolidate.removeAll();
		}
	}
}
