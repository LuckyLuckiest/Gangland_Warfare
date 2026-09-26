package org.luckyraven.gangland.sign.aspect;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;

import java.math.BigDecimal;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("MoneyAspect - a buy sign the player cannot afford says so")
class MoneyAspectTest {

	@TempDir
	Path dir;

	@Test
	@SuppressWarnings("unchecked")
	void failureReason_withdraw_namesTheMissingFunds() {
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider().withString("Errors.Shop.Purchase.Insufficient_Funds",
		                                                   "need %money_symbol%%price%"));

		UserManager<Player> users   = mock(UserManager.class);
		User<Player>        user    = mock(User.class);
		EconomyHandler      economy = mock(EconomyHandler.class);
		Player              player  = mock(Player.class);
		ParsedSign          sign    = mock(ParsedSign.class);
		when(users.getUser(player)).thenReturn(user);
		when(user.getEconomy()).thenReturn(economy);
		when(economy.getAmount()).thenReturn(BigDecimal.ONE);
		when(sign.getPrice()).thenReturn(10.0);

		MoneyAspect aspect = new MoneyAspect(users, MoneyAspect.TransactionType.WITHDRAW);

		assertEquals(Messages.SHOP_PURCHASE_INSUFFICIENT_FUNDS.toString()
		                                                      .replace("%money_symbol%", Settings.getMoneySymbol())
		                                                      .replace("%price%", Settings.formatAmount(Currency.of(10.0))),
		             aspect.failureReason(player, sign));
	}
}
