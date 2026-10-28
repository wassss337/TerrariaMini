package terrariamini;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

public final class ModManager {
    private static final List<TerrariaMiniAPI> mods = new ArrayList<>();
    private static final List<URLClassLoader> loaders = new ArrayList<>();
    private static final Map<String, List<TerrariaMiniAPI>> commandHandlers = new HashMap<>();
    private static GameContext ctx;

    private ModManager() {}

    public static void loadAll(GameContext context) {
        ctx = context;
        mods.clear();
        closeAll();
        File dir = new File("mods");
        dir.mkdirs();
        File[] jars = dir.listFiles((d, n) -> n.endsWith(".jar"));
        if (jars == null) return;

        for (File jar : jars) {
            try {
                URLClassLoader cl = new URLClassLoader(
                        new URL[]{ jar.toURI().toURL() },
                        TerrariaMini.class.getClassLoader());
                loaders.add(cl);
                List<String> classes = readClassNames(jar);
                int loaded = 0;
                for (String cn : classes) {
                    try {
                        Class<?> c = Class.forName(cn, true, cl);
                        if (TerrariaMiniAPI.class.isAssignableFrom(c)
                                && !c.isInterface()
                                && !Modifier.isAbstract(c.getModifiers())) {
                            TerrariaMiniAPI mod = (TerrariaMiniAPI) c.getDeclaredConstructor().newInstance();
                            mods.add(mod);
                            mod.onLoad(ctx);
                            RecipeRegistry rr = new RecipeRegistry(TerrariaMini.Crafting.recipes);
                            mod.registerRecipes(rr);
                            System.out.println("[Моды] Загружен: " + mod.getName() + " v" + mod.getVersion());
                            loaded++;
                        }
                    } catch (Throwable t) {
                        System.out.println("[Моды] Ошибка класса " + cn + ": " + t);
                    }
                }
                if (loaded == 0) System.out.println("[Моды] " + jar.getName() + " — нет классов TerrariaMiniAPI");
            } catch (Throwable t) {
                System.out.println("[Моды] Ошибка jar " + jar.getName() + ": " + t);
            }
        }
        System.out.println("[Моды] Всего модов: " + mods.size());
    }

    /** Ищет все .class в jar. */
    private static List<String> readClassNames(File jar) throws IOException {
        List<String> names = new ArrayList<>();
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(jar)) {
            Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.endsWith(".class") && !n.contains("$")) {
                    names.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        }
        return names;
    }

    public static void tick() {
        if (ctx == null) return;
        for (TerrariaMiniAPI m : mods) {
            try { m.onTick(ctx); } catch (Throwable t) { t.printStackTrace(); }
        }
    }
    public static void playerJoin() {
        if (ctx == null) return;
        for (TerrariaMiniAPI m : mods) {
            try { m.onPlayerJoin(ctx); } catch (Throwable t) { t.printStackTrace(); }
        }
    }
    public static void blockBreak(int x, int y, int id) {
        if (ctx == null) return;
        for (TerrariaMiniAPI m : mods) {
            try { m.onBlockBreak(ctx, x, y, id); } catch (Throwable t) { t.printStackTrace(); }
        }
    }
    public static void blockPlace(int x, int y, int id) {
        if (ctx == null) return;
        for (TerrariaMiniAPI m : mods) {
            try { m.onBlockPlace(ctx, x, y, id); } catch (Throwable t) { t.printStackTrace(); }
        }
    }
    public static void monsterKilled(int type) {
        if (ctx == null) return;
        for (TerrariaMiniAPI m : mods) {
            try { m.onMonsterKilled(ctx, type); } catch (Throwable t) { t.printStackTrace(); }
        }
    }

    /** Вызывается из executeChatCommand, если команда /mod <name> ... */
    public static boolean dispatchCommand(String[] args) {
        if (ctx == null || mods.isEmpty()) return false;
        for (TerrariaMiniAPI m : mods) {
            try { m.onCommand(ctx, args); } catch (Throwable t) { t.printStackTrace(); }
        }
        return true;
    }

    public static void closeAll() {
        for (URLClassLoader cl : loaders) {
            try { cl.close(); } catch (Exception ignored) {}
        }
        loaders.clear();
        mods.clear();
    }
}