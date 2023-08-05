package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.ItemBuilder;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ReadNBTCommand extends CommandHandler {

	public ReadNBTCommand(Gangland gangland) {
		super(gangland, "nbt", true, "read-nbt", "readnbt");
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player player = (Player) commandSender;

		ItemStack itemHeld = player.getInventory().getItemInMainHand();
		if (itemHeld.getType().name().toUpperCase().contains("AIR")) return;

		ItemBuilder  itemBuilder = new ItemBuilder(itemHeld);
		String       allNbt      = itemBuilder.toString();
		List<String> unique      = new ArrayList<>();

		for (Map.Entry<String, Object> entry : ItemBuilder.getSpecialNBTs().entrySet())
			if (allNbt.contains(entry.getKey())) unique.add("&6" + entry.getKey() + ": &e" + entry.getValue());

		if (unique.isEmpty()) unique.add(allNbt);

		player.sendMessage(ChatUtil.color(unique.toArray(String[]::new)));
	}

	@Override
	protected void initializeArguments(Gangland gangland) {

	}

	@Override
	protected void help(CommandSender sender, int page) {

	}

}
