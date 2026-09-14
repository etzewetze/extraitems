package de.extraitems;

/** Compact versioned chunk format, deliberately independent of entity UUIDs. */
record PlantRecord(int x, int y, int z, String crop, int stage, int progress) {
    String position() { return x + "," + y + "," + z; }
    String encode() { return position() + ";" + crop + ";" + stage + ";" + progress; }
    static PlantRecord decode(String line, int chunkX, int chunkZ, int minY, int maxY) {
        String[] parts = line.split(";", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Ungültiger Pflanzeneintrag");
        String[] xyz = parts[0].split(",", -1);
        if (xyz.length != 3 || !parts[1].matches("[a-z0-9_]+")) throw new IllegalArgumentException("Ungültige Pflanzenposition/ID");
        int x = Integer.parseInt(xyz[0]), y = Integer.parseInt(xyz[1]), z = Integer.parseInt(xyz[2]);
        int stage = Integer.parseInt(parts[2]), progress = Integer.parseInt(parts[3]);
        if ((x >> 4) != chunkX || (z >> 4) != chunkZ || y <= minY || y >= maxY || stage < 0 || stage > 15 || progress < 0 || progress > 86400)
            throw new IllegalArgumentException("Pflanzenposition oder Fortschritt außerhalb des gültigen Bereichs");
        return new PlantRecord(x, y, z, parts[1], stage, progress);
    }
    PlantRecord tick(int secondsPerStage, int maxStage, boolean conditionsMet) {
        if (!conditionsMet || stage >= maxStage) return this;
        return progress + 1 >= secondsPerStage ? new PlantRecord(x,y,z,crop,stage+1,0) : new PlantRecord(x,y,z,crop,stage,progress+1);
    }
    PlantRecord stage(int value) { return new PlantRecord(x,y,z,crop,value,0); }
}
