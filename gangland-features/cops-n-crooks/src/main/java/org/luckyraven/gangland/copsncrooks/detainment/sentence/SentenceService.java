package org.luckyraven.gangland.copsncrooks.detainment.sentence;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseReason;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.util.ActionBarManager;

import java.util.Map;
import java.util.UUID;

/**
 * Ticks jailed players' sentence countdowns. Runs a repeating BukkitTask that walks the detainment registry every
 * second, updates the sentence-remaining action bar on each JAILED player, and auto-releases through the release
 * pipeline when the timer expires.
 */
@CustomLog
public class SentenceService implements BeanLifecycle {

	private static final long TICK_PERIOD_TICKS = 20L;

	private final JavaPlugin                plugin;
	private final DetainmentRegistry        detainmentRegistry;
	private final DetainmentService         detainmentService;
	private final ReleasePipeline           releasePipeline;
	private final DetainmentMessageContract messages;
	private final DetainmentSoundContract   sounds;

	private BukkitTask task;

	public SentenceService(JavaPlugin plugin, DetainmentRegistry detainmentRegistry,
	                       DetainmentService detainmentService, ReleasePipeline releasePipeline,
	                       DetainmentMessageContract messages, DetainmentSoundContract sounds) {
		this.plugin             = plugin;
		this.detainmentRegistry = detainmentRegistry;
		this.detainmentService  = detainmentService;
		this.releasePipeline    = releasePipeline;
		this.messages           = messages;
		this.sounds             = sounds;
	}

	public long getRemainingSeconds(Player player) {
		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		if (detained == null || detained.getSentenceExpiresAt() == null) return 0L;
		long remainingMs = detained.getSentenceExpiresAt() - System.currentTimeMillis();
		return Math.max(0L, remainingMs / 1000L);
	}

	public void tick(Player player) {
		if (!detainmentService.isJailed(player)) return;

		DetainedPlayer detained = detainmentRegistry.getDetainedPlayers().get(player.getUniqueId());
		if (detained == null || detained.getSentenceExpiresAt() == null) return;

		long remaining = detained.getSentenceExpiresAt() - System.currentTimeMillis();
		if (remaining > 0) {
			long secondsRemaining = Math.max(1, remaining / 1000L);
			ActionBarManager.send(plugin, player, messages.sentenceTickActionBar(secondsRemaining), 25L);
			return;
		}

		log.debug("Sentence served for {} - auto-releasing.", player.getName());
		sounds.playSentenceComplete(player);
		releasePipeline.release(player, ReleaseReason.SENTENCE_COMPLETE);
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (task != null) return;
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
	}

	@Override
	public void onClear() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	private void tickAll() {
		Map<UUID, DetainedPlayer> detained = detainmentRegistry.getDetainedPlayers();
		for (DetainedPlayer entry : detained.values()) {
			if (entry.getState() != DetainmentState.JAILED) continue;
			Player player = Bukkit.getPlayer(entry.getPlayerId());
			if (player == null || !player.isOnline()) continue;
			tick(player);
		}
	}
}
