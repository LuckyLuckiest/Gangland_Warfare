package me.luckyraven.gadget.car.vehicle;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.CarKey;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Represents an active driving session — a player currently operating a car in the world.
 * <p>
 * Vehicle health is displayed via a {@link BossBar}; fuel via the action bar. Both are updated every tick by
 * {@link VehicleMovementTask}.
 */
@Getter
public class VehicleSession {

	private final VehicleEntity       entity;
	private final Player              driver;
	private final UUID                driverUUID;
	private final BossBar             healthBar;
	@Setter
	private       Car                 car;
	@Setter
	private       VehicleMovementTask task;
	private       int                 currentFuel;
	private       int                 currentDurability;

	// WASD input state — written by VehicleInputInterceptor (Netty IO thread), read by VehicleMovementTask (main thread)
	private volatile boolean inputForward;
	private volatile boolean inputBackward;
	private volatile boolean inputLeft;
	private volatile boolean inputRight;

	/**
	 * @param initialFuel fuel carried over from the item (or {@code car.getMaxFuel()} for a fresh car)
	 * @param initialDurability durability carried over from the item
	 */
	public VehicleSession(VehicleEntity entity, Car car, Player driver, int initialFuel, int initialDurability) {
		this.entity            = entity;
		this.car               = car;
		this.driver            = driver;
		this.driverUUID        = driver.getUniqueId();
		this.currentFuel       = initialFuel;
		this.currentDurability = initialDurability;

		this.healthBar = Bukkit.createBossBar(buildHealthTitle(), BarColor.RED, BarStyle.SOLID);
		this.healthBar.setProgress(durabilityProgress());
		this.healthBar.addPlayer(driver);
	}

	public void consumeFuel(int amount) {
		currentFuel = Math.max(0, currentFuel - amount);
	}

	public boolean hasFuel() {
		return !car.isFuelEnabled() || currentFuel > 0;
	}

	public void damage(int amount) {
		currentDurability = Math.max(0, currentDurability - amount);
	}

	public boolean isDestroyed() {
		return currentDurability <= 0;
	}

	public void setInput(boolean forward, boolean backward, boolean left, boolean right) {
		this.inputForward  = forward;
		this.inputBackward = backward;
		this.inputLeft     = left;
		this.inputRight    = right;
	}

	/**
	 * Refreshes the boss bar health display and action bar fuel display for the driver. Called every tick by
	 * {@link VehicleMovementTask}.
	 */
	public void updateDisplays() {
		healthBar.setProgress(durabilityProgress());
		healthBar.setTitle(buildHealthTitle());

		if (car.isFuelEnabled()) {
			ChatUtil.sendActionBar(driver, "&6⛽ Fuel &f" + currentFuel + "&7/&f" + car.getMaxFuel());
		}
	}

	/**
	 * Removes the boss bar from all viewers. Must be called when the session ends.
	 */
	public void removeDisplays() {
		healthBar.removeAll();
	}

	/**
	 * Builds the car item with updated fuel and durability NBT values to return to the player.
	 */
	public ItemStack buildReturnItem() {
		ItemStack   item    = car.buildItem();
		ItemBuilder builder = new ItemBuilder(item);

		builder.addTag(CarKey.CAR_FUEL.getKey(), currentFuel);
		builder.addTag(CarKey.CAR_DURABILITY.getKey(), currentDurability);
		builder.addTag(CarKey.CAR_OWNER.getKey(), driverUUID.toString());

		return builder.build();
	}

	// ------------------------------------------------------------------

	private double durabilityProgress() {
		if (car.getMaxDurability() <= 0) return 1.0;
		return Math.clamp((double) currentDurability / car.getMaxDurability(), 0.0, 1.0);
	}

	private String buildHealthTitle() {
		return ChatUtil.color(
				"&c❤ " + car.getDisplayName() + " &8— &7" + currentDurability + "&8/&7" + car.getMaxDurability());
	}
}
