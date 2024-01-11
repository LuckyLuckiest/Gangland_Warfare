package me.luckyraven.command.sub.debug;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.datastructure.JsonFormatter;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ReadNBTCommand extends CommandHandler {

	public ReadNBTCommand(Gangland gangland) {
		super(gangland, "nbt", true, "read-nbt", "readnbt");
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player player = (Player) commandSender;

		ItemStack itemHeld = player.getInventory().getItemInMainHand();
		if (itemHeld.getType().name().toUpperCase().contains("AIR")) return;

		ItemBuilder   itemBuilder   = new ItemBuilder(itemHeld);
		String        allNbt        = itemBuilder.toString();
		JsonFormatter jsonFormatter = new JsonFormatter();

		player.sendMessage(ChatUtil.color(jsonFormatter.formatToJson(allNbt, " ".repeat(3))));
	}

	@Override
	protected void initializeArguments() {

	}

	@Override
	protected void help(CommandSender sender, int page) {

	}

}
