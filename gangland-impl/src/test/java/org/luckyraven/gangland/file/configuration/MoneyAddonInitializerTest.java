package org.luckyraven.gangland.file.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.item.money.MoneyAddon;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what {@link MoneyAddonInitializer#getFileHandler()} returns before the hand-written getter is replaced by
 * Lombok's {@code @Getter}: the very {@link FileHandler} the {@link FileManager} handed out for {@code money}, after
 * the file was checked as loaded.
 */
@DisplayName("MoneyAddonInitializer")
class MoneyAddonInitializerTest {

	@Test
	@DisplayName("getFileHandler returns the money file handler the FileManager provided, after checkFileLoaded")
	void getFileHandler_returnsTheHandlerFromTheFileManager() throws IOException {
		FileManager fileManager = mock(FileManager.class);
		FileHandler handler     = mock(FileHandler.class);
		when(fileManager.getFile("money")).thenReturn(handler);

		MoneyAddonInitializer initializer = new MoneyAddonInitializer(fileManager, mock(MoneyAddon.class));

		assertSame(handler, initializer.getFileHandler());
		var order = inOrder(fileManager);
		order.verify(fileManager).checkFileLoaded("money");
		order.verify(fileManager).getFile("money");
	}
}
