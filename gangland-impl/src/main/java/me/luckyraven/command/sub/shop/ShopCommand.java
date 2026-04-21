package me.luckyraven.command.sub.shop;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.core.bean.command.CommandHandler;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.shop.view.ShopAdminView;
import org.bukkit.command.CommandSender;

import java.util.Map;

@CommandHandler
public class ShopCommand extends Command {

	private final ShopRegistry  shopRegistry;
	private final ShopAdminView adminView;

	public ShopCommand(Gangland gangland, ShopRegistry shopRegistry, ShopAdminView adminView) {
		super(gangland, "shop", true, "shops");
		this.shopRegistry = shopRegistry;
		this.adminView    = adminView;

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
		                                                 shopRegistry, adminView));
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
