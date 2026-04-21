package me.luckyraven.copsncrooks.detainment.breakfree;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.detainment.economy.DetainmentCostsContract;
import me.luckyraven.copsncrooks.detainment.message.DetainmentMessageContract;
import me.luckyraven.copsncrooks.detainment.release.ReleasePipeline;
import me.luckyraven.copsncrooks.detainment.release.ReleaseReason;
import me.luckyraven.copsncrooks.detainment.sound.DetainmentSoundContract;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.core.utilities.ActionBarManager;
import me.luckyraven.core.utilities.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Break-free minigame. While HANDCUFFED a player can spam the Sneak key to fill a tap counter; once the counter reaches
 * the configured threshold the release pipeline fires. Unlike bribe-the-cop this does NOT clear wanted, so cops will
 * re-engage on the next AI tick — the idea is a tense, risky escape instead of a clean exit.
 */
@RequiredArgsConstructor
public class BreakFreeService implements BeanLifecycle {

	private static final long PROMPT_TICK_PERIOD = 20L; // push the prompt once per second

	private final JavaPlugin                plugin;
	private final DetainmentService         detainmentService;
	private final DetainmentCostsContract   costs;
	private final DetainmentMessageContract messages;
	private final ReleasePipeline           releasePipeline;
	private final DetainmentSoundContract   sounds;

	private final Map<UUID, Counter> counters = new ConcurrentHashMap<>();

	private BukkitTask promptTask;

	public void registerTap(Player player) {
		if (player == null) return;
		if (!detainmentService.isHandcuffed(player)) return;

		int  required      = Math.max(1, costs.getBreakFreeTapsRequired());
		long resetWindowMs = Math.max(1, costs.getBreakFreeResetWindowTicks()) * 50L;
		long now           = System.currentTimeMillis();

		Counter counter = counters.computeIfAbsent(player.getUniqueId(), id -> new Counter());
		if (now - counter.lastTapMs > resetWindowMs) {
			counter.taps = 0;
		}
		counter.taps++;
		counter.lastTapMs = now;

		if (counter.taps >= required) {
			counters.remove(player.getUniqueId());
			ChatUtil.sendTitle(player, messages.breakFreeSuccessTitle(), messages.breakFreeSuccessSubtitle());
			sounds.playBreakFreeSuccess(player);
			releasePipeline.release(player, ReleaseReason.BREAK_FREE);
			return;
		}

		ActionBarManager.send(plugin, player,
		                      messages.breakFreeProgressActionBar(counter.taps, required), 40L);
	}

	public void reset(UUID playerId) {
		if (playerId == null) return;
		counters.remove(playerId);
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		if (promptTask != null) return;
		promptTask = Bukkit.getScheduler().runTaskTimer(plugin, this::promptAllHandcuffed,
		                                                PROMPT_TICK_PERIOD, PROMPT_TICK_PERIOD);
	}

	@Override
	public void onClear() {
		if (promptTask != null) {
			promptTask.cancel();
			promptTask = null;
		}
	}

	/**
	 * Pushes the break-free prompt to every HANDCUFFED player. Lets the player see "tap Sneak" even before they start
	 * tapping, so they know the minigame exists. The counter value is whatever is current (0 until they tap, then the
	 * live count until it resets).
	 */
	private void promptAllHandcuffed() {
		int  required      = Math.max(1, costs.getBreakFreeTapsRequired());
		long resetWindowMs = Math.max(1, costs.getBreakFreeResetWindowTicks()) * 50L;
		long now           = System.currentTimeMillis();

		for (Player player : Bukkit.getOnlinePlayers()) {
			if (!detainmentService.isHandcuffed(player)) continue;

			Counter counter = counters.get(player.getUniqueId());
			int     current = 0;
			if (counter != null && now - counter.lastTapMs <= resetWindowMs) current = counter.taps;

			ActionBarManager.send(plugin, player,
			                      messages.breakFreeProgressActionBar(current, required), 25L);
		}
	}

	private static final class Counter {
		private int  taps;
		private long lastTapMs;
	}
}
