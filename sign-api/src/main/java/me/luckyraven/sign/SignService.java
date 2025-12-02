package me.luckyraven.sign;

import lombok.Getter;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.sign.service.SignInteractionService;
import me.luckyraven.sign.validation.SignValidationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

@Getter
public abstract class SignService {

	private static final Logger logger = LogManager.getLogger(SignService.class.getSimpleName());

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
			logger.warn("There was a problem registering the sign type");
		}
	}

	private void registerEntries() throws SignValidationException {
		List<SignTypeDefinition> register = setupSigns();

		for (SignTypeDefinition definition : register) {
			registry.register(definition);
		}
	}
}
