package org.luckyraven.gangland.npcshops.holdup;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.npcshops.integration.GanglandTraderEconomy;
import org.luckyraven.keystone.economy.EconomyHandler;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GanglandTraderEconomy.deposit - the wallet credit behind the hold-up payout")
class GanglandTraderEconomyDepositTest {

	@SuppressWarnings("unchecked")
	private final UserManager<Player> users  = mock(UserManager.class);
	private final Player              player = mock(Player.class);

	@Test
	@DisplayName("credits the player's economy and reports success")
	@SuppressWarnings("unchecked")
	void deposits() {
		User<Player>   user    = mock(User.class);
		EconomyHandler economy = new EconomyHandler(null);
		economy.setAmount(BigDecimal.ZERO);
		when(users.getUser(player)).thenReturn(user);
		when(user.getEconomy()).thenReturn(economy);

		assertTrue(new GanglandTraderEconomy(users).deposit(player, new BigDecimal("345.67")));

		assertEquals(0, economy.getAmount().compareTo(new BigDecimal("345.67")));
	}

	@Test
	@DisplayName("a player without a loaded account gets nothing and false")
	void noAccount() {
		when(users.getUser(player)).thenReturn(null);
		assertFalse(new GanglandTraderEconomy(users).deposit(player, new BigDecimal("10")));
	}

}
