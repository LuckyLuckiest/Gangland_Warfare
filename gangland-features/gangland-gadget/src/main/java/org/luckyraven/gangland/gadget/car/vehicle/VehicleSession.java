package org.luckyraven.gangland.gadget.car.vehicle;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.ActionBarManager;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.CarKey;
import org.luckyraven.gangland.gadget.car.ExhaustSide;
import org.luckyraven.gangland.gadget.car.vehicle.entity.VehicleEntity;
import org.luckyraven.gangland.gadget.fuel.FuelService;
import org.luckyraven.gangland.item.fuel.FuelKey;

import java.util.UUID;

import static org.luckyraven.gangland.gadget.car.ExhaustSide.random;

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
	/**
	 * Last known location of the car entity, refreshed every tick by {@link VehicleMovementTask} while the entity is
	 * alive. Used by {@code CarService#forcePark} to persist a driven car's location when the underlying entity is
	 * force-despawned mid-drive — more reliable than {@code entity.getLocation()} (which may be stale on an invalid
	 * entity) or {@code driver.getLocation()} (which is post-ejection, possibly several blocks off).
	 */
	@Setter
	private       Location            lastKnownLocation;

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
	 * display is handled separately by the movement task via {@link FuelService}.
	 *
	 * @param fuelDisplay optional action bar message for fuel; {@code null} if fuel is disabled
	 */
	public void updateDisplays(@Nullable String fuelDisplay) {
		healthBar.setProgress(durabilityProgress());
		healthBar.setTitle(buildHealthTitle());

		if (fuelDisplay != null) {
			ActionBarManager.sendBackground(driver, fuelDisplay, 10);
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
		ItemStack   item    = car.buildItem(Bukkit.getPlayer(driverUUID));
		ItemBuilder builder = new ItemBuilder(item);

		builder.addTag(CarKey.CAR_DURABILITY.getKey(), currentDurability);
		builder.addTag(FuelKey.FUEL_CURRENT.getKey(), currentFuel);
		builder.addTag(FuelKey.FUEL_MAX.getKey(), maxFuel);
		builder.addTag(CarKey.CAR_OWNER.getKey(), driverUUID.toString());
		builder.addTag(CarKey.CAR_EXHAUST_SIDE.getKey(), exhaustSide.name());

		return builder.build();
	}

	// ------------------------------------------------------------------

	private double durabilityProgress() {
		if (car.getMaxDurability() <= 0) return 1.0;
		return Math.max(0.0, Math.min((double) currentDurability / car.getMaxDurability(), 1.0));
	}

	private String buildHealthTitle() {
		return ChatUtil.color(
				"&c❤ " + car.getDisplayName() + " &8— &7" + currentDurability + "&8/&7" + car.getMaxDurability());
	}
}
