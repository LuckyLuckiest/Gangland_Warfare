package org.luckyraven.gangland.shop.handler;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.shop.ShopRegistry;
import org.luckyraven.keystone.shop.event.ShopEditedEvent;
import org.luckyraven.keystone.shop.message.ShopMessageContract;

/**
 * Persists edits made through the shared admin view and reports back to the editing admin. Generic over any shop
 * integration — both trader NPCs and future shops fire the same {@link ShopEditedEvent}.
 *
 * <p>WS4 G1a (S1): stays in {@code gangland-impl} rather than moving to {@code keystone-shop} — it is the only
 * user of {@code keystone-bean} among the shop files, and {@code keystone-shop} ships as a zero-DI pure library.
 * Only the imports moved (shop-domain types now come from {@code org.luckyraven.keystone.shop.*}); this class's
 * own package and behaviour are unchanged.
 */
@CustomLog
@ListenerHandler
@RequiredArgsConstructor
public class ShopEditPersistenceHandler implements Listener {

	private final ShopRegistry        shopRegistry;
	private final ShopMessageContract messages;

	@EventHandler
	public void onShopEdited(ShopEditedEvent event) {
		shopRegistry.save(event.getDefinition());
		event.getAdmin().sendMessage(messages.shopSaved(event.getDefinition().getKey()));
		log.info("Admin {} saved shop '{}'", event.getAdmin().getName(), event.getDefinition().getKey());
	}

}
