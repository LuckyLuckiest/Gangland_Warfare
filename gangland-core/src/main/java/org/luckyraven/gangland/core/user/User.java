package org.luckyraven.gangland.core.user;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.util.Placeholder;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.EconomyOwner;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.bounty.BountyContext;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedContext;

import java.math.BigDecimal;
import java.util.*;

/**
 * Handles all users registered data, only for online users.
 *
 * @param <T> type of the user
 */
@Getter
@Setter
public class User<T extends OfflinePlayer> implements BountyContext, WantedContext, EconomyOwner {

	private final T                     user;
	private final UUID                  uuid;
	private final Bounty                bounty;
	private final Level                 level;
	private final Wanted                wanted;
	private final EconomyHandler        economy;
	private final Placeholder           placeholder;

	@Nullable
	private Bank bank;
	private int  kills, deaths, mobKills, gangId;
	private PermissionAttachment permissionAttachment;

	/**
	 * Instantiates a new User. Prefer constructing through {@link UserFactory} so the {@link Placeholder}
	 * dependency comes from the bean container.
	 */
	public User(JavaPlugin plugin, T user, Placeholder placeholder) {
		this.user              = user;
		this.uuid              = user.getUniqueId();
		this.bounty            = new Bounty(IdentitySettings.getBountyEachKillValue(),
		                                    IdentitySettings.getBountyTimerMultiple());
		this.level             = new Level();
		this.wanted            = new Wanted(plugin, IdentitySettings.getWantedLevelIncrement(),
		                                    IdentitySettings.getWantedMaximumLevel());
		this.economy           = new EconomyHandler(this);
		this.placeholder       = placeholder;

		this.wanted.setOwner(user.getPlayer());

		this.kills  = this.deaths = this.mobKills = 0;
		this.gangId = -1;
		this.bank   = null;
	}

	/**
	 * Has bank boolean.
	 *
	 * @return the boolean
	 */
	public boolean hasBank() {
		return this.bank != null;
	}

	/**
	 * Reset gang.
	 */
	public void resetGang() {
		this.gangId = -1;
	}

	/**
	 * Has gang boolean.
	 *
	 * @return the boolean
	 */
	public boolean hasGang() {
		return this.gangId != -1;
	}

	@Override
	public int getUserLevel() {
		return level.getLevelValue();
	}

	@Override
	public BigDecimal withdraw(BigDecimal requestedAmount) {
		BigDecimal normalised = Currency.of(requestedAmount);
		try {
			economy.withdrawAmount(normalised);
			return normalised;
		} catch (EconomyException ignored) {
			BigDecimal balance = economy.getAmount();
			try {
				economy.withdrawAmount(balance);
			} catch (EconomyException ignored2) {
				return Currency.ZERO;
			}
			return balance;
		}
	}

	public void sendMessage(String text) {
		if (!(user instanceof Player player)) return;

		String resolved = placeholder.convert(player, text);
		String message  = ChatUtil.color(resolved);

		player.sendMessage(message);
	}

	public void sendMessage(String... texts) {
		for (String text : texts) sendMessage(text);
	}

	/**
	 * Gets a kills/deaths ratio of the user.
	 *
	 * @return the kd ratio
	 */
	public double getKillDeathRatio() {
		return deaths == 0 ? 0D : (double) kills / deaths;
	}

	/**
	 * Every permission node currently granted through {@link #getPermissionAttachment()}. Narrow, rank-free
	 * replacement for the deleted {@code flushPermissions(Rank)} (WS5 G0, B2) — a caller that needs rank-shaped
	 * permission logic builds it from this plus {@link #setPermission(String, boolean)} (the gang module's
	 * {@code RankPermissionApplier}, from G2 onward).
	 *
	 * @return an empty list when no attachment has been set yet (offline users, or before permissions are applied)
	 */
	public List<String> grantedPermissionNames() {
		if (permissionAttachment == null) return List.of();
		return new ArrayList<>(permissionAttachment.getPermissions().keySet());
	}

	/**
	 * Grants or revokes a single permission node on this user's live {@link PermissionAttachment}. A no-op when the
	 * attachment hasn't been set yet.
	 */
	public void setPermission(String permission, boolean value) {
		if (permissionAttachment == null) return;
		permissionAttachment.setPermission(permission, value);
	}

	/**
	 * Clears any override this attachment holds for {@code permission}, letting it fall back to whatever a
	 * group/Vault/LuckPerms grant would otherwise resolve to. Narrow, rank-free mirror of the exact call the
	 * deleted {@code flushPermissions(Rank)} made to clear every node before re-applying a rank (WS5 G0 fix
	 * round 1, F1) — {@link #setPermission(String, boolean)} with {@code false} is <b>not</b> equivalent: an
	 * explicit {@code false} is an override that beats a group grant, where an unset node defers to it, which is
	 * exactly what {@code VaultPermissionBridge} relies on. A no-op when the attachment hasn't been set yet.
	 */
	public void unsetPermission(String permission) {
		if (permissionAttachment == null) return;
		permissionAttachment.unsetPermission(permission);
	}

	/**
	 * Refreshes this user's client-side command list, if online. No-op for an offline/non-{@link Player} user.
	 */
	public void updateCommands() {
		if (user instanceof Player player) player.updateCommands();
	}

	@Override
	public String toString() {
		return String.format("User{data=%s,kd=%.2f,balance=%s,level=%d,bounty=%s,gangId=%d,permissions=%s}", user,
		                     getKillDeathRatio(), economy.getAmount().toPlainString(), level.getLevelValue(),
		                     bounty.getAmount().toPlainString(),
		                     gangId, permissionAttachment != null ?
		                             permissionAttachment.getPermissions().keySet()
									 .stream().toList() :
		                             "NA");
	}

}
