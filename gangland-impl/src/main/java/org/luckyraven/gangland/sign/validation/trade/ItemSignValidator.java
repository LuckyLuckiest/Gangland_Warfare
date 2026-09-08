package org.luckyraven.gangland.sign.validation.trade;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.validation.AbstractSignValidator;
import org.luckyraven.keystone.item.ItemParser;

/**
 * Mirrors {@link org.luckyraven.gangland.sign.type.trade.BaseTradeSign#getDefinedItem}'s resolution order exactly,
 * so a sign never validates content its own resolver would then fail to build: a bare unique-item key first, then
 * the full {@link ItemParser} grammar (bare material name or a prefixed definition string).
 */
public class ItemSignValidator extends AbstractSignValidator {

	private final UniqueItemAddon uniqueItemAddon;
	private final ItemParser      itemParser;

	public ItemSignValidator(SignType signType, UniqueItemAddon uniqueItemAddon, ItemParser itemParser) {
		super(signType, Settings.getMoneySymbol());

		this.uniqueItemAddon = uniqueItemAddon;
		this.itemParser      = itemParser;
	}

	@Override
	protected boolean isValidContent(String content) {
		if (uniqueItemAddon.getUniqueItem(content) != null) return true;

		return itemParser.parse(content) != null;
	}
}
