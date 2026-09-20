package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.menu.filter.FilterAdapter;
import org.luckyraven.gangland.menu.filter.FilterField;
import org.luckyraven.gangland.menu.filter.StandardFilterField;

import java.util.Locale;

/**
 * Projects {@link Gang} instances onto the canonical {@link StandardFilterField} axes that the gang-search view
 * supports. Returning {@code null} marks a field as unsupported; the applier treats unsupported fields as non-matches
 * and sorts them last.
 *
 * <p>Moved from {@code gangland-domain} to {@code gangland-impl} at WS2 G2 (0.10.0): {@code gangland-domain} no
 * longer depends on {@code inventory-api} (severing the domain inversion), but this class's {@code FilterAdapter}/
 * {@code FilterField}/{@code StandardFilterField} types still live in {@code inventory-api} until WS2 G3a moves them
 * to {@code gangland-impl}'s own {@code menu.filter} package — so this class has to live somewhere that still
 * depends on {@code inventory-api} in the meantime. Package unchanged, so no importer needed updating.
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
