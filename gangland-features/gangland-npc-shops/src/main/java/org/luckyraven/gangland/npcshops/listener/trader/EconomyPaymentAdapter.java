package org.luckyraven.gangland.npcshops.listener.trader;

import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.keystone.shop.transaction.PaymentException;
import org.luckyraven.keystone.shop.transaction.PaymentHandler;

import java.math.BigDecimal;

/** Exposes a user's {@link EconomyHandler} as the {@link PaymentHandler} the shop services expect. */
final class EconomyPaymentAdapter {

	private EconomyPaymentAdapter() {
	}

	static PaymentHandler adapt(EconomyHandler economy) {
		return new PaymentHandler() {
			@Override
			public BigDecimal getBalance() {
				return economy.getAmount();
			}

			@Override
			public void withdraw(BigDecimal amount) throws PaymentException {
				try {
					economy.withdrawAmount(amount);
				} catch (EconomyException e) {
					throw new PaymentException(e.getMessage(), e);
				}
			}

			@Override
			public void deposit(BigDecimal amount) throws PaymentException {
				try {
					economy.depositAmount(amount);
				} catch (EconomyException e) {
					throw new PaymentException(e.getMessage(), e);
				}
			}
		};
	}
}
