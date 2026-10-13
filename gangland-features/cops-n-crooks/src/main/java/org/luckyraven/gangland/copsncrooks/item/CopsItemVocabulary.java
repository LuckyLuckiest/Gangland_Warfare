package org.luckyraven.gangland.copsncrooks.item;

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
public class CopsItemVocabulary implements ItemVocabulary {

	/** The vocabulary namespace and the converter type of an item string. */
	public static final String NAMESPACE = "copsncrooks";

	public CopsItemVocabulary(CopsItems items) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Builds the vocabulary and registers it as an {@link ItemVocabulary} service owned by {@code plugin}. */
	public static CopsItemVocabulary publish(JavaPlugin plugin, CopsItems items) {
		throw new UnsupportedOperationException("TDD stub");
	}

	@Override
	public String namespace() {
		return "";
	}

	@Override
	public void contribute(ItemVocabularyRegistrar registrar) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
