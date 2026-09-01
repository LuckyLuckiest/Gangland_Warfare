package org.luckyraven.gangland.gang.user;

import com.google.common.base.Preconditions;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Permission;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserManager<T extends OfflinePlayer> implements BeanLifecycle {

	private final JavaPlugin         gangland;
	private final RepositoryRegistry repositoryRegistry;
	private final UserFactory        userFactory;
	private final Map<T, User<T>>    users;

	public UserManager(JavaPlugin gangland,
	                   RepositoryRegistry repositoryRegistry,
	                   UserFactory userFactory) {
		this.gangland           = gangland;
		this.repositoryRegistry = repositoryRegistry;
		this.userFactory        = userFactory;
		this.users              = new HashMap<>();
	}

	/**
	 * Constructs a new {@link User} for the given player with all dependencies wired by {@link UserFactory}. Callers
	 * that need to also cache the user should follow up with {@link #add(User)}.
	 */
	public User<T> create(T player) {
		return userFactory.create(player);
	}

	public void initialize() {
		IRepository<User<? extends OfflinePlayer>> userRepository = repositoryRegistry.getGenericRepository(User.class);
		IRepository<Bank>                          bankRepository = repositoryRegistry.getRepository(Bank.class);

		userRepository.setDataSupplier(() -> users.values()
				.stream().<User<? extends OfflinePlayer>>map(u -> u).toList());

		bankRepository.setDataSupplier(() -> users.values()
				.stream().filter(User::hasBank).map(User::getBank).toList());
	}

	public void initializeUserPermission(User<Player> user, Member member) {
		Rank rank = member.getRank();

		if (rank == null) return;

		// attach all the permissions when the user has the specified rank
		PermissionAttachment attachment = user.getUser().addAttachment(gangland);

		user.setPermissionAttachment(attachment);

		for (Permission perm : rank.getPermissions())
			attachment.setPermission(perm.getPermission(), true);

		// apparently updates the command list according to the permission list
		user.getUser().updateCommands();
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

	@Override
	public void onPreClear() {
		for (User<T> user : users.values()) {
			user.getWanted().stopTimer();
			user.getBounty().stopTimer();

			if (user.getScoreboard() == null) continue;

			user.getScoreboard().end();
			user.setScoreboard(null);
		}
	}

	@Override
	public void onClear() {
		clear();
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		initialize();
	}

	public boolean contains(User<T> user) {
		if (user == null) return false;
		return users.containsKey(user.getUser());
	}

	@Nullable
	public User<T> getUser(T userPred) {
		return users.get(userPred);
	}

	public int size() {
		return users.size();
	}

	/**
	 * @return unmodifiable view of the cached users map.
	 */
	public Map<T, User<T>> getUsers() {
		return Collections.unmodifiableMap(users);
	}

	@Override
	public String toString() {
		Map<T, User<T>> userMap = users;
		List<String> users = userMap.values()
				.stream().map(User::toString).toList();
		return "users=" + users;
	}

}
