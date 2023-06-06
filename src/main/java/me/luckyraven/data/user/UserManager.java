package me.luckyraven.data.user;

import com.google.common.base.Preconditions;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class UserManager<T> {

	private final Set<User<T>> users;

	public UserManager() {
		users = new HashSet<>();
	}

	public void add(User<T> user) {
		users.add(user);
	}

	public void remove(@NotNull User<T> user) {
		Preconditions.checkArgument(user != null, "User can't be null!");

		users.remove(user);
	}

	public boolean contains(User<T> user) {
		return users.contains(user);
	}

	public User<T> getUser(T userPred) {
		for (User<T> user : users) if (user.getUser().equals(userPred)) return user;
		return null;
	}

	public int size() {
		return users.size();
	}

	/**
	 * Creates a new instance of similar data set
	 *
	 * @return new HashSet of users
	 */
	public Set<User<?>> getUsers() {
		return new HashSet<>(users);
	}

}
