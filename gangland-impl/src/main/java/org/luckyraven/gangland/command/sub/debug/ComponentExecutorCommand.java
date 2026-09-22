package org.luckyraven.gangland.command.sub.debug;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.extension.CommandContributions;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.util.GanglandChatUtil;

/**
 * {@code /glw option} — {@code click resource} (the resourcepack reconnect help text) is the only branch that
 * stayed here; {@code gang rank <target> <rank>} moved wholesale to the gang module's
 * {@code GangOptionContribution} (WS5 G2 step 14) — every branch it had was gang/rank logic.
 */
@CommandHandler
public final class ComponentExecutorCommand extends Command {

	/**
	 * Sub-arguments a runtime module attaches under {@code option} (the gang module contributes {@code gang}).
	 * Empty when no module is installed — the core never names them.
	 */
	private final CommandContributions contributions;

	public ComponentExecutorCommand(JavaPlugin gangland, DependencyContainer container) {
		super(gangland, "option", false);
		this.contributions = CommandContributions.from(container);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) { }

	@Override
	protected void initializeArguments() {
		Argument resource = resourcePack();

		getArgument().addSubArgument(resource);
		getArgument().addAllSubArguments(contributions.createFor("option", getArgumentTree(), getArgument()));
	}

	@Override
	protected void help(CommandSender sender, int page) { }

	private Argument resourcePack() {
		Argument click = new Argument(getPlugin(), "click", getArgumentTree());

		// glw option click resource
		Argument resource = new Argument(getPlugin(), "resource", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.prefixMessage(
					"&7You should first disconnect from the server and click the server tab you created, " +
					"next click &8(&bEdit&8)&7 then change &8(&bServer Resource Packs&8)&7 to either " +
					"&aPrompt&7 or &aEnabled&7 then click &8(&bDone&8)&7 button and log back in."));
			sender.sendMessage(GanglandChatUtil.color(
					"&7If you chose &aPrompt &7then click &8(&bYes&8)&7 once you joined back into the server, " +
					"then the download process will start."));
		});

		click.addSubArgument(resource);

		return click;
	}

}
