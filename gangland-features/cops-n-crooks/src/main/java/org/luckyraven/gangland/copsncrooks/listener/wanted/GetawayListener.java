package org.luckyraven.gangland.copsncrooks.listener.wanted;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService.Reason;
import org.luckyraven.gangland.copsncrooks.wanted.getaway.GetawayService.Result;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pays the getaway at WantedEndEvent LOW (arc alive) and prints the line at MONITOR, after the pursuit report.
 *
 * @since 0.21.0
 */
@CustomLog
@ListenerHandler
public class GetawayListener implements Listener {

	private final GetawayService service;
	private final ChaseArcs      arcs;
	private final WantedMessages messages;
	// ponytail: main-thread only, a plain map per player would do; kept concurrent to match the other listeners' hand-off maps
	private final Map<UUID, String> pending = new ConcurrentHashMap<>();

	public GetawayListener(GetawayService service, ChaseArcs arcs, WantedMessages messages) {
		this.service  = service;
		this.arcs     = arcs;
		this.messages = messages;
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onEndAward(WantedEndEvent event) {
		Player player = event.getPlayer();
		ChaseArc arc = arcs.arc(player.getUniqueId());
		if (arc == null) {
			return;
		}

		Result result = service.award(player, arc, event.getCause());
		Reason reason = result.reason();
		if (reason != Reason.NOT_EVASION && reason != Reason.DISABLED) {
			log.debug("GETAWAY {} peak={} xp={} reason={}", player.getName(), arc.earnedPeak(), result.granted(),
			          reason);
		}

		if (reason == Reason.PAID && result.granted() > 0) {
			pending.put(player.getUniqueId(),
			            messages.format(WantedMessages.Key.GETAWAY_LINE, Map.of("xp", String.valueOf(result.granted()))));
		} else if (reason == Reason.CAPPED) {
			pending.put(player.getUniqueId(), messages.format(WantedMessages.Key.GETAWAY_CAPPED, Map.of()));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEndSend(WantedEndEvent event) {
		Player player = event.getPlayer();
		String line = pending.remove(player.getUniqueId());
		if (line != null) {
			player.sendMessage(line);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onQuit(PlayerQuitEvent event) {
		pending.remove(event.getPlayer().getUniqueId());
	}
}
