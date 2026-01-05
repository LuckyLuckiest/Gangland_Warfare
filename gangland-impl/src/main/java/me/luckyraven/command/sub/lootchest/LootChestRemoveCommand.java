package me.luckyraven.command.sub.lootchest;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.LootChestTable;
import me.luckyraven.loot.data.LootChestData;
import me.luckyraven.lootchest.LootChestManager;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.List;
import java.util.Optional;

class LootChestRemoveCommand extends SubArgument {

	private final Gangland gangland;

	protected LootChestRemoveCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "remove", tree, parent);

		this.gangland = gangland;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return ((argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			// Get the block the player is looking at
			Block targetBlock = player.getTargetBlockExact(5);

			if (targetBlock == null) {
				player.sendMessage(ChatUtil.commandMessage("&cYou must be looking at a block within 5 blocks!"));
				return;
			}

			Location         location = targetBlock.getLocation();
			LootChestManager manager  = gangland.getInitializer().getLootChestManager();

			Optional<LootChestData> chestOptional = manager.getChestAt(location);

			if (chestOptional.isEmpty()) {
				player.sendMessage(ChatUtil.commandMessage("&cNo loot chest found at that location!"));
				return;
			}

			LootChestData chestData = chestOptional.get();

			// Remove from service (handles holograms, cooldowns, etc.)
			manager.unregisterChest(chestData.getId());

			// Remove from database
			GanglandDatabase ganglandDatabase = gangland.getInitializer().getGanglandDatabase();
			DatabaseHelper   helper           = new DatabaseHelper(gangland, ganglandDatabase);
			List<Table<?>>   tables           = ganglandDatabase.getTables();

			LootChestTable lootChestTable = gangland.getInitializer()
													.getInstanceFromTables(LootChestTable.class, tables);

			helper.runQueries(database -> {
				database.table(lootChestTable.getName()).delete("id", chestData.getId(), Types.INTEGER);
			});

			player.sendMessage(ChatUtil.commandMessage("&aLoot chest removed successfully!"));
		});
	}

}
