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
    @Test void archiveIsDeterministicRootedAndHashTracksContent() throws Exception {
        Files.writeString(directory.resolve("pack.mcmeta"),"{}");
        Files.createDirectories(directory.resolve("assets/extraitems"));
        Files.writeString(directory.resolve("assets/extraitems/example.json"),"{}");
        byte[] first = PackArchive.build(directory), second = PackArchive.build(directory);
        assertArrayEquals(first,second);
        Set<String> names = new HashSet<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(first))) { for (ZipEntry e; (e=zip.getNextEntry())!=null;) names.add(e.getName()); }
        assertEquals(Set.of("pack.mcmeta","assets/extraitems/example.json"),names);
        Files.writeString(directory.resolve("assets/extraitems/example.json"),"{\"new\":true}");
        assertFalse(Arrays.equals(PackArchive.sha1(first),PackArchive.sha1(PackArchive.build(directory))));
    }
    @Test void archiveRefusesMissingMetadataAndSymlinks() throws Exception {
        assertThrows(IOException.class,()->PackArchive.build(directory));
        Files.writeString(directory.resolve("pack.mcmeta"),"{}");
        Files.createSymbolicLink(directory.resolve("outside"),directory.resolve("pack.mcmeta"));
        assertThrows(IOException.class,()->PackArchive.build(directory));
    }
    @Test void atomicPublicationReplacesAnOlderPack() throws Exception {
        Path target = directory.resolve("generated/extraitems.zip");
        PackArchive.writeAtomic(target,new byte[]{1}); PackArchive.writeAtomic(target,new byte[]{2,3});
        assertArrayEquals(new byte[]{2,3},Files.readAllBytes(target));
        try (var paths=Files.list(target.getParent())) { assertEquals(1,paths.count()); }
    }
    @Test void httpServesExactBytesAndDoesNotExposeOtherPaths() throws Exception {
        byte[] bytes = {0x50,0x4b,3,4,1,2,3};
        try (var server = new PackHttpServer("127.0.0.1",0,bytes,PackArchive.hex(PackArchive.sha1(bytes))); var client = HttpClient.newHttpClient()) {
            String base = "http://127.0.0.1:"+server.port();
            var response = client.send(HttpRequest.newBuilder(URI.create(base+"/extraitems.zip?v=1")).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,response.statusCode()); assertArrayEquals(bytes,response.body());
            assertEquals("application/zip",response.headers().firstValue("Content-Type").orElseThrow());
            assertEquals(404,client.send(HttpRequest.newBuilder(URI.create(base+"/config.yml")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(404,client.send(HttpRequest.newBuilder(URI.create(base+"/extraitems.zip/../config.yml")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            assertEquals(405,client.send(HttpRequest.newBuilder(URI.create(base+"/extraitems.zip")).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding()).statusCode());
            var head = client.send(HttpRequest.newBuilder(URI.create(base+"/extraitems.zip")).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,head.statusCode()); assertEquals(0,head.body().length);
            assertEquals("7",head.headers().firstValue("Content-Length").orElseThrow());
        }
    }
    @Test void urlValidationRejectsLocalPathsCredentialsAndFragments() {
        for (String url : List.of("","pack.zip","file:///etc/passwd","https://user:pass@example.org/pack.zip","https://example.org/pack.zip#foo"))
            assertThrows(IllegalArgumentException.class,()->PackService.validateUrl(url));
        assertEquals("https://example.org/pack.zip",PackService.validateUrl("https://example.org/pack.zip"));
    }
}
