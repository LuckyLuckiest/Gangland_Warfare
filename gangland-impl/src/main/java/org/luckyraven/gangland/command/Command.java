package org.luckyraven.gangland.command;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.data.CommandInformation;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.file.configuration.Messages;

import java.util.Map;

/**
 * Gangland's subcommand base — a thin adapter over Keystone's {@link org.luckyraven.keystone.command.Command}
 * (1.7.2 migration). Keystone owns the argument tree, two-phase construction and dispatch; this class keeps the
 * consumer-side concerns Keystone deliberately ships without:
 *
 * <ul>
 *     <li>the {@code gangland.command.<label>} permission namespace (decoupled from the plugin name via the
 *     Keystone 1.7.2 prefix overload — the plugin is named {@code Gangland_Warfare});</li>
 *     <li>the {@link HelpInfo} + {@link InformationManager}/{@code commands.json} help layer;</li>
 *     <li>{@link Messages}-localized permission / players-only replies in {@link #runExecute}.</li>
 * </ul>
 */
@Getter
public abstract class Command extends org.luckyraven.keystone.command.Command {

	/**
	 * Static binding to the singleton {@link InformationManager}, set once by {@code GanglandContext.runCommandPhase()}
	 * before commands are scanned. Subclasses use {@link #getCommandInformation(String)} / {@link #getCommands()}
	 * during their constructors to build help info — passing the manager through every subclass {@code super(...)} call
	 * would touch 25+ files for one read, so a single binding is used here.
	 */
	@Setter(value = AccessLevel.PUBLIC)
	private static InformationManager informationManager;

	@Getter(value = AccessLevel.PROTECTED)
	private final Gangland gangland;
	private final HelpInfo helpInfo;

	public Command(Gangland gangland, String label, boolean user, String... alias) {
		super(gangland, Gangland.FULL_PREFIX, label, user, alias);

		this.gangland = gangland;
		this.helpInfo = new HelpInfo();
	}

	/**
	 * Keystone's version answers permission / players-only failures with hardcoded English; this override keeps the
	 * {@link Messages}-localized replies.
	 */
	@Override
	public void runExecute(String commandPrefix, CommandSender sender, String[] args) {
		if (!sender.hasPermission(getPermission())) {
			sender.sendMessage(Messages.COMMAND_NO_PERM.toString());
			return;
		}

		if (isUser() && !(sender instanceof Player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return;
		}

		getArgument().execute(commandPrefix, sender, args);
	}

	/**
	 * Public bridge over the protected {@code help} hook so {@link CommandManager} (a different package than
	 * Keystone's) can render per-subcommand help pages.
	 */
	public void renderHelp(CommandSender sender, int page) {
		help(sender, page);
	}

	public CommandInformation getCommandInformation(String info) {
		return getCommands().get(info);
	}

	public Map<String, CommandInformation> getCommands() {
		return informationManager.getCommands();
	}

}
