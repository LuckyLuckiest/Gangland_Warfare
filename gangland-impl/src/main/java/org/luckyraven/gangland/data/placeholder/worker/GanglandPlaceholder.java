package org.luckyraven.gangland.data.placeholder.worker;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTier;
import org.luckyraven.gangland.copsncrooks.npc.banker.tier.BankTierRegistry;
import org.luckyraven.gangland.core.color.ColorUtil;
import org.luckyraven.gangland.core.placeholder.PlaceholderHandler;
import org.luckyraven.gangland.core.placeholder.effect.ConditionalFlashWrapper;
import org.luckyraven.gangland.core.placeholder.effect.FlashPlaceholderWrapper;
import org.luckyraven.gangland.core.placeholder.replacer.Replacer;
import org.luckyraven.gangland.core.utilities.NumberUtil;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.economy.Currency;
import org.luckyraven.gangland.economy.bank.Bank;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.Level;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.gang.wanted.Wanted;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

public class GanglandPlaceholder extends PlaceholderHandler {

	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final UniqueItemAddon     uniqueItemAddon;
	private final BankTierRegistry    bankTierRegistry;

	public GanglandPlaceholder(String prefix,
	                           Replacer.Closure closure,
	                           UserManager<Player> userManager,
	                           MemberManager memberManager,
	                           GangManager gangManager,
	                           UniqueItemAddon uniqueItemAddon,
	                           BankTierRegistry bankTierRegistry,
	                           PlaceholderService placeholderService) {
		super(prefix, closure);
		this.userManager      = userManager;
		this.memberManager    = memberManager;
		this.gangManager      = gangManager;
		this.uniqueItemAddon  = uniqueItemAddon;
		this.bankTierRegistry = bankTierRegistry;
		placeholderService.register(this);
	}

	private static String formatUntil(@Nullable Instant target) {
		if (target == null) return "available";
		Duration remaining = Duration.between(Instant.now(), target);
		if (remaining.isNegative() || remaining.isZero()) return "available";
		return formatDuration(remaining);
	}

	private static String formatReadyIn(@Nullable Instant lastClaimAt, Duration window) {
		if (lastClaimAt == null) return "available";
		Instant readyAt = lastClaimAt.plus(window);
		return formatUntil(readyAt);
	}

	private static String formatDuration(Duration duration) {
		long totalSec = Math.max(0L, duration.getSeconds());
		long days     = totalSec / 86_400L;
		long hours    = (totalSec % 86_400L) / 3_600L;
		long mins     = (totalSec % 3_600L) / 60L;
		if (days > 0) return days + "d " + hours + "h";
		if (hours > 0) return hours + "h " + mins + "m";
		if (mins > 0) return mins + "m";
		return "<1m";
	}

	private static String formatPercent(double pct) {
		if (pct == Math.floor(pct)) return String.valueOf((long) pct);
		return String.format("%.2f", pct);
	}

	@Override
	public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameter) {
		String param = parameter.toLowerCase();

		if (player == null) {
			String value = getSetting(param);
			return value == null ? "NA" : value;
		}

		if (ConditionalFlashWrapper.isConditionalFlash(param)) {
			return ConditionalFlashWrapper.processConditionalFlash(param, this::resolveInnerPlaceholder, player);
		}

		// check for flash effect first
		if (FlashPlaceholderWrapper.isFlashPlaceholder(param)) {
			return FlashPlaceholderWrapper.processFlash(param, this::resolveInnerPlaceholder, player);
		}

		return resolveInnerPlaceholder(player, param);
	}

	/**
	 * Internal method to resolve placeholders without effects.
	 *
	 * @param player the player data.
	 * @param parameter the placeholder parameter.
	 *
	 * @return the resolved placeholder.
	 */
	@Nullable
	private String resolveInnerPlaceholder(OfflinePlayer player, @NotNull String parameter) {
		if (player == null) return null;

		String param = parameter.toLowerCase();

		String value = null;

		if (param.contains("user_")) value = getUser(player, param);
		if (value != null) return value;

		if (param.contains("bank_")) value = getBank(player, param);
		if (value != null) return value;

		if (param.contains("gang_")) value = getGang(player, param);
		if (value != null) return value;

		if (param.contains("unique-item_")) value = getUniqueItem(param);
		if (value != null) return value;

		value = getSetting(param);
		if (value == null) return "NA";

		return value;
	}

	@Nullable
	private String getSetting(String parameter) {
		Object value = Settings.getSettingsPlaceholder()
		                       .entrySet()
				.stream()
				.filter(entry -> entry.getKey().equals(parameter))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse(null);

		if (value == null) return null;

		if (value instanceof Double) return Settings.formatDouble((double) value);

		return String.valueOf(value);
	}

	@Nullable
	private String getUser(OfflinePlayer player, String parameter) {
		// for member
		Member member  = memberManager.getMember(player.getUniqueId());
		String userStr = "user_";

		if (member == null) return null;

		if (parameter.equals(userStr + "has-gang")) return String.valueOf(member.hasGang());
		if (parameter.equals(userStr + "gang-id")) return !member.hasGang() ? null : String.valueOf(member.getGangId());
		if (parameter.equals(userStr + "gang-join-date"))
			return !member.hasGang() ? null : member.getGangJoinDateString();
		if (parameter.equals(userStr + "contribution"))
			return !member.hasGang() ? null : Settings.formatDouble(member.getContribution());
		if (parameter.equals(userStr + "contributed-amount")) {
			return !member.hasGang() ?
			       null :
			       NumberUtil.valueFormat(Settings.getGangContributionRate() * member.getContribution());
		}
		if (parameter.equals(userStr + "has-rank")) return String.valueOf(member.hasRank());
		if (parameter.equals(userStr + "rank")) return member.getRank() == null ? null : member.getRank().getName();

		// for user
		Player onlinePlayer = player.getPlayer();
		if (!player.isOnline() || onlinePlayer == null) return null;

		User<Player> user = userManager.getUser(onlinePlayer);

		if (user == null) return null;

		// economy
		if (parameter.equals(userStr + "balance")) return NumberUtil.valueFormat(user.getEconomy().getAmount());
		if (parameter.equals(userStr + "has-bank")) return String.valueOf(user.hasBank());

		// bounty
		if (parameter.equals(userStr + "bounty")) return NumberUtil.valueFormat(user.getBounty().getAmount());
		if (parameter.equals(userStr + "has-bounty")) return String.valueOf(user.getBounty().hasBounty());

		if (parameter.equals(userStr + "kd")) return Settings.formatDouble(user.getKillDeathRatio());
		if (parameter.equals(userStr + "mob-kills")) return String.valueOf(user.getMobKills());
		if (parameter.equals(userStr + "kills")) return String.valueOf(user.getKills());
		if (parameter.equals(userStr + "deaths")) return String.valueOf(user.getDeaths());

		// wanted
		Wanted wanted = user.getWanted();
		if (parameter.equals(userStr + "wanted")) return wanted.getLevelStars();
		if (parameter.equals(userStr + "wanted-level")) return String.valueOf(wanted.getLevel());
		if (parameter.equals(userStr + "wanted-max-level")) return String.valueOf(wanted.getMaxLevel());
		if (parameter.equals(userStr + "is-wanted")) return String.valueOf(wanted.isWanted());

		// level
		return getLevelPlaceholder(parameter, userStr, user.getLevel());
	}

	@Nullable
	private String getBank(OfflinePlayer player, String parameter) {
		// for bank
		Player onlinePlayer = player.getPlayer();
		if (!player.isOnline() || onlinePlayer == null) return null;

		User<Player> user = userManager.getUser(onlinePlayer);

		if (user == null) return null;

		Bank   bank    = user.getBank();
		String bankStr = "bank_";

		if (bank == null) return null;

		if (parameter.equals(bankStr + "name")) return bank.getName();
		if (parameter.equals(bankStr + "balance")) return NumberUtil.valueFormat(bank.getEconomy().getAmount());

		BankTier tier = bankTierRegistry.get(bank.getTierId());
		if (tier == null) tier = bankTierRegistry.first();

		if (parameter.equals(bankStr + "tier")) return tier == null ? "" : tier.id();
		if (parameter.equals(bankStr + "tier_display")) return tier == null ? "" : tier.displayName();
		if (parameter.equals(bankStr + "tier_cap")) {
			return NumberUtil.valueFormat(tier == null ? Currency.ZERO : tier.maxBalance());
		}
		if (parameter.equals(bankStr + "daily_deposit_limit")) {
			return NumberUtil.valueFormat(tier == null ? Currency.ZERO : tier.dailyDepositLimit());
		}
		if (parameter.equals(bankStr + "deposited_today")) {
			return NumberUtil.valueFormat(Currency.of(bank.getDepositedToday()));
		}
		if (parameter.equals(bankStr + "remaining_deposit")) {
			BigDecimal limit = tier == null ? Currency.ZERO : tier.dailyDepositLimit();
			if (limit.signum() <= 0) return "∞";
			BigDecimal remaining = limit.subtract(Currency.of(bank.getDepositedToday()));
			if (remaining.signum() < 0) remaining = Currency.ZERO;
			return NumberUtil.valueFormat(remaining);
		}
		if (parameter.equals(bankStr + "next_reset")) {
			return formatUntil(bank.getCapResetAt());
		}
		if (parameter.equals(bankStr + "interest_rate")) {
			double rate = tier == null ? 0D : tier.interestRate();
			return formatPercent(rate * 100D);
		}
		if (parameter.equals(bankStr + "weekly_amount")) {
			return NumberUtil.valueFormat(tier == null ? Currency.ZERO : tier.weeklyLoanAmount());
		}
		if (parameter.equals(bankStr + "monthly_amount")) {
			return NumberUtil.valueFormat(tier == null ? Currency.ZERO : tier.monthlyLoanAmount());
		}
		if (parameter.equals(bankStr + "weekly_ready_in")) {
			return formatReadyIn(bank.getLastWeeklyLoanAt(), Duration.ofDays(7));
		}
		if (parameter.equals(bankStr + "monthly_ready_in")) {
			return formatReadyIn(bank.getLastMonthlyLoanAt(), Duration.ofDays(30));
		}

		return null;
	}

	@Nullable
	private String getGang(OfflinePlayer player, String parameter) {
		// for gang
		Member member = memberManager.getMember(player.getUniqueId());

		if (member == null) return null;

		Gang   gang    = gangManager.getGang(member.getGangId());
		String gangStr = "gang_";

		if (gang == null) return null;

		// info
		if (parameter.equals(gangStr + "id")) return String.valueOf(gang.getId());
		if (parameter.equals(gangStr + "name")) return gang.getName();
		if (parameter.equals(gangStr + "display-name")) return gang.getDisplayNameString();
		if (parameter.equals(gangStr + "state")) return gang.getState().name().toLowerCase();
		if (parameter.equals(gangStr + "color")) return gang.getColor();
		if (parameter.equals(gangStr + "color-name")) return gang.getColor().toLowerCase().replace("_", " ");
		if (parameter.equals(gangStr + "color-code")) return ColorUtil.getColorCode(gang.getColor());
		if (parameter.equals(gangStr + "description")) return gang.getDescription();
		if (parameter.equals(gangStr + "created")) return gang.getDateCreatedString();

		// economy
		if (parameter.equals(gangStr + "balance")) return NumberUtil.valueFormat(gang.getEconomy().getAmount());

		// bounty
		if (parameter.equals(gangStr + "bounty")) return NumberUtil.valueFormat(gang.getBounty().getAmount());
		if (parameter.equals(gangStr + "has-bounty")) return String.valueOf(gang.getBounty().hasBounty());

		// members
		if (parameter.equals(gangStr + "members-size")) return String.valueOf(gang.getMembers().size());
		if (parameter.equals(gangStr + "online-members-size"))
			return String.valueOf(gang.getOnlineMembers(userManager::getUser).size());
		if (parameter.equals(gangStr + "offline-members-size"))
			return String.valueOf(gang.getMembers().size() - gang.getOnlineMembers(userManager::getUser).size());

		// ally
		if (parameter.equals(gangStr + "ally-list")) return gang.getAllyListString();
		if (parameter.equals(gangStr + "ally-size")) return String.valueOf(gang.getAllies().size());

		// level
		return getLevelPlaceholder(parameter, gangStr, gang.getLevel());
	}

	@Nullable
	private String getLevelPlaceholder(String parameter, String type, Level level) {
		if (parameter.equals(type + "level")) return String.valueOf(level.getLevelValue());
		if (parameter.equals(type + "level-max")) return String.valueOf(level.getMaxLevel());
		if (parameter.equals(type + "level-next")) return String.valueOf(level.nextLevel());
		if (parameter.equals(type + "level-previous")) return String.valueOf(level.previousLevel());
		if (parameter.equals(type + "experience")) return NumberUtil.valueFormat(level.getExperience());
		if (parameter.equals(type + "experience-percentage")) return NumberUtil.valueFormat(level.getPercentage());
		if (parameter.equals(type + "experience-next-level"))
			return NumberUtil.valueFormat(level.experienceCalculation(level.nextLevel()));
		if (parameter.equals(type + "experience-previous-level"))
			return NumberUtil.valueFormat(level.experienceCalculation(level.previousLevel()));
		if (parameter.equals(type + "experience-current-level"))
			return NumberUtil.valueFormat(level.experienceCalculation(level.getLevelValue()));
		if (parameter.startsWith(type + "experience-level-")) {
			String param = parameter.substring(parameter.lastIndexOf('-') + 1);
			int    value;
			try {
				value = Integer.parseInt(param);
			} catch (NumberFormatException exception) {
				return null;
			}
			return NumberUtil.valueFormat(level.experienceCalculation(value));
		}

		return null;
	}

	@Nullable
	private String getUniqueItem(String parameter) {
		String prefix = "unique-item_";

		if (!parameter.startsWith(prefix)) return null;

		String   remainder = parameter.substring(prefix.length());
		String[] parts     = remainder.split("_");

		if (parts.length < 2) return null;

		String itemKey  = parts[0];
		String property = parts[1];

		UniqueItem uniqueItem = uniqueItemAddon.getUniqueItem(itemKey);

		if (uniqueItem == null) return null;

		return switch (property) {
			case "name" -> uniqueItem.getName();
			case "permission" -> uniqueItem.getPermission();
			case "material" -> uniqueItem.getMaterial().name();
			case "add-on-join" -> String.valueOf(uniqueItem.isAddOnJoin());
			case "add-on-respawn" -> String.valueOf(uniqueItem.isAddOnRespawn());
			case "drop-on-death" -> String.valueOf(uniqueItem.isDropOnDeath());
			case "allow-duplicates" -> String.valueOf(uniqueItem.isAllowDuplicates());
			case "add-to-inventory" -> String.valueOf(uniqueItem.isAddToInventory());
			case "lore" -> String.join("\n", uniqueItem.getLore());
			case "inventory-slot" -> String.valueOf(uniqueItem.getInventorySlot());
			case "overrides-slot" -> String.valueOf(uniqueItem.isOverridesSlot());
			case "movable" -> String.valueOf(uniqueItem.isMovable());
			case "droppable" -> String.valueOf(uniqueItem.isDroppable());
			default -> null;
		};
	}

}
