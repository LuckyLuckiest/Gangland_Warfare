package me.luckyraven.gadget.car.vehicle;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.CarKey;
import me.luckyraven.gadget.car.ExhaustSide;
import me.luckyraven.gadget.car.vehicle.entity.VehicleEntity;
import me.luckyraven.item.fuel.FuelKey;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import static me.luckyraven.gadget.car.ExhaustSide.random;

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
	private final int                 maxFuel;
	private final ExhaustSide         exhaustSide;
	@Setter
	private       Car                 car;
	@Setter
	private       VehicleMovementTask task;
	private       int                 currentDurability;
	private       int                 currentFuel;

	// WASD input state — written by VehicleInputInterceptor (Netty IO thread), read by VehicleMovementTask (main thread)
	private volatile boolean inputForward;
	private volatile boolean inputBackward;
	private volatile boolean inputLeft;
	private volatile boolean inputRight;

	/**
	 * @param initialDurability durability carried over from the parked vehicle
	 * @param initialFuel fuel carried over from the parked vehicle (entity PDC / DB record)
	 * @param maxFuel maximum fuel capacity from the car's fuel definition
	 */
	public VehicleSession(VehicleEntity entity, Car car, Player driver, int initialDurability, int initialFuel,
	                      int maxFuel, @Nullable ExhaustSide exhaustSide) {
		this.entity            = entity;
		this.car               = car;
		this.driver            = driver;
		this.driverUUID        = driver.getUniqueId();
		this.currentDurability = initialDurability;
		this.currentFuel       = initialFuel;
		this.maxFuel           = maxFuel;
		this.exhaustSide       = exhaustSide != null ? exhaustSide : random();

		this.healthBar = Bukkit.createBossBar(buildHealthTitle(), BarColor.RED, BarStyle.SOLID);
		this.healthBar.setProgress(durabilityProgress());
		this.healthBar.addPlayer(driver);
	}

	public void damage(int amount) {
		currentDurability = Math.max(0, currentDurability - amount);
	}

	public boolean hasFuel() {
		return currentFuel > 0;
	}

	public void consumeFuel(int amount) {
		currentFuel = Math.max(0, currentFuel - amount);
	}

	public void addFuel(int amount) {
		currentFuel = Math.min(maxFuel, currentFuel + amount);
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
	 * Refreshes the boss bar health display for the driver. Called every tick by {@link VehicleMovementTask}. Fuel
	 * display is handled separately by the movement task via {@link me.luckyraven.gadget.fuel.FuelService}.
	 *
	 * @param fuelDisplay optional action bar message for fuel; {@code null} if fuel is disabled
	 */
	public void updateDisplays(@Nullable String fuelDisplay) {
		healthBar.setProgress(durabilityProgress());
		healthBar.setTitle(buildHealthTitle());

		if (fuelDisplay != null) {
			ChatUtil.sendActionBar(driver, fuelDisplay);
		}
	}

	/**
	 * Removes the boss bar from all viewers. Must be called when the session ends.
	 */
	public void removeDisplays() {
		healthBar.removeAll();
	}

	/**
	 * Builds the car item with updated durability and fuel NBT values to return to the player.
	 */
	public ItemStack buildReturnItem() {
		ItemStack   item    = car.buildItem();
		ItemBuilder builder = new ItemBuilder(item);

		builder.addTag(CarKey.CAR_DURABILITY.getKey(), currentDurability);
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), currentFuel);
		builder.addTag(CarKey.CAR_OWNER.getKey(), driverUUID.toString());
		builder.addTag(CarKey.CAR_EXHAUST_SIDE.getKey(), exhaustSide.name());

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
