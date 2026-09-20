package org.luckyraven.gangland.civilians;

import org.luckyraven.bartizan.api.combat.CombatEligibility;
import org.luckyraven.gangland.civilians.npc.combat.GanglandCombatEligibility;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/**
 * WS7 G5b: isolated out of {@link CiviliansModuleConfig} because {@code gangland-civilians}' {@code module.yml}
 * drops the hard, fail-fast {@code Plugins: [Bartizan]} dependency. This bean's return type names a Bartizan type
 * directly ({@link CombatEligibility}), and Keystone's {@code ReflectionGuard.orSkip} skips a WHOLE
 * {@code @Configuration} class on the first unresolvable signature it hits — keeping this bean on the same class as
 * {@code CiviliansModuleConfig}'s other 8 Bartizan-free beans would have sunk all of them together on a
 * Bartizan-less server. Living alone here, only this one bean (and the {@code combatEligibility}
 * {@code ServicesManager} publication it feeds) is lost when Bartizan is absent — the intended degrade, since
 * Bartizan has nothing to pull from the {@code ServicesManager} anyway if it isn't running.
 *
 * <p>The wave's one direction reversal: Gangland <strong>publishes</strong> {@link GanglandCombatEligibility} on the
 * {@code ServicesManager} under Bartizan's {@link CombatEligibility} interface (never the concrete class — per
 * {@code BeanFactory}'s {@code publishToServicesManager} contract) so Bartizan can pull it lazily on every hit
 * check via {@code Bukkit.getServicesManager().getRegistration(CombatEligibility.class)}.
 */
@Configuration
public class CombatEligibilityConfig {

	@Bean(publishToServicesManager = true)
	public CombatEligibility combatEligibility() {
		return new GanglandCombatEligibility();
	}

}
