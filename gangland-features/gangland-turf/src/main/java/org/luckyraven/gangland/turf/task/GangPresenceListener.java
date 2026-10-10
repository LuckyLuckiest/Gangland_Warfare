package org.luckyraven.gangland.turf.task;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.turf.capture.CaptureRules;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps each gang's {@code lastMemberOnlineAt} stamp tight around the qualifying online edges (W2-R1 D1) instead of
 * drifting with the heartbeat in {@link GangPresenceTracker}:
 *
 * <ul>
 *   <li><b>Join:</b> a qualifying member (see {@link GangPresenceTracker#qualifies}) who logs in while the window is
 *       closed wakes the gang: the wake-up is recorded and the member gets one camping line per owned turf with rivals
 *       inside. Then the stamp is refreshed. A relog while the window is still open does not wake the gang again.</li>
 *   <li><b>Quit:</b> stamp once more only when the quitting member qualifies and no other qualifying member of the
 *       gang is still online, so the logout window starts from the moment the last qualifying member left.</li>
 * </ul>
 *
 * <p>A {@code @ListenerHandler} is never a {@code @Bean}: the framework constructs this listener and injects the
 * tracker, turfs and texts by type.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class GangPresenceListener implements Listener {

	private final GangLookupContract     gangs;
	private final GangMembership         membership;
	private final GangPresenceTracker    tracker;
	private final TurfManager            turfs;
	private final TurfMessages           text;
	private final TurfMessageContract    messages;

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		Gang   gang   = gangOf(player);
		if (gang == null) {
			return;
		}
		long         now   = System.currentTimeMillis();
		CaptureRules rules = tracker.rules();
		if (!GangPresenceTracker.qualifies(gang, GangPresenceTracker.memberOf(gang, player.getUniqueId()), rules,
		                                   now)) {
			return;
		}
		if (tracker.windowClosed(gang, now)) {
			tracker.recordWakeUp(gang.getId(), now);
			sendCampingLines(player, gang, rules);
		}
		gang.setLastMemberOnlineAt(now);
	}

	// RED STUB (rf-presence): handler body lands with T-248.
	@EventHandler
	public void onOwnerChanged(TurfOwnerChangedEvent event) {
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player quitting = event.getPlayer();
		Gang   gang     = gangOf(quitting);
		if (gang == null) {
			return;
		}
		long         now   = System.currentTimeMillis();
		CaptureRules rules = tracker.rules();
		if (!GangPresenceTracker.qualifies(gang, GangPresenceTracker.memberOf(gang, quitting.getUniqueId()), rules,
		                                   now)) {
			return;
		}
		if (hasOtherQualifyingMemberOnline(gang, quitting, rules, now)) {
			return;
		}
		gang.setLastMemberOnlineAt(now);
	}

	/**
	 * The player's gang, or null for a gangless player or a gang that no longer exists. Reads {@link GangMembership}
	 * rather than the user record: on a real join the user is created gang-less and its gang id is hydrated later by an
	 * async task, so at join time the user record cannot yet say which gang the player belongs to.
	 */
	private Gang gangOf(Player player) {
		int gangId = membership.gangIdOf(player.getUniqueId());
		return gangId == -1 ? null : gangs.findById(gangId);
	}

	/** One camping line per owned turf that has rival attackers inside: the rival with the most, ties to lowest id. */
	private void sendCampingLines(Player player, Gang gang, CaptureRules rules) {
		String time = messages.formatDuration(rules.wakeUpShieldSeconds());
		for (Turf turf : turfs.getAll()) {
			if (turf.isUnclaimed() || turf.getOwnerGangId() != gang.getId()) {
				continue;
			}
			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			if (state == null || state.getChallengersInside().isEmpty()) {
				continue;
			}
			int rivalId = mostInside(state.getChallengersInside());
			player.sendMessage(text.text("Capture.Camping", "&c%gang% are camping %turf% &7- shield ends in %time%",
			                             "gang", GangDisplayNameResolver.resolve(gangs.findById(rivalId)),
			                             "turf", turf.getDisplayName(),
			                             "time", time));
		}
	}

	private static int mostInside(Map<Integer, Integer> inside) {
		return inside.entrySet().stream()
		             .max(Comparator.comparingInt(Map.Entry<Integer, Integer>::getValue)
		                            .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
		             .map(Map.Entry::getKey)
		             .orElseThrow();
	}

	private boolean hasOtherQualifyingMemberOnline(Gang gang, Player quitting, CaptureRules rules, long now) {
		for (Player online : Bukkit.getOnlinePlayers()) {
			if (online.equals(quitting)) {
				continue;
			}
			UUID uuid = online.getUniqueId();
			if (membership.gangIdOf(uuid) != gang.getId()) {
				continue;
			}
			Member member = GangPresenceTracker.memberOf(gang, uuid);
			if (GangPresenceTracker.qualifies(gang, member, rules, now)) {
				return true;
			}
		}
		return false;
	}
}
