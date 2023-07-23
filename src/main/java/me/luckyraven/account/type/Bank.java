package me.luckyraven.account.type;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.account.Account;
import me.luckyraven.data.user.User;

import java.util.UUID;

@Getter
@Setter
public class Bank extends Account<UUID, User<?>> {

	private String name;
	private double balance;

	public Bank(UUID uuid, User<?> user, String name) {
		super(uuid, user);
		this.name = name;
		this.balance = 0D;
	}

	@Override
	public String toString() {
		return String.format("uuid=%s,name=%s,balance=%.2f", getKey(), name, balance);
	}

}
