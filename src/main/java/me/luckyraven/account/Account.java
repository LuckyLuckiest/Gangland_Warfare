package me.luckyraven.account;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

public class Account<K, V> {

	@Getter
	@Setter
	private       K         key;
	@Getter
	@Setter
	private       V         value;
	@Getter
	private final Map<K, V> account;

	public Account(K key, V value) {
		this.key = key;
		this.value = value;
		this.account = new HashMap<>();
	}

	public boolean hasAccount(K key) {
		return account.containsKey(key);
	}

}
