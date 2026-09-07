package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.inventory.filter.FilterAdapter;
import org.luckyraven.gangland.inventory.filter.FilterField;
import org.luckyraven.gangland.inventory.filter.StandardFilterField;

import java.util.Locale;

/**
 * Projects {@link Gang} instances onto the canonical {@link StandardFilterField} axes that the gang-search view
 * supports. Returning {@code null} marks a field as unsupported; the applier treats unsupported fields as non-matches
 * and sorts them last.
 */
public final class GangFilterAdapter implements FilterAdapter<Gang> {

	@Override
	public Object project(Gang gang, FilterField field) {
		if (gang == null || field == null) return null;
		if (field instanceof StandardFilterField std) {
			return switch (std) {
				case NAME -> gang.getDisplayNameString() == null
				             ? ""
				             : gang.getDisplayNameString().toLowerCase(Locale.ROOT);
				case DESCRIPTION -> gang.getDescription() == null ? "" : gang.getDescription();
				case COLOR -> gang.getColor() == null ? "" : gang.getColor();
				case MEMBERS -> gang.getMembers() == null ? 0 : gang.getMembers().size();
				case DATE -> gang.getCreated();
				default -> null;
			};
		}
		return null;
	}

}
