package org.luckyraven.gangland.sign.validation;

import org.luckyraven.gangland.sign.SignType;

/**
 * Validates sign format and content
 */
public interface SignValidator {

	/**
	 * Validate all four lines of a sign
	 *
	 * @param lines The sign lines (must be length 4)
	 *
	 * @throws SignValidationException if validation fails
	 */
	void validate(String[] lines) throws SignValidationException;

	/**
	 * Get the sign type this validator handles
	 */
	SignType getSignType();
}
