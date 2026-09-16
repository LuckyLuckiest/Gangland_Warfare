package org.luckyraven.gangland.gadget.jetpack;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * NBT tag keys used to identify and store jetpack item data.
 */
@Getter
@RequiredArgsConstructor
public enum JetpackKey {

	JETPACK_ID("jetpack");

	private final String key;
}
