package me.luckyraven.shop.handler;

import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.shop.ShopRegistry;
import me.luckyraven.shop.event.ShopEditedEvent;
import me.luckyraven.shop.message.ShopMessageContract;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

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
