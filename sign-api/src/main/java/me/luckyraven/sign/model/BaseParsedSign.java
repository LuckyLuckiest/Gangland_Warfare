package me.luckyraven.sign.model;

import lombok.Getter;
import me.luckyraven.sign.SignType;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;

@Getter
public abstract class BaseParsedSign implements ParsedSign {

	private final SignType            signType;
	private final String              content;
	private final double              price;
	private final int                 amount;
	private final Location            location;
	private final String[]            rawLines;
	private final Map<String, Object> metadata;

	protected BaseParsedSign(SignType signType, String content, double price, int amount, Location location,
							 String[] rawLines) {
		this.signType = signType;
		this.content  = content;
		this.price    = price;
		this.amount   = amount;
		this.location = location;
		this.rawLines = rawLines;
		this.metadata = new HashMap<>();
	}

	@Override
	public <T> T getMetadata(String key, Class<T> type) {
		Object value = metadata.get(key);

		if (type.isInstance(value)) {
			return type.cast(value);
		}

		return null;
	}

	@Override
	public boolean hasMetadata(String key) {
		return metadata.containsKey(key);
	}

	protected void setMetadata(String key, Object value) {
		metadata.put(key, value);
	}

}
