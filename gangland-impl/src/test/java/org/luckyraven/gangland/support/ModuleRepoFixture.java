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

	/**
	 * Writes a jar at {@code jar} whose {@code module.yml} carries the given id, version and {@code Host_Api}, and
	 * (optionally) a {@code Depends} list - for tests driving the remove-warns-dependants path (gi=84).
	 */
	public static Path moduleJar(Path jar, String id, String version, String hostApi, String... depends)
			throws IOException {
		StringBuilder yaml = new StringBuilder("Id: ").append(id)
				.append("\nVersion: ").append(version)
				.append("\nMain: org.example.").append(id).append(".Main")
				.append("\nHost_Api: ").append(hostApi).append('\n');

		if (depends.length > 0) {
			yaml.append("Depends:\n");
			for (String depend : depends) yaml.append("  - ").append(depend).append('\n');
		}

		return TestJars.builder(jar).text(ModuleDescriptorReader.DESCRIPTOR_ENTRY, yaml.toString()).write();
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
