package me.luckyraven.sign;

import lombok.Getter;
import me.luckyraven.sign.registry.SignTypeDefinition;
import me.luckyraven.sign.registry.SignTypeRegistry;
import me.luckyraven.sign.service.SignInteractionService;

import java.util.List;

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
	public abstract List<SignTypeDefinition> setupSigns();

	public void initialize() {
		registerEntries();
	}

	private void registerEntries() {
		List<SignTypeDefinition> register = setupSigns();

		for (SignTypeDefinition definition : register) {
			registry.register(definition);
		}
	}
}
