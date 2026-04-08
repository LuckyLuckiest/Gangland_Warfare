package me.luckyraven.money;

import lombok.RequiredArgsConstructor;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.util.utilities.ActionBarManager;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.entity.Player;

/**
 * Gangland-impl implementation of the {@link MoneyDepositService} contract: looks up the {@link User} for the picking
 * player, deposits via {@code EconomyHandler}, and emits both a chat line and an action-bar flash using the templates
 * configured in {@code money.yml}.
 */
@RequiredArgsConstructor
public class GanglandMoneyDepositService implements MoneyDepositService {

	private static final String SYMBOL_PLACEHOLDER = "%symbol%";
	private static final String AMOUNT_PLACEHOLDER = "%amount%";

	private final UserManager<Player> userManager;
	private final MoneyAddon          moneyAddon;

	private static String formatAmount(double amount) {
		// Whole-number drops are the common case; only show decimals when the rolled value isn't whole.
		if (amount == Math.floor(amount)) return Long.toString((long) amount);
		return Double.toString(amount);
	}

	private static String applyPlaceholders(String input, String symbol, String amountText) {
		return input.replace(SYMBOL_PLACEHOLDER, symbol).replace(AMOUNT_PLACEHOLDER, amountText);
	}

	@Override
	public void deposit(Player player, double amount, String variationId) {
		if (player == null || amount <= 0) return;

		User<Player> user = userManager.getUser(player);
		if (user == null) return;

		user.getEconomy().deposit(amount);

		String symbol     = moneyAddon.getCurrencySymbol();
		String amountText = formatAmount(amount);

		String chatTemplate = moneyAddon.getPickupChatMessage();
		if (chatTemplate != null && !chatTemplate.isEmpty()) {
			player.sendMessage(ChatUtil.color(applyPlaceholders(chatTemplate, symbol, amountText)));
		}

		String actionBarTemplate = moneyAddon.getPickupActionBar();
		if (actionBarTemplate != null && !actionBarTemplate.isEmpty()) {
			ActionBarManager.send(player, applyPlaceholders(actionBarTemplate, symbol, amountText));
		}
	}

	@Override
	public double getBalance(Player player) {
		if (player == null) return 0;
		User<Player> user = userManager.getUser(player);
		if (user == null) return 0;
		return user.getEconomy().getBalance();
	}

	@Override
	public String getCurrencySymbol() {
		return moneyAddon.getCurrencySymbol();
	}

}
