package me.luckyraven.database.tables.player;

import me.luckyraven.data.account.user.User;
import me.luckyraven.market.bank.Bank;
import me.luckyraven.persistence.database.component.Attribute;
import me.luckyraven.persistence.database.component.Table;
import org.bukkit.OfflinePlayer;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

public class BankTable extends Table<Bank> {

	public BankTable(UserTable userTable) {
		super("bank");

		Attribute<UUID>   uuid    = new Attribute<>("uuid", true, UUID.class);
		Attribute<String> name    = new Attribute<>("name", false, String.class);
		Attribute<Double> balance = new Attribute<>("balance", false, Double.class);

		balance.setDefaultValue(0D);

		uuid.setForeignKey(userTable.get("uuid"), userTable);

		this.addAttribute(uuid);
		this.addAttribute(name);
		this.addAttribute(balance);
	}

	@Override
	public Object[] getData(Bank data) {
		return new Object[]{data.getUuid().toString(), data.getName(), data.getEconomy().getBalance()};
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
