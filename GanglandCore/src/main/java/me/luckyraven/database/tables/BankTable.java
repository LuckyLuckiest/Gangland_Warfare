package me.luckyraven.database.tables;

import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;
import org.bukkit.OfflinePlayer;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

public class BankTable extends Table<User<? extends OfflinePlayer>> {

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
	public Object[] getData(User<? extends OfflinePlayer> data) {
		Bank bank = Bank.getInstance(data);

		if (bank == null) return new Object[]{ };

		return new Object[]{data.getUser().getUniqueId().toString(), bank.getName(), bank.getEconomy().getBalance()};
	}

	@Override
	public Map<String, Object> searchCriteria(User<? extends OfflinePlayer> data) {
		return createSearchCriteria("uuid = ?", new Object[]{data.getUser().getUniqueId().toString()},
									new int[]{Types.CHAR}, new int[]{0});
	}
}
