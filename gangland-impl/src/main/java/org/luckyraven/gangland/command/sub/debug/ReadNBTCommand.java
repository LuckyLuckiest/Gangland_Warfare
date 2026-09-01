package org.luckyraven.gangland.command.sub.debug;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.datastructure.JsonFormatter;
import org.luckyraven.gangland.lootchest.LootChestWandTag;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.weapon.WeaponTag;

import java.util.LinkedHashMap;
import java.util.Map;

@CommandHandler
public final class ReadNBTCommand extends Command {

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

		player.sendMessage(GanglandChatUtil.color(jsonFormatter.formatToJson(allNbt, " ".repeat(3))));
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
				player.sendMessage(GanglandChatUtil.color("&cNo important NBT tags found on this item."));
				return;
			}

			StringBuilder output = new StringBuilder("&6&lNBT Tags:\n");
			for (Map.Entry<String, String> entry : presentTags.entrySet()) {
				output.append("&7- &e").append(entry.getKey()).append("&7: &f").append(entry.getValue()).append("\n");
			}

			player.sendMessage(GanglandChatUtil.color(output.toString()));
		});

		getArgument().addSubArgument(brief);
	}

	@Override
	protected void help(CommandSender sender, int page) { }

}
