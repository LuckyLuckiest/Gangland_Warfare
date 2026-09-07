package org.luckyraven.gangland.database.tables.player;

import org.bukkit.OfflinePlayer;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class BankTable extends Table<Bank> {

	public BankTable(UserTable userTable) {
		super("bank");

		Attribute<UUID>   uuid            = new Attribute<>("uuid", true, UUID.class);
		Attribute<String> name            = new Attribute<>("name", false, String.class);
		Attribute<String> balance         = new Attribute<>("balance", false, String.class);
		Attribute<String> tierId          = new Attribute<>("tier_id", false, String.class);
		Attribute<Double> depositedToday  = new Attribute<>("deposited_today", false, Double.class);
		Attribute<String> capResetAt      = new Attribute<>("cap_reset_at", false, String.class);
		Attribute<String> lastInterestAt  = new Attribute<>("last_interest_at", false, String.class);
		Attribute<String> lastWeeklyLoan  = new Attribute<>("last_weekly_loan_at", false, String.class);
		Attribute<String> lastMonthlyLoan = new Attribute<>("last_monthly_loan_at", false, String.class);

		balance.setDefaultValue(Currency.ZERO.toPlainString());
		depositedToday.setDefaultValue(0D);
		tierId.setCanBeNull(true);
		capResetAt.setCanBeNull(true);
		lastInterestAt.setCanBeNull(true);
		lastWeeklyLoan.setCanBeNull(true);
		lastMonthlyLoan.setCanBeNull(true);

		uuid.setForeignKey(userTable.get("uuid"), userTable);

		this.addAttribute(uuid);
		this.addAttribute(name);
		this.addAttribute(balance);
		this.addAttribute(tierId);
		this.addAttribute(depositedToday);
		this.addAttribute(capResetAt);
		this.addAttribute(lastInterestAt);
		this.addAttribute(lastWeeklyLoan);
		this.addAttribute(lastMonthlyLoan);
	}

	@Override
	public Object[] getData(Bank data) {
		Instant reset           = data.getCapResetAt();
		Instant lastInterestAt  = data.getLastInterestAt();
		Instant lastWeeklyLoan  = data.getLastWeeklyLoanAt();
		Instant lastMonthlyLoan = data.getLastMonthlyLoanAt();
		return new Object[]{
				data.getUuid().toString(),
				data.getName(),
				Currency.plainString(data.getEconomy().getAmount()),
				data.getTierId(),
				data.getDepositedToday(),
				reset == null ? null : reset.toString(),
				lastInterestAt == null ? null : lastInterestAt.toString(),
				lastWeeklyLoan == null ? null : lastWeeklyLoan.toString(),
				lastMonthlyLoan == null ? null : lastMonthlyLoan.toString()
		};
	}

	@Override
	public Map<String, Object> searchCriteria(Bank data) {
		return createSearchCriteria("uuid = ?", new Object[]{data.getUuid().toString()}, new int[]{Types.CHAR},
		                            new int[]{0});
	}

	public Map<String, Object> searchCriteria(User<? extends OfflinePlayer> user) {
		return createSearchCriteria("uuid = ?", new Object[]{user.getUuid().toString()}, new int[]{Types.CHAR},
		                            new int[]{0});
	}
}
