package org.luckyraven.gangland.copsncrooks.detainment.breakfree;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleasePipeline;
import org.luckyraven.gangland.copsncrooks.detainment.release.ReleaseReason;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.crime.CrimeService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.crime.Crimes;
import lombok.CustomLog;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.util.ActionBarManager;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * Break-free minigame. While HANDCUFFED a player can spam the Sneak key to fill a tap counter; once the counter reaches
 * the configured threshold the release pipeline fires. Unlike bribe-the-cop this does NOT clear wanted, so cops will
 * re-engage on the next AI tick — the idea is a tense, risky escape instead of a clean exit.
 */
@CustomLog
public class BreakFreeService implements BeanLifecycle {

	private static final long PROMPT_TICK_PERIOD = 20L; // push the prompt once per second

	private final JavaPlugin                plugin;
	private final DetainmentService         detainmentService;
	private final DetainmentCostsContract   costs;
	private final DetainmentMessageContract messages;
	private final ReleasePipeline           releasePipeline;
	private final DetainmentSoundContract   sounds;
	private final CrimeService              crimes;
	private final WantedMessages            wantedMessages;
	private final DoubleSupplier            random;

	/** 0.21.0 seams so tests need no NBT: does the player hold a pick / remove one. */
	Predicate<Player> pickFinder   = Lockpicks::has;
	Predicate<Player> pickConsumer = Lockpicks::consumeOne;

	private final Map<UUID, Counter> counters = new ConcurrentHashMap<>();

	private BukkitTask promptTask;

	public BreakFreeService(JavaPlugin plugin, DetainmentService detainmentService, DetainmentCostsContract costs,
	                        DetainmentMessageContract messages, ReleasePipeline releasePipeline,
	                        DetainmentSoundContract sounds, CrimeService crimes, WantedMessages wantedMessages,
	                        DoubleSupplier random) {
		this.plugin            = plugin;
		this.detainmentService = detainmentService;
		this.costs             = costs;
		this.messages          = messages;
		this.releasePipeline   = releasePipeline;
		this.sounds            = sounds;
		this.crimes            = crimes;
		this.wantedMessages    = wantedMessages;
		this.random            = random;
	}

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
		boolean pick   = pickFinder.test(player);
		int     weight = pick ? Math.max(1, costs.getLockpickTapWeight()) : 1;
		counter.taps += weight;
		counter.lastTapMs = now;

		boolean snapped = false;
		if (pick && random.getAsDouble() < costs.getLockpickSnapChance()) {
			snapped = pickConsumer.test(player);
			if (snapped) player.sendMessage(wantedMessages.format(WantedMessages.Key.LOCKPICK_SNAPPED, Map.of()));
		}
		log.debug("LOCKPICK {} weight={} snapped={}", player.getName(), weight, snapped);

		if (counter.taps >= required) {
			counters.remove(player.getUniqueId());
			ChatUtil.sendTitle(player, messages.breakFreeSuccessTitle(), messages.breakFreeSuccessSubtitle());
			sounds.playBreakFreeSuccess(player);
			crimes.commit(player, Crimes.RESISTING_ARREST, player.getLocation());
			releasePipeline.release(player, ReleaseReason.BREAK_FREE);
			return;
		}

		ActionBarManager.send(plugin, player,
		                      messages.breakFreeProgressActionBar(Math.min(counter.taps, required), required), 40L);
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
