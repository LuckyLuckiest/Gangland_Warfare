package org.luckyraven.gangland.gadget.car.access;

import java.util.UUID;

/**
 * The one gang question {@link CarAccessPolicy} needs answered. The gadget module has no dependency on
 * {@code gangland-domain}, so the membership lookup is supplied by {@code gangland-impl} the same way
 * {@code CarMessageContract} supplies the strings.
 */
@FunctionalInterface
public interface CarGangContract {

	/**
	 * @return {@code true} when both players are members of the same gang
	 */
	boolean sharesGang(UUID one, UUID other);

}
