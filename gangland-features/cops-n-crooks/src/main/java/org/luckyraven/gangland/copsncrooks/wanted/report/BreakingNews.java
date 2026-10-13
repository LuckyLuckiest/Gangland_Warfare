package org.luckyraven.gangland.copsncrooks.wanted.report;

import lombok.CustomLog;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArc;
import org.luckyraven.gangland.copsncrooks.wanted.config.PursuitReportSettings;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.ChaseArcs;
import org.luckyraven.gangland.core.wanted.Wanted;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Server-wide breaking news for big chases: the live headline once per chase (4 stars or 120 s) under a global cooldown, and
 * the closing headline for a chase that made the news. Holds the only cooldown stamp. Main thread only.
 *
 * @since 0.21.0
 */
@CustomLog
public final class BreakingNews {

	private boolean announced;
	private long    lastNewsAt;

	private final ChaseArcs         arcs;
	private final ChaseConfigLoader config;
	private final WantedMessages    messages;
	private final Consumer<String>  broadcast;

	/** {@code broadcast} sends one coloured line to every online player. */
	public BreakingNews(ChaseArcs arcs, ChaseConfigLoader config, WantedMessages messages, Consumer<String> broadcast) {
		this.arcs      = arcs;
		this.config    = config;
		this.messages  = messages;
		this.broadcast = broadcast;
	}

	/**
	 * Sends the live headline when every condition holds; true when it went out.
	 * <p>
	 * The {@code reason} argument is currently ignored: the debug log reports the trigger computed here ({@code STARS} or
	 * {@code TIME}), not the caller's value. Kept so call sites stay unchanged; no behaviour depends on it.
	 */
	public boolean check(Player player, int level, String reason) {
		PursuitReportSettings settings = config.getPursuitReport();
		ChaseArc              arc      = arcs.arc(player.getUniqueId());
		if (!settings.newsEnabled() || arc == null || arc.newsSent()) {
			return false;
		}

		long    now       = arcs.now();
		boolean starTest  = Math.max(level, arc.peak()) >= settings.newsMinStars();
		boolean timeTest  = (now - arc.startedAt()) / 1000L >= settings.newsMinSeconds();
		if (!starTest && !timeTest) {
			return false;
		}
		if (announced && now - lastNewsAt < settings.newsCooldownSeconds() * 1000L) {
			return false;
		}

		String why = starTest ? "STARS" : "TIME";
		broadcast.accept(messages.format(WantedMessages.Key.NEWS_LIVE, Map.of("player", player.getName(), "stars",
		                                                                     stars(Math.max(level, arc.peak())))));
		arcs.markNews(player.getUniqueId());
		announced  = true;
		lastNewsAt = now;
		log.debug("BREAKING_NEWS {} reason={}", player.getName(), why);
		return true;
	}

	/** The AI-tick hook: the time trigger. */
	public void onAiTick(Player player, @Nullable CopGroup group) {
		check(player, 0, "TIME");
	}

	/** The closing headline for a chase that made the news; no cooldown. True when it went out. */
	public boolean closing(Player player, ChaseArc arc, String ending) {
		if (!config.getPursuitReport().newsEnabled() || !arc.newsSent()) {
			return false;
		}

		broadcast.accept(messages.format(WantedMessages.Key.NEWS_END, Map.of("player", player.getName(), "stars",
		                                                                    stars(arc.peak()), "ending", ending)));
		log.debug("BREAKING_NEWS {} reason={}", player.getName(), "END");
		return true;
	}

	private static String stars(int level) {
		return Wanted.buildStars(level, Math.max(5, level));
	}
}
