package org.luckyraven.gangland.sign;

import org.bukkit.Location;
import org.luckyraven.gangland.sign.model.ParsedSign;
import org.luckyraven.gangland.sign.parser.SignParser;
import org.luckyraven.gangland.sign.validation.SignValidationException;
import org.luckyraven.gangland.sign.validation.SignValidator;

/**
 * Read-time redirection for a pre-0.9.0 weapon/ammo/wearable trade-sign header (gangland-0.9.0.md T-G4b): resolves
 * a sign whose first line is still the legacy header (e.g. {@code glw-weapon-buy}) as the generic
 * {@code item-buy}/{@code item-sell} type, prefixing the content line with the alias namespace
 * ({@code weapon:}/{@code ammo:}/{@code wearable:}) before delegating to that type's own validator/parser. The
 * physical sign block is never rewritten — only the in-memory {@code lines} array handed to the delegate.
 *
 * <p>{@link SignManager#setupSigns()} registers one instance per legacy header, under its own {@link SignType}, so
 * the existing {@code SignTypeRegistry.findByLine} choke point (already case- and bracket-insensitive) is the only
 * place matching happens — this class adds no second normalisation layer.
 */
final class LegacyAliasSignAdapter implements SignParser, SignValidator {

	private final SignType      legacyType;
	private final SignType      delegateType;
	private final String        definitionPrefix;
	private final SignParser    delegateParser;
	private final SignValidator delegateValidator;

	LegacyAliasSignAdapter(SignType legacyType, SignType delegateType, String definitionPrefix,
	                       SignParser delegateParser, SignValidator delegateValidator) {
		this.legacyType        = legacyType;
		this.delegateType      = delegateType;
		this.definitionPrefix  = definitionPrefix;
		this.delegateParser    = delegateParser;
		this.delegateValidator = delegateValidator;
	}

	/**
	 * Rewrites line 1 (the header) to the delegate type's own generated name — so its validator's built-in
	 * type check passes — and prefixes line 2 (the content) with this alias's definition namespace.
	 */
	private String[] redirect(String[] lines) {
		String[] redirected = lines.clone();

		redirected[0] = delegateType.generated();
		redirected[1] = definitionPrefix + ":" + (lines[1] == null ? "" : lines[1]);

		return redirected;
	}

	@Override
	public ParsedSign parse(String[] lines, Location location) throws SignValidationException {
		return delegateParser.parse(redirect(lines), location);
	}

	@Override
	public void validate(String[] lines) throws SignValidationException {
		delegateValidator.validate(redirect(lines));
	}

	@Override
	public SignType getSignType() {
		return legacyType;
	}

}
