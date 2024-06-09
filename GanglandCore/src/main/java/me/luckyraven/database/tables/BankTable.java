package me.luckyraven.database.tables;

import me.luckyraven.data.account.Account;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.user.User;
import me.luckyraven.database.component.Attribute;
import me.luckyraven.database.component.Table;
import org.bukkit.OfflinePlayer;

import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BankTable extends Table<User<? extends OfflinePlayer>> {

	public BankTable(UserTable userTable) {
		super("bank");

		Attribute<UUID>   uuid    = new Attribute<>("uuid", true);
		Attribute<String> name    = new Attribute<>("name", false);
		Attribute<Double> balance = new Attribute<>("balance", false);

		balance.setDefaultValue(0D);

		uuid.setForeignKey(userTable.get("uuid"), userTable);

		this.addAttribute(uuid);
		this.addAttribute(name);
		this.addAttribute(balance);
	}

	@Override
	public Object[] getData(User<? extends OfflinePlayer> data) {
		for (Account<?, ?> account : data.getLinkedAccounts())
			if (account instanceof Bank bank)
				return new Object[]{data.getUser().getUniqueId(), bank.getName(), bank.getEconomy().getBalance()};

		return null;
	}

	@Override
	public Map<String, Object> searchCriteria(User<? extends OfflinePlayer> data) {
		Map<String, Object> search = new HashMap<>();

		search.put("search", "uuid = ?");
		search.put("info", new Object[]{data.getUser().getUniqueId()});
		search.put("type", new Object[]{Types.CHAR});
		search.put("index", new int[]{0});

		return Collections.unmodifiableMap(search);
	}
}
