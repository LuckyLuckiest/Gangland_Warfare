package me.luckyraven.shop.transaction;

/**
 * Minimal payment abstraction used by {@link ShopPurchaseService}. Integrations adapt this to whatever economy system
 * they run on top of (e.g. {@code EconomyHandler} in gangland-impl) — the shop-api itself stays economy-agnostic.
 */
public interface PaymentHandler {

	double getBalance();

	void withdraw(double amount) throws PaymentException;

	void deposit(double amount) throws PaymentException;

}
