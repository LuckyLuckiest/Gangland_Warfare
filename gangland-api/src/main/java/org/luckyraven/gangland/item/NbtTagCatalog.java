package org.luckyraven.gangland.item;

import java.util.ArrayList;
import java.util.List;

/** Ordered catalogue of "interesting" NBT tag names for {@code /glw debug nbt brief}. */
public final class NbtTagCatalog {

	private final List<String> tags = new ArrayList<>();

	public void register(String... names) {
		if (names == null) return;

		for (String name : names) {
			if (name == null || name.isBlank() || tags.contains(name)) continue;

			tags.add(name);
		}
	}

	public List<String> tags() {
		return List.copyOf(tags);
	}
}
