package me.luckyraven.sign.registry;

import me.luckyraven.sign.model.SignFormat;
import me.luckyraven.sign.validation.SignValidationException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SignFormatRegistry {

	private final Map<String, SignFormat> formats;

	public SignFormatRegistry() {
		this.formats = new HashMap<>();
	}

	public void register(SignFormat format) throws SignValidationException {
		if (format == null || format.getFormatName() == null) {
			throw new SignValidationException("Attempted to register null or invalid format");
		}

		formats.put(format.getFormatName().toLowerCase(), format);
	}

	public Optional<SignFormat> getFormat(String formatName) {
		if (formatName == null) {
			return Optional.empty();
		}

		return Optional.ofNullable(formats.get(formatName.toLowerCase()));
	}

	public Optional<SignFormat> getFormatByPrefix(String prefix) {
		if (prefix == null) {
			return Optional.empty();
		}

		String normalized = prefix.toLowerCase();

		return formats.values()
				.stream().filter(format -> format.getSignTypePrefix().equalsIgnoreCase(normalized)).findFirst();
	}

	public boolean hasFormat(String formatName) {
		return formats.containsKey(formatName.toLowerCase());
	}

	public void unregister(String formatName) {
		formats.remove(formatName.toLowerCase());
	}

	public void clear() {
		formats.clear();
	}

}
