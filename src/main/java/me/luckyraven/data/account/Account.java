package me.luckyraven.data.account;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class Account<K, V> {

	private final Map<K, V> account;
	private       K         key;
	private       V         value;

	public Account(K key, V value) {
		this.key = key;
		this.value = value;
		this.account = new HashMap<>();
	}

	public boolean hasAccount(K key) {
		return account.containsKey(key);
	}

}
