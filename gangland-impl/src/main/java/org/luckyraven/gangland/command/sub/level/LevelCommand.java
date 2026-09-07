package org.luckyraven.gangland.command.sub.level;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.gangland.command.sub.level.experience.LevelExperienceCommand;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.user.Level;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CommandHandler
public final class LevelCommand extends Command {

	private final UserManager<Player> userManager;

	public LevelCommand(Gangland gangland, @Qualifier("online") UserManager<Player> userManager) {
		super(gangland, "level", true);

		this.userManager = userManager;

		var list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("level"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();
		getHelpInfo().addAll(list);
	}

	/**
	 * Resolve the effective target of a level operation.
	 * <p>
	 * If {@code args} contains an entry at {@code targetIdx}, that token is treated as an online player name and looked
	 * up; otherwise the sender itself is returned. Emits {@code PLAYER_NOT_FOUND} or {@code NOT_PLAYER} on failure and
	 * returns {@code null} — callers should abort their action when null is returned.
	 */
	public static User<Player> resolveTarget(CommandSender sender, String[] args, int targetIdx,
	                                         UserManager<Player> userManager) {
		if (args.length > targetIdx) {
			String name   = args[targetIdx];
			Player target = Bukkit.getPlayer(name);

			if (target == null) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", name));
				return null;
			}

			return userManager.getUser(target);
		}

		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return null;
		}

		return userManager.getUser(player);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		Player       player = (Player) commandSender;
		User<Player> user   = userManager.getUser(player);

		if (user == null) return;

		Level  level        = user.getLevel();
		String currentLevel = String.valueOf(level.getLevelValue());
		String maxLevel     = String.valueOf(level.getMaxLevel());

		double exp         = level.getExperience();
		double requiredExp = level.experienceCalculation(level.nextLevel());
		double percentage  = level.getPercentage();

		String experience         = String.format("%.2f", exp);
		String requiredExperience = String.format("%.2f", requiredExp);
		String percentageStr      = String.format("%.2f", percentage);

		int           totalBars = 20;
		StringBuilder builder   = new StringBuilder(totalBars);

		char bar            = Messages.LEVEL_METER_BAR.toString().charAt(0);
		int  completeBars   = (int) (totalBars * (exp / requiredExp));
		int  incompleteBars = totalBars - completeBars;

		for (int i = 0; i < completeBars; i++)
		     builder.append(Messages.LEVEL_COMPLETE_COLOR).append("&l").append(bar);

		for (int i = 0; i < incompleteBars; i++)
		     builder.append(Messages.LEVEL_INCOMPLETE_COLOR).append("&l").append(bar);

		String progressBar = GanglandChatUtil.color(builder.toString());

		String stats = Messages.LEVEL_STATS.toString()
		                                   .replace("%player%", player.getName())
		                                   .replace("%level%", currentLevel)
		                                   .replace("%max_level%", maxLevel)
		                                   .replace("%experience%", experience)
		                                   .replace("%required_experience%", requiredExperience)
		                                   .replace("%percentage%", percentageStr)
		                                   .replace("%progress_bar%", progressBar);
		user.sendMessage(stats);
	}

	@Override
	protected void initializeArguments() {
		Argument add        = new LevelAddCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		Argument remove     = new LevelRemoveCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		Argument experience = new LevelExperienceCommand(getGangland(), getArgumentTree(), getArgument(), userManager);
		Argument next       = new LevelNextCommand(getGangland(), getArgumentTree(), getArgument(), userManager);

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(add);
		arguments.add(remove);
		arguments.add(experience);
		arguments.add(next);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Level");
	}

}
