package terrariamini;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import terrariamini.ItemStack;

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
                            try { mod.registerContent(new ModRegistry(mod.getName())); }
                            catch (Throwable t) { t.printStackTrace(); }
                            mod.onLoad(ctx);
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
public static final class ModRegistry {
        private final String modName;
        public ModRegistry(String modName) { this.modName = modName; }

        public ItemBuilder item(String key) { return new ItemBuilder(key); }
        public final class ItemBuilder {
            private final String key;
            private String name; private int maxStack = 999, maxDur = 0; private String tooltip = "";
            private java.util.function.BiConsumer<GameContext, ItemStack> onUse = null;
            private ItemBuilder(String k) { key = modName + ":" + k; name = k; }
            public ItemBuilder name(String n)    { name = n; return this; }
            public ItemBuilder maxStack(int n)   { maxStack = n; return this; }
            public ItemBuilder durability(int n) { maxDur = n; return this; }
            public ItemBuilder tooltip(String t) { tooltip = t; return this; }
            public ItemBuilder onUse(java.util.function.BiConsumer<GameContext, ItemStack> a) { onUse = a; return this; }
            public ItemDef build() {
                ItemDef d = Items.register(key, name, maxStack, maxDur, tooltip);
                if (onUse != null) ItemUse.register(d.numericId, onUse);
                return d;
            }
        }

        public BlockBuilder block(String key) { return new BlockBuilder(key); }
        public final class BlockBuilder {
            private final String key;
            private String name; private boolean solid = true; private int hardness = 2, light = 0;
            private BlockBuilder(String k) { key = modName + ":" + k; name = k; }
            public BlockBuilder name(String n)   { name = n; return this; }
            public BlockBuilder solid(boolean s){ solid = s; return this; }
            public BlockBuilder hardness(int h)  { hardness = h; return this; }
            public BlockBuilder light(int l)     { light = l; return this; }
            public BlockDef build() { return Blocks.register(key, name, solid, hardness, light); }
        }

        public MobDef mob(String key, int hp, int dmg) { return Mobs.register(modName + ":" + key, hp, dmg); }
        public void mechanic(String id, Mechanic m) { Mechanics.register(modName + ":" + id, m); }
        public void recipe(int resId, int resCnt, int[] ingIds, int[] ingCnts, boolean needsTable) {
            TerrariaMini.Crafting.recipes.add(new TerrariaMini.Crafting.Recipe(resId, resCnt, ingIds, ingCnts, needsTable, "mod:" + modName));
        }
        public void command(String name, CommandHandler h) { Commands.register(name, h); }

        public interface Mechanic {
            void onTick(GameContext ctx);
            default void onPlayerJoin(GameContext ctx) {}
            default void onBlockBreak(GameContext ctx, int x, int y, int blockId) {}
        }
        public interface CommandHandler {
            void execute(GameContext ctx, String[] args);
            default List<String> complete(GameContext ctx, String[] args) { return List.of(); }
        }
    }

    // ---------- ITEM REGISTRY ----------
    public static final class ItemDef implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String key, name, tooltip;
        public final int numericId, maxStack, maxDurability;
        ItemDef(String k, String n, int id, int ms, int md, String t) {
            key = k; name = n; numericId = id; maxStack = ms; maxDurability = md; tooltip = t;
        }
    }
    public static final class Items {
        private static final Map<String, ItemDef> byKey = new LinkedHashMap<>();
        private static final Map<Integer, ItemDef> byId = new HashMap<>();
        private static int nextId = 1000;
        public static ItemDef register(String k, String n, int ms, int md, String t) {
            ItemDef d = new ItemDef(k, n, nextId++, ms, md, t);
            byKey.put(k, d); byId.put(d.numericId, d); return d;
        }
        public static ItemDef get(String k) { return byKey.get(k); }
        public static ItemDef get(int id)   { return byId.get(id); }
        public static Collection<ItemDef> all() { return byKey.values(); }
        public static ItemStack create(String k, int c) {
            ItemDef d = byKey.get(k); if (d == null) return null;
            return new ItemStack(d.numericId, c);
        }
    }

    // ---------- BLOCK REGISTRY ----------
    public static final class BlockDef implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String key, name; public final boolean solid;
        public final int numericId, hardness, light;
        BlockDef(String k, String n, int id, boolean s, int h, int l) {
            key = k; name = n; numericId = id; solid = s; hardness = h; light = l;
        }
    }
    public static final class Blocks {
        private static final Map<String, BlockDef> byKey = new LinkedHashMap<>();
        private static final Map<Integer, BlockDef> byId = new HashMap<>();
        private static int nextId = 2000;
        public static BlockDef register(String k, String n, boolean s, int h, int l) {
            BlockDef d = new BlockDef(k, n, nextId++, s, h, l);
            byKey.put(k, d); byId.put(d.numericId, d); return d;
        }
        public static BlockDef get(String k) { return byKey.get(k); }
        public static BlockDef get(int id)   { return byId.get(id); }
        public static Collection<BlockDef> all() { return byKey.values(); }
    }

    // ---------- MOB REGISTRY ----------
    public static final class MobDef implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public final String key; public final int hp, damage;
        MobDef(String k, int h, int d) { key = k; hp = h; damage = d; }
    }
    public static final class Mobs {
        private static final Map<String, MobDef> byKey = new LinkedHashMap<>();
        private static final Map<Integer, MobDef> byId = new HashMap<>();
        private static int nextId = 5000;
        public static MobDef register(String k, int h, int d) {
            MobDef m = new MobDef(k, h, d);
            byKey.put(k, m); byId.put(nextId++, m); return m;
        }
        public static MobDef get(String k) { return byKey.get(k); }
        public static MobDef get(int id)   { return byId.get(id); }
    }

    // ---------- MECHANICS ----------
    public static final class Mechanics {
        private static final Map<String, ModRegistry.Mechanic> all = new LinkedHashMap<>();
        public static void register(String id, ModRegistry.Mechanic m) { all.put(id, m); }
        public static Collection<ModRegistry.Mechanic> all() { return all.values(); }
    }

    // ---------- ITEM USE ----------
    public static final class ItemUse {
        private static final Map<Integer, java.util.function.BiConsumer<GameContext, ItemStack>> map = new HashMap<>();
        public static void register(int id, java.util.function.BiConsumer<GameContext, ItemStack> fn) { map.put(id, fn); }
        public static java.util.function.BiConsumer<GameContext, ItemStack> get(int id) { return map.get(id); }
    }

    // ---------- MOD COMMANDS ----------
    public static final class Commands {
        private static final Map<String, ModRegistry.CommandHandler> all = new HashMap<>();
        public static void register(String n, ModRegistry.CommandHandler h) { all.put(n, h); }
        public static ModRegistry.CommandHandler get(String n) { return all.get(n); }
        public static Set<String> names() { return all.keySet(); }
    }
}