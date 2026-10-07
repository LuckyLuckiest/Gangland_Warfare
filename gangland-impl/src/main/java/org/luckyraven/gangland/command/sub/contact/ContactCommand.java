package org.luckyraven.gangland.command.sub.contact;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.data.wanted.ContactDesk;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.command.CommandHandler;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.economy.exception.EconomyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** {@code /glw contact [stars]}: pays a crooked desk sergeant to wipe stars while no cop has eyes on you. */
@CommandHandler
public final class ContactCommand extends Command {

	private final UserManager<Player> userManager;
	private final ContactDesk         desk;

	public ContactCommand(JavaPlugin gangland, @Qualifier("online") UserManager<Player> userManager,
	                      ContactDesk desk) {
		super(gangland, "contact", true);

		this.userManager = userManager;
		this.desk        = desk;

		var list = getCommands().entrySet()
		                        .stream()
		                        .filter(entry -> entry.getKey().startsWith("contact"))
		                        .sorted(Map.Entry.comparingByKey())
		                        .map(Map.Entry::getValue)
		                        .toList();

		getHelpInfo().addAll(list);
	}

	@Override
	protected void onExecute(Argument argument, CommandSender sender, String[] args) {
		if (sender instanceof Player player) contact(player, 1);
	}

	@Override
	protected void initializeArguments() {
		Argument stars = new OptionalArgument(getPlugin(), getArgumentTree(), (argument, sender, args) -> {
			if (!(sender instanceof Player player)) return;

			int amount;
			try {
				amount = Integer.parseInt(args[1]);
			} catch (NumberFormatException exception) {
				sender.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", args[1]));
				return;
			}

			contact(player, Math.max(1, amount));
		}, sender -> IntStream.rangeClosed(1, Math.max(1, Settings.getContactsMaxStars()))
		                      .mapToObj(String::valueOf)
		                      .toList());

		getArgument().addSubArgument(stars);
	}

	/** One phone call: every refusal returns before a cent or a star moves. */
	void contact(Player player, int requested) {
		if (!Settings.isContactsEnabled()) {
			player.sendMessage(Messages.CONTACT_DISABLED.toString());
			return;
		}

		User<Player> user = userManager.getUser(player);

		if (user == null) return;

		Wanted wanted = user.getWanted();
		int    level  = wanted.getLevel();

		if (level <= 0) {
			player.sendMessage(Messages.CONTACT_NOT_WANTED.toString());
			return;
		}

		if (desk.seen(player.getUniqueId())) {
			player.sendMessage(Messages.CONTACT_SEEN.toString());
			return;
		}

		long left = desk.cooldownLeftMs(player.getUniqueId());

		if (left > 0) {
			player.sendMessage(Messages.CONTACT_COOLDOWN.toString().replace("%time%", ContactDesk.formatLeft(left)));
			return;
		}

		int        stars = Math.min(Math.min(requested, Math.max(1, Settings.getContactsMaxStars())), level);
		BigDecimal price = desk.priceFor(stars);

		if (user.getEconomy().getAmount().compareTo(price) < 0) {
			player.sendMessage(replaceMoney(Messages.CONTACT_NO_MONEY.toString(), price));
			return;
		}

		try {
			user.getEconomy().withdrawAmount(price);
		} catch (EconomyException exception) {
			player.sendMessage(replaceMoney(Messages.CONTACT_NO_MONEY.toString(), price));
			return;
		}

		wanted.setLevel(level - stars, WantedCause.CONTACT);
		desk.startCooldown(player.getUniqueId());

		player.sendMessage(replaceMoney(Messages.CONTACT_USED.toString(), price).replace("%stars%",
		                                                                                 String.valueOf(stars)));
	}

	private static String replaceMoney(String text, BigDecimal price) {
		return text.replace("%money_symbol%", Settings.getMoneySymbol()).replace("%amount%", Settings.formatAmount(price));
	}

	@Override
	protected void help(CommandSender sender, int page) {
		getHelpInfo().displayHelp(sender, page, "Contact");
	}

}
