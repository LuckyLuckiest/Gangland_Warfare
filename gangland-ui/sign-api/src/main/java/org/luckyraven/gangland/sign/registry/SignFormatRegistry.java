package org.luckyraven.gangland.sign.registry;

import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.validation.SignValidationException;

import java.util.HashMap;
import java.util.Locale;
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

		formats.put(format.getFormatName().toLowerCase(Locale.ROOT), format);
	}

	public Optional<SignFormat> getFormat(String formatName) {
		if (formatName == null) {
			return Optional.empty();
		}

		return Optional.ofNullable(formats.get(formatName.toLowerCase(Locale.ROOT)));
	}

	public Optional<SignFormat> getFormatByPrefix(String prefix) {
		if (prefix == null) {
			return Optional.empty();
		}

		String normalized = prefix.toLowerCase(Locale.ROOT);

		return formats.values()
				.stream().filter(format -> format.getSignTypePrefix().equalsIgnoreCase(normalized)).findFirst();
	}

	public boolean hasFormat(String formatName) {
		return formats.containsKey(formatName.toLowerCase(Locale.ROOT));
	}

	public void unregister(String formatName) {
		formats.remove(formatName.toLowerCase(Locale.ROOT));
	}

	public void clear() {
		formats.clear();
	}

}
