package org.luckyraven.gangland.turf.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureSettings;
import org.luckyraven.gangland.turf.command.TurfCommand;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfCapturedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.log.TurfEventBuffer;
import org.luckyraven.gangland.turf.log.TurfEventBuffer.Entry;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


/**
 * Records what happened to a gang's turfs (capture, loss, held, stalled, released) into {@link TurfEventBuffer} and,
 * when a member joins, replays the income and events since his last quit, then the map link (TF-37). A player who
 * has no quit stamp (first join since a restart) gets the newest few lines instead.
 */
@ListenerHandler
public final class TurfDigestListener implements Listener {

	/** Most lines a join digest shows, the income line included. */
	static final int MAX_LINES      = 5;
	/** Lines shown on a join with no quit stamp (first join since a restart). */
	static final int RESTART_LINES  = 3;
	/** Delay after join before the digest is sent, so the client has finished loading. */
	private static final long JOIN_DELAY_TICKS = 20L;

	private final TurfEventBuffer buffer;
	private final TurfManager     turfs;
	private final GangLookupContract gangs;
	private final UserLookupContract users;
	private final TurfMessages    text;
	private final CaptureSettings capture;
	private final JavaPlugin      plugin;

	public TurfDigestListener(TurfEventBuffer buffer,
	                          TurfManager turfs,
	                          GangLookupContract gangs,
	                          UserLookupContract users,
	                          TurfMessages text,
	                          CaptureSettings capture,
	                          JavaPlugin plugin) {
		this.buffer  = buffer;
		this.turfs   = turfs;
		this.gangs   = gangs;
		this.users   = users;
		this.text    = text;
		this.capture = capture;
		this.plugin  = plugin;
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		buffer.markQuit(event.getPlayer().getUniqueId(), System.currentTimeMillis());
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		Bukkit.getScheduler().runTaskLater(plugin, () -> {
			if (player.isOnline()) {
				sendDigest(player, System.currentTimeMillis());
			}
		}, JOIN_DELAY_TICKS);
	}

	@EventHandler
	public void onCaptured(TurfCapturedEvent event) {
		long   now      = System.currentTimeMillis();
		Turf   turf     = event.getTurf();
		String turfName = turf.getDisplayName();
		Gang   winner   = event.getNewOwner();
		Gang   loser    = event.getOldOwner();
		record(winner.getId(), now, text.text("Digest.Captured", "&aCaptured %turf% &7from %gang%",
		                                      "turf", turfName,
		                                      "gang", loser == null ? "unclaimed" : gangName(loser)));
		if (loser != null) {
			record(loser.getId(), now, text.text("Digest.Lost", "&cLost %turf% &7to %gang%",
			                                     "turf", turfName,
			                                     "gang", gangName(winner)));
		}
	}

	@EventHandler
	public void onCaptureFailed(TurfCaptureFailedEvent event) {
		long    now        = System.currentTimeMillis();
		Turf    turf       = event.getTurf();
		String  turfName   = turf.getDisplayName();
		Integer owner      = turf.getOwnerGangId();
		Integer challenger = event.getChallengerGangId();
		switch (event.getReason()) {
			case DEFENDED, ABANDONED -> {
				if (owner != null) {
					record(owner, now, text.text("Digest.Held", "&aHeld %turf% &7against %gang%",
					                             "turf", turfName,
					                             "gang", challengerName(challenger)));
				}
			}
			case STALLED -> {
				String stalled = text.text("Digest.Stalled", "&7Attack on %turf% stalled", "turf", turfName);
				if (owner != null) {
					record(owner, now, stalled);
				}
				if (challenger != null) {
					record(challenger, now, stalled);
				}
			}
			default -> {
				// CANCELLED is an admin or ownership action; it is not shown in the digest.
			}
		}
	}

	@EventHandler
	public void onOwnerChanged(TurfOwnerChangedEvent event) {
		Integer previous = event.getOldOwnerGangId();
		if (previous == null) {
			return;
		}
		long   now      = System.currentTimeMillis();
		String turfName = event.getTurf().getDisplayName();
		switch (event.getCause()) {
			case INACTIVITY -> record(previous, now,
			                          text.text("Digest.Released_Inactive",
			                                    "&cLost %turf% &7(no owner online for %days% days)",
			                                    "turf", turfName,
			                                    "days", capture.getInactivityAutoReleaseDays()));
			case ORPHANED -> record(previous, now,
			                        text.text("Digest.Released_Orphaned", "&cLost %turf% &7(gang data missing)",
			                                  "turf", turfName));
			default -> {
				// Captures are recorded by onCaptured; admin and gang-delete changes are not digest lines.
			}
		}
	}

	@EventHandler
	public void onGangDelete(GangDeleteEvent event) {
		buffer.forgetGang(event.getGang().getId());
	}

	/**
	 * Sends a joining member his digest: the income since his last quit (when any), then the newest event lines since
	 * that quit (or the last three when he has no quit stamp), then the map link. A player with no gang gets nothing.
	 */
	void sendDigest(Player player, long now) {
		User<Player> user = users.findByPlayer(player);
		if (user == null || !user.hasGang()) {
			return;
		}
		int          gangId = user.getGangId();
		long         since  = buffer.lastQuit(player.getUniqueId());
		List<String> lines  = new ArrayList<>();
		List<Entry>  events;
		if (since > 0) {
			BigDecimal income = buffer.incomeSince(gangId, since);
			if (income.signum() > 0) {
				lines.add(text.text("Digest.Income", "&7Income earned while you were away: &a%money_symbol%%amount%",
				                    "money_symbol", Settings.getMoneySymbol(),
				                    "amount", Settings.formatAmount(income)));
			}
			events = buffer.since(gangId, since, MAX_LINES - lines.size());
		} else {
			events = buffer.latest(gangId, RESTART_LINES);
		}
		for (Entry event : events) {
			lines.add(event.line());
		}
		if (lines.isEmpty()) {
			return;
		}
		for (String line : lines) {
			player.sendMessage(line);
		}
		TurfCommand.sendMapLink(player, text);
	}

	private void record(int gangId, long now, String line) {
		buffer.record(gangId, now, line);
	}

	private String gangName(Gang gang) {
		return GangDisplayNameResolver.resolve(gang);
	}

	private String challengerName(@Nullable Integer gangId) {
		if (gangId == null) {
			return "unknown";
		}
		return GangDisplayNameResolver.resolve(gangs.findById(gangId));
	}
}
