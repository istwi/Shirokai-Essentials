package dev.shirokai.essentials;
import dev.shirokai.essentials.migrations.EssentialImporter;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EssentialImporterTest {
    @TempDir Path dir;
    NbtCompound location(){var n=new NbtCompound();n.putString("WorldRegistryKey","multiworld:custom");n.putDouble("x",1.5);n.putDouble("y",64);n.putDouble("z",-2.5);n.putFloat("headYaw",90);n.putFloat("pitch",10);return n;}
    void write(Path p,NbtCompound data)throws Exception{Files.createDirectories(p.getParent());var root=new NbtCompound();root.put("data",data);NbtIo.writeCompressed(root,p);}
    @Test void readsActual0352NbtAndPreservesNamespacePermissionAndSources()throws Exception{
        UUID id=UUID.randomUUID();var p=new NbtCompound();p.putUuid("playerUuid",id);var homes=new NbtCompound();homes.put("Casa",location());p.put("homes",homes);Path file=dir.resolve("modplayerdata").resolve(id+".dat");write(file,p);byte[] original=Files.readAllBytes(file);
        var world=new NbtCompound();world.put("spawn",location());var warps=new NbtCompound();var warp=location();warp.putString("permissionString","vip");warps.put("VIP",warp);world.put("warps",warps);write(dir.resolve("essentialcommands/world_data.dat"),world);
        var plan=EssentialImporter.inspect(dir);assertEquals("casa",plan.homes().getFirst().name());assertEquals("multiworld:custom",plan.homes().getFirst().position().dimension());assertEquals("vip",plan.warps().getFirst().permission());assertEquals(90,plan.spawn().yaw());assertArrayEquals(original,Files.readAllBytes(file));assertEquals(2,plan.hashes().size());
    }
    @Test void mismatchedUuidAbortsBeforeAnyWrite()throws Exception{
        var p=new NbtCompound();p.putUuid("playerUuid",UUID.randomUUID());write(dir.resolve("modplayerdata").resolve(UUID.randomUUID()+".dat"),p);assertThrows(java.io.IOException.class,()->EssentialImporter.inspect(dir));
    }
    @Test void malformedNumericFieldsNeverSilentlyBecomeZero(){var n=location();n.putString("x","invalid");assertThrows(IllegalArgumentException.class,()->EssentialImporter.position(n));}
    @Test void missingSourcesFailExplicitly(){assertThrows(java.io.IOException.class,()->EssentialImporter.inspect(dir));}
}
