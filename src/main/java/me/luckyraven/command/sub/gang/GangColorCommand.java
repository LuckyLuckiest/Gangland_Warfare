package me.luckyraven.command.sub.gang;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.bukkit.inventory.Inventory;
import me.luckyraven.bukkit.inventory.InventoryAddons;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.TriConsumer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.GangDatabase;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.color.Color;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.color.MaterialType;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class GangColorCommand extends SubArgument {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	public GangColorCommand(Gangland gangland, Tree<Argument> tree, UserManager<Player> userManager,
	                        GangManager gangManager) {
		super("color", tree);

		this.gangland = gangland;
		this.userManager = userManager;
		this.gangManager = gangManager;

		setPermission(getPermission() + ".color");
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			Inventory colorGUI   = new Inventory(gangland, "&5&lChoose a color", Inventory.MAX_SLOTS);
			Inventory confirmGUI = new Inventory(gangland, "&4&lAre you sure?", Inventory.MAX_SLOTS);

			int row = 2, column = 2;
			for (Color color : Color.values()) {
				String colorName = color.name();
				String colorCode = color.getColorCode();

				MaterialType type         = MaterialType.WOOL;
				String       materialName = type.name();

				Material material = ColorUtil.getMaterialByColor(colorName, materialName);

				if (material == null) return;

				String name = colorCode + ChatUtil.capitalize(colorName.toLowerCase().replace("_", " ")) + " " +
						ChatUtil.capitalize(materialName.toLowerCase().replace("_", " "));

				ItemBuilder itemBuilder = new ItemBuilder(material).setDisplayName(name);

				colorGUI.setItem((row - 1) * 9 + (column - 1), itemBuilder, false, (inventory, item) -> {
					confirmGUI.setItem(22, itemBuilder.build(), false);

					Material mat = ColorUtil.getMaterialByColor(colorName, MaterialType.STAINED_GLASS_PANE.name());
					InventoryAddons.aroundSlot(confirmGUI, 22, mat);

					confirmGUI.setItem(49, XMaterial.GREEN_CONCRETE.parseMaterial(), "&aConfirm", null, false, false,
					                   (inv, it) -> {
						                   // save the data in gang
						                   gang.setColor(colorName);

						                   // inform player
						                   String colorSelected = ChatUtil.color(colorCode + ChatUtil.capitalize(
								                   colorName.toLowerCase().replace("_", " ")));
						                   player.sendMessage(MessageAddon.GANG_COLOR_SET.toString()
						                                                                 .replace("%color%",
						                                                                          colorSelected));

						                   // update database
						                   for (DatabaseHandler handler : gangland.getInitializer()
						                                                          .getDatabaseManager()
						                                                          .getDatabases())
							                   if (handler instanceof GangDatabase gangDatabase) {
								                   DatabaseHelper helper = new DatabaseHelper(gangland, handler);

								                   helper.runQueries(database -> gangDatabase.updateDataTable(gang));
								                   break;
							                   }

						                   confirmGUI.close(player);
					                   });

					InventoryAddons.fillInventory(confirmGUI);

					confirmGUI.open(player);
				});

				if (column % 8 == 0) {
					column = 2;
					++row;
				} else ++column;
			}

			colorGUI.setItem((6 - 1) * 9, XMaterial.RED_CONCRETE.parseMaterial(), "&4Exit", null, false, false,
			                 (inventory, item) -> inventory.close(player));

			InventoryAddons.fillInventory(colorGUI);

			colorGUI.open(player);
		};
	}

}
