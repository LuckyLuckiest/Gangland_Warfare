package org.luckyraven.gangland.shop;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.shop.admin.view.ShopAdminFlow;
import org.luckyraven.keystone.shop.ShopDefinition;

/**
 * The only {@link ShopAdminOpener} implementation — wraps the real {@link ShopAdminFlow} so a runtime module
 * (which compiles against {@code gangland-api} only) can open the shop-admin editor without naming a
 * {@code gangland-impl} type directly (WS4 G1a, B1).
 */
@RequiredArgsConstructor
public final class ShopAdminOpenerImpl implements ShopAdminOpener {

	private final ShopAdminFlow shopAdminFlow;

	@Override
	public void openAdmin(Player admin, ShopDefinition definition) {
		shopAdminFlow.start(admin, definition);
	}

}
