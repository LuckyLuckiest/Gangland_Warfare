package org.luckyraven.gangland.item.money;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("MoneyDepositService - interface defaults")
class MoneyDepositServiceDefaultsTest {

	@Test
	@DisplayName("depositPickup default credits through deposit(double) and accepts the pickup")
	void depositPickupDefaultDepositsAndReturnsTrue() {
		MoneyDepositService service = mock(MoneyDepositService.class, CALLS_REAL_METHODS);
		Player              player  = mock(Player.class);

		assertTrue(service.depositPickup(player, 50D, "small"));

		verify(service).deposit(player, 50D, "small");
	}

}
