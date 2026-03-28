package me.luckyraven.sign.aspect;

import lombok.RequiredArgsConstructor;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.gadget.car.Car;
import me.luckyraven.gadget.car.CarManager;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.item.unique.UniqueItem;
import me.luckyraven.util.item.wearable.Wearable;
import me.luckyraven.util.item.wearable.WearableTrait;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.ammo.Ammunition;
import me.luckyraven.weapon.ammo.AmmunitionManager;
import me.luckyraven.weapon.dto.AmmunitionData;
import me.luckyraven.weapon.types.gun.GunWeapon;
import me.luckyraven.weapon.wearable.WearableService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class ViewInventoryAspect implements SignAspect {

	private final JavaPlugin        plugin;
	private final WeaponService     weaponService;
	private final AmmunitionManager ammunitionManager;
	private final CarManager        carManager;
	private final WearableService   wearableService;
	private final UniqueItemAddon   uniqueItemAddon;

	@Override
	public AspectResult execute(Player player, ParsedSign sign) {
		String itemName = sign.getContent();

		// Try to find weapon first
		Weapon weapon = findWeapon(itemName);

		if (weapon != null) {
			openWeaponView(player, weapon);
			return AspectResult.success("Opened weapon view: " + itemName);
		}

		// Try to find ammunition
		if (ammunitionManager.getAmmunitionKeys().contains(itemName)) {
			openAmmunitionView(player, itemName);
			return AspectResult.success("Opened ammunition view: " + itemName);
		}

		// Try to find wearable
		Wearable wearable = wearableService.getWearable(itemName);
		if (wearable != null) {
			openWearableView(player, wearable);
			return AspectResult.success("Opened wearable view: " + itemName);
		}

		// Try to find car
		Car car = findCar(itemName);
		if (car != null) {
			openCarView(player, car);
			return AspectResult.success("Opened car view: " + itemName);
		}

		// Generic item view
		openGenericItemView(player, itemName);
		return AspectResult.success("Opened item view: " + itemName);
	}

	@Override
	public boolean canExecute(Player player, ParsedSign sign) {
		String itemName = sign.getContent();

		// Check if weapon exists
		if (findWeapon(itemName) != null) {
			return true;
		}

		// Check if ammunition exists
		if (ammunitionManager.getAmmunitionKeys().contains(itemName)) {
			return true;
		}

		// Allow any item name for generic view
		return !itemName.isEmpty();
	}

	@Override
	public String getName() {
		return "ViewInventoryAspect";
	}

	private void openWeaponView(Player player, Weapon weapon) {
		// Determine inventory size based on ammunition types
		List<String> compatibleAmmo = getCompatibleAmmunition(weapon);
		int          requiredSlots  = 1 + compatibleAmmo.size(); // 1 for weapon + ammo types
		int          inventorySize  = Math.max(9, InventoryHandler.factorOfNine(requiredSlots));

		// Create inventory handler
		String title = "&6View: &e" + weapon.getDisplayName();

		InventoryHandler inventory = new InventoryHandler(plugin, title, inventorySize, player);

		// Add weapon in center-left position
		int       weaponSlot = 3;
		ItemStack weaponItem = weapon.buildItem();

		List<String> weaponLore = new ArrayList<>();
		weaponLore.add("&7Type: &f" + weapon.getCategory().name());
		if (weapon instanceof GunWeapon gun && gun.getAmmunitionData() != null) {
			weaponLore.add("&7Damage: &c" + gun.getProjectileData().getDamage());
			weaponLore.add("&7Magazine: &e" + gun.getAmmunitionData().getMaxMagCapacity());
			weaponLore.add("&7Fire Rate: &a" + gun.getProjectileData().getCooldown() + "ms");
		}
		weaponLore.add("");
		weaponLore.add("&7Compatible Ammo: " + compatibleAmmo);

		ItemBuilder weaponBuilder = new ItemBuilder(weaponItem).setLore(weaponLore);

		inventory.setItem(weaponSlot, weaponBuilder, false, null);

		// Add compatible ammunition starting from slot after weapon
		int ammoStartSlot = weaponSlot + 2;
		for (int i = 0; i < compatibleAmmo.size(); i++) {
			String    ammoName = compatibleAmmo.get(i);
			ItemStack ammoItem = createAmmunitionItem(ammoName, weapon);

			if (ammoItem != null) {
				inventory.setItem(ammoStartSlot + i - 1, ammoItem, false, null);
			}
		}

		// Add decorative glass panes in empty slots
		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());

		InventoryUtil.fillInventory(inventory, fill);

		inventory.open(player);
	}

	private void openAmmunitionView(Player player, String ammoName) {
		String title = "&6View: &e" + ammoName;

		InventoryHandler inventory = new InventoryHandler(plugin, title, 9, player);

		ItemStack ammoItem = createAmmunitionItem(ammoName, null);

		if (ammoItem != null) {
			inventory.setItem(4, ammoItem, false, null);
		}

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());

		InventoryUtil.fillInventory(inventory, fill);

		inventory.open(player);
	}

	private ItemStack createAmmunitionItem(String ammoName, Weapon forWeapon) {
		// Get ammunition material from config or default
		Material ammoMaterial = ammunitionManager.getAmmunition(ammoName).getMaterial();

		List<String> lore = new ArrayList<>();
		lore.add("&7Type: &fAmmunition");
		lore.add("&7Name: &e" + ammoName);

		if (forWeapon != null) {
			lore.add("");
			lore.add("&aCompatible with " + forWeapon.getDisplayName());
		}

		return new ItemBuilder(ammoMaterial).setDisplayName("&e" + ammoName).setLore(lore).build();
	}

	private void openGenericItemView(Player player, String itemName) {
		String title = "&6View: &e" + itemName;

		InventoryHandler inventory = new InventoryHandler(plugin, title, 9, player);

		// Try to create item from material name
		Material material = Material.matchMaterial(itemName.toUpperCase().replace(" ", "_"));
		if (material == null) {
			material = Material.BARRIER;
		}

		List<String> lore = new ArrayList<>();
		lore.add("&7Item: &f" + itemName);
		lore.add("");
		lore.add("&cThis item is not configured");

		inventory.setItem(4, material, "&e" + itemName, lore, false, false, null);

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());

		InventoryUtil.fillInventory(inventory, fill);

		inventory.open(player);
	}

	private void openCarView(Player player, Car car) {
		String title = "&6View: &e" + car.getDisplayName();

		InventoryHandler inventory = new InventoryHandler(plugin, title, 9, player);

		List<String> lore = new ArrayList<>();
		lore.add("&7Speed: &f" + car.getMaxSpeed() + " &7blocks/tick");
		lore.add("&7Acceleration: &f" + car.getAcceleration());
		lore.add("&7Health: &f" + car.getMaxHealth() + " HP");
		lore.add("&7Durability: &f" + car.getMaxDurability());

		if (car.isFuelEnabled()) {
			lore.add("&7Fuel: &fRequired");
			lore.add("&7Fuel Type: &f" + car.getFuelKey());
		} else {
			lore.add("&7Fuel: &aUnlimited");
		}

		ItemStack carItem = new ItemBuilder(car.buildItem()).setLore(lore).build();

		inventory.setItem(4, carItem, false, null);

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());

		InventoryUtil.fillInventory(inventory, fill);

		inventory.open(player);
	}

	private void openWearableView(Player player, Wearable wearable) {
		String title = "&6View: &e" + wearable.getName();

		InventoryHandler inventory = new InventoryHandler(plugin, title, 9, player);

		// Wearable item in slot 3 with stats lore
		List<String> lore = new ArrayList<>();
		lore.add("&7Type: &fWearable");
		lore.add("&7Base Reduction: &f" + (int) (wearable.getBaseDamageReduction() * 100) + "%");

		if (wearable.getTraits() != null && !wearable.getTraits().isEmpty()) {
			lore.add("&7Traits:");
			for (Map.Entry<WearableTrait, Integer> entry : wearable.getTraits().entrySet()) {
				lore.add("  &8- &f" + entry.getKey().name() + " " + entry.getValue());
			}
		}

		if (wearable.isJetpack()) {
			lore.add("");
			lore.add("&bJetpack Properties:");
			lore.add("  &7Ascend Power: &f" + wearable.getAscendPower());
			lore.add("  &7Glide Rate: &f" + wearable.getGlideDescentRate());
			lore.add("  &7Fuel Type: &f" + wearable.getFuelKey());
		}

		ItemStack wearableItem = new ItemBuilder(wearable.buildItem()).setLore(lore).build();
		inventory.setItem(3, wearableItem, false, null);

		// If jetpack, show required fuel item in slot 5
		if (wearable.isJetpack() && wearable.getFuelKey() != null) {
			UniqueItem fuelUniqueItem = findFuelUniqueItem(wearable.getFuelKey());
			if (fuelUniqueItem != null) {
				List<String> fuelLore = new ArrayList<>();
				fuelLore.add("&7Required Fuel");
				fuelLore.add("&7Type: &f" + fuelUniqueItem.getName());

				ItemStack fuelItem = new ItemBuilder(fuelUniqueItem.buildItem()).setLore(fuelLore).build();
				inventory.setItem(5, fuelItem, false, null);
			}
		}

		Fill fill = new Fill(Settings.getInventoryFillName(), Settings.getInventoryFillItem());
		InventoryUtil.fillInventory(inventory, fill);
		inventory.open(player);
	}

	private UniqueItem findFuelUniqueItem(String fuelKey) {
		for (UniqueItem item : uniqueItemAddon.getUniqueItems().values()) {
			if (item.getFuel() != null && fuelKey.equals(item.getFuel().getFuelKey())) {
				return item;
			}
		}
		return null;
	}

	private Car findCar(String identifier) {
		return carManager.getCar(identifier);
	}

	private Weapon findWeapon(String identifier) {
		return weaponService.getWeapons()
		                    .values()
				.stream()
				.filter(w -> w.getName().equalsIgnoreCase(identifier) ||
				             w.getDisplayName().equalsIgnoreCase(identifier))
				.findFirst()
				.orElse(null);
	}

	private List<String> getCompatibleAmmunition(Weapon weapon) {
		List<String> compatible = new ArrayList<>();

		// Add the weapon's primary ammunition type
		AmmunitionData ammunitionData = weapon.getAmmunitionData();
		if (ammunitionData == null) return compatible;

		Ammunition ammoType = ammunitionData.getAmmoType();
		if (ammoType == null) return compatible;

		String ammoName = ammoType.getName().toLowerCase();
		if (ammunitionManager.getAmmunitionKeys().contains(ammoName)) {
			compatible.add(ammoName);
		}

		return compatible;
	}

}
