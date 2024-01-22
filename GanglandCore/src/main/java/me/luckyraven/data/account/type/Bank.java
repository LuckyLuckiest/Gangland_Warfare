package me.luckyraven.data.account.type;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.account.Account;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.data.user.User;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

@Getter
@Setter
public class Bank extends Account<UUID, User<? extends OfflinePlayer>> {

	private final EconomyHandler economy;

	private String name;

	public Bank(User<? extends OfflinePlayer> user, String name) {
		super(user.getUser().getUniqueId(), user);
		this.name    = name;
		this.economy = new EconomyHandler(0D, user, false);
	}

	@Override
	public String toString() {
		return String.format("Bank{uuid=%s,name=%s,balance=%.2f}", getKey(), name, economy.getBalance());
	}

}
