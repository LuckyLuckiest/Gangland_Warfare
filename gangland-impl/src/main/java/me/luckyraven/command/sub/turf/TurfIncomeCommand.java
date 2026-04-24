package me.luckyraven.command.sub.turf;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.economy.Currency;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.selection.WandSelectionManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code /glw turf income <amount>} — sets the per-interval income payout of the admin's active-selection turf. Amount
 * is stored on the {@code Turf} and persisted; the existing {@code TurfIncomeDistributor} picks up the new value on its
 * next scheduled payout.
 */
class TurfIncomeCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;

	protected TurfIncomeCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            TurfManager turfs, WandSelectionManager selections, TurfMessageContract messages) {
		super(gangland, "income", tree, parent);

		this.gangland   = gangland;
		this.tree       = tree;
		this.turfs      = turfs;
		this.selections = selections;
		this.messages   = messages;

		this.addSubArgument(amountArgument());
	}

	private static BigDecimal parseAmount(String raw) {
		try {
			return new BigDecimal(raw);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<amount>"));
	}

	private OptionalArgument amountArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) {
				return;
			}
			BigDecimal amount = parseAmount(args[2]);
			if (amount == null || amount.signum() < 0) {
				messages.send(sender, "TURF_INCOME_INVALID", "amount", args[2]);
				return;
			}
			turf.setIncomeAmount(Currency.of(amount));
			turfs.persist(turf);

			messages.send(sender, "TURF_INCOME_SUCCESS",
			              "turf", turf.getDisplayName(),
			              "money_symbol", Settings.getMoneySymbol(),
			              "amount", Settings.formatAmount(turf.getIncomeAmount()),
			              "time", messages.formatDuration(Settings.getTurfIncomeIntervalMinutes() * 60L));
		}, sender -> List.of("<amount>", "100", "500", "1000"));
	}
}
