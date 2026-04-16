package me.luckyraven.market.event;

public record ShockTarget(Kind kind, String id) {

	public static ShockTarget item(String itemId) {
		return new ShockTarget(Kind.ITEM, itemId);
	}

	public static ShockTarget category(String categoryId) {
		return new ShockTarget(Kind.CATEGORY, categoryId);
	}

	public boolean matches(String itemId, String categoryId) {
		return switch (kind) {
			case ITEM -> id.equalsIgnoreCase(itemId);
			case CATEGORY -> id.equalsIgnoreCase(categoryId);
		};
	}

	public enum Kind {
		ITEM,
		CATEGORY
	}
}
