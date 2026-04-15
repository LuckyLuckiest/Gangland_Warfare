package me.luckyraven.shop.transaction;

/**
 * Thrown by {@link PaymentHandler#withdraw} when a withdrawal cannot be executed. Integrations wrap their native
 * economy exceptions (e.g. {@code EconomyException}) in this type so {@link ShopPurchaseService} can stay
 * economy-agnostic.
 */
public class PaymentException extends Exception {

	public PaymentException(String message) {
		super(message);
	}

	public PaymentException(String message, Throwable cause) {
		super(message, cause);
	}

}
