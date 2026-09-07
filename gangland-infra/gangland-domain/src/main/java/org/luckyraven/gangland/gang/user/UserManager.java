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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class UserManager<T extends OfflinePlayer> implements BeanLifecycle {

	private final JavaPlugin          gangland;
	private final RepositoryRegistry  repositoryRegistry;
	private final UserFactory         userFactory;
	/**
	 * Keyed by {@link UUID}, never by the Bukkit handle. {@code CraftEntity.equals}/{@code hashCode} compare the
	 * entity id while {@code CraftOfflinePlayer} compares the uuid, so a {@code Player}-keyed entry could never be
	 * found through an {@code OfflinePlayer} handle (or a fresh {@code Player} after a rejoin) and the quit-time
	 * snapshot survived the join eviction, overwriting the live row on the next autosave.
	 */
	private final Map<UUID, User<T>>  users;

	/**
	 * Every manager whose cache must be persisted together with this one — always contains {@code this}, and grows
	 * through {@link #link(UserManager)}. The {@code online} and {@code offline} beans share the <b>same</b>
	 * {@code UserRepository} and {@code BankRepository} instances, so whichever calls {@link #initialize()} last
	 * overwrites the other's data supplier and {@code RepositoryRegistry.saveAll()} would persist only one of the two
	 * caches (CL-23). Supplying the union of the linked caches makes that overwrite harmless.
	 */
	private Set<UserManager<? extends OfflinePlayer>> cacheGroup;

	public UserManager(JavaPlugin gangland,
	                   RepositoryRegistry repositoryRegistry,
	                   UserFactory userFactory) {
		this.gangland           = gangland;
		this.repositoryRegistry = repositoryRegistry;
		this.userFactory        = userFactory;
		this.users              = new HashMap<>();
		this.cacheGroup         = Collections.newSetFromMap(new IdentityHashMap<>());

		this.cacheGroup.add(this);
	}

	/**
	 * Joins {@code other}'s cache to this manager's persistence group, symmetrically: after the call both managers
	 * share one group containing both, so {@link #initialize()} on either registers a data supplier that sees every
	 * cached user regardless of which bean initialised last.
	 *
	 * @param other the sibling manager to persist alongside this one; {@code null} and {@code this} are ignored
	 */
	public void link(UserManager<? extends OfflinePlayer> other) {
		if (other == null || other == this) return;

		this.cacheGroup.addAll(other.cacheGroup);

		for (UserManager<? extends OfflinePlayer> manager : this.cacheGroup) {
			manager.cacheGroup = this.cacheGroup;
		}
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

		userRepository.setDataSupplier(this::groupedUsers);
		bankRepository.setDataSupplier(this::groupedBanks);
	}

	/**
	 * @return every cached user across this manager's whole {@link #link(UserManager) linked} group.
	 */
	private List<User<? extends OfflinePlayer>> groupedUsers() {
		List<User<? extends OfflinePlayer>> grouped = new ArrayList<>();

		for (UserManager<? extends OfflinePlayer> manager : cacheGroup) {
			grouped.addAll(manager.users.values());
		}

		return grouped;
	}

	/**
	 * @return every bank held by a cached user across this manager's whole linked group.
	 */
	private List<Bank> groupedBanks() {
		List<Bank> grouped = new ArrayList<>();

		for (UserManager<? extends OfflinePlayer> manager : cacheGroup) {
			for (User<? extends OfflinePlayer> user : manager.users.values()) {
				if (!user.hasBank()) continue;

				grouped.add(user.getBank());
			}
		}

		return grouped;
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
		users.put(user.getUuid(), user);
	}

	public void remove(@NotNull User<T> user) {
		Preconditions.checkArgument(user != null, "User can't be null!");

		users.remove(user.getUuid());
	}

	/**
	 * Evicts the cached user for {@code uuid}, whichever Bukkit handle flavour it was added with.
	 *
	 * @return the evicted user, or {@code null} when nothing was cached for that uuid.
	 */
	@Nullable
	public User<T> remove(UUID uuid) {
		if (uuid == null) return null;

		return users.remove(uuid);
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
		return users.containsKey(user.getUuid());
	}

	@Nullable
	public User<T> getUser(T userPred) {
		if (userPred == null) return null;

		return users.get(userPred.getUniqueId());
	}

	/**
	 * Uuid-based lookup. Prefer this over the handle-based overload when the caller only holds an id.
	 */
	@Nullable
	public User<T> getUser(UUID uuid) {
		if (uuid == null) return null;

		return users.get(uuid);
	}

	public int size() {
		return users.size();
	}

	/**
	 * @return unmodifiable view of the cached users map.
	 */
	public Map<UUID, User<T>> getUsers() {
		return Collections.unmodifiableMap(users);
	}

	@Override
	public String toString() {
		Map<UUID, User<T>> userMap = users;
		List<String> users = userMap.values()
				.stream().map(User::toString).toList();
		return "users=" + users;
	}

}
