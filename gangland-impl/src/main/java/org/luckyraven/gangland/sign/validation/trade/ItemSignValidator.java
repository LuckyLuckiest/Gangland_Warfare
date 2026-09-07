package org.luckyraven.gangland.sign.validation.trade;

import com.cryptomorin.xseries.XMaterial;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.validation.AbstractSignValidator;

import java.util.Arrays;
import java.util.Objects;

public class ItemSignValidator extends AbstractSignValidator {

	private final UniqueItemAddon uniqueItemAddon;

	public ItemSignValidator(SignType signType, UniqueItemAddon uniqueItemAddon) {
		super(signType, Settings.getMoneySymbol());

		this.uniqueItemAddon = uniqueItemAddon;
	}

	@Override
	protected boolean isValidContent(String content) {
		// check unique items first
		if (uniqueItemAddon.getUniqueItem(content) != null) return true;

		// fall back to vanilla materials
		return Arrays.stream(XMaterial.values())
				.map(XMaterial::get)
				.filter(Objects::nonNull)
				.anyMatch(material -> material.name().equalsIgnoreCase(content));
	}
}
