package me.luckyraven.command.sub.debug;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.lootchest.LootChestWandTag;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.datastructure.JsonFormatter;
import me.luckyraven.weapon.WeaponTag;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ReadNBTCommand extends CommandHandler {

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
		Argument brief = new Argument(getGangland(), "brief", getArgumentTree(), (argument, sender, args) -> {
			Player    player   = (Player) sender;
			ItemStack itemHeld = player.getInventory().getItemInMainHand();

			if (itemHeld.getType().name().toUpperCase().contains("AIR")) return;

			ItemBuilder itemBuilder = new ItemBuilder(itemHeld);

			// Collect only tags that have values
			Map<String, String> presentTags = new LinkedHashMap<>();

			for (WeaponTag tag : WeaponTag.values()) {
				String tagName = tag.name().toLowerCase();

				if (!itemBuilder.hasNBTTag(tagName)) continue;

				presentTags.put(tagName, String.valueOf(itemBuilder.getTagData(tagName)));
			}

			for (LootChestWandTag tag : LootChestWandTag.values()) {
				String tagName = tag.toString().toLowerCase();

				if (!itemBuilder.hasNBTTag(tagName)) continue;

				presentTags.put(tagName, String.valueOf(itemBuilder.getTagData(tagName)));
			}

			if (presentTags.isEmpty()) {
				player.sendMessage(ChatUtil.color("&cNo important NBT tags found on this item."));
				return;
			}

			StringBuilder output = new StringBuilder("&6&lNBT Tags:\n");
			for (Map.Entry<String, String> entry : presentTags.entrySet()) {
				output.append("&7- &e").append(entry.getKey()).append("&7: &f").append(entry.getValue()).append("\n");
			}

			player.sendMessage(ChatUtil.color(output.toString()));
		});

		getArgument().addSubArgument(brief);
	}

	@Override
	protected void help(CommandSender sender, int page) { }

}
