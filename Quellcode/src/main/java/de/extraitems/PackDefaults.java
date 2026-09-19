package de.extraitems;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Installs bundled pack assets and safely refreshes them once per pack revision. */
final class PackDefaults {
    interface Source { byte[] read(String relative) throws IOException; }
    record Result(int installed, int updated, Path backup) {}

    private PackDefaults() {}

    static Result sync(Path dataRoot, List<String> manifest, String revision, Source source) throws IOException {
        if (revision == null || !revision.matches("[a-zA-Z0-9._-]+")) {
            throw new IOException("Ungültige Pack-Revision");
        }
        Path packRoot = dataRoot.resolve("resourcepack").toAbsolutePath().normalize();
        Path marker = dataRoot.resolve("generated/default-pack-revision.txt").toAbsolutePath().normalize();
        String installedRevision = Files.isRegularFile(marker)
                ? Files.readString(marker, StandardCharsets.UTF_8).trim() : "legacy";
        boolean upgrade = !revision.equals(installedRevision);
        Path backup = null;
        int installed = 0, updated = 0;

        for (String relative : manifest) {
            if (relative == null || relative.isBlank()) continue;
            Path target = safeTarget(packRoot, relative);
            byte[] bundled = Objects.requireNonNull(source.read(relative), "Pack-Ressource fehlt: " + relative);
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                PackArchive.writeAtomic(target, bundled);
                installed++;
                continue;
            }
            refuseSymlinks(packRoot, target);
            if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Pack-Ziel ist keine normale Datei: " + relative);
            }
            byte[] current = Files.readAllBytes(target);
            if (!upgrade || Arrays.equals(current, bundled)) continue;

            if (backup == null) {
                Path backupRoot = dataRoot.resolve("resourcepack-backups").toAbsolutePath().normalize();
                Files.createDirectories(backupRoot);
                backup = Files.createTempDirectory(backupRoot, "before-" + revision + "-");
            }
            Path saved = backup.resolve(relative).normalize();
            if (!saved.startsWith(backup)) throw new IOException("Ungültiger Backup-Pfad: " + relative);
            Files.createDirectories(saved.getParent());
            Files.copy(target, saved, StandardCopyOption.COPY_ATTRIBUTES);
            PackArchive.writeAtomic(target, bundled);
            updated++;
        }

        if (upgrade) PackArchive.writeAtomic(marker, (revision + "\n").getBytes(StandardCharsets.UTF_8));
        return new Result(installed, updated, backup);
    }

    private static Path safeTarget(Path packRoot, String relative) throws IOException {
        Path source = Path.of(relative.replace('\\', '/'));
        Path target = packRoot.resolve(source).normalize();
        if (source.isAbsolute() || !target.startsWith(packRoot) || relative.contains(":")) {
            throw new IOException("Ungültiger Manifestpfad: " + relative);
        }
        refuseSymlinks(packRoot, target.getParent());
        return target;
    }

    private static void refuseSymlinks(Path root, Path target) throws IOException {
        Path current = root;
        if (Files.isSymbolicLink(current)) throw new IOException("Symlink im Pack-Pfad: " + current);
        Path relative = root.relativize(target);
        for (Path part : relative) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("Symlink im Pack-Pfad: " + current);
        }
    }
}
