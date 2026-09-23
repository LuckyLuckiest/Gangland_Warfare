package org.luckyraven.gangland.data.placeholder.worker;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.data.economy.BankTierView;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.keystone.placeholder.PlaceholderHandler;
import org.luckyraven.keystone.placeholder.effect.ConditionalFlashWrapper;
import org.luckyraven.keystone.placeholder.effect.FlashPlaceholderWrapper;
import org.luckyraven.keystone.placeholder.replacer.Replacer;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContributions;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.file.configuration.SettingsRedaction;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.item.unique.UniqueItem;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.luckyraven.keystone.bean.Qualifier;

public class GanglandPlaceholder extends PlaceholderHandler {

	private final UserManager<Player> userManager;
	private final UniqueItemAddon     uniqueItemAddon;
	private final BankTiers           bankTiers;
	private final DependencyContainer container;

	// Lazily resolved on first placeholder request, not at construction: GanglandPlaceholder is a CONFIG-phase
	// bean, and a module's PlaceholderContribution bean has no declared parameter edge forcing it to construct
	// first within that same phase (unlike CommandContributions, which is only ever looked up from the later
	// COMMAND phase, after every module bean is guaranteed to exist). Any placeholder request happens well after
	// bootstrap completes, so resolving here instead is always safe. Cached forever once resolved (W54 F7): safe
	// because modules load once per start (Keystone's ModuleLoader) — the set of installed PlaceholderContribution
	// beans never changes after boot/reload, so there is no later point where re-resolving could see a different
	// answer.
	private volatile PlaceholderContributions contributions;

	public GanglandPlaceholder(String prefix,
	                           Replacer.Closure closure,
	                           @Qualifier("online") UserManager<Player> userManager,
	                           UniqueItemAddon uniqueItemAddon,
	                           BankTiers bankTiers,
	                           DependencyContainer container,
	                           PlaceholderService placeholderService) {
		super(prefix, closure);
		this.userManager     = userManager;
		this.uniqueItemAddon = uniqueItemAddon;
		this.bankTiers       = bankTiers;
		this.container       = container;
		placeholderService.register(this);
	}

	private PlaceholderContributions contributions() {
		PlaceholderContributions current = contributions;
		if (current == null) {
			current = PlaceholderContributions.from(container);
			contributions = current;
		}
		return current;
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

		if (param.contains("unique-item_")) value = getUniqueItem(param);
		if (value != null) return value;

		// gang_* and the member-touching user_* placeholders (has-gang, gang-id, rank, ...) are gang-module-owned;
		// GangPlaceholderContribution answers them when the module is installed, "NA" otherwise (S4).
		value = contributions().resolve(player, param);
		if (value != null) return value;

		value = getSetting(param);
		if (value == null) return "NA";

		return value;
	}

	@Nullable
	private String getSetting(String parameter) {
		// CM-13: the placeholder map carries the MySQL credentials, and a placeholder is renderable by anyone.
		if (SettingsRedaction.isSensitive(parameter)) return null;

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
		String userStr = "user_";

		// The member-touching user_* placeholders (has-gang, gang-id, gang-join-date, contribution,
		// contributed-amount, has-rank, rank) are gang-module-owned — GangPlaceholderContribution answers them;
		// this method only ever sees the pure-User family below.

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

		BankTierView tier = bankTiers.tierFor(bank);

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
