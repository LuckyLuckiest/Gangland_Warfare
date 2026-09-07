package org.luckyraven.gangland.data.economy;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.economy.bank.Bank;

import java.util.function.Function;

/**
 * Seam 2 holder: the bank tier catalogue. Always present as a core bean so {@code /glw bank}, the death penalty and
 * the bank placeholders always construct; inert (no caps, no daily limit, no insurance discount, empty tier
 * placeholders) until the cops-n-crooks module installs a lookup from its {@code @PostConstruct}. See
 * documentation/module-loader.md, "Core seams".
 */
public final class BankTiers {

	private volatile Function<Bank, BankTierView> lookup;

	public void install(Function<Bank, BankTierView> lookup) {
		this.lookup = lookup;
	}

	/**
	 * @return the tier for {@code bank}, or {@code null} when no tier catalogue is installed.
	 */
	@Nullable
	public BankTierView tierFor(Bank bank) {
		Function<Bank, BankTierView> current = this.lookup;
		if (current == null || bank == null) return null;
		return current.apply(bank);
	}
}
