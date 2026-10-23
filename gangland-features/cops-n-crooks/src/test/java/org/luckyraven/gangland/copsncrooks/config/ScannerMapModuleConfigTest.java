package org.luckyraven.gangland.copsncrooks.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.CopsNCrooksModule;
import org.luckyraven.gangland.copsncrooks.listener.scanner.ScannerMapListener;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.place.AdminRegionRegistry;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapRenderer;
import org.luckyraven.gangland.copsncrooks.scanner.ScannerMapView;
import org.luckyraven.gangland.copsncrooks.scanner.SearchZoneCache;
import org.luckyraven.gangland.copsncrooks.support.FakeRepository;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lane scanmap: the Scanner Map beans, the live settings supplier and the module registration. */
@DisplayName("ScannerMapModuleConfig")
class ScannerMapModuleConfigTest {

	private final ScannerMapModuleConfig config = new ScannerMapModuleConfig();

	@Test
	@DisplayName("settingsOf falls back to the shipped defaults before the first config load")
	void settingsOf_defaultsWithoutProvider() {
		assertSame(ScannerMapSettings.DEFAULT, ScannerMapModuleConfig.settingsOf(() -> null).get());
	}

	@Test
	@DisplayName("settingsOf reads the provider on every call, so a reload is seen")
	void settingsOf_readsPerCall() {
		AtomicReference<CopConfigProvider> current = new AtomicReference<>();
		CopConfigProvider first  = mock(CopConfigProvider.class);
		CopConfigProvider second = mock(CopConfigProvider.class);
		ScannerMapSettings one   = new ScannerMapSettings(true, 2, 20, false, "one", List.of());
		ScannerMapSettings two   = new ScannerMapSettings(true, 4, 40, true, "two", List.of());
		when(first.getScannerMapSettings()).thenReturn(one);
		when(second.getScannerMapSettings()).thenReturn(two);
		current.set(first);

		var settings = ScannerMapModuleConfig.settingsOf(current::get);

		assertEquals("one", settings.get().itemName());
		current.set(second);
		assertEquals("two", settings.get().itemName());
	}

	@Test
	@DisplayName("the zone cache bean is a fresh cache")
	void searchZoneCacheBean() {
		assertNotNull(config.searchZoneCache());
	}

	@Test
	@DisplayName("the renderer and view beans are built from the loader, cache, cop manager and regions")
	void rendererAndViewBeans() {
		ScannerMapRenderer renderer = config.scannerMapRenderer(mock(CopLoader.class), new SearchZoneCache(),
		                                                       mock(CopManager.class),
		                                                       new AdminRegionRegistry(new FakeRepository<>()));

		assertNotNull(renderer);
		assertNotNull(config.scannerMapView(renderer));
	}

	@Test
	@DisplayName("CopsNCrooksModule registers this configuration, and the listener lives under the scanned package")
	void moduleRegistersConfiguration() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new CopsNCrooksModule().configure(registrations);

		assertTrue(registrations.configurations().contains(ScannerMapModuleConfig.class));
		assertTrue(ScannerMapListener.class.getPackageName().startsWith(CopsNCrooksModule.LISTENER_PACKAGE));
		assertNotNull(ScannerMapView.class);
	}
}
