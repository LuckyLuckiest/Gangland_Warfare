package org.luckyraven.gangland.shop.handler;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.shop.ShopRegistry;
import org.luckyraven.gangland.shop.event.ShopEditedEvent;
import org.luckyraven.gangland.shop.message.ShopMessageContract;

/**
 * Persists edits made through the shared admin view and reports back to the editing admin. Generic over any shop
 * integration — both trader NPCs and future shops fire the same {@link ShopEditedEvent}.
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
