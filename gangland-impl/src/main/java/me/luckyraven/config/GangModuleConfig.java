package me.luckyraven.config;

import me.luckyraven.core.bean.Bean;
import me.luckyraven.core.bean.Configuration;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.data.permission.PermissionManager;
import me.luckyraven.file.configuration.gang.*;
import me.luckyraven.gang.GangAlliance;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.GangSettings;
import me.luckyraven.gang.contract.*;
import me.luckyraven.gang.member.Member;
import me.luckyraven.gang.rank.RankManager;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.gang.vault.permission.VaultPermissionBridge;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import org.bukkit.entity.Player;

/**
 * Wires gangland-impl's {@code Settings}, {@code Messages}, {@link VaultPermissionBridge}, {@link PermissionManager},
 * and repository implementations to the gang module's contract interfaces so the gang module never imports them
 * directly.
 */
@Configuration
public final class GangModuleConfig {

	@Bean
	public GangSettingsContract gangSettingsContract() {
		GangSettingsContract contract = new GanglandGangSettings();
		// Bind the static facade so gang-module data classes (Gang / User / Level) can
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
