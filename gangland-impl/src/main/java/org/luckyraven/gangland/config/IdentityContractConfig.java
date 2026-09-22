package org.luckyraven.gangland.config;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.core.permission.PermissionRegistryContract;
import org.luckyraven.gangland.data.gang.GangMembership;
import org.luckyraven.gangland.file.configuration.gang.GanglandPermissionRegistry;
import org.luckyraven.gangland.file.configuration.gang.GanglandUserLookup;
import org.luckyraven.gangland.file.configuration.identity.GanglandIdentitySettings;

/**
 * Replaces the deleted {@code GangModuleConfig} (WS5 G1 step 9): wires only the identity-side / always-present
 * contracts that gangland-impl still owns directly. Every gang-owned contract moved to the gang module's own
 * {@code GangConfig} instead.
 */
@Configuration
public final class IdentityContractConfig {

	/**
	 * Split out of the old 14-getter {@code gangSettingsContract()} bean (WS5 G0, B2): the identity slice
	 * ({@code User}/{@code Level}, gangland-core) needs its 11 getters bound before either data class is
	 * constructed.
	 */
	@Bean
	public IdentitySettingsContract identitySettingsContract() {
		IdentitySettingsContract contract = new GanglandIdentitySettings();
		IdentitySettings.bind(contract);
		return contract;
	}

	@Bean
	public UserLookupContract userLookupContract(@Qualifier("online") UserManager<Player> userManager) {
		return new GanglandUserLookup(userManager);
	}

	@Bean
	public PermissionRegistryContract permissionRegistryContract(PermissionManager permissionManager) {
		return new GanglandPermissionRegistry(permissionManager);
	}

	/**
	 * WS5 G1 step 9b (R9): the always-present, inert-until-installed "is/which gang" fact holder — zero-arg bound
	 * exactly like {@code DataConfig.bankTiers()}. The gang module's {@code GangMembershipInstaller} calls
	 * {@link GangMembership#install} from its {@code @PostConstruct} once its own beans exist.
	 */
	@Bean
	public GangMembership gangMembership() {
		return new GangMembership();
	}
}
