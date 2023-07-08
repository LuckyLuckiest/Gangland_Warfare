package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import org.bukkit.command.CommandSender;

public class SCTesting extends CommandHandler {

	public SCTesting(Gangland gangland) {
		super(gangland, "testing", false, "test", "t");
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {

	}

	@Override
	protected void initializeArguments(Gangland gangland) {
		Argument test1 = new Argument("test1", getArgumentTree());
		Argument test2 = new Argument("test2", getArgumentTree());
		Argument test3 = new Argument("test3", getArgumentTree());
		Argument test4 = new Argument("test4", getArgumentTree());

		Argument testSub = new Argument("sub", getArgumentTree());

		test1.addSubArgument(new Argument(testSub));
		test2.addSubArgument(new Argument(testSub));
		test3.addSubArgument(new Argument(testSub));
		test4.addSubArgument(new Argument(testSub));

		getArgument().addSubArgument(test1);
		getArgument().addSubArgument(test2);
		getArgument().addSubArgument(test3);
		getArgument().addSubArgument(test4);
	}

	@Override
	protected void help(CommandSender sender, int page) {

	}

}
