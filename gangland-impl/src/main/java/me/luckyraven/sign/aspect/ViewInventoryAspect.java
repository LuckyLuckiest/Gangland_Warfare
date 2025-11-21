package me.luckyraven.sign.aspect;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.sign.model.ParsedSign;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponService;
import me.luckyraven.weapon.configuration.AmmunitionAddon;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ViewInventoryAspect implements SignAspect {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final WeaponService       weaponService;
	private final AmmunitionAddon     ammunitionAddon;

	public ViewInventoryAspect(Gangland gangland, WeaponService weaponService, AmmunitionAddon ammunitionAddon) {
		this.gangland        = gangland;
		this.userManager     = gangland.getInitializer().getUserManager();
		this.weaponService   = weaponService;
		this.ammunitionAddon = ammunitionAddon;
	}

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
		if (ammunitionAddon.getAmmunitionKeys().contains(itemName)) {
			openAmmunitionView(player, itemName);
			return AspectResult.success("Opened ammunition view: " + itemName);
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
		if (ammunitionAddon.getAmmunitionKeys().contains(itemName)) {
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
		User<Player> user = userManager.getUser(player);
		// Determine inventory size based on ammunition types
		List<String> compatibleAmmo = getCompatibleAmmunition(weapon);
		int          requiredSlots  = 1 + compatibleAmmo.size(); // 1 for weapon + ammo types
		int          inventorySize  = Math.max(9, InventoryHandler.factorOfNine(requiredSlots));

		// Create inventory handler
		String title = "&6View: &e" + weapon.getDisplayName();

		InventoryHandler inventory = new InventoryHandler(gangland, title, inventorySize, player);

		// Add weapon in center-left position
		int       weaponSlot = 3;
		ItemStack weaponItem = weapon.buildItem();

		List<String> weaponLore = new ArrayList<>();
		weaponLore.add("&7Type: &f" + weapon.getCategory().name());
		weaponLore.add("&7Damage: &c" + weapon.getProjectileDamage());
		weaponLore.add("&7Magazine: &e" + weapon.getMaxMagCapacity());
		weaponLore.add("&7Fire Rate: &a" + weapon.getProjectileCooldown() + "ms");
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
		Fill fill = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());

		InventoryUtil.fillInventory(inventory, fill);

		// Register and open inventory
		gangland.getServer().getPluginManager().registerEvents(inventory, gangland);
		inventory.open(player);
	}

	private void openAmmunitionView(Player player, String ammoName) {
		User<Player> user  = userManager.getUser(player);
		String       title = "&6View: &e" + ammoName;

		InventoryHandler inventory = new InventoryHandler(gangland, title, 9, player);

		ItemStack ammoItem = createAmmunitionItem(ammoName, null);

		if (ammoItem != null) {
			inventory.setItem(4, ammoItem, false, null);
		}

		Fill fill = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());

		InventoryUtil.fillInventory(inventory, fill);

		gangland.getServer().getPluginManager().registerEvents(inventory, gangland);
		inventory.open(player);
	}

	private void openGenericItemView(Player player, String itemName) {
		String title = "&6View: &e" + itemName;

		InventoryHandler inventory = new InventoryHandler(gangland, title, 9, player);

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

		Fill fill = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());

		InventoryUtil.fillInventory(inventory, fill);

		gangland.getServer().getPluginManager().registerEvents(inventory, gangland);
		inventory.open(player);
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
		if (weapon.getReloadAmmoType() != null) {
			String ammoName = weapon.getReloadAmmoType().getName().toLowerCase();
			if (ammunitionAddon.getAmmunitionKeys().contains(ammoName)) {
				compatible.add(ammoName);
			}
		}

		return compatible;
	}

	private ItemStack createAmmunitionItem(String ammoName, Weapon forWeapon) {
		// Get ammunition material from config or default
		Material ammoMaterial = ammunitionAddon.getAmmunition(ammoName).getMaterial();

		List<String> lore = new ArrayList<>();
		lore.add("&7Type: &fAmmunition");
		lore.add("&7Name: &e" + ammoName);

		if (forWeapon != null) {
			lore.add("");
			lore.add("&aCompatible with " + forWeapon.getDisplayName());
		}

		return new ItemBuilder(ammoMaterial).setDisplayName("&e" + ammoName.toUpperCase()).setLore(lore).build();
	}

}
