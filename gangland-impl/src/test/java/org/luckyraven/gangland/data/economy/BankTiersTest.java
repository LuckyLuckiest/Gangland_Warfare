package org.luckyraven.gangland.data.economy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.economy.bank.Bank;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Pins the seam-2 holder's inert-by-default contract: {@code /glw bank}, the death penalty and the bank
 * placeholders always construct against {@link BankTiers}, whether or not the cops-n-crooks module (the Banker NPC
 * catalogue) is installed. See documentation/module-loader.md, "Core seams".
 */
@DisplayName("BankTiers")
class BankTiersTest {

	@Test
	@DisplayName("tierFor returns null before install")
	void tierFor_beforeInstall_returnsNull() {
		BankTiers tiers = new BankTiers();

		assertNull(tiers.tierFor(mock(Bank.class)));
	}

	@Test
	@DisplayName("tierFor returns the installed function's result after install")
	void tierFor_afterInstall_returnsFunctionResult() {
		BankTiers    tiers = new BankTiers();
		Bank         bank  = mock(Bank.class);
		BankTierView view  = mock(BankTierView.class);

		tiers.install(b -> b == bank ? view : null);

		assertSame(view, tiers.tierFor(bank));
	}
}
