package org.luckyraven.gangland.support;

import org.luckyraven.keystone.module.ModuleDescriptorReader;
import org.luckyraven.keystone.module.artifact.ArtifactCoordinate;
import org.luckyraven.keystone.testkit.TestJars;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Real module jars and a {@code file:} Maven repository serving them, so the module commands and
 * {@code ModuleUpdateService} can be driven offline (Keystone's {@code ArtifactResolver} treats a {@code file:}
 * repository exactly like a remote one, mandatory {@code .sha256} included).
 */
public final class ModuleRepoFixture {

	private ModuleRepoFixture() {
	}

	/** Writes a jar at {@code jar} whose {@code module.yml} carries the given id, version and {@code Host_Api}. */
	public static Path moduleJar(Path jar, String id, String version, String hostApi) throws IOException {
		return TestJars.builder(jar)
		               .text(ModuleDescriptorReader.DESCRIPTOR_ENTRY,
		                     "Id: " + id + "\nVersion: " + version + "\nMain: org.example." + id + ".Main\nHost_Api: "
		                     + hostApi + "\n")
		               .write();
	}

	/** Publishes a module jar plus its {@code .sha256} under {@code repoRoot} at a versioned {@code coordinate}. */
	public static void publish(Path repoRoot, String coordinate, String id, String hostApi)
			throws IOException, NoSuchAlgorithmException {
		ArtifactCoordinate parsed = ArtifactCoordinate.parse(coordinate);
		Path               jar    = moduleJar(repoRoot.resolve(parsed.jarPath()), id, parsed.version(), hostApi);
		byte[]             digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar));

		Files.writeString(jar.resolveSibling(jar.getFileName() + ".sha256"), HexFormat.of().formatHex(digest) + "\n");
	}
}
