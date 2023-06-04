package me.luckyraven.data.account.types;

import me.luckyraven.data.User;
import me.luckyraven.data.account.Account;

import java.util.UUID;

public class Bank extends Account<UUID, User<?>> {

	public Bank(UUID uuid, User<?> user) {
		super(uuid, user);
	}

}
