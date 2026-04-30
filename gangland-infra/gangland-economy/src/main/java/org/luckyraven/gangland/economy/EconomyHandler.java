package org.luckyraven.gangland.economy;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.economy.exception.EconomyException;

import java.math.BigDecimal;

/**
 * Canonical balance holder. Stores the balance as a {@link BigDecimal} at {@link Currency#SCALE} precision so huge
 * values round-trip without {@code double} precision loss. The only public money API is {@link #getAmount()} /
 * {@link #setAmount(BigDecimal)} / {@link #depositAmount(BigDecimal)} / {@link #withdrawAmount(BigDecimal)}.
 *
 * <p>Vault sync: when enabled, reads widen the Vault double balance to BigDecimal; writes downcast to
 * {@code double}. Beyond {@code 2^53} Vault itself loses precision — that's Vault's ceiling, not ours.
 */
@Getter
@Setter
public class EconomyHandler {

	@Getter
	@Setter
	private static Economy vaultEconomy;

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final boolean useUser;

	@Nullable
	@Setter
	private EconomyOwner user;

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private BigDecimal amount;

	public EconomyHandler(@Nullable EconomyOwner user) {
		this(Currency.ZERO, user, user != null);
	}

	public EconomyHandler(BigDecimal amount, @Nullable EconomyOwner user, boolean useUser) {
		this.amount  = Currency.of(amount);
		this.user    = user;
		this.useUser = useUser;
	}

	public BigDecimal getAmount() {
		if (useUserInfo() && vaultEconomy != null) {
			return Currency.of(vaultEconomy.getBalance(user.getUser()));
		}
		return amount;
	}

	public void setAmount(BigDecimal newAmount) {
		this.amount = Currency.of(newAmount);

		if (!(useUserInfo() && vaultEconomy != null)) return;

		OfflinePlayer offlinePlayer = user.getUser();
		vaultEconomy.withdrawPlayer(offlinePlayer, vaultEconomy.getBalance(offlinePlayer));
		vaultEconomy.depositPlayer(offlinePlayer, this.amount.doubleValue());
	}

	public void depositAmount(BigDecimal delta) {
		setAmount(getAmount().add(Currency.of(delta)));
	}

	public void withdrawAmount(BigDecimal delta) throws EconomyException {
		BigDecimal current    = getAmount();
		BigDecimal normalised = Currency.of(delta);
		if (normalised.compareTo(current) > 0) throw new EconomyException("Amount exceeding balance");
		setAmount(current.subtract(normalised));
	}

	private boolean useUserInfo() {
		return user != null && useUser;
	}

}
