package me.luckyraven.data.economy;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.utilities.ActionBarManager;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.data.placeholder.PlaceholderService;
import me.luckyraven.economy.Currency;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Gangland-impl implementation of the {@link MoneyDepositService} contract: looks up the {@link User} for the picking
 * player, deposits via {@code EconomyHandler}, and emits both a chat line and an action-bar flash using the templates
 * configured in {@code money.yml}.
 */
@RequiredArgsConstructor
public class GanglandMoneyDepositService implements MoneyDepositService {

	private static final String AMOUNT_PLACEHOLDER = "%amount%";

	private final UserManager<Player> userManager;
	private final MoneyAddon          moneyAddon;
	private final PlaceholderService  placeholderService;

	private static String formatAmount(double amount) {
		// Whole-number drops are the common case; only show decimals when the rolled value isn't whole.
		if (amount == Math.floor(amount)) return Long.toString((long) amount);
		return Double.toString(amount);
	}

	@Override
	public void deposit(Player player, double amount, String variationId) {
		if (player == null || amount <= 0) return;
		deposit(player, Currency.of(amount), variationId);
	}

	@Override
	public void deposit(Player player, BigDecimal amount, String variationId) {
		if (player == null || amount == null || amount.signum() <= 0) return;

		User<Player> user = userManager.getUser(player);
		if (user == null) return;

		user.getEconomy().depositAmount(Currency.of(amount));

		String amountText = formatAmount(amount.doubleValue());

		String chatTemplate = moneyAddon.getPickupChatMessage();
		if (chatTemplate != null && !chatTemplate.isEmpty()) {
			String resolved = applyAmountAndResolve(player, chatTemplate, amountText);
			player.sendMessage(ChatUtil.color(resolved));
		}

		String actionBarTemplate = moneyAddon.getPickupActionBar();
		if (actionBarTemplate != null && !actionBarTemplate.isEmpty()) {
			String resolved = applyAmountAndResolve(player, actionBarTemplate, amountText);
			ActionBarManager.send(player, resolved);
		}
	}

	@Override
	public String resolvePlaceholders(@Nullable Player player, String text) {
		if (text == null || text.isEmpty()) return text;
		return placeholderService.convert(player, text);
	}

	@Override
	public double getBalance(Player player) {
		if (player == null) return 0;
		User<Player> user = userManager.getUser(player);
		if (user == null) return 0;
		return user.getEconomy().getAmount().doubleValue();
	}

	@Override
	public BigDecimal getBalanceAsAmount(Player player) {
		if (player == null) return Currency.ZERO;
		User<Player> user = userManager.getUser(player);
		if (user == null) return Currency.ZERO;
		return user.getEconomy().getAmount();
	}

	@Override
	public String getCurrencySymbol() {
		return Settings.getMoneySymbol();
	}

	private String applyAmountAndResolve(Player player, String input, String amountText) {
		String replaced = input.replace(AMOUNT_PLACEHOLDER, amountText);
		return resolvePlaceholders(player, replaced);
	}

}
