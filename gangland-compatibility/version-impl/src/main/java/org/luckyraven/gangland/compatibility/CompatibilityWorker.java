package org.luckyraven.gangland.compatibility;

import com.viaversion.viaversion.api.ViaAPI;
import lombok.CustomLog;
import org.luckyraven.gangland.compatibility.recoil.RecoilCompatibility;
import org.luckyraven.keystone.nms.VersionedAdapterLoader;

import java.util.function.Supplier;

/**
 * Resolves the per-version {@link Compatibility} adapter for the running server. Keystone owns the heavy
 * lifting — CraftBukkit revision detection (package parse + the upstream-maintained release→revision table) and
 * the reflective adapter load ({@link VersionedAdapterLoader}, faults through Diagnostics); this class keeps only
 * Gangland's contract and fallback: when no {@code version.v1_XX_RY} adapter matches, the Bukkit-API
 * {@link RecoilCompatibility} takes over with the ViaVersion supplier wired in.
 */
@CustomLog
public class CompatibilityWorker implements Compatibility {

	/**
	 * The package the per-version adapter modules compile into — class names match CraftBukkit revisions.
	 */
	static final String VERSION_PACKAGE = "org.luckyraven.gangland.compatibility.version";

	private final RecoilCompatibility recoilCompatibility;

	public CompatibilityWorker(Supplier<ViaAPI<?>> viaApiSupplier) {
		Compatibility compatibility = VersionedAdapterLoader.loadOrFallback(Compatibility.class, VERSION_PACKAGE,
		                                                                    () -> null);

		RecoilCompatibility resolved = compatibility == null ? null : compatibility.getRecoilCompatibility();

		if (resolved == null) {
			log.info("Using default recoil (limited functionality).");

			RecoilCompatibility fallback = new RecoilCompatibility();
			fallback.setViaApiSupplier(viaApiSupplier);
			resolved = fallback;
		}

		this.recoilCompatibility = resolved;
	}

	@Override
	public RecoilCompatibility getRecoilCompatibility() {
		return recoilCompatibility;
	}

}
