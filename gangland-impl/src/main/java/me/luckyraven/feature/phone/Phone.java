package me.luckyraven.feature.phone;

import com.cryptomorin.xseries.XMaterial;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.user.User;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.multi.MultiInventory;
import me.luckyraven.inventory.multi.MultiInventoryCreation;
import me.luckyraven.inventory.part.ButtonTags;
import me.luckyraven.inventory.part.Fill;
import me.luckyraven.inventory.util.InventoryUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.color.ColorUtil;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Phone {

	@Getter
	private final String           name;
	private final InventoryHandler inventoryHandler;
	private final ItemBuilder      item;
	private final Gangland         gangland;
	private final User<Player>     user;

	private @Getter String displayName;

	public Phone(Gangland gangland, User<Player> user, String name) {
		this.name        = name;
		this.displayName = name;
		this.gangland    = gangland;
		this.user        = user;

		int size = InventoryHandler.MAX_SLOTS;
		this.inventoryHandler = new InventoryHandler(gangland, displayName, size, user.getUser());

		this.item = new ItemBuilder(getPhoneMaterial()).setDisplayName(displayName).addTag("uniqueItem", "phone");
	}

	public static Material getPhoneMaterial() {
		Material phoneItem = XMaterial.matchXMaterial(SettingAddon.getPhoneItem())
				.stream().toList().getFirst().get();
		if (phoneItem == null) return XMaterial.REDSTONE.get();
		return phoneItem;
	}

	public static boolean isPhone(ItemStack item) {
		ItemBuilder itemBuilder = new ItemBuilder(item);
		return itemBuilder.hasNBTTag("uniqueItem") && itemBuilder.getStringTagData("uniqueItem").equals("phone");
	}

	public static boolean hasPhone(Player player) {
		Material    phoneItem = getPhoneMaterial();
		ItemStack[] contents  = player.getInventory().getContents();

		for (ItemStack item : contents)
			if (item != null && item.getType() == phoneItem) if (isPhone(item)) return true;

		return false;
	}

	public void setDisplayName(String displayName) {
		inventoryHandler.rename(gangland, displayName);
		this.displayName = displayName;
		this.item.setDisplayName(displayName);
	}

	public void openInventory() {
		PhoneInventoryEvent event = new PhoneInventoryEvent(user);
		gangland.getServer().getPluginManager().callEvent(event);

		populateInventory(user.getUser(), (pl, inventory, item) -> inventory.open(pl));
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
	private void populateInventory(Player player, TriConsumer<Player, InventoryHandler, ItemBuilder> callback) {
		// missions
		inventoryHandler.setItem(11, XMaterial.DIAMOND.get(), "&eMissions", null, false, false);

		// gang
		GangManager gangManager = gangland.getInitializer().getGangManager();
		inventoryHandler.setItem(13, XMaterial.CAULDRON.get(), "&eGang", null, false, false, (p, inventory, item) -> {
			gangInventory(user, gangManager, callback);
		});

		// property
		inventoryHandler.setItem(15, XMaterial.FURNACE_MINECART.get(), "&eProperty", null, false, false);

		// account
		ItemBuilder itemBuilder = new ItemBuilder(XMaterial.PLAYER_HEAD.get());
		itemBuilder.setDisplayName("&eAccount").modifyNBT(nbt -> nbt.setString("SkullOwner", user.getUser().getName()));

		inventoryHandler.setItem(38, itemBuilder.build(), false, (p, inventory, item) -> {
			// show player data
		});

		// bounties
		inventoryHandler.setItem(40, XMaterial.NETHER_STAR.get(), "&eBounties", null, false, false,
								 (p, inventory, item) -> {
									 // show current bounties and leaderboard
								 });

		// contacts
		inventoryHandler.setItem(42, XMaterial.BOOK.get(), "&eContacts", null, false, false, (p, inventory, item) -> {
			// show taxi services
		});

		Fill line = new Fill(SettingAddon.getInventoryLineName(), SettingAddon.getInventoryLineItem());

		InventoryUtil.verticalLine(inventoryHandler, line, 2);
		InventoryUtil.verticalLine(inventoryHandler, line, 8);

		Fill fill = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());

		InventoryUtil.fillInventory(inventoryHandler, fill);
	}

	private void gangInventory(User<Player> user, GangManager gangManager,
							   TriConsumer<Player, InventoryHandler, ItemBuilder> callback) {
		// show create gang and search for gang
		String title = "&6&lGang";
		int    size  = 5 * 9;

		InventoryHandler newGang = new InventoryHandler(gangland, title, size, user.getUser());

		if (user.hasGang()) {
			Gang gang = gangManager.getGang(user.getGangId());

			// my gang
			newGang.setItem(21, XMaterial.SLIME_BALL.get(),
							String.format("&r%s%s&7 Gang", ColorUtil.getColorCode(gang.getColor()),
										  gang.getDisplayNameString()), null, false, false,
							(player1, inv, it) -> player1.performCommand("glw gang"));
		} else {
			// create gang
			List<String> createLore = new ArrayList<>(
					List.of(String.format("&7Costs &a%s%s", SettingAddon.getMoneySymbol(),
										  SettingAddon.formatDouble(SettingAddon.getGangCreateFee()))));

			newGang.setItem(21, XMaterial.SLIME_BALL.get(), "&c&lCreate Gang", createLore, false, false,
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

		Fill fill = new Fill(SettingAddon.getInventoryFillName(), SettingAddon.getInventoryFillItem());

		// search gang
		newGang.setItem(23, XMaterial.BOOKSHELF.get(), "&b&lSearch Gang", null, true, false, (player1, inv, it) -> {
			// open a multi inventory that displays all the gangs
			List<Gang> gangs = gangManager.getGangs().values()
					.stream().toList();
			List<ItemStack> gangsItems = new ArrayList<>();

			for (Gang gang : gangs) {
				ItemBuilder itemBuilder = new ItemBuilder(XMaterial.PLAYER_HEAD.get()).setDisplayName(
						String.format("&r%s%s&7 Gang", ColorUtil.getColorCode(gang.getColor()),
									  gang.getDisplayNameString())).setLore("&e" + gang.getDescription());

				UUID uuid = gang.getGroup()
						.stream()
						.filter(member -> Objects.requireNonNull(member.getRank())
												 .getName()
												 .equalsIgnoreCase(SettingAddon.getGangRankTail()))
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
			Map<ItemStack, TriConsumer<Player, InventoryHandler, ItemBuilder>> staticItems = new LinkedHashMap<>();

			String title1 = "&6&lGangs View";

			ButtonTags buttonTags = new ButtonTags(SettingAddon.getPreviousPage(), SettingAddon.getHomePage(),
												   SettingAddon.getNextPage());

			MultiInventory multiInventory = MultiInventoryCreation.dynamicMultiInventory(gangland, player1, gangsItems,
																						 title1, true, true, fill,
																						 buttonTags, staticItems);

			if (multiInventory == null) return;

			// search
			ItemBuilder searchItem = new ItemBuilder(XMaterial.WRITABLE_BOOK.get()).setDisplayName("&eSearch");
			staticItems.put(searchItem.build(), (player2, currInv, itemBuilder) -> {
				// opens an anvil and enters the query
				new AnvilGUI.Builder().onClick((slot, stateSnapshot) -> {
					String output = stateSnapshot.getText();

					if (output == null || output.isEmpty()) {
						return Collections.emptyList();
					}

					List<ItemStack> items = gangsItems.stream()
							.filter(itemStack -> Objects.requireNonNull(itemStack.getItemMeta())
														.getDisplayName()
														.toLowerCase()
														.contains(output.toLowerCase()))
							.toList();

					multiInventory.updateItems(gangland, items, stateSnapshot.getPlayer(), true, fill, staticItems);
					callback.accept(stateSnapshot.getPlayer(), currInv, itemBuilder);

					return List.of(AnvilGUI.ResponseAction.close());
				}).text("").title("Enter the query").plugin(gangland).open(player2);

				// when done, close the anvil and change the items according to the query
			});
			// sort
			ItemBuilder sortItem = new ItemBuilder(XMaterial.GLOW_ITEM_FRAME.get());
			staticItems.put(sortItem.build(), (player2, currInv, itemBuilder) -> {
				// sorts the items according to the name
			});

			// need to update because when initialized the process was not done accordingly,
			// this way should be changed since you update the items after initialization
			multiInventory.updateItems(gangland, gangsItems, player1, true, fill, staticItems);

			multiInventory.open(player1);
		});

		Fill line = new Fill(SettingAddon.getInventoryLineName(), SettingAddon.getInventoryLineItem());

		InventoryUtil.verticalLine(newGang, line, 2);
		InventoryUtil.verticalLine(newGang, line, 8);

		InventoryUtil.horizontalLine(newGang, line, 1);
		InventoryUtil.horizontalLine(newGang, line, newGang.getSize() / 9);

		InventoryUtil.fillInventory(newGang, fill);

		newGang.open(user.getUser());
	}

}
