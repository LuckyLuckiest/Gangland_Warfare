package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.keystone.bean.Bean;
import org.luckyraven.keystone.bean.Configuration;

/** Lane W5-H beans: safehouses and rallies. The body arrives with that lane. */
@Configuration
public final class GangSafehouseConfig {

	@Bean
	public GangSafehouseService gangSafehouseService() {
		return new GangSafehouseService();
	}
}
