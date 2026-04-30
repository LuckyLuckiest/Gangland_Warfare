package org.luckyraven.gangland.sign;

import lombok.CustomLog;
import lombok.Getter;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;
import org.luckyraven.gangland.sign.registry.SignTypeRegistry;
import org.luckyraven.gangland.sign.service.SignInteractionService;
import org.luckyraven.gangland.sign.validation.SignValidationException;

import java.util.List;

@CustomLog
@Getter
public abstract class SignService {

	private final SignTypeRegistry       registry;
	private final SignInteractionService signService;

	public SignService(SignTypeRegistry registry, SignInteractionService signInteractionService) {
		this.registry    = registry;
		this.signService = signInteractionService;
	}

	/**
	 * Initialize and register all sign types
	 */
	public abstract List<SignTypeDefinition> setupSigns() throws SignValidationException;

	public void initialize() {
		try {
			registerEntries();
		} catch (SignValidationException exception) {
			log.warn("There was a problem registering the sign type");
		}
	}

	private void registerEntries() throws SignValidationException {
		List<SignTypeDefinition> register = setupSigns();

		for (SignTypeDefinition definition : register) {
			registry.register(definition);
		}
	}
}
