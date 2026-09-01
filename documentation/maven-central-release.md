# Releasing to Maven Central (Sonatype Central Portal)

This project is configured to satisfy every requirement in
<https://central.sonatype.org/publish/requirements/>. This document covers the one-time account
setup and the per-release procedure.

## How each Central requirement is met

| Requirement                            | How it is satisfied                                                                                                                                              |
|----------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| File checksums (`.md5`, `.sha1`)       | Generated automatically by `central-publishing-maven-plugin` for every file in the upload bundle. Its `checksums` default is `all`, so the optional `.sha256`/`.sha512` are produced too. Nothing to do manually. |
| Sources jar (`-sources.jar`)           | `maven-source-plugin` in the `central-release` profile (parent POM).                                                                                             |
| Javadoc jar (`-javadoc.jar`)           | `maven-javadoc-plugin` in the `central-release` profile (doclint disabled so legacy comments don't fail the build).                                              |
| GPG/PGP signatures (`.asc`)            | `maven-gpg-plugin` in the `central-release` profile, bound to `verify`.                                                                                          |
| Correct coordinates                    | `groupId` `org.luckyraven` — requires namespace verification, see below. `version` comes from `${revision}` (e.g. `0.8.0`) and must never end in `-SNAPSHOT` for a release. |
| Project name, description, URL         | Declared in the parent POM, inherited by all modules.                                                                                                            |
| License information                    | MIT, declared in the parent POM `<licenses>`; `LICENSE` file at the repo root.                                                                                   |
| Developer information                  | Parent POM `<developers>`.                                                                                                                                       |
| SCM information                        | Parent POM `<scm>` pointing at the GitHub repository.                                                                                                            |
| Resolvable `<version>` in deployed POMs| `flatten-maven-plugin` (`resolveCiFriendliesOnly`) rewrites the CI-friendly `${revision}` placeholder into the literal version in every installed/deployed POM.  |

The shaded server jar (`gangland-build`) is **excluded** from Central publishing via
`<excludeArtifacts>` — it is a fat assembly jar with no sources of its own and is distributed
through SpigotMC/GitHub Releases instead. Local builds still produce it exactly as before.

## One-time setup

### 1. Central Portal account and namespace

1. Sign in at <https://central.sonatype.com/> (GitHub login is easiest).
2. Register the namespace `org.luckyraven` under *Namespaces*. Because it is a reverse-domain
   namespace, the portal asks you to prove ownership of **luckyraven.org** by adding a DNS TXT
   record with the verification key it shows you. If you do not own that domain, use the
   GitHub-backed namespace `io.github.luckyluckiest` instead (verified by creating a temporary
   public repo named after the verification key) — that would mean changing the `groupId` in the
   POMs, but Java packages could stay `org.luckyraven`.
3. Under your account, *Generate User Token*. Put it in `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>central</id>
            <username><!-- token username --></username>
            <password><!-- token password --></password>
        </server>
    </servers>
</settings>
```

The `<id>central</id>` must match the `publishingServerId` configured in the parent POM.

### 2. GPG key

Install [Gpg4win](https://gpg4win.org/) (or `gpg` from Git for Windows), then:

```
gpg --gen-key
gpg --keyserver keyserver.ubuntu.com --send-keys <YOUR_KEY_ID>
```

Central verifies signatures against public keyservers (`keyserver.ubuntu.com`,
`keys.openpgp.org`, `pgp.mit.edu`), so the public key must be uploaded before the first release.
The `maven-gpg-plugin` will prompt for the passphrase via pinentry during the build; to select a
specific key, add `-Dgpg.keyname=<YOUR_KEY_ID>`.

## Per-release procedure

1. Set the release version: update `<revision>` in the root `pom.xml` (must not end in
   `-SNAPSHOT`).
2. Build and publish the bundle:

```
mvn clean deploy -P central-release
```

3. The plugin uploads the bundle to the Central Portal and waits for validation. Since
   `autoPublish` is `false`, nothing goes live yet: review the validated deployment at
   <https://central.sonatype.com/publishing/deployments> and press **Publish** (or **Drop** to
   discard). Once published, a release is permanent and cannot be removed or altered.
4. Tag the release in git: `git tag v0.8.0 && git push origin v0.8.0`.

To publish without the manual portal step (e.g. from CI), flip `<autoPublish>` to `true` in the
parent POM or pass `-DautoPublish=true`.

## Notes and caveats

- **SNAPSHOT dependencies**: `spigot-api`, `anvilgui`, and `citizens-main` are SNAPSHOT versions.
  Central accepts this (only the project's own version is validated), and the parent POM's
  `<repositories>` are published with it so consumers can resolve them — but pinning released
  versions where possible is cleaner.
- **Local NMS artifacts**: the `version-*` modules depend on `org.spigotmc:spigot` NMS jars that
  only exist in your local repository via BuildTools. Consumers of those published modules will
  not be able to resolve that dependency (it is `provided` scope, so it only matters if they
  compile against it).
- A validation failure in the portal costs nothing — fix the issue and deploy again. Only
  **Publish** is irreversible.
