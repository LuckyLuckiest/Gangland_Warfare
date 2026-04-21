package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.data.account.gang.GangSearchFilter;
import me.luckyraven.data.account.gang.GangSearchFilterStore;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.inventory.InventoryRuntimeContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

class GangSearchCommand extends SubArgument {

	private static final String INVENTORY_NAME = "phone_gang_search";
	private static final String TOKEN_CLEAR    = "clear";
	private static final String TOKEN_SORT     = "sort";

	private final Gangland                gangland;
	private final Tree<Argument>          tree;
	private final UserManager<Player>     userManager;
	private final GangSearchFilterStore   filterStore;
	private final InventoryRuntimeContext inventoryRuntimeContext;

	protected GangSearchCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager, GangSearchFilterStore filterStore,
	                            InventoryRuntimeContext inventoryRuntimeContext) {
		super(gangland, "search", tree, parent);

		this.gangland                = gangland;
		this.tree                    = tree;
		this.userManager             = userManager;
		this.filterStore             = filterStore;
		this.inventoryRuntimeContext = inventoryRuntimeContext;

		gangSearch();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			filterStore.clear(player);
			inventoryRuntimeContext.openInventoryForPlayer(player, INVENTORY_NAME);
		};
	}

	private void gangSearch() {
		Argument token = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;
			if (userManager.getUser(player) == null) return;

			String value = joinTrailing(args, 2);

			GangSearchFilter current = filterStore.get(player);
			GangSearchFilter next;

			if (value.equalsIgnoreCase(TOKEN_CLEAR)) {
				next = GangSearchFilter.EMPTY;
			} else if (value.equalsIgnoreCase(TOKEN_SORT)) {
				next = current.withSort(current.sort().next());
			} else {
				next = current.withName(value);
			}

			filterStore.set(player, next);
			inventoryRuntimeContext.openInventoryForPlayer(player, INVENTORY_NAME);
		}, sender -> List.of(TOKEN_CLEAR, TOKEN_SORT, "<name>"));

		this.addSubArgument(token);
	}

	private static String joinTrailing(String[] args, int from) {
		if (args == null || args.length <= from) return "";
		StringBuilder out = new StringBuilder(args[from]);
		for (int i = from + 1; i < args.length; i++) out.append(' ').append(args[i]);
		return out.toString();
	}

}
