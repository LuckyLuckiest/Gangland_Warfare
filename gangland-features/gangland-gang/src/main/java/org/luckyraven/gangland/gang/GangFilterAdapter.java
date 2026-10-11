package org.luckyraven.gangland.gang;

import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.menu.filter.FilterAdapter;
import org.luckyraven.gangland.menu.filter.FilterBinding;
import org.luckyraven.gangland.menu.filter.FilterRegistry;
import org.luckyraven.gangland.menu.filter.SortDescriptor;
import org.luckyraven.keystone.bean.PostConstruct;
import org.luckyraven.gangland.menu.filter.FilterField;
import org.luckyraven.gangland.menu.filter.StandardFilterField;

import java.util.ArrayList;
import java.util.List;
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

	private final GangStatsService statsService;

	private final FilterRegistry registry;

	public GangFilterAdapter() {
		this(new GangStatsService(), null);
	}

	public GangFilterAdapter(GangStatsService statsService) {
		this(statsService, null);
	}

	public GangFilterAdapter(GangStatsService statsService, FilterRegistry registry) {
		this.statsService = statsService;
		this.registry     = registry;
	}

	/** Adds the stat sort axes to the impl-registered {@code gangs} binding (the registry is filled before this runs). */
	@PostConstruct
	public void registerStatSorts() {
		FilterBinding binding = registry == null ? null : registry.get("gangs");
		if (binding == null) return;

		List<SortDescriptor> cycle = new ArrayList<>(binding.sortCycle());
		for (GangStatFilterField field : List.of(GangStatFilterField.KILLS, GangStatFilterField.KDR,
		                                         GangStatFilterField.LEVEL, GangStatFilterField.ONLINE)) {
			cycle.add(SortDescriptor.desc(field));
		}
		registry.register(new FilterBinding(binding.id(), binding.targetInventory(), binding.fields(),
		                                    binding.defaultSort(), cycle));
	}

	@Override
	public Object project(Gang gang, FilterField field) {
		if (gang == null || field == null) return null;
		if (field instanceof GangStatFilterField stat) {
			return statsService.snapshot().row(gang.getId()).map(row -> row.value(stat.stat())).orElse(0D);
		}
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
