package org.luckyraven.gangland.sign.service;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.parser.SignParser;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.registry.SignTypeRegistry;
import org.luckyraven.gangland.sign.validation.SignValidationException;

import java.util.Optional;

@Getter
public abstract class SignInteractionService {

	private final String               prefix;
	private final SignTypeRegistry     registry;
	private final SignFormatterService formatterService;

	public SignInteractionService(String prefix, SignTypeRegistry registry, SignFormatterService formatterService) {
		this.prefix           = prefix;
		this.registry         = registry;
		this.formatterService = formatterService;
	}

	public abstract boolean handlerInteraction(Player player, ParsedSign sign);

	public void validateSign(String[] lines) throws SignValidationException {
		if (lines == null || lines.length < 4) {
			throw new SignValidationException("The sign must have 4 lines!");
		}

		String title = lines[0];

		Optional<SignTypeDefinition> definition = registry.findByLine(title);

		if (definition.isEmpty()) {
			throw new SignValidationException("Unknown sign type: " + title);
		}

		SignTypeDefinition def = definition.get();

		def.getSignValidator().validate(lines);
	}

	public Optional<ParsedSign> parseSign(String[] lines, Location location) throws SignValidationException {
		Optional<SignTypeDefinition> definition = registry.findByLine(lines[0]);

		if (definition.isEmpty()) {
			return Optional.empty();
		}

		SignParser parser = definition.get().getSignParser();
		ParsedSign parsed = parser.parse(lines, location);

		return Optional.of(parsed);
	}

	public String[] formatForDisplay(String[] lines, String moneySymbol) {
		try {
			return formatterService.formatForDisplay(lines, moneySymbol);
		} catch (SignValidationException exception) {
			// fallback to default formatting if a custom format fails
			return lines;
		}
	}

}
