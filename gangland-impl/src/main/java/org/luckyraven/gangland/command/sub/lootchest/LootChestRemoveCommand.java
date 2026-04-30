package org.luckyraven.gangland.command.sub.lootchest;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.data.LootChestData;

import java.util.Optional;

class LootChestRemoveCommand extends SubArgument {

	private final LootChestManager lootChestManager;
	private final GanglandDatabase ganglandDatabase;

	protected LootChestRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                                 LootChestManager lootChestManager, GanglandDatabase ganglandDatabase) {
		super(gangland, "remove", tree, parent);
		this.lootChestManager = lootChestManager;
		this.ganglandDatabase = ganglandDatabase;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return ((argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			// Get the block the player is looking at
			Block targetBlock = player.getTargetBlockExact(5);

			if (targetBlock == null) {
				player.sendMessage(Messages.LOOT_CHEST_MUST_LOOK_AT_BLOCK.toString());
				return;
			}

			Location location = targetBlock.getLocation();

			Optional<LootChestData> chestOptional = lootChestManager.getChestAt(location);

			if (chestOptional.isEmpty()) {
				player.sendMessage(Messages.LOOT_CHEST_NO_CHEST_AT_LOCATION.toString());
				return;
			}

			LootChestData chestData = chestOptional.get();

			// Remove from service (handles holograms, cooldowns, etc.)
			lootChestManager.unregisterChest(chestData.getId());

			// Remove from database via repository
			var lootChestRepository = ganglandDatabase.getRepositoryRegistry()
			                                          .getRepository(LootChestData.class);
			lootChestRepository.delete(chestData);

			player.sendMessage(Messages.LOOT_CHEST_REMOVED.toString());
		});
	}

}
