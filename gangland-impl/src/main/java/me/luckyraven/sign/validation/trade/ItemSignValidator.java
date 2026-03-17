package me.luckyraven.sign.validation.trade;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.item.configuration.UniqueItemAddon;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.validation.AbstractSignValidator;

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
