package de.extraitems;

import java.nio.file.Path;

/** Compile against target/classes, then run de.extraitems.ExportPack with PACK_DIRECTORY OUTPUT_ZIP. */
class ExportPack {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("PACK_DIRECTORY OUTPUT_ZIP erforderlich");
        byte[] pack = PackArchive.build(Path.of(args[0]));
        PackArchive.writeAtomic(Path.of(args[1]),pack);
        System.out.println("SHA-1 " + PackArchive.hex(PackArchive.sha1(pack)) + " | " + pack.length + " Bytes");
    }
}
