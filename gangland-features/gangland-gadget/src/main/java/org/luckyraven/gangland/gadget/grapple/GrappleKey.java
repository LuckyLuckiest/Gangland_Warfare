package org.luckyraven.gangland.gadget.grapple;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * NBT tag keys used to identify and store grapple item data.
 */
@Getter
@RequiredArgsConstructor
public enum GrappleKey {

	GRAPPLE_ID("grapple");

	private final String key;
}
