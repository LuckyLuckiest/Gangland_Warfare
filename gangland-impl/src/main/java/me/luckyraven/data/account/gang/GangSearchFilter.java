package me.luckyraven.data.account.gang;

public record GangSearchFilter(String nameQuery, SortKey sort) {

	public static final GangSearchFilter EMPTY = new GangSearchFilter("", SortKey.NAME);

	public GangSearchFilter withName(String name) {
		return new GangSearchFilter(name == null ? "" : name, sort);
	}

	public GangSearchFilter withSort(SortKey next) {
		return new GangSearchFilter(nameQuery, next);
	}

	public boolean hasNameQuery() {
		return nameQuery != null && !nameQuery.isEmpty();
	}

	public enum SortKey {
		NAME,
		MEMBERS,
		CREATED;

		public SortKey next() {
			SortKey[] values = values();
			return values[(ordinal() + 1) % values.length];
		}

		public String displayName() {
			return switch (this) {
				case NAME -> "Name (A-Z)";
				case MEMBERS -> "Members";
				case CREATED -> "Newest";
			};
		}
	}

}
