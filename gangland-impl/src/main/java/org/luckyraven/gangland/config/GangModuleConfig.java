package org.luckyraven.gangland.config;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.file.configuration.gang.*;
import org.luckyraven.gangland.file.configuration.identity.GanglandIdentitySettings;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.contract.*;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;

/**
 * Wires gangland-impl's {@code Settings}, {@code Messages}, {@link VaultPermissionBridge}, {@link PermissionManager},
 * and repository implementations to the gang module's contract interfaces so the gang module never imports them
 * directly.
 */
@Configuration
public final class GangModuleConfig {

	/**
	 * Split out of the old 14-getter {@code gangSettingsContract()} bean (WS5 G0, B2): the identity slice
	 * ({@code User}/{@code Level}, gangland-core) needs its 11 getters bound before either data class is
	 * constructed, same as {@link #gangSettingsContract()} does for the gang-only remainder.
	 */
	@Bean
	public IdentitySettingsContract identitySettingsContract() {
		IdentitySettingsContract contract = new GanglandIdentitySettings();
		IdentitySettings.bind(contract);
		return contract;
	}

	@Bean
	public GangSettingsContract gangSettingsContract() {
		GangSettingsContract contract = new GanglandGangSettings();
		// Bind the static facade so gang-module data classes (Gang) can
		// read settings from their constructors without injecting the contract.
		GangSettings.bind(contract);
		return contract;
	}

	@Bean
	public GangMessageContract gangMessageContract() {
		return new GanglandGangMessages();
	}

	@Bean
	public GangPermissionBridgeContract gangPermissionBridgeContract() {
		return new GanglandGangPermissionBridge();
	}

	@Bean
	public GangLookupContract gangLookupContract(GangManager gangManager) {
		// GangManager implements GangLookupContract directly — return it as the contract.
		return gangManager;
	}

	@Bean
	public UserLookupContract userLookupContract(@Qualifier("online") UserManager<Player> userManager) {
		return new GanglandUserLookup(userManager);
	}

	@Bean
	public RankLookupContract rankLookupContract(RankManager rankManager) {
		return new GanglandRankLookup(rankManager);
	}

	@Bean
	public PermissionRegistryContract permissionRegistryContract(PermissionManager permissionManager) {
		return new GanglandPermissionRegistry(permissionManager);
	}

	@Bean
	public GangAllianceRepositoryContract gangAllianceRepositoryContract(RepositoryRegistry registry) {
		return (GangAllianceRepositoryContract) registry.getRepository(GangAlliance.class);
	}

	@Bean
	public MemberRepositoryContract memberRepositoryContract(RepositoryRegistry registry) {
		return (MemberRepositoryContract) registry.getRepository(Member.class);
	}
}
