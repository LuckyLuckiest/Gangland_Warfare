package org.luckyraven.gangland.lootchest.command;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.data.LootChestData;

import java.util.Optional;

class LootChestRemoveCommand extends SubArgument {

	private final LootChestManager   lootChestManager;
	private final RepositoryRegistry repositoryRegistry;

	protected LootChestRemoveCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                                 LootChestManager lootChestManager, RepositoryRegistry repositoryRegistry) {
		super(gangland, "remove", tree, parent);
		this.lootChestManager   = lootChestManager;
		this.repositoryRegistry = repositoryRegistry;
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
			var lootChestRepository = repositoryRegistry.getRepository(LootChestData.class);
			lootChestRepository.delete(chestData);

			player.sendMessage(Messages.LOOT_CHEST_REMOVED.toString());
		});
	}

}
