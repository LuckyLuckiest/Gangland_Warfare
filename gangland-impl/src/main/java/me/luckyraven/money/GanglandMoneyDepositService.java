package me.luckyraven.money;

import lombok.RequiredArgsConstructor;
import me.luckyraven.Gangland;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.item.money.MoneyAddon;
import me.luckyraven.item.money.MoneyDepositService;
import me.luckyraven.util.utilities.ActionBarManager;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Gangland-impl implementation of the {@link MoneyDepositService} contract: looks up the {@link User} for the picking
 * player, deposits via {@code EconomyHandler}, and emits both a chat line and an action-bar flash using the templates
 * configured in {@code money.yml}.
 */
@RequiredArgsConstructor
public class GanglandMoneyDepositService implements MoneyDepositService {

	private static final String AMOUNT_PLACEHOLDER = "%amount%";

	private final Gangland            gangland;
	private final UserManager<Player> userManager;
	private final MoneyAddon          moneyAddon;

	private static String formatAmount(double amount) {
		// Whole-number drops are the common case; only show decimals when the rolled value isn't whole.
		if (amount == Math.floor(amount)) return Long.toString((long) amount);
		return Double.toString(amount);
	}

	@Override
	public void deposit(Player player, double amount, String variationId) {
		if (player == null || amount <= 0) return;

		User<Player> user = userManager.getUser(player);
		if (user == null) return;

		user.getEconomy().deposit(amount);

		String amountText = formatAmount(amount);

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
		return gangland.getInitializer().getPlaceholderService().convert(player, text);
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
		return Settings.getMoneySymbol();
	}

	private String applyAmountAndResolve(Player player, String input, String amountText) {
		String replaced = input.replace(AMOUNT_PLACEHOLDER, amountText);
		return resolvePlaceholders(player, replaced);
	}

}
