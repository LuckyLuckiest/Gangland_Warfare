package org.luckyraven.gangland.command.sub.shop;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.core.bean.command.CommandHandler;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.shop.view.ShopAdminFlow;

import java.util.Map;

@CommandHandler
public class ShopCommand extends Command {

	private final ShopRegistry  shopRegistry;
	private final ShopAdminFlow adminFlow;

	public ShopCommand(Gangland gangland, ShopRegistry shopRegistry, ShopAdminFlow adminFlow) {
		super(gangland, "shop", true, "shops");
		this.shopRegistry = shopRegistry;
		this.adminFlow    = adminFlow;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("shop"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender sender, String[] args) {
		help(sender, 1);
	}

	@Override
	protected void initializeArguments() {
		getArgument().addSubArgument(new ShopCreateCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                   shopRegistry));
		getArgument().addSubArgument(new ShopEditCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                 shopRegistry, adminFlow));
		getArgument().addSubArgument(new ShopListCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                 shopRegistry));
		getArgument().addSubArgument(new ShopRemoveCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                   shopRegistry));
		getArgument().addSubArgument(new ShopTitleCommand(getGangland(), getArgumentTree(), getArgument(),
		                                                  shopRegistry));
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Shop");
	}

}
