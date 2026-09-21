package org.luckyraven.gangland.shop;

import org.bukkit.entity.Player;
import org.luckyraven.keystone.shop.ShopDefinition;

/**
 * WS4 G1a, B1: the module boundary a runtime module crosses to open the core's shop-admin editor UI without
 * naming a {@code gangland-impl} type directly (a module compiles against {@code gangland-api} only). One
 * implementation, {@code ShopAdminOpenerImpl} (gangland-impl), wraps the real {@code ShopAdminFlow} and is
 * registered as a {@code @Bean} under this interface type; npc-shops' {@code ShopViewOpenerImpl} depends on the
 * interface only.
 *
 * <p>Signature verified against the two real call sites this replaces (both previously called
 * {@code ShopAdminFlow.start(Player, ShopDefinition)} directly): {@code ShopViewOpenerImpl.openFor}'s
 * sneaking-admin branch, and {@code ShopViewOpenerImpl.openAdminView} (the {@code /glw shop edit} path).
 */
public interface ShopAdminOpener {

	void openAdmin(Player admin, ShopDefinition definition);

}
