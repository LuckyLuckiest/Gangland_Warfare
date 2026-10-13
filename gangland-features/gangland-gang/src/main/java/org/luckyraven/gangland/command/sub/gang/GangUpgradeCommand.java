package org.luckyraven.gangland.command.sub.gang;

import lombok.CustomLog;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.events.gang.GangLevelUpEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.keystone.util.TriConsumer;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * {@code /glw gang upgrade [confirm]} (D16 C): held-turf XP fills the bar, the level is then claimed by paying its
 * {@code Levels.Level_<n>.Cost} from the gang bank. Without {@code confirm} it shows what is still missing (or that the
 * level is ready); {@code confirm} needs both met, the upgrade rank node, and takes the fee once, only after a level-up
 * that nobody cancelled. Money never adds XP. A free level (cost 0) never reaches this command: the XP levels the gang
 * on its own, see {@code GangManager.addExperience}.
 */
@CustomLog
final class GangUpgradeCommand extends SubArgument {

	private final GangLimits          limits;
	private final GangLevelTable      levelTable;
	private final GangManager         gangManager;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangMessages        messages;
	private final ConfirmArgument     confirm;

	GangUpgradeCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangLimits limits,
	                   GangLevelTable levelTable, GangManager gangManager, UserManager<Player> userManager,
	                   MemberManager memberManager, GangMessages messages) {
		super(gangland, "upgrade", tree, parent);

		this.limits        = limits;
		this.levelTable    = levelTable;
		this.gangManager   = gangManager;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.messages      = messages;

		this.confirm = new ConfirmArgument(gangland, tree, (argument, sender, args) -> claim(sender));
		this.addSubArgument(confirm);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> preview(sender);
	}

	private void preview(CommandSender sender) {
		Gang gang = senderGang(sender);
		if (gang == null) return;

		Level level = gang.getLevel();
		if (atTop(level)) {
			sender.sendMessage(messages.text("Levels.Max", "&7Your gang is already at the highest level."));
			return;
		}

		// the player asked for this: arm the confirm word for him, the claim re-checks everything
		confirm.lock(sender);
		sender.sendMessage(statusLine(gang));
	}

	private void claim(CommandSender sender) {
		Gang gang = senderGang(sender);
		if (gang == null) return;

		Level level = gang.getLevel();
		if (atTop(level)) {
			sender.sendMessage(messages.text("Levels.Max", "&7Your gang is already at the highest level."));
			return;
		}

		Player player = (Player) sender;
		if (!GangPermissions.allows(memberManager.getMember(player.getUniqueId()), player, GangPermissions.UPGRADE)) {
			sender.sendMessage(messages.text("Levels.No_Permission", "&cYour rank may not upgrade the gang."));
			return;
		}

		int  next = level.nextLevel();
		long cost = (long) Math.ceil(levelTable.row(next).cost());
		// Level.addLevels levels even below the bar (it only burns XP when it has it), so the bar is checked here
		boolean barFull = level.getExperience() >= level.experienceCalculation(next);
		boolean paid    = gang.getEconomy().getAmount().compareTo(BigDecimal.valueOf(cost)) >= 0;
		if (!barFull || !paid) {
			sender.sendMessage(statusLine(gang));
			return;
		}

		// US-43: the fee follows the level-up. A cancelled GangLevelUpEvent makes addLevels return 0 and change nothing.
		if (level.addLevels(1, new GangLevelUpEvent(false, gang, level)) != 1) {
			sender.sendMessage(messages.text("Levels.Cancelled", "&cThe level-up was cancelled. Nothing was charged."));
			return;
		}

		if (cost > 0) {
			try {
				gang.getEconomy().withdrawAmount(BigDecimal.valueOf(cost));
			} catch (EconomyException exception) {
				log.warn("Gang {} reached level {} but the fee of {} could not be taken: {}", gang.getId(),
				         level.getLevelValue(), cost, exception.getMessage());
			}
		}

		sender.sendMessage(messages.text("Levels.Claimed",
		                                 "&aYour gang reached level &e%level%&a for &e%money_symbol%%cost%&a from the gang bank.",
		                                 "level", level.getLevelValue(), "money_symbol", Settings.getMoneySymbol(),
		                                 "cost", Settings.formatAmount(BigDecimal.valueOf(cost))));
	}

	/** "Level n needs ... more" while something is short, "Ready: level n costs ..." once both are met. */
	private String statusLine(Gang gang) {
		Level  level        = gang.getLevel();
		int    next         = level.nextLevel();
		double cost         = levelTable.row(next).cost();
		double xpMissing    = limits.upgradeXpMissing(gang.getId());
		double moneyMissing = Math.max(0D, cost - gang.getEconomy().getAmount().doubleValue());

		if (xpMissing > 0D || moneyMissing > 0D) {
			return messages.text("Levels.Preview",
			                     "&7Level &e%level% &7needs &c%xp_missing% &7more XP and &c%money_symbol%%money_missing% &7more in the bank.",
			                     "level", next, "xp_missing", String.format(Locale.ROOT, "%d", (long) Math.ceil(xpMissing)),
			                     "money_symbol", Settings.getMoneySymbol(), "money_missing",
			                     Settings.formatAmount(BigDecimal.valueOf((long) Math.ceil(moneyMissing))));
		}

		return messages.text("Levels.Ready", "&aReady: level &e%level% &acosts &e%money_symbol%%cost% &7- &e/glw gang upgrade confirm",
		                     "level", next, "money_symbol", Settings.getMoneySymbol(), "cost",
		                     Settings.formatAmount(BigDecimal.valueOf((long) Math.ceil(cost))));
	}

	private static boolean atTop(Level level) {
		return level.getLevelValue() >= level.getMaxLevel();
	}

	/** The sender's gang, or {@code null} after telling a sender with no gang (or the console) so. */
	private Gang senderGang(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return null;
		}

		User<Player> user = userManager.getUser(player);
		Gang         gang = user == null || !user.hasGang() ? null : gangManager.getGang(user.getGangId());
		if (gang == null) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
		}
		return gang;
	}
}
