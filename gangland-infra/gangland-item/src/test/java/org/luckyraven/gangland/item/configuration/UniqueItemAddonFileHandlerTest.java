package org.luckyraven.gangland.item.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.item.fuel.FuelService;
import org.luckyraven.keystone.permission.PermissionManager;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("UniqueItemAddon - the file handler it exposes as a FileInitializer")
class UniqueItemAddonFileHandlerTest {

	@Test
	@DisplayName("getFileHandler returns the unique_items handler the constructor looked up, also through FileInitializer")
	void getFileHandler_returnsTheUniqueItemsHandler() throws Exception {
		FileManager files   = mock(FileManager.class);
		FileHandler handler = mock(FileHandler.class);
		when(files.getFile("unique_items")).thenReturn(handler);

		UniqueItemAddon addon = new UniqueItemAddon(mock(PermissionManager.class), files, mock(FuelService.class), null);

		verify(files).checkFileLoaded("unique_items");
		assertSame(handler, addon.getFileHandler());
		FileInitializer asInitializer = addon;
		assertSame(handler, asInitializer.getFileHandler());
	}
}
