package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.data.CommandInformation;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public class SCGang extends CommandHandler {


	public SCGang(Gangland gangland) {
		super(gangland, "gang", false);

		List<CommandInformation> list = getCommands().entrySet()
		                                             .stream()
		                                             .filter(entry -> entry.getKey().startsWith("gang"))
		                                             .sorted(Map.Entry.comparingByKey())
		                                             .map(Map.Entry::getValue)
		                                             .toList();
		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(CommandSender commandSender, String[] arguments) {
		// displays the stats of the gang
		// TODO
		getCommands().keySet().stream().filter(commandInformation -> commandInformation.startsWith("gang")).forEach(
				commandSender::sendMessage);
	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		// create gang
		Argument create = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

		});

		// delete gang
		Argument delete = new Argument(new String[]{"delete"}, getArgumentTree(), (sender, args) -> {

		});

		// add user to gang
		Argument addUser = new Argument(new String[]{"add"}, getArgumentTree(), (sender, args) -> {

		});

		// remove user from gang
		Argument removeUser = new Argument(new String[]{"kick"}, getArgumentTree(), (sender, args) -> {

		});

		// promote user in gang
		Argument promoteUser = new Argument(new String[]{"promote"}, getArgumentTree(), (sender, args) -> {

		});

		// demote user in gang
		Argument demoteUser = new Argument(new String[]{"demote"}, getArgumentTree(), (sender, args) -> {

		});

		// deposit money to gang
		Argument deposit = new Argument(new String[]{"deposit"}, getArgumentTree(), (sender, args) -> {

		});

		// withdraw money from gang
		Argument withdraw = new Argument(new String[]{"withdraw"}, getArgumentTree(), (sender, args) -> {

		});

		// balance of gang
		Argument balance = new Argument(new String[]{"balance", "bal"}, getArgumentTree(), (sender, args) -> {

		});

		// add sub arguments
		getArgument().addSubArgument(create);
		getArgument().addSubArgument(delete);
		getArgument().addSubArgument(addUser);
		getArgument().addSubArgument(removeUser);
		getArgument().addSubArgument(promoteUser);
		getArgument().addSubArgument(demoteUser);
		getArgument().addSubArgument(deposit);
		getArgument().addSubArgument(withdraw);
		getArgument().addSubArgument(balance);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Gang");
	}

}
