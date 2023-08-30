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

import java.util.Map;
import java.util.TreeMap;

public class ReadNBTCommand extends CommandHandler {

	public ReadNBTCommand(Gangland gangland) {
		super(gangland, "nbt", true, "read-nbt", "readnbt");
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player player = (Player) commandSender;

		ItemStack itemHeld = player.getInventory().getItemInMainHand();
		if (itemHeld.getType().name().toUpperCase().contains("AIR")) return;

		ItemBuilder         itemBuilder = new ItemBuilder(itemHeld);
		String              allNbt      = itemBuilder.toString();
		Map<String, Object> unique      = new TreeMap<>();

		for (Map.Entry<String, Object> entry : ItemBuilder.getSpecialNBTs().entrySet())
			if (allNbt.contains(entry.getKey())) unique.put(entry.getKey(), entry.getValue());

		JsonFormatter jsonFormatter = new JsonFormatter();
		String        value;

		if (unique.isEmpty()) value = allNbt;
		else value = jsonFormatter.createJson(unique);

		player.sendMessage(ChatUtil.color(jsonFormatter.formatToJson(value, " ".repeat(3))));
	}

	@Override
	protected void initializeArguments() {

	}

	@Override
	protected void help(CommandSender sender, int page) {

	}

}
