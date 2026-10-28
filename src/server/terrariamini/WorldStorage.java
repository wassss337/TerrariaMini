package terrariamini;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class WorldStorage {
    public static final String DATA_FILE = "data/region.world";
    private WorldStorage() {}

    public static void saveWorldFolder(File root, TerrariaMini.World world) throws IOException {
        File dataDir = new File(root, "data");
        dataDir.mkdirs();
        File f = new File(root, DATA_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new GZIPOutputStream(new FileOutputStream(f)))) {
            oos.writeObject(world);
        }
    }

    public static TerrariaMini.World loadWorldFolder(File root) throws IOException, ClassNotFoundException {
        File f = new File(root, DATA_FILE);
        if (!f.exists()) return null;
        try (ObjectInputStream ois = new ObjectInputStream(
                new GZIPInputStream(new FileInputStream(f)))) {
            TerrariaMini.World w = (TerrariaMini.World) ois.readObject();
            w.ensureRuntimeState();
            return w;
        }
    }

    public static List<File> listWorlds(File savesRoot) {
        List<File> out = new ArrayList<>();
        File[] dirs = savesRoot.listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) if (new File(d, DATA_FILE).exists()) out.add(d);
        out.sort((a, b) -> Long.compare(
                new File(b, DATA_FILE).lastModified(),
                new File(a, DATA_FILE).lastModified()));
        return out;
    }

    public static File createWorldFolder(File savesRoot, String name) throws IOException {
        File root = new File(savesRoot, name);
        new File(root, "data").mkdirs();
        new File(root, "datapacks").mkdirs();
        new File(root, "playerdata").mkdirs();
        return root;
    }

    public static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}