package me.luckyraven.inventory.loot;

import lombok.Getter;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Represents an active loot chest opening session with countdown
 */
@Getter
public class LootChestSession {

	private final UUID             sessionId;
	private final Player           player;
	private final LootChestData    chestData;
	private final InventoryHandler inventory;
	private final CountdownTimer   countdownTimer;
	private final List<ItemStack>  generatedLoot;

	private SessionState state;

	public LootChestSession(JavaPlugin plugin, Player player, LootChestData chestData,
							InventoryHandler inventory, List<ItemStack> generatedLoot,
							long countdownTime, Consumer<LootChestSession> onCountdownTick,
							Consumer<LootChestSession> onComplete) {
		this.sessionId     = UUID.randomUUID();
		this.player        = player;
		this.chestData     = chestData;
		this.inventory     = inventory;
		this.generatedLoot = generatedLoot;
		this.state         = SessionState.COUNTDOWN;

		this.countdownTimer = new CountdownTimer(
				plugin, countdownTime,
				timer -> onStart(),
				timer -> {
					if (onCountdownTick != null) onCountdownTick.accept(this);
				},
				timer -> {
					completeCountdown();
					if (onComplete != null) onComplete.accept(this);
				}
		);
	}

	public void start(boolean async) {
		countdownTimer.start(async);
	}

	public void cancel() {
		if (state == SessionState.COUNTDOWN) {
			countdownTimer.stop();
			state = SessionState.CANCELLED;
		}
	}

	public void close() {
		state = SessionState.CLOSED;
		chestData.markAsLooted();
	}

	public long getTimeLeft() {
		return countdownTimer.getTimeLeft();
	}

	private void onStart() {
		state = SessionState.COUNTDOWN;
	}

	private void completeCountdown() {
		state = SessionState.OPEN;
		populateInventory();
		inventory.open(player);
		state = SessionState.LOOTING;
	}

	private void populateInventory() {
		int slot = 0;
		for (ItemStack item : generatedLoot) {
			if (slot >= inventory.getSize()) break;
			if (item != null) {
				inventory.setItem(slot++, item, true);
			}
		}
	}

	public enum SessionState {
		COUNTDOWN,
		OPEN,
		LOOTING,
		CLOSED,
		CANCELLED
	}

}
