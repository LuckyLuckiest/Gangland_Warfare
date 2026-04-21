package me.luckyraven.command.sub.lootchest;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.core.command.CommandHandler;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.LootChestWand;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
