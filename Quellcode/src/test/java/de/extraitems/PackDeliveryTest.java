package de.extraitems;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

class PackDeliveryTest {
    @TempDir Path directory;

    @Test
    void archiveIsDeterministicRootedAndHashTracksContent() throws Exception {
        Files.writeString(directory.resolve("pack.mcmeta"), "{}");
        Files.createDirectories(directory.resolve("assets/extraitems"));
        Files.writeString(directory.resolve("assets/extraitems/example.json"), "{}");
        byte[] first = PackArchive.build(directory);
        byte[] second = PackArchive.build(directory);
        assertArrayEquals(first, second);

        Set<String> names = new HashSet<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(first))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) names.add(entry.getName());
        }
        assertEquals(Set.of("pack.mcmeta", "assets/extraitems/example.json"), names);

        Files.writeString(directory.resolve("assets/extraitems/example.json"), "{\"new\":true}");
        assertFalse(Arrays.equals(PackArchive.sha1(first), PackArchive.sha1(PackArchive.build(directory))));
    }

    @Test
    void archiveRefusesMissingMetadataAndSymlinks() throws Exception {
        assertThrows(IOException.class, () -> PackArchive.build(directory));
        Files.writeString(directory.resolve("pack.mcmeta"), "{}");
        Files.createSymbolicLink(directory.resolve("outside"), directory.resolve("pack.mcmeta"));
        assertThrows(IOException.class, () -> PackArchive.build(directory));
    }

    @Test
    void importedOverlayIsMergedAndCannotSilentlyOverrideBaseAssets() throws Exception {
        Path base = directory.resolve("base");
        Path overlay = directory.resolve("overlay");
        Files.createDirectories(base.resolve("assets/base"));
        Files.createDirectories(overlay.resolve("assets/demo"));
        Files.writeString(base.resolve("pack.mcmeta"), "{}");
        Files.writeString(base.resolve("assets/base/item.json"), "base");
        Files.writeString(overlay.resolve("assets/demo/item.json"), "imported");

        Set<String> names = new HashSet<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(PackArchive.build(base, overlay)))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) names.add(entry.getName());
        }
        assertEquals(Set.of("pack.mcmeta", "assets/base/item.json", "assets/demo/item.json"), names);

        Files.createDirectories(overlay.resolve("assets/base"));
        Files.writeString(overlay.resolve("assets/base/item.json"), "different");
        assertThrows(IOException.class, () -> PackArchive.build(base, overlay));
    }

    @Test
    void atomicPublicationReplacesAnOlderPack() throws Exception {
        Path target = directory.resolve("generated/extraitems.zip");
        PackArchive.writeAtomic(target, new byte[]{1});
        PackArchive.writeAtomic(target, new byte[]{2, 3});
        assertArrayEquals(new byte[]{2, 3}, Files.readAllBytes(target));
        try (var paths = Files.list(target.getParent())) {
            assertEquals(1, paths.count());
        }
    }

    @Test
    void bundledPackRevisionUpdatesOldDefaultsAndKeepsABackup() throws Exception {
        Path target = directory.resolve("resourcepack/assets/extraitems/model.json");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "old-or-custom");

        var first = PackDefaults.sync(directory, List.of("assets/extraitems/model.json"), "2",
                path -> "fixed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1, first.updated());
        assertEquals("fixed", Files.readString(target));
        assertEquals("old-or-custom", Files.readString(first.backup().resolve("assets/extraitems/model.json")));

        Files.writeString(target, "server-customization");
        var second = PackDefaults.sync(directory, List.of("assets/extraitems/model.json"), "2",
                path -> "fixed".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0, second.updated());
        assertEquals("server-customization", Files.readString(target));
    }

    @Test
    void bundledPackSyncInstallsMissingAssetsWithoutDiscardingCurrentRevisionChanges() throws Exception {
        PackDefaults.sync(directory, List.of("pack.mcmeta"), "2", path -> "{}".getBytes());
        Path custom = directory.resolve("resourcepack/pack.mcmeta");
        Files.writeString(custom, "custom");

        var result = PackDefaults.sync(directory, List.of("pack.mcmeta", "new.json"), "2",
                path -> path.equals("pack.mcmeta") ? "{}".getBytes() : "new".getBytes());
        assertEquals("custom", Files.readString(custom));
        assertEquals("new", Files.readString(directory.resolve("resourcepack/new.json")));
        assertEquals(1, result.installed());
        assertEquals(0, result.updated());
    }

    @Test
    void httpServesExactBytesAndDoesNotExposeOtherPaths() throws Exception {
        byte[] bytes = {0x50, 0x4b, 3, 4, 1, 2, 3};
        try (var server = new PackHttpServer("127.0.0.1", 0, bytes,
                PackArchive.hex(PackArchive.sha1(bytes))); var client = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:" + server.port();
            var response = client.send(HttpRequest.newBuilder(
                    URI.create(base + "/extraitems.zip?v=1")).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, response.statusCode());
            assertArrayEquals(bytes, response.body());
            assertEquals("application/zip", response.headers().firstValue("Content-Type").orElseThrow());

            assertEquals(404, client.send(HttpRequest.newBuilder(URI.create(base + "/config.yml"))
                    .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(404, client.send(HttpRequest.newBuilder(
                    URI.create(base + "/extraitems.zip/../config.yml")).GET().build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(405, client.send(HttpRequest.newBuilder(URI.create(base + "/extraitems.zip"))
                    .POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.discarding()).statusCode());

            var head = client.send(HttpRequest.newBuilder(URI.create(base + "/extraitems.zip"))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, head.statusCode());
            assertEquals(0, head.body().length);
            assertEquals("7", head.headers().firstValue("Content-Length").orElseThrow());
        }
    }

    @Test
    void urlValidationRejectsLocalPathsCredentialsAndFragments() {
        for (String url : List.of("", "pack.zip", "file:///etc/passwd",
                "https://user:pass@example.org/pack.zip", "https://example.org/pack.zip#foo")) {
            assertThrows(IllegalArgumentException.class, () -> PackService.validateUrl(url));
        }
        assertEquals("https://example.org/pack.zip",
                PackService.validateUrl("https://example.org/pack.zip"));
    }

    @Test
    void joiningAddressIsReducedToItsHost() {
        assertEquals("play.example.org", PackService.joiningHost("play.example.org:25565"));
        assertEquals("127.0.0.1", PackService.joiningHost("127.0.0.1:25565"));
        assertEquals("2001:db8::1", PackService.joiningHost("[2001:db8::1]:25565"));
        assertThrows(IllegalStateException.class, () -> PackService.joiningHost(""));
        assertThrows(IllegalStateException.class, () -> PackService.joiningHost("0.0.0.0:25565"));
    }

    @Test
    void selfHostedUrlUsesJoiningHostAndOmitsDefaultPorts() {
        assertEquals("http://play.example.org/extraitems.zip?sha1=abc",
                PackService.buildSelfHostedUrl("http", "play.example.org", 80, "abc"));
        assertEquals("https://play.example.org/extraitems.zip?sha1=abc",
                PackService.buildSelfHostedUrl("https", "play.example.org", 443, "abc"));
        assertEquals("http://127.0.0.1:8123/extraitems.zip?sha1=abc",
                PackService.buildSelfHostedUrl("http", "127.0.0.1", 8123, "abc"));
        assertEquals("http://[2001:db8::1]:8123/extraitems.zip?sha1=abc",
                PackService.buildSelfHostedUrl("http", "2001:db8::1", 8123, "abc"));
    }
}
