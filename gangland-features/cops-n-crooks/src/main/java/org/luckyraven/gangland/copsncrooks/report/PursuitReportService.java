package org.luckyraven.gangland.copsncrooks.report;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The 0.12 pursuit report: tracks a chase from {@code WantedStartEvent} to {@code WantedEndEvent}
 * ({@link PursuitReportListener} drives every call below), sends the player a short chat report once it ends, and —
 * for a chase reaching {@code Breaking_News.Min_Stars} or {@code Breaking_News.Min_Duration_Seconds} — broadcasts a
 * server-wide "breaking news" line, subject to a global {@code Breaking_News.Cooldown_Seconds}. The whole feature
 * no-ops when {@code Pursuit_Report.Enable} is {@code false}. All state is main-thread only.
 */
public class PursuitReportService {

	private final CopLoader         copLoader;
	private final DetainmentService detainment;
	private final LongSupplier      clock;

	private final Map<UUID, ChaseRecord> chases;

	/** Epoch millis of the last breaking-news broadcast; negative until the first one. */
	private long lastBroadcastAt = -1L;

	public PursuitReportService(CopLoader copLoader, DetainmentService detainment) {
		this(copLoader, detainment, System::currentTimeMillis);
	}

	/**
	 * Test seam: {@code clock} supplies the epoch milliseconds chase duration and the broadcast cooldown are measured
	 * against.
	 */
	PursuitReportService(CopLoader copLoader, DetainmentService detainment, LongSupplier clock) {
		this.copLoader  = copLoader;
		this.detainment = detainment;
		this.clock      = clock;
		this.chases     = new HashMap<>();
	}

	/**
	 * Starts tracking a chase. A no-op when {@code Pursuit_Report.Enable} is {@code false}.
	 *
	 * @param player the newly-wanted player
	 * @param level the wanted level the chase started at
	 */
	public void start(Player player, int level) {
		if (!getConfig().isEnabled()) return;

		ChaseRecord record = new ChaseRecord(clock.getAsLong());
		record.raiseStars(level);
		chases.put(player.getUniqueId(), record);
	}

	/**
	 * Raises the chase's top star, if {@code level} is higher than what was already recorded. A no-op when the
	 * player has no chase running (feature disabled, or {@link #start} never saw them).
	 *
	 * @param playerId the wanted player
	 * @param level the new wanted level
	 */
	public void raise(UUID playerId, int level) {
		ChaseRecord record = chases.get(playerId);
		if (record != null) record.raiseStars(level);
	}

	/**
	 * Credits a cop kill to the killer's chase, if they have one running.
	 *
	 * @param killer the player who killed the cop; {@code null} for an unattributed death
	 */
	public void copKilled(@Nullable Player killer) {
		if (killer == null) return;

		ChaseRecord record = chases.get(killer.getUniqueId());
		if (record != null) record.copKilled();
	}

	/**
	 * Marks the chase as ending in death or downed, so it later resolves as {@link ChaseOutcome#WASTED} regardless of
	 * how the wanted level itself is cleared.
	 *
	 * @param playerId the player
	 */
	public void markWasted(UUID playerId) {
		ChaseRecord record = chases.get(playerId);
		if (record != null) record.markWasted();
	}

	/**
	 * Records the moment the player was cuffed, for {@link ChaseRecord}'s busted grace window.
	 *
	 * @param playerId the cuffed player
	 */
	public void markCuffed(UUID playerId) {
		ChaseRecord record = chases.get(playerId);
		if (record != null) record.markCuffed(clock.getAsLong());
	}

	/**
	 * Resolves the chase, sends the player their report and, if it qualifies, broadcasts the breaking-news line.
	 * A no-op when the player has no chase running.
	 *
	 * @param player the player whose chase just ended
	 */
	public void finish(Player player) {
		ChaseRecord record = chases.remove(player.getUniqueId());
		if (record == null) return;

		if (!getConfig().isEnabled()) return;

		long         now     = clock.getAsLong();
		ChaseOutcome outcome = record.resolve(detainment.isRestrained(player), now);

		for (String line : formatReport(record, outcome, now)) {
			player.sendMessage(line);
		}

		if (shouldBroadcast(record, now)) {
			lastBroadcastAt = now;
			Bukkit.broadcastMessage(formatBreakingNews(player, record, outcome, now));
		}
	}

	/**
	 * Forgets a chase without reporting it — the player left mid-chase.
	 *
	 * @param playerId the player
	 */
	public void discard(UUID playerId) {
		chases.remove(playerId);
	}

	/**
	 * @return the loaded {@code Pursuit_Report:} config, or {@link PursuitReportConfig#defaults()} while none is
	 * loaded
	 */
	public PursuitReportConfig getConfig() {
		PursuitReportConfig loaded = copLoader.getLoadedPursuitReportConfig();
		return loaded != null ? loaded : PursuitReportConfig.defaults();
	}

	/**
	 * Formats {@code Messages.Report}, colored, with {@code %outcome%}, {@code %duration%}, {@code %stars%} and
	 * {@code %cops%} substituted.
	 */
	List<String> formatReport(ChaseRecord record, ChaseOutcome outcome, long now) {
		PursuitReportConfig config = getConfig();

		String outcomeText = GanglandChatUtil.color(config.outcomeMessage(outcome));
		String duration    = formatDuration(record.durationSeconds(now));
		String stars       = Wanted.buildStars(record.getMaxStars(), Settings.getWantedMaximumLevel());
		String cops        = String.valueOf(record.getCopsKilled());

		List<String> lines = new ArrayList<>();
		for (String template : config.getReportMessages()) {
			String replaced = template.replace("%outcome%", outcomeText).replace("%duration%", duration)
			                          .replace("%stars%", stars).replace("%cops%", cops);
			lines.add(GanglandChatUtil.color(replaced));
		}
		return lines;
	}

	/**
	 * @return whether the chase qualifies for the breaking-news broadcast ({@code Breaking_News.Min_Stars} or
	 * {@code Breaking_News.Min_Duration_Seconds}) and the global cooldown has elapsed
	 */
	boolean shouldBroadcast(ChaseRecord record, long now) {
		PursuitReportConfig config = getConfig();
		if (!config.isBreakingNewsEnabled()) return false;

		boolean qualifies = record.getMaxStars() >= config.getBreakingNewsMinStars()
		                 || record.durationSeconds(now) >= config.getBreakingNewsMinDurationSeconds();
		if (!qualifies) return false;

		if (lastBroadcastAt < 0) return true;

		long cooldownMillis = config.getBreakingNewsCooldownSeconds() * 1000L;
		return now - lastBroadcastAt >= cooldownMillis;
	}

	private String formatBreakingNews(Player player, ChaseRecord record, ChaseOutcome outcome, long now) {
		PursuitReportConfig config = getConfig();

		String duration = formatDuration(record.durationSeconds(now));
		String stars    = Wanted.buildStars(record.getMaxStars(), Settings.getWantedMaximumLevel());
		String cops     = String.valueOf(record.getCopsKilled());

		String replaced = config.getBreakingNewsMessage()
		                        .replace("%player%", player.getName())
		                        .replace("%outcome_plain%", plainOutcome(outcome))
		                        .replace("%duration%", duration)
		                        .replace("%stars%", stars)
		                        .replace("%cops%", cops);

		return GanglandChatUtil.color(replaced);
	}

	private static String plainOutcome(ChaseOutcome outcome) {
		return switch (outcome) {
			case ESCAPED -> "escaped";
			case BUSTED  -> "was busted";
			case WASTED  -> "went down";
		};
	}

	/**
	 * @return {@code seconds} formatted as {@code m:ss}
	 */
	private static String formatDuration(long seconds) {
		long clamped = Math.max(0L, seconds);
		long minutes = clamped / 60L;
		long secs    = clamped % 60L;
		return minutes + ":" + (secs < 10L ? "0" + secs : String.valueOf(secs));
	}

}
