package org.luckyraven.gangland.turf.task;

import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.UserLookupContract;
import org.luckyraven.gangland.gang.user.User;

/**
 * Keeps each gang's {@code lastMemberOnlineAt} timestamp tight around real online transitions instead of drifting with
 * the 1-minute heartbeat. The heartbeat in {@link GangPresenceTracker} still handles the bulk refresh while members
 * stay online; this listener nails down the two edges that matter for the turf post-logoff grace window:
 *
 * <ul>
 *   <li><b>Join:</b> stamp the timestamp the moment a member connects so the grace resets without waiting up to 60s
 *       for the next heartbeat tick.</li>
 *   <li><b>Quit:</b> if this is the last online member of the gang, stamp the timestamp once more so the spec-defined
 *       10-minute grace starts from the precise quit moment, not from the prior heartbeat (which could be almost a
 *       full minute stale).</li>
 * </ul>
 */
@ListenerHandler
@RequiredArgsConstructor
public final class GangPresenceListener implements Listener {

	private final GangLookupContract gangs;
	private final UserLookupContract users;

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		stampFor(event.getPlayer(), null);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player quitting = event.getPlayer();
		// Only stamp on quit if this was the last online member of the gang — otherwise the remaining members
		// keep the heartbeat alive and stamping here would be pointless (and wrong, since we want the timer to
		// start only when the gang has truly gone dark).
		stampFor(quitting, quitting);
	}

	private void stampFor(Player player, Player ignoreQuitting) {
		User<Player> user = users.findByPlayer(player);
		if (user == null || !user.hasGang()) {
			return;
		}
		Gang gang = gangs.findById(user.getGangId());
		if (gang == null) {
			return;
		}
		if (ignoreQuitting != null && hasOtherOnlineMember(user.getGangId(), ignoreQuitting)) {
			return;
		}
		gang.setLastMemberOnlineAt(System.currentTimeMillis());
	}

	private boolean hasOtherOnlineMember(int gangId, Player quitting) {
		for (Player online : Bukkit.getOnlinePlayers()) {
			if (online.equals(quitting)) {
				continue;
			}
			User<Player> other = users.findByPlayer(online);
			if (other != null && other.hasGang() && other.getGangId() == gangId) {
				return true;
			}
		}
		return false;
	}
}
