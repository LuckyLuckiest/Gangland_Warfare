package org.luckyraven.gangland.weapon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.weapon.command.WeaponCommand;
import org.luckyraven.gangland.weapon.database.WeaponRepository;
import org.luckyraven.gangland.weapon.listener.player.WeaponQuitCleanupListener;
import org.luckyraven.gangland.weapon.listener.wearable.WearableEquipListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The weapon module's declaration to the host (flip 4 of the 2026-09-07 module split): both configuration classes
 * in declaration order, its listener/command/repository packages, and that the declared packages match where the
 * classes actually live. Modelled on {@code MailModuleTest} / {@code TurfModuleTest}.
 */
@DisplayName("WeaponModule")
class WeaponModuleTest {

	@Test
	@DisplayName("configure registers WeaponFileConfig and WeaponModuleConfig plus the listener, command and repository packages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new WeaponModule().configure(registrations);

		assertEquals(List.of(WeaponFileConfig.class, WeaponModuleConfig.class), registrations.configurations());
		assertEquals(List.of(WeaponModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(WeaponModule.COMMAND_PACKAGE), registrations.commandPackages());
		assertEquals(List.of(WeaponModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertEquals(WeaponModule.COMMAND_PACKAGE, WeaponCommand.class.getPackageName());
		assertEquals(WeaponModule.REPOSITORY_PACKAGE, WeaponRepository.class.getPackageName());
		assertTrue(WeaponQuitCleanupListener.class.getPackageName().startsWith(WeaponModule.LISTENER_PACKAGE),
				"WeaponQuitCleanupListener lives under a sub-package of " + WeaponModule.LISTENER_PACKAGE);
	}

	@Test
	@DisplayName("WearableEquipListener lives under the weapon module's listener package (T-14)")
	void wearableEquipListener_livesUnderModuleListenerPackage() {
		// Wearables have been the weapon module's since 0.8.4; WearableEquipListener used to live in the
		// core-compiled gangland-item module and needed WearableEquipService, a bean only this module provides,
		// which broke a weapon-less server's boot. It was relocated here (T-14, 2026-09-08) - see
		// ItemListenerModuleBoundaryTest in gangland-item for the matching core-side boundary guard.
		assertTrue(WearableEquipListener.class.getPackageName().startsWith(WeaponModule.LISTENER_PACKAGE),
				"WearableEquipListener lives under a sub-package of " + WeaponModule.LISTENER_PACKAGE);
	}
}
