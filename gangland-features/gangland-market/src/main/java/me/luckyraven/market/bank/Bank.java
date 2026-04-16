package me.luckyraven.market.bank;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class Bank {

	private final UUID           uuid;
	private final EconomyHandler economy;

	private String name;

	public Bank(UUID uuid, String name) {
		this.uuid    = uuid;
		this.name    = name;
		this.economy = new EconomyHandler(0D, null, false);
	}

	@Override
	public String toString() {
		return String.format("Bank{uuid=%s,name=%s,balance=%.2f}", uuid.toString(), name, economy.getBalance());
	}

}
