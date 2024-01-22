package me.luckyraven.feature.phone;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.bukkit.inventory.MultiInventory;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.InventoryUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.color.ColorUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Phone {

	private final @Getter String           name;
	private final         InventoryHandler inventoryHandler;
	private final         ItemBuilder      item;
	private final         Gangland         gangland;
	private final         User<Player>     user;

	private @Getter String displayName;

	public Phone(Gangland gangland, User<Player> user, String name) {
		this.name             = name;
		this.displayName      = name;
		this.gangland         = gangland;
		this.user             = user;
		this.inventoryHandler = new InventoryHandler(gangland, displayName, InventoryHandler.MAX_SLOTS, user, true);
		this.item             = new ItemBuilder(getPhoneMaterial()).setDisplayName(displayName)
																   .addTag("uniqueItem", "phone");
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
		ItemBuilder itemBuilder = new ItemBuilder(item);
		return itemBuilder.hasNBTTag("uniqueItem") && itemBuilder.getTagData("uniqueItem").equals("phone");
	}

	public static boolean hasPhone(Player player) {
		Material    phoneItem = getPhoneMaterial();
		ItemStack[] contents  = player.getInventory().getContents();

		for (ItemStack item : contents)
			if (item != null && item.getType() == phoneItem) if (isPhone(item)) return true;

		return false;
	}

	public void setDisplayName(String displayName) {
		InventoryHandler.rename(user, gangland, inventoryHandler, displayName);
		this.displayName = displayName;
		this.item.setDisplayName(displayName);
	}

	public void openInventory() {
		PhoneInventoryEvent event = new PhoneInventoryEvent(user);
		gangland.getServer().getPluginManager().callEvent(event);

		populateInventory(user, (pl, inventory, item) -> inventory.open(pl));
		inventoryHandler.open(user.getUser());
	}

	public void closeInventory() {
		inventoryHandler.close(user.getUser());
	}

	public org.bukkit.inventory.Inventory getInventoryHandler() {
		return inventoryHandler.getInventory();
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

	// TODO use the inventory system to avoid hardcoded inventories
	private void populateInventory(User<Player> user, TriConsumer<Player, InventoryHandler, ItemBuilder> callback) {
		// missions
		inventoryHandler.setItem(11, XMaterial.DIAMOND.parseMaterial(), "&eMissions", null, false, false);

		// gang
		GangManager gangManager = gangland.getInitializer().getGangManager();
		inventoryHandler.setItem(13, XMaterial.CAULDRON.parseMaterial(), "&eGang", null, false, false,
								 (player, inventory, item) -> gangInventory(user, gangManager, callback));

		// property
		inventoryHandler.setItem(15, XMaterial.FURNACE_MINECART.parseMaterial(), "&eProperty", null, false, false);

		// account
		ItemBuilder itemBuilder = new ItemBuilder(XMaterial.PLAYER_HEAD.parseMaterial());
		itemBuilder.setDisplayName("&eAccount").modifyNBT(nbt -> nbt.setString("SkullOwner", user.getUser().getName()));

		inventoryHandler.setItem(38, itemBuilder.build(), false, (player, inventory, item) -> {
			// show player data
		});

		// bounties
		inventoryHandler.setItem(40, XMaterial.NETHER_STAR.parseMaterial(), "&eBounties", null, false, false,
								 (player, inventory, item) -> {
									 // show current bounties and leaderboard
								 });

		// contacts
		inventoryHandler.setItem(42, XMaterial.BOOK.parseMaterial(), "&eContacts", null, false, false,
								 (player, inventory, item) -> {
									 // show taxi services
								 });

		InventoryUtil.verticalLine(inventoryHandler, 2);
		InventoryUtil.verticalLine(inventoryHandler, 8);

		InventoryUtil.fillInventory(inventoryHandler);
	}

	private void gangInventory(User<Player> user, GangManager gangManager,
							   TriConsumer<Player, InventoryHandler, ItemBuilder> callback) {
		// show create gang and search for gang
		InventoryHandler newGang = new InventoryHandler(gangland, "&6&lGang", 5 * 9, user, false);

		if (user.hasGang()) {
			Gang gang = gangManager.getGang(user.getGangId());

			// my gang
			newGang.setItem(21, XMaterial.SLIME_BALL.parseMaterial(),
							String.format("&r%s%s&7 Gang", ColorUtil.getColorCode(gang.getColor()),
										  gang.getDisplayNameString()), null, false, false,
							(player1, inv, it) -> player1.performCommand("glw gang"));
		} else {
			// create gang
			List<String> createLore = new ArrayList<>(
					List.of(String.format("&7Costs &a%s%s", SettingAddon.getMoneySymbol(),
										  SettingAddon.formatDouble(SettingAddon.getGangCreateFee()))));

			newGang.setItem(21, XMaterial.SLIME_BALL.parseMaterial(), "&c&lCreate Gang", createLore, false, false,
							(player1, inv, it) -> {
								// open an anvil to set the name
								new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
									String output = stateSnapshot.getText();

									if (output == null || output.isEmpty()) {
										stateSnapshot.getPlayer()
													 .sendMessage(MessageAddon.INVALID_GANG_NAME.toString());
										return Collections.emptyList();
									}

									// then perform the command
									stateSnapshot.getPlayer().performCommand("glw gang create " + output);

									return List.of(AnvilGUI.ResponseAction.close());
								}).text("Name").title("Create Gang").plugin(gangland).open(player1);
							});
		}

		// search gang
		newGang.setItem(23, XMaterial.BOOKSHELF.parseMaterial(), "&b&lSearch Gang", null, true, false,
						(player1, inv, it) -> {
							// open a multi inventory that displays all the gangs
							User<Player>    user1      = gangland.getInitializer().getUserManager().getUser(player1);
							List<Gang>      gangs      = gangManager.getGangs().values().stream().toList();
							List<ItemStack> gangsItems = new ArrayList<>();

							for (Gang gang : gangs) {
								ItemBuilder itemBuilder = new ItemBuilder(
										XMaterial.PLAYER_HEAD.parseMaterial()).setDisplayName(
																					  String.format("&r%s%s&7 Gang", ColorUtil.getColorCode(gang.getColor()),
																									gang.getDisplayNameString()))
																			  .setLore("&e" + gang.getDescription());

								UUID uuid = gang.getGroup()
												.stream()
												.filter(member -> Objects.requireNonNull(member.getRank())
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

							// need to use a list to save the location
							Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems
									= new LinkedHashMap<>();
							MultiInventory multiInventory = MultiInventory.dynamicMultiInventory(gangland, user1,
																								 gangsItems,
																								 "&6&lGangs View", true,
																								 true, staticItems);

							// search
							ItemBuilder searchItem = new ItemBuilder(
									XMaterial.WRITABLE_BOOK.parseMaterial()).setDisplayName("&eSearch");
							staticItems.put(searchItem.build(), (player2, currInv, itemBuilder) -> {
								// opens an anvil and enters the query
								new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
									User<Player> user2 = gangland.getInitializer()
																 .getUserManager()
																 .getUser(stateSnapshot.getPlayer());
									String output = stateSnapshot.getText();

									if (output == null || output.isEmpty()) {
										return Collections.emptyList();
									}

									List<ItemStack> items = gangsItems.stream()
																	  .filter(itemStack -> Objects.requireNonNull(
																										  itemStack.getItemMeta())
																								  .getDisplayName()
																								  .toLowerCase()
																								  .contains(
																										  output.toLowerCase()))
																	  .toList();

									multiInventory.updateItems(items, user2, true, staticItems);
									callback.accept(stateSnapshot.getPlayer(), currInv, itemBuilder);

									return List.of(AnvilGUI.ResponseAction.close());
								}).text("").title("Enter the query").plugin(gangland).open(player2);

								// when done, close the anvil and change the items according to the query
							});
							// sort
							ItemBuilder sortItem = new ItemBuilder(XMaterial.GLOW_ITEM_FRAME.parseMaterial());
							staticItems.put(sortItem.build(), (player2, currInv, itemBuilder) -> {
								// sorts the items according to the name
							});

							// need to update because when initialized the process was not done accordingly,
							// this way should be changed since you update the items after initialization
							multiInventory.updateItems(gangsItems, user1, true, staticItems);

							multiInventory.open(player1);
						});

		InventoryUtil.verticalLine(newGang, 2);
		InventoryUtil.verticalLine(newGang, 8);

		InventoryUtil.horizontalLine(newGang, 1);
		InventoryUtil.horizontalLine(newGang, newGang.getSize() / 9);

		InventoryUtil.fillInventory(newGang);

		newGang.open(user.getUser());
	}

}
