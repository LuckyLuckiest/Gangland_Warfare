package me.luckyraven.command.sub.lootchest;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.lootchest.LootChestWand;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class LootChestWandCommand extends CommandHandler {


	public LootChestWandCommand(Gangland gangland) {
		super(gangland, "lootchest", true, "wand", "lootchestwand", "chestwand", "lcwand");

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

		LootChestWand lootChestWand = new LootChestWand(getGangland());
		ItemStack     wand          = lootChestWand.createWand();

		player.getInventory().addItem(wand);

		String command = "/" + getGangland().getShortPrefix() + " wand edit";
		String hold    = "&7Hold the wand and use '&e" + command + "' &7to configure settings.";

		player.sendMessage(ChatUtil.color("&a&lLoot Chest Wand &7has been added to your inventory!",
										  "&7Right-click on an allowed block to create a loot chest.", hold));
	}

	@Override
	protected void initializeArguments() {
		Argument editArg   = new LootChestWandEditCommand(getGangland(), getArgumentTree(), getArgument());
		Argument removeArg = new LootChestRemoveCommand(getGangland(), getArgumentTree(), getArgument());

		getArgument().addSubArgument(editArg);
		getArgument().addSubArgument(removeArg);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Loot Chest Wand");
	}

}
