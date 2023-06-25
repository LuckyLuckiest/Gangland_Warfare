package me.luckyraven.account;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class Account<K, V> {

	@Getter
	private final K         key;
	@Getter
	private final V         value;
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
