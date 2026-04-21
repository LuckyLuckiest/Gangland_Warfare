package me.luckyraven.shop.transaction;

import java.math.BigDecimal;

/**
 * Minimal payment abstraction used by {@link ShopPurchaseService}. Integrations adapt this to whatever economy system
 * they run on top of (e.g. {@code EconomyHandler} in gangland-impl) — the shop-api itself stays economy-agnostic.
 */
public interface PaymentHandler {

	BigDecimal getBalance();

	void withdraw(BigDecimal amount) throws PaymentException;

	void deposit(BigDecimal amount) throws PaymentException;

}
