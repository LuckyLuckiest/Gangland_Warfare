package org.luckyraven.gangland.shop.support;

import org.luckyraven.gangland.shop.transaction.PaymentException;
import org.luckyraven.gangland.shop.transaction.PaymentHandler;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory {@link PaymentHandler} test double. Records every {@link #withdraw}/{@link #deposit} call (in order) so
 * a test can assert exactly what money moved, and can be told to throw {@link PaymentException} on demand to pin the
 * {@code ECONOMY_ERROR} branches of {@code ShopPurchaseService} / {@code ShopSellService}.
 *
 * <p>Per {@code documentation/TESTING.md} §6 ("prefer fakes over deep mock chains"), this stands in for
 * {@code mock(PaymentHandler.class)} — {@link PaymentHandler} is a tiny interface and a hand-rolled fake makes the
 * money-movement assertions read as plain data rather than a Mockito {@code verify} chain.
 */
public final class FakePaymentHandler implements PaymentHandler {

	public final List<BigDecimal> withdrawals = new ArrayList<>();
	public final List<BigDecimal> deposits    = new ArrayList<>();

	private BigDecimal balance;
	private boolean    throwOnWithdraw;
	private boolean    throwOnDeposit;
	private String     failureMessage = "payment failed";

	public FakePaymentHandler(BigDecimal balance) {
		this.balance = balance;
	}

	public void throwOnWithdraw(String message) {
		this.throwOnWithdraw = true;
		this.failureMessage  = message;
	}

	public void throwOnDeposit(String message) {
		this.throwOnDeposit = true;
		this.failureMessage = message;
	}

	@Override
	public BigDecimal getBalance() {
		return balance;
	}

	@Override
	public void withdraw(BigDecimal amount) throws PaymentException {
		if (throwOnWithdraw) {
			throw new PaymentException(failureMessage);
		}
		withdrawals.add(amount);
		balance = balance.subtract(amount);
	}

	@Override
	public void deposit(BigDecimal amount) throws PaymentException {
		if (throwOnDeposit) {
			throw new PaymentException(failureMessage);
		}
		deposits.add(amount);
		balance = balance.add(amount);
	}

}
