package me.luckyraven.account.type;

import me.luckyraven.data.user.User;
import me.luckyraven.account.Account;

import java.util.UUID;

public class Bank extends Account<UUID, User<?>> {

	public Bank(UUID uuid, User<?> user) {
		super(uuid, user);
	}

}
