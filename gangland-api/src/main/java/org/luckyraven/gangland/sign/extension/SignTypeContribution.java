package org.luckyraven.gangland.sign.extension;

import org.luckyraven.gangland.sign.type.Sign;

import java.util.List;

/**
 * Sign types a runtime module adds to the catalogue {@link org.luckyraven.gangland.sign.SignManager#setupSigns()}
 * builds. The core cannot name a module type, so a module that wants a "car-buy" or "car-sell" sign registers a
 * bean implementing this contract; {@code SignManager} pulls every contribution out of the container while it
 * builds its definitions and appends what {@link #signs(String)} returns.
 */
public interface SignTypeContribution {

	/**
	 * The sign types this contribution adds. {@code signPrefix} is the plugin's sign prefix with the dash already
	 * appended ("glw-"), matching what {@code SignManager.setupSigns()} builds its own keys with.
	 */
	List<Sign> signs(String signPrefix);
}
