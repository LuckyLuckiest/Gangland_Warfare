package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.CommandHandler;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.command.data.CommandInformation;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.level.Level;
import me.luckyraven.feature.level.LevelUpEvent;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class LevelCommand extends CommandHandler {

	public LevelCommand(Gangland gangland) {
		super(gangland, "level", true);

		List<CommandInformation> list = getCommands().entrySet()
				.stream()
				.filter(entry -> entry.getKey().startsWith("level"))
				.sorted(Map.Entry.comparingByKey())
				.map(Map.Entry::getValue)
				.toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) {
		UserManager<Player> userManager = getGangland().getInitializer().getUserManager();
		Player              player      = (Player) commandSender;
		User<Player>        user        = userManager.getUser(player);

		Level  level        = user.getLevel();
		String currentLevel = String.valueOf(level.getLevelValue());
		String maxLevel     = String.valueOf(level.getMaxLevel());

		double exp = level.getExperience(), requiredExp = level.experienceCalculation(level.nextLevel()), percentage
				= level.getPercentage();

		String experience         = String.format("%.2f", exp);
		String requiredExperience = String.format("%.2f", requiredExp);
		String percentageStr      = String.format("%.2f", percentage);

		int           totalBars = 20;
		StringBuilder builder   = new StringBuilder(totalBars);

		char bar            = MessageAddon.LEVEL_METER_BAR.toString().charAt(0);
		int  completeBars   = (int) (totalBars * (exp / requiredExp));
		int  incompleteBars = totalBars - completeBars;

		for (int i = 0; i < completeBars; i++)
			 builder.append(MessageAddon.LEVEL_COMPLETE_COLOR).append("&l").append(bar);

		for (int i = 0; i < incompleteBars; i++)
			 builder.append(MessageAddon.LEVEL_INCOMPLETE_COLOR).append("&l").append(bar);

		String progressBar = ChatUtil.color(builder.toString());

		String stats = MessageAddon.LEVEL_STATS.toString()
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
		UserManager<Player> userManager = getGangland().getInitializer().getUserManager();

		String[] expArr = {"exp", "experience"};
		Argument experience = new Argument(getGangland(), expArr, getArgumentTree(), (argument, sender, args) -> {
			ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<add/remove>");
		}, getPermission() + ".experience");

		Argument expAdd = new Argument(getGangland(), "add", getArgumentTree(), (argument, sender, args) -> {
			ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>");
		}, experience.getPermission() + ".add");

		Argument expRemove = new Argument(getGangland(), "remove", getArgumentTree(), (argument, sender, args) -> {
			ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>");
		}, experience.getPermission() + ".remove");

		Argument expOptional = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			double argAmount;

			try {
				argAmount = Double.parseDouble(args[3]);
			} catch (NumberFormatException exception) {
				user.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString());
				return;
			}

			switch (args[2].toLowerCase()) {
				case "add" -> {
					LevelUpEvent event = new LevelUpEvent(user.getLevel());
					event.setUser(user);

					user.getLevel().addExperience(argAmount, event);
				}
				case "remove" -> user.getLevel().removeExperience(argAmount);
			}

			String type = args[2].toUpperCase();
			user.sendMessage(MessageAddon.valueOf("LEVEL_EXP_" + type)
										 .toString()
										 .replace("%experience%", String.valueOf(argAmount)));
		}, sender -> List.of("<amount>"));

		expAdd.addSubArgument(expOptional);
		expRemove.addSubArgument(expOptional);

		experience.addSubArgument(expAdd);
		experience.addSubArgument(expRemove);

		String levelPerm = getPermission() + ".level";
		getArgument().addPermission(levelPerm);

		Argument levelAdd = new Argument(getGangland(), "add", getArgumentTree(), (argument, sender, args) -> {
			ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>");
		}, levelPerm + ".add");

		Argument levelRemove = new Argument(getGangland(), "remove", getArgumentTree(), (argument, sender, args) -> {
			ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<amount>");
		}, levelPerm + ".remove");

		Argument levelOptional = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			int argAmount;

			try {
				argAmount = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				user.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString());
				return;
			}

			int levels = 0;

			switch (args[1].toLowerCase()) {
				case "add" -> levels = user.getLevel().addLevels(argAmount);
				case "remove" -> levels = user.getLevel().removeLevels(argAmount);
			}

			String type = args[1].toUpperCase();
			user.sendMessage(
					MessageAddon.valueOf("LEVEL_" + type).toString().replace("%level%", String.valueOf(levels)));
		}, sender -> List.of("<amount>"));

		levelAdd.addSubArgument(levelOptional);
		levelRemove.addSubArgument(levelOptional);

		Argument next = new Argument(getGangland(), "next", getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			Level  level        = user.getLevel();
			String currentLevel = String.valueOf(level.getLevelValue());
			String maxLevel     = String.valueOf(level.getMaxLevel());

			double expTemp = level.getExperience(), requiredExp = level.experienceCalculation(level.nextLevel());

			String exp                = String.format("%.2f", expTemp);
			String requiredExperience = String.format("%.2f", requiredExp);

			String message = MessageAddon.LEVEL_NEXT.toString()
													.replace("%level%", currentLevel)
													.replace("%max_level%", maxLevel)
													.replace("%experience%", exp)
													.replace("%required_experience%", requiredExperience);
			user.sendMessage(message);
		});

		// add sub arguments
		List<Argument> arguments = new ArrayList<>();

		arguments.add(levelAdd);
		arguments.add(levelRemove);
		arguments.add(experience);
		arguments.add(next);

		getArgument().addAllSubArguments(arguments);
	}

	@Override
	protected void help(CommandSender sender, int page) {

	}

}
