package me.luckyraven.phone;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.Inventory;
import me.luckyraven.bukkit.inventory.InventoryAddons;
import me.luckyraven.bukkit.inventory.MultiInventory;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.color.ColorUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Phone {

	private final @Getter String      name;
	private final         Inventory   inventory;
	private final         ItemBuilder item;
	private final         Gangland    gangland;

	private @Getter String displayName;

	public Phone(String name) {
		this.name = name;
		this.displayName = name;
		this.gangland = JavaPlugin.getPlugin(Gangland.class);
		this.inventory = new Inventory(gangland, displayName, Inventory.MAX_SLOTS);
		this.item = new ItemBuilder(getPhoneMaterial()).setDisplayName(displayName).addTag("uniqueItem", "phone");
	}

	public static Material getPhoneMaterial() {
		Material phoneItem = XMaterial.matchXMaterial(SettingAddon.getPhoneItem())
		                              .stream()
		                              .toList()
		                              .get(0)
		                              .parseMaterial();
		if (phoneItem == null) return XMaterial.REDSTONE.parseMaterial();
		return phoneItem;
	}

	public static boolean isPhone(ItemStack item) {
		return new ItemBuilder(item).hasNBTTag("uniqueItem");
	}

	public static boolean hasPhone(Player player) {
		Material    phoneItem = getPhoneMaterial();
		ItemStack[] contents  = player.getInventory().getContents();

		for (ItemStack item : contents)
			if (item != null && item.getType() == phoneItem) if (isPhone(item)) return true;

		return false;
	}

	public void setDisplayName(String displayName) {
		inventory.rename(displayName);
		this.displayName = displayName;
		this.item.setDisplayName(displayName);
	}

	public void openInventory(Player player) {
		User<Player> user = gangland.getInitializer().getUserManager().getUser(player);

		PhoneInventoryEvent event = new PhoneInventoryEvent(user);
		gangland.getServer().getPluginManager().callEvent(event);

		populateInventory(user);
		inventory.open(player);
	}

	public void closeInventory(Player player) {
		inventory.close(player);
	}

	public org.bukkit.inventory.Inventory getInventory() {
		return inventory.getInventory();
	}

	public void addPhoneToInventory(Player player) {
		if (!addItem(player, SettingAddon.getPhoneSlot())) player.sendMessage(MessageAddon.INVENTORY_FULL.toString());
	}

	private boolean addItem(Player player, int slot) {
		if (slot >= player.getInventory().getSize() || slot > 35) return false;

		if (player.getInventory().getItem(slot) != null) return addItem(player, slot + 1);
		else player.getInventory().setItem(slot, item.build());

		return true;
	}

	private void populateInventory(User<Player> user) {
		// missions
		inventory.setItem(11, XMaterial.DIAMOND.parseMaterial(), "&eMissions", null, false, false);

		// gang
		GangManager gangManager = gangland.getInitializer().getGangManager();
		inventory.setItem(13, XMaterial.CAULDRON.parseMaterial(), "&eGang", null, false, false, (inventory, item) -> {
			// show create gang and search for gang
			Inventory newGang = new Inventory(gangland, "&6&lGang", 5 * 9);

			if (user.hasGang()) {
				Gang gang = gangManager.getGang(user.getGangId());

				// my gang
				newGang.setItem(21, XMaterial.SLIME_BALL.parseMaterial(),
				                String.format("&r%s%s&7 Gang", ColorUtil.getColorCode(gang.getColor()),
				                              gang.getDisplayNameString()), null, false, false,
				                (inv, it) -> user.getUser().performCommand("glw gang"));
			} else {
				// create gang
				List<String> createLore = new ArrayList<>(
						List.of(String.format("&7Costs &a%s%s", SettingAddon.getMoneySymbol(),
						                      SettingAddon.formatDouble(SettingAddon.getGangCreateFee()))));

				newGang.setItem(21, XMaterial.SLIME_BALL.parseMaterial(), "&c&lCreate Gang", createLore, false, false,
				                (inv, it) -> {
					                // open an anvil to set the name
					                new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
						                String output = stateSnapshot.getText();

						                if (output == null || output.isEmpty()) {
							                stateSnapshot.getPlayer().sendMessage(
									                MessageAddon.INVALID_GANG_NAME.toString());
							                return Collections.emptyList();
						                }

						                // then perform the command
						                stateSnapshot.getPlayer().performCommand("glw gang create " + output);

						                return List.of(AnvilGUI.ResponseAction.close());
					                }).text("Name").title("Create Gang").plugin(gangland).open(user.getUser());
				                });
			}

			// search gang
			newGang.setItem(23, XMaterial.BOOKSHELF.parseMaterial(), "&b&lSearch Gang", null, true, false,
			                (inv, it) -> {
				                // open a multi inventory that displays all the gangs
				                List<Gang>      gangs      = gangManager.getGangs().values().stream().toList();
				                List<ItemStack> gangsItems = new ArrayList<>();

				                for (Gang gang : gangs) {
					                ItemBuilder itemBuilder = new ItemBuilder(
							                XMaterial.PLAYER_HEAD.parseMaterial()).setDisplayName(
							                String.format("&r%s%s&7 Gang", ColorUtil.getColorCode(gang.getColor()),
							                              gang.getDisplayNameString())).setLore(
							                "&e" + gang.getDescription());

					                UUID uuid = gang.getGroup()
					                                .stream()
					                                .filter(member -> member.getRank()
					                                                        .getName()
					                                                        .equalsIgnoreCase(
							                                                        SettingAddon.getGangRankTail()))
					                                .findFirst()
					                                .map(Member::getUuid)
					                                .orElse(null);

					                String name = "";
					                if (uuid != null) {
						                name = Bukkit.getOfflinePlayer(uuid).getName();
						                if (name == null) name = "";
					                }

					                String finalName = name;
					                itemBuilder.modifyNBT(nbt -> nbt.setString("SkullOwner", finalName));

					                gangsItems.add(itemBuilder.build());
				                }

				                List<ItemStack> sideItems = new ArrayList<>();

				                // search
				                ItemBuilder searchItem = new ItemBuilder(
						                XMaterial.WRITABLE_BOOK.parseMaterial()).setDisplayName("&eSearch");
				                sideItems.add(searchItem.build());
				                // sort
				                ItemBuilder sortItem = new ItemBuilder(XMaterial.GLOW_ITEM_FRAME.parseMaterial());

				                MultiInventory multiInventory = MultiInventory.dynamicMultiInventory(gangland,
				                                                                                     gangsItems,
				                                                                                     "&6&lGangs View",
				                                                                                     user.getUser(),
				                                                                                     true, true,
				                                                                                     sideItems.toArray(
						                                                                                     ItemStack[]::new));

				                multiInventory.open(user.getUser());
			                });

			InventoryAddons.verticalLine(newGang, 2);
			InventoryAddons.verticalLine(newGang, 8);

			InventoryAddons.horizontalLine(newGang, 1);
			InventoryAddons.horizontalLine(newGang, newGang.getSize() / 9);

			InventoryAddons.fillInventory(newGang);

			newGang.open(user.getUser());
		});

		// property
		inventory.setItem(15, XMaterial.FURNACE_MINECART.parseMaterial(), "&eProperty", null, false, false);

		// account
		ItemBuilder itemBuilder = new ItemBuilder(XMaterial.PLAYER_HEAD.parseMaterial());
		itemBuilder.setDisplayName("&eAccount").modifyNBT(nbt -> nbt.setString("SkullOwner", user.getUser().getName()));

		inventory.setItem(38, itemBuilder.build(), false, (inventory, item) -> {
			// show player data
		});

		// bounties
		inventory.setItem(40, XMaterial.NETHER_STAR.parseMaterial(), "&eBounties", null, false, false,
		                  (inventory, item) -> {
			                  // show current bounties and leaderboard
		                  });

		// contacts
		inventory.setItem(42, XMaterial.BOOK.parseMaterial(), "&eContacts", null, false, false, (inventory, item) -> {
			// show taxi services
		});

		InventoryAddons.verticalLine(inventory, 2);
		InventoryAddons.verticalLine(inventory, 8);

		InventoryAddons.fillInventory(inventory);
	}

}
