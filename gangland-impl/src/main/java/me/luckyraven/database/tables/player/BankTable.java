package me.luckyraven.database.tables.player;

import me.luckyraven.data.account.user.User;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;
import org.bukkit.OfflinePlayer;

import java.sql.Types;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class BankTable extends Table<Bank> {

	public BankTable(UserTable userTable) {
		super("bank");

		Attribute<UUID>   uuid           = new Attribute<>("uuid", true, UUID.class);
		Attribute<String> name           = new Attribute<>("name", false, String.class);
		Attribute<Double> balance        = new Attribute<>("balance", false, Double.class);
		Attribute<String> tierId         = new Attribute<>("tier_id", false, String.class);
		Attribute<Double> depositedToday = new Attribute<>("deposited_today", false, Double.class);
		Attribute<Double> withdrawnToday = new Attribute<>("withdrawn_today", false, Double.class);
		Attribute<String> capResetAt     = new Attribute<>("cap_reset_at", false, String.class);

		balance.setDefaultValue(0D);
		depositedToday.setDefaultValue(0D);
		withdrawnToday.setDefaultValue(0D);
		tierId.setCanBeNull(true);
		capResetAt.setCanBeNull(true);

		uuid.setForeignKey(userTable.get("uuid"), userTable);

		this.addAttribute(uuid);
		this.addAttribute(name);
		this.addAttribute(balance);
		this.addAttribute(tierId);
		this.addAttribute(depositedToday);
		this.addAttribute(withdrawnToday);
		this.addAttribute(capResetAt);
	}

	@Override
	public Object[] getData(Bank data) {
		Instant reset = data.getCapResetAt();
		return new Object[]{
				data.getUuid().toString(),
				data.getName(),
				data.getEconomy().getBalance(),
				data.getTierId(),
				data.getDepositedToday(),
				data.getWithdrawnToday(),
				reset == null ? null : reset.toString()
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
