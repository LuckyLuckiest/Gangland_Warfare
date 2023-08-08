package me.luckyraven.data.user;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserManager<T> {

	private final Map<T, User<T>> users;

	public UserManager() {
		users = new HashMap<>();
	}

	public void add(User<T> user) {
		users.put(user.getUser(), user);
	}

	public void remove(@NotNull User<T> user) {
		Preconditions.checkArgument(user != null, "User can't be null!");

		users.remove(user.getUser());
	}

	public void clear() {
		users.clear();
	}

	public boolean contains(User<T> user) {
		if (user == null) return false;
		return users.containsKey(user.getUser());
	}

	public User<T> getUser(T userPred) {
		return users.get(userPred);
	}

	public int size() {
		return users.size();
	}

	/**
	 * Creates a new instance of similar data set
	 *
	 * @return new HashSet of users
	 */
	public Map<T, User<T>> getUsers() {
		return new HashMap<>(users);
	}

	@Override
	public String toString() {
		Map<T, User<T>> userMap = users;
		List<String>    users   = userMap.values().stream().map(User::toString).toList();
		return "users=" + users;
	}

}
