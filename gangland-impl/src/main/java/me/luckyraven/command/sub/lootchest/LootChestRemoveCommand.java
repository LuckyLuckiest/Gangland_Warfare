package me.luckyraven.command.sub.lootchest;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.lootchest.data.LootChestData;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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
