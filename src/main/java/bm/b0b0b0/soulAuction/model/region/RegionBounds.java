package bm.b0b0b0.soulAuction.model.region;

public record RegionBounds(
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
) {

    public int centerX() {
        return (minX + maxX) / 2;
    }

    public int centerY() {
        return (minY + maxY) / 2;
    }

    public int centerZ() {
        return (minZ + maxZ) / 2;
    }

    public int spanX() {
        return maxX - minX + 1;
    }

    public int spanY() {
        return maxY - minY + 1;
    }

    public int spanZ() {
        return maxZ - minZ + 1;
    }

    public long blockVolume() {
        return (long) spanX() * spanY() * spanZ();
    }

    public String formattedSizeLowercase() {
        return spanX() + "x" + spanY() + "x" + spanZ();
    }

    public String formattedMinCorner() {
        return minX + ", " + minY + ", " + minZ;
    }

    public String formattedMaxCorner() {
        return maxX + ", " + maxY + ", " + maxZ;
    }

    public String formattedCenter() {
        return centerX() + ", " + centerY() + ", " + centerZ();
    }

    public String formattedSize() {
        return spanX() + "×" + spanY() + "×" + spanZ();
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
