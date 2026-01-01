package me.luckyraven.inventory.loot.data;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents a cracking/lockpicking minigame session. The player must complete a minigame within the time limit to open
 * the chest.
 */
@Getter
public class CrackingSession {

	private final UUID          sessionId;
	private final JavaPlugin    plugin;
	private final Player        player;
	private final LootChestData chestData;
	private final LootTable     lootTable;
	private final LootTier      tier;
	private final long          totalTime;

	private long       timeRemaining;
	private BukkitTask timerTask;
	private CrackState state;

	@Setter
	private int progress;       // 0-100 progress for minigame

	@Setter
	private int targetProgress; // Target to reach (default 100)

	public CrackingSession(JavaPlugin plugin, Player player, LootChestData chestData, LootTable lootTable,
						   LootTier tier, long timeSeconds) {
		this.sessionId      = UUID.randomUUID();
		this.plugin         = plugin;
		this.player         = player;
		this.chestData      = chestData;
		this.lootTable      = lootTable;
		this.tier           = tier;
		this.totalTime      = timeSeconds;
		this.timeRemaining  = timeSeconds;
		this.state          = CrackState.PENDING;
		this.progress       = 0;
		this.targetProgress = 100;
	}

	/**
	 * Starts the cracking timer
	 *
	 * @param onTick Called every second with remaining time
	 * @param onSuccess Called when player completes the minigame in time
	 * @param onFailed Called when time runs out
	 */
	public void start(BiConsumer<CrackingSession, Long> onTick, Consumer<CrackingSession> onSuccess,
					  Consumer<CrackingSession> onFailed) {
		this.state = CrackState.IN_PROGRESS;

		timerTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
			if (state == CrackState.COMPLETED) {
				stopTimer();
				onSuccess.accept(this);
				return;
			}

			if (timeRemaining <= 0) {
				state = CrackState.FAILED;
				stopTimer();
				onFailed.accept(this);
				return;
			}

			onTick.accept(this, timeRemaining);
			timeRemaining--;
		}, 0L, 20L);
	}

	/**
	 * Marks the cracking as complete (player succeeded the minigame)
	 */
	public void complete() {
		if (state != CrackState.IN_PROGRESS) return;
		this.state = CrackState.COMPLETED;
	}

	/**
	 * Cancels the cracking session
	 */
	public void cancel() {
		this.state = CrackState.CANCELLED;
		stopTimer();
	}

	/**
	 * Adds progress to the minigame
	 *
	 * @param amount Progress to add (0-100 scale)
	 */
	public void addProgress(int amount) {
		this.progress = Math.min(targetProgress, progress + amount);
		if (progress >= targetProgress) {
			complete();
		}
	}

	/**
	 * Gets the progress percentage (0.0 - 1.0)
	 */
	public double getProgressPercentage() {
		return (double) progress / targetProgress;
	}

	/**
	 * Gets the time remaining percentage (0.0 - 1.0)
	 */
	public double getTimePercentage() {
		return (double) timeRemaining / totalTime;
	}

	/**
	 * Checks if cracking is successful
	 */
	public boolean isSuccessful() {
		return state == CrackState.COMPLETED;
	}

	/**
	 * Checks if cracking failed
	 */
	public boolean isFailed() {
		return state == CrackState.FAILED;
	}

	/**
	 * Checks if cracking is still in progress
	 */
	public boolean isInProgress() {
		return state == CrackState.IN_PROGRESS;
	}

	private void stopTimer() {
		if (timerTask != null) {
			timerTask.cancel();
			timerTask = null;
		}
	}

	public enum CrackState {
		PENDING,
		IN_PROGRESS,
		COMPLETED,
		FAILED,
		CANCELLED
	}

}
