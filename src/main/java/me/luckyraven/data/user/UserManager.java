package me.luckyraven.data.user;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class UserManager {

	private final Set<User<?>> users;

	public UserManager() {
		users = new HashSet<>();
	}

	public void add(User<?> user) {
		users.add(user);
	}

	public void remove(User<?> user) {
		users.remove(user);
	}

	public boolean contains(User<?> user) {
		return users.contains(user);
	}

	public User<?> getUser(Predicate<User<?>> predicate) {
		for (User<?> user : users) if (predicate.test(user)) return user;
		return null;
	}

	public int size() {
		return users.size();
	}

}
