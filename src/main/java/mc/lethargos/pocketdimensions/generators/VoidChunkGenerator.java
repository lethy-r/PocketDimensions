package mc.lethargos.pocketdimensions.generators;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/**
 * Generates empty void worlds with a single small platform so players have
 * somewhere to stand. Uses the classic ChunkGenerator API, which is stable
 * across every supported server version.
 */
public class VoidChunkGenerator extends ChunkGenerator {

    public static final int PLATFORM_Y = 64;

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData data = createChunkData(world);
        if (chunkX == 0 && chunkZ == 0) {
            for (int x = 5; x <= 11; x++) {
                for (int z = 5; z <= 11; z++) {
                    data.setBlock(x, PLATFORM_Y, z, Material.SMOOTH_STONE);
                }
            }
        }
        return data;
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return true;
    }
}
