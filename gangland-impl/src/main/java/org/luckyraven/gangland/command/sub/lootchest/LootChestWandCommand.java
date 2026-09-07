package org.luckyraven.gangland.command.sub.lootchest;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.LootChestWand;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Map;

@CommandHandler
public class LootChestWandCommand extends Command {

	private final LootChestManager lootChestManager;
	private final GanglandDatabase ganglandDatabase;

	public LootChestWandCommand(Gangland gangland,
	                            LootChestManager lootChestManager,
	                            GanglandDatabase ganglandDatabase) {
		super(gangland, "lootchest", true, "wand", "lootchestwand", "chestwand", "lcwand");

		this.lootChestManager = lootChestManager;
		this.ganglandDatabase = ganglandDatabase;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("lootchest"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player player = (Player) commandSender;

		LootChestWand lootChestWand = new LootChestWand(getGangland(), lootChestManager, Gangland.SHORT_PREFIX);
		ItemStack     wand          = lootChestWand.createWand();

		player.getInventory().addItem(wand);

		String command = "/" + Gangland.SHORT_PREFIX + " wand edit";
		String hold    = "&7Hold the wand and use '&e" + command + "' &7to configure settings.";

		player.sendMessage(GanglandChatUtil.color("&a&lLoot Chest Wand &7has been added to your inventory!",
		                                          "&7Right-click on an allowed block to create a loot chest.", hold));
	}

	@Override
	protected void initializeArguments() {
		Argument editArg = new LootChestWandEditCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                lootChestManager);
		Argument removeArg = new LootChestRemoveCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                lootChestManager, ganglandDatabase);

		getArgument().addSubArgument(editArg);
		getArgument().addSubArgument(removeArg);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Loot Chest Wand");
	}

}
