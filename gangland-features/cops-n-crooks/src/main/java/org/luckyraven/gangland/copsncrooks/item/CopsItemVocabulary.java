package org.luckyraven.gangland.copsncrooks.item;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.item.spi.ItemVocabulary;
import org.luckyraven.keystone.item.spi.ItemVocabularyRegistrar;

/**
 * Publishes the cops items to shop, trader and loot item strings: {@code copsncrooks:police_scanner} and
 * {@code copsncrooks:scanner_map}. Registered on the {@code ServicesManager} before
 * {@code GanglandContext.installItemVocabularies()} folds every vocabulary into the item registries.
 *
 * @since 0.20.0
 */
@CustomLog
public class CopsItemVocabulary implements ItemVocabulary {

	/** The vocabulary namespace and the converter type of an item string. */
	public static final String NAMESPACE = "copsncrooks";

	private final CopsItems items;

	public CopsItemVocabulary(CopsItems items) {
		this.items = items;
	}

	/** Builds the vocabulary and registers it as an {@link ItemVocabulary} service owned by {@code plugin}. */
	public static CopsItemVocabulary publish(JavaPlugin plugin, CopsItems items) {
		CopsItemVocabulary vocabulary = new CopsItemVocabulary(items);
		Bukkit.getServicesManager().register(ItemVocabulary.class, vocabulary, plugin, ServicePriority.Normal);
		log.info("Published the copsncrooks item vocabulary");
		return vocabulary;
	}

	@Override
	public String namespace() {
		return NAMESPACE;
	}

	@Override
	public void contribute(ItemVocabularyRegistrar registrar) {
		registrar.converter(NAMESPACE,
		                    (type, modifier, attributes) -> modifier == null ? null : items.create(modifier));
	}
}
