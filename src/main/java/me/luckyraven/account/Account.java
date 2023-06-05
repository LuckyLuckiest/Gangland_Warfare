package me.luckyraven.account;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class Account<K, V> {

	private @Getter
	final K key;

	private @Getter
	final V value;

	private @Getter
	final Map<K, V> account = new HashMap<>();

	public Account(K key, V value) {
		this.key = key;
		this.value = value;
	}

	public boolean hasAccount(K key) {
		return account.containsKey(key);
	}

}
