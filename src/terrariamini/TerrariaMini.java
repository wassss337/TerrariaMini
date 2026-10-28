package terrariamini;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.sound.sampled.*;
import javax.swing.*;

public class TerrariaMini extends JPanel implements ActionListener, KeyListener, MouseListener, MouseMotionListener, MouseWheelListener {
    public void apiChat(String text, Color color) { addChatMessage(text, color); }
    public Player apiPlayer() { return player; }
    public World apiWorld() { return world; }
    public List<Monster> apiMonsters() { return monsters; }
    public long apiGameTime() { return gameTime; }
    public void apiSetGameTime(long t) { gameTime = t; }
    public boolean apiIsNight() { return isNight(); }
    public void apiGiveItem(int id, int count) { addToInventory(ItemStack.of(id, count)); }
    public void apiAddXp(int a) { addXp(a); }
    public static final int TILE = 32;
    public static final int VIEW_W = 30;
    public static final int VIEW_H = 20;
    public static final int SCREEN_W = TILE * VIEW_W;
    public static final int SCREEN_H = TILE * VIEW_H;
    public static final int INV_COLS = 9;
    public static final int INV_ROWS = 4;
    public static final int SLOT = 44;
    public static final int WORLD_W = 2400;
    public static final int WORLD_H = 400;
    public static long currentGameTime = 0;
    private Monster activeBoss = null;
    private int bossMaxHp = 0;
    private static final int BOSS_SLIME = 100;
    private static final int BOSS_EYE = 101;
    private static final int BOSS_SKELETON = 102;
    private static final int BOSS_HELL = 103;
    private String playerName = "Игрок";
    private boolean typingName = false;
    private boolean isMultiplayer = false;
    private long lastSpacePress = 0;
    private int myPlayerId = -1;
    private GameState prevState = GameState.MENU;
    private int spawnX = -1, spawnY = -1;
    public GameState getState() { return state; }
    private File currentWorldDir = null;  
    private static final String[] CHANGELOG = {
        "TerrariaMini beta 1.2.2",
        "",
        "+ Исправлена постановка блоков",
        "+ Обновление модов",
        "",
        "Спасибо за игру!"
    };
    private int changelogScroll = 0;

    enum GameState { MENU, WORLD_SELECT, WORLD_CREATE, PLAYING, PAUSED, DEAD, ACHIEVEMENTS, SETTINGS, MULTIPLAYER, LOADING, CHANGELOG }
    enum GameMode { SURVIVAL, CREATIVE }

    private GameState state = GameState.MENU;
    private GameMode mode = GameMode.SURVIVAL;

    private final javax.swing.Timer timer;
    private World world;
    private Player player;
    private final List<Monster> monsters = new ArrayList<>();
    private final List<Villager> villagers = new ArrayList<>();
    private final List<FloatingText> floaters = new ArrayList<>();
    private final List<Achievement> achievements = new ArrayList<>();
    private final List<RemotePlayer> remotePlayers = new ArrayList<>();
    private final List<PeacefulMob> peacefulMobs = new ArrayList<>();

    private final boolean[] keys = new boolean[256];
    private int mouseX, mouseY;
    private boolean mouseDown, rightMouseDown;

    private long gameTime = 0;
    private static final int DAY_LENGTH = 60 * 60 * 3;

    private final List<WorldSave> worldSaves = new ArrayList<>();
    private int selectedWorldIndex = 0;
    private String currentWorldName = "world";
    private String inputSeed = "";
    private boolean typingSeed = false;

    private final ItemStack[] inventory = new ItemStack[INV_COLS * INV_ROWS];
    private int selectedSlot = 0;
    private boolean inventoryOpen = false;
    private ItemStack draggedStack = null;
    private int draggedFromSlot = -1;

    private final ItemStack[] craftGrid = new ItemStack[9];
    private ItemStack craftResult = null;
    private Crafting.Recipe currentRecipe = null;
    private boolean nearWorkbench = false;

    private WorkBlock activeContainer = null;
    private ItemStack[] containerSlots = null;
    private Villager activeVillager = null;

    private int monsterSpawnCooldown = 0;

    private boolean fullscreen = false;
    private JFrame frame;
    private Rectangle windowedBounds;

    private int xp = 0, xpLevel = 0, xpToNext = 20;

    private long lastFpsTime = System.currentTimeMillis();
    private int framesThisSecond = 0;
    private int fps = 0;

    private boolean chatOpen = false;
    private String chatInput = "";
    private final List<ChatMessage> chatLog = new ArrayList<>();
    private int chatVisibleCount = 8;

    private int digX = -1, digY = -1;
    private int digProgress = 0;
    private int digRequired = 100;

    private MultiplayerManager mp = null;
    private boolean mpHost = false;
    private String mpAddress = "127.0.0.1";
    private boolean typingMpAddress = false;

    private boolean creativePaletteOpen = false;
    private int creativePaletteScroll = 0;
    private static final int[] CREATIVE_BLOCKS = {
        World.DIRT, World.STONE, World.GRASS, World.SAND, World.WOOD, World.LEAVES,
        World.COAL, World.IRON, World.GOLD, World.DIAMOND, World.SNOW, World.ICE,
        World.PLANK, World.TORCH, World.GLASS, World.LANTERN, World.LADDER, World.BED,
        World.SIGN, World.BOOKSHELF, World.CHEST, World.FURNACE, World.WORKBENCH,
        World.DOOR_CLOSED, World.BG_STONE, World.BG_DIRT, World.BG_WOOD, World.BG_PLANK,
        World.SWORD_WOOD, World.SWORD_STONE, World.SWORD_IRON, World.SWORD_DIAMOND,
        World.PICK_WOOD, World.PICK_STONE, World.PICK_IRON, World.PICK_DIAMOND,
        World.AXE_WOOD, World.AXE_STONE, World.AXE_IRON, World.AXE_DIAMOND,
        World.IRON_INGOT, World.GOLD_INGOT, World.DIAMOND_GEM, World.COAL_ITEM,
        World.COIN_GOLD
    };
    private final java.util.Set<Integer> defeatedBosses = new java.util.HashSet<>();

    public TerrariaMini(JFrame frame) {
        this.frame = frame;
        setPreferredSize(new Dimension(SCREEN_W, SCREEN_H));
        setBackground(Color.BLACK);
        setFocusable(true);
        setDoubleBuffered(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);
        addMouseWheelListener(this);
        ensureSavesDir();
        refreshWorldSaves();
        initAchievements();
        refreshDataPacks();
        Sound.init();
        TerrariaMiniAPI api = null;
        GameContext ctx = new GameContext(this);
        ModManager.loadAll(ctx);
        timer = new javax.swing.Timer(16, this);
        timer.start();
    }
    private static String biomeName(int b) {
        switch (b) {
            case World.BIOME_DESERT: return "Пустыня";
            case World.BIOME_SNOW: return "Тундра";
            case World.BIOME_MOUNTAIN: return "Горы";
            case World.BIOME_JUNGLE: return "Джунгли";
            case World.BIOME_CORRUPTION: return "Искажение";
            case World.BIOME_HELL: return "Ад";
            default: return "Лес";
        }
    }
    private static int parseBiome(String s) {
        switch (s.toLowerCase()) {
            case "forest": return World.BIOME_FOREST;
            case "desert": return World.BIOME_DESERT;
            case "snow": case "tundra": return World.BIOME_SNOW;
            case "mountain": return World.BIOME_MOUNTAIN;
            case "jungle": return World.BIOME_JUNGLE;
            case "corruption": case "corrupt": return World.BIOME_CORRUPTION;
            case "hell": return World.BIOME_HELL;
            default: return -1;
        }
    }
    private void teleportToBiome(int targetBiome) {
        for (int x = 10; x < world.getWidth() - 10; x += 5) {
            if (world.biomeAt(x) == targetBiome) {
                int y = world.findSurface(x);
                player.x = x * TILE;
                player.y = (y - 3) * TILE;
                player.vx = 0; player.vy = 0;
                addChatMessage("Телепорт в биом: " + biomeName(targetBiome), Color.GREEN);
                return;
            }
        }
        addChatMessage("Биом не найден в этом мире", Color.RED);
    }
    private static final Map<Character, Color> MC_COLORS = new HashMap<>();
    static {
        MC_COLORS.put('0', new Color(0,0,0));       MC_COLORS.put('1', new Color(0,0,170));
        MC_COLORS.put('2', new Color(0,170,0));     MC_COLORS.put('3', new Color(0,170,170));
        MC_COLORS.put('4', new Color(170,0,0));     MC_COLORS.put('5', new Color(170,0,170));
        MC_COLORS.put('6', new Color(255,170,0));   MC_COLORS.put('7', new Color(170,170,170));
        MC_COLORS.put('8', new Color(85,85,85));    MC_COLORS.put('9', new Color(85,85,255));
        MC_COLORS.put('a', new Color(85,255,85));   MC_COLORS.put('b', new Color(85,255,255));
        MC_COLORS.put('c', new Color(255,85,85));   MC_COLORS.put('d', new Color(255,85,255));
        MC_COLORS.put('e', new Color(255,255,85));  MC_COLORS.put('f', new Color(255,255,255));
    }

    /** Возвращает [очищенный текст, цвет]. Если кодов нет — цвет по умолчанию. */
    private static Object[] parseMcColor(String text, Color def) {
        if (text == null) return new Object[]{"", def};
        Color color = def;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                Color mc = MC_COLORS.get(Character.toLowerCase(text.charAt(i + 1)));
                if (mc != null) { color = mc; i++; continue; }
            }
            sb.append(c);
        }
        return new Object[]{sb.toString(), color};
    }

    private String playerDataFile(String playerId) {
        if (currentWorldDir == null) return null;
        return new File(new File(currentWorldDir, "playerdata"), playerId + ".dat").getPath();
    }
    private String currentPlayerId() {
        return (myPlayerId >= 0) ? String.valueOf(myPlayerId) : "host";
    }

    private void savePlayerProgress() {
        if (player == null || currentWorldDir == null) return;
        String path = playerDataFile(currentPlayerId());
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(path))) {
            PlayerSave ps = new PlayerSave();
            ps.x = player.x; ps.y = player.y;
            ps.hp = player.hp; ps.maxHp = player.maxHp;
            ps.hunger = player.hunger; ps.satiety = player.satiety;
            ps.xp = xp; ps.xpLevel = xpLevel; ps.xpToNext = xpToNext;
            ps.selectedSlot = selectedSlot;
            ps.name = playerName;
            ps.inventory = new ItemStack[inventory.length];
            for (int i = 0; i < inventory.length; i++)
                if (inventory[i] != null) ps.inventory[i] = inventory[i].copy();
            for (Achievement a : achievements)
                if (a.unlocked) ps.unlockedAchievements.add(a.id);
            out.writeObject(ps);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void loadPlayerProgress() {
        if (currentWorldDir == null) return;
        File f = new File(playerDataFile(currentPlayerId()));
        if (!f.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
            PlayerSave ps = (PlayerSave) in.readObject();
            if (player != null) {
                player.x = ps.x; player.y = ps.y;
                player.hp = ps.hp; player.maxHp = ps.maxHp;
                player.hunger = ps.hunger; player.satiety = ps.satiety;
            }
            xp = ps.xp; xpLevel = ps.xpLevel; xpToNext = ps.xpToNext;
            selectedSlot = ps.selectedSlot;
            for (int i = 0; i < inventory.length && i < ps.inventory.length; i++)
                inventory[i] = ps.inventory[i];
            for (Achievement a : achievements) a.unlocked = ps.unlockedAchievements.contains(a.id);
            if (ps.name != null && !ps.name.isEmpty()) playerName = ps.name;
            addChatMessage("Прогресс загружен", new Color(120, 255, 120));
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void initAchievements() {
        achievements.add(new Achievement("first_block", "Первый блок", "Сломай первый блок"));
        achievements.add(new Achievement("wood", "Лесоруб", "Собери 10 дерева"));
        achievements.add(new Achievement("iron", "Железный человек", "Получи железный слиток"));
        achievements.add(new Achievement("gold", "Золотая жила", "Найди золото"));
        achievements.add(new Achievement("diamond", "Алмазный добытчик", "Найди алмаз"));
        achievements.add(new Achievement("first_monster", "Охотник", "Убей первого монстра"));
        achievements.add(new Achievement("village", "Путешественник", "Найди деревню"));
        achievements.add(new Achievement("night", "Ночной страж", "Переживи первую ночь"));
        achievements.add(new Achievement("smelt", "Плавка", "Переплавь руду в печи"));
        achievements.add(new Achievement("rich", "Богач", "Накопи 100 золотых монет"));
        achievements.add(new Achievement("level5", "Опытный", "Достигни 5 уровня"));
        achievements.add(new Achievement("craft", "Ремесленник", "Скрафти первый предмет"));
        achievements.add(new Achievement("trade", "Торговец", "Соверши первую сделку с жителем"));
        achievements.add(new Achievement("multiplayer", "Сетевой игрок", "Подключись к серверу"));
        achievements.add(new Achievement("boss_slime", "Слизнеед", "Победи Короля Слизней"));
        achievements.add(new Achievement("boss_eye", "Око за око", "Победи Глаз Ктулху"));
        achievements.add(new Achievement("boss_skeleton", "Костолом", "Победи Скелетрона"));
        achievements.add(new Achievement("boss_hell", "Покоритель Ада", "Победи Короля Ада"));
    }

    private void unlock(String id) {
        for (Achievement a : achievements) {
            if (a.id.equals(id) && !a.unlocked) {
                a.unlocked = true;
                if (player != null) floaters.add(new FloatingText("★ " + a.title, player.x, player.y - 50, new Color(255, 220, 100)));
                addChatMessage("★ Достижение: " + a.title, new Color(255, 220, 100));
                Sound.play(Sound.ACHIEVEMENT);
            }
        }
    }

    private void spawnBoss(int type) {
        Monster boss = new Monster(player.x + 10 * TILE, player.y - 5 * TILE, type);
        monsters.add(boss);
        activeBoss = boss;
        bossMaxHp = (int) boss.maxHp;
        Sound.play(Sound.ACHIEVEMENT);
        String name = type == 100 ? "Король Слизней" : type == 101 ? "Глаз Ктулху" : type == 102 ? "Скелетрон" : "Король Ада";
        addChatMessage("⚠ БОСС: " + name + " появился!", new Color(255, 100, 100));
    }

    private boolean bossDefeated(int type) { return defeatedBosses.contains(type); }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state != prevState) {
            if (state != prevState) {
                if (state == GameState.MENU) Music.play(Music.TRACK_MENU);
                else if (state == GameState.PLAYING) {
                    if (activeBoss != null) Music.play(Music.TRACK_BOSS);
                    else if (player != null && world != null
                            && player.y / TILE > world.getHeight() * 0.85) Music.play(Music.TRACK_HELL);
                    else if (isNight()) Music.play(Music.TRACK_NIGHT);
                    else Music.play(Music.TRACK_DAY);
                } else if (state == GameState.WORLD_SELECT || state == GameState.WORLD_CREATE
                        || state == GameState.MULTIPLAYER || state == GameState.SETTINGS
                        || state == GameState.CHANGELOG || state == GameState.ACHIEVEMENTS) {
                    // музыка меню продолжает играть
                } else {
                    Music.stop();
                }
                prevState = state;
            }
            // WORLD_SELECT / SETTINGS / CHANGELOG / ACHIEVEMENTS — музыку не трогаем,
            // она продолжает играть «в меню»
            prevState = state;
        }
        framesThisSecond++;
        long now = System.currentTimeMillis();
        if (now - lastFpsTime >= 1000) {
            fps = framesThisSecond;
            framesThisSecond = 0;
            lastFpsTime = now;
        }
        if (state == GameState.PLAYING) {
            gameTime++;
            updatePlaying();
        }
        repaint();
    }

    private void updatePlaying() {
        if (mode == GameMode.CREATIVE) {
            player.hp = player.maxHp;
            player.godMode = true;
        } else {
            player.godMode = false;
        }
        currentGameTime = gameTime;
        if (player == null || world == null) return;
        if (world.getWidth() <= 10) return;

        if (mp != null) {
            mp.tick(player);
            mp.updateRemotePlayers(remotePlayers);
        }

        nearWorkbench = false;
        int pxTile = (int) (player.x / TILE);
        int pyTile = (int) (player.y / TILE);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                int b = world.getBlock(pxTile + dx, pyTile + dy);
                if (b == World.WORKBENCH || b == World.CRAFT_TABLE) { nearWorkbench = true; break; }
            }
            if (nearWorkbench) break;
        }

        if (!chatOpen) {
            double dx = 0;
            if (keys[KeyEvent.VK_A] || keys[KeyEvent.VK_LEFT]) dx -= 1;
            if (keys[KeyEvent.VK_D] || keys[KeyEvent.VK_RIGHT]) dx += 1;
            boolean jump = keys[KeyEvent.VK_SPACE] || keys[KeyEvent.VK_W] || keys[KeyEvent.VK_UP];
            player.update(dx, jump, world);
            checkFallDamage();
        }

        world.tick();

        for (WorkBlock wb : world.workBlocks) {
            if (wb.type == World.FURNACE) {
                int before = wb.getResultCount();
                wb.tickFurnace();
                if (wb.getResultCount() > before) unlock("smelt");
            }
        }

        for (Iterator<Monster> it = monsters.iterator(); it.hasNext();) {
            Monster m = it.next();
            m.update(player, world, monsters);
            if (m.dead) it.remove();
        }

        for (Villager v : villagers) v.update(world);
        for (PeacefulMob pm : peacefulMobs) pm.update(player, world);

        // Спавн боссов
        boolean isHostOrSolo = (mp == null) || mp.isHost();
        if (isHostOrSolo) {
            if (activeBoss == null && !isNight() && gameTime > DAY_LENGTH * 1.5 && !bossDefeated(BOSS_SLIME)) spawnBoss(BOSS_SLIME);
            if (activeBoss == null && isNight() && gameTime > DAY_LENGTH * 2.5 && !bossDefeated(BOSS_EYE)) spawnBoss(BOSS_EYE);
            if (activeBoss == null && player.y / TILE > world.getHeight() * 0.85 && !bossDefeated(BOSS_HELL)) spawnBoss(BOSS_HELL);
        }
        // Ночной спавн — только у хоста
        boolean night = isNight();
        if (night && isHostOrSolo) {
            monsterSpawnCooldown--;
            if (monsterSpawnCooldown <= 0 && monsters.size() < 15) {
                spawnMonsterNearPlayer();
                monsterSpawnCooldown = 60 + new Random().nextInt(120);
            }
        }
        // Мирные мобы днём
        if (!isNight() && peacefulMobs.size() < 40 && gameTime % 200 == 0) {
            int mx = (int) ((player.x / TILE) + (new Random().nextInt(40) - 20));
            if (mx > 10 && mx < world.getWidth() - 10) {
                int my = world.findSurface(mx) - 2;
                if (world.getBlock(mx, my) == World.AIR) peacefulMobs.add(new PeacefulMob(mx * TILE, my * TILE, new Random().nextInt(4)));
            }
        }

        for (Iterator<FloatingText> it = floaters.iterator(); it.hasNext();) {
            FloatingText ft = it.next();
            ft.y -= 0.5;
            ft.life--;
            if (ft.life <= 0) it.remove();
        }

        if (mouseDown && !inventoryOpen && digX >= 0) continueDigging();
        else { digProgress = 0; digX = -1; }

        // Урон от лавы
        int pxT = (int) ((player.x + Player.W / 2) / TILE);
        int pyT = (int) ((player.y + Player.H / 2) / TILE);
        int lvl = world.getLiquid(pxT, pyT);
        if (lvl >= 8 && player.hurtCooldown == 0 && !player.godMode) {
            player.hp -= 15;
            player.hurtCooldown = 30;
            Sound.play(Sound.HURT);
            floaters.add(new FloatingText("-15 (лава)", player.x, player.y - 20, new Color(255, 100, 50)));
        }

        if (player.hp <= 0 && state == GameState.PLAYING) {
            state = GameState.DEAD;
            Sound.play(Sound.DEATH);
            addChatMessage("Ты умер!", new Color(255, 100, 100));
        }
        if (Modules.HUNGER_SYSTEM && player.hunger < 20 && !isNight()) {
            player.hunger += 0.02;
            if (player.hunger > player.maxHunger) player.hunger = player.maxHunger;
        }
        ModManager.tick();
    }

    private void checkFallDamage() {
        if (!Modules.FALL_DAMAGE || player == null) return;

        if (player.onGround) {
            if (player.wasInAir) {
                double fallDist = (player.fallStartY - player.y) / TILE;
                if (fallDist > 4.0 && !player.godMode) {
                    int dmg = (int) ((fallDist - 4.0) * 8);
                    if (dmg > 0) {
                        player.hp -= dmg;
                        player.hurtCooldown = 30;
                        floaters.add(new FloatingText("-" + dmg + " HP", player.x, player.y - 20, new Color(255, 80, 80)));
                        Sound.play(Sound.HURT);
                    }
                }
                player.wasInAir = false;
                player.fallStartY = player.y;
            }
        } else {
            // В воздухе — запоминаем только первую точку отрыва
            if (!player.wasInAir) {
                player.fallStartY = player.y;
                player.wasInAir = true;
            } else {
                // Обновляем верхнюю точку, если игрок поднялся выше
                if (player.y < player.fallStartY) {
                    player.fallStartY = player.y;
                }
            }
        }
    }

    private boolean isNight() { return (gameTime % DAY_LENGTH) / (double) DAY_LENGTH > 0.5; }
    private double dayProgress() { return (gameTime % DAY_LENGTH) / (double) DAY_LENGTH; }

    private void spawnMonsterNearPlayer() {
        Random rnd = new Random();
        int side = rnd.nextBoolean() ? 1 : -1;
        int dist = 15 + rnd.nextInt(10);
        double mx = player.x + side * dist * TILE;
        double my = player.y - rnd.nextInt(3) * TILE;
        int tx = (int) (mx / TILE);
        int ty = (int) (my / TILE);
        for (int i = 0; i < 20; i++) if (ty + i < world.getHeight() && world.isSolid(tx, ty + i)) { my = (ty + i) * TILE - Monster.H; break; }
        if (tx < 2 || tx > world.getWidth() - 2) return;
        monsters.add(new Monster(mx, my, rnd.nextInt(3)));
    }

    private void addToInventory(ItemStack stack) {
        for (int i = 0; i < inventory.length; i++) if (inventory[i] != null && inventory[i].id == stack.id && inventory[i].count < 999) { inventory[i].count += stack.count; return; }
        for (int i = 0; i < inventory.length; i++) if (inventory[i] == null) { inventory[i] = stack.copy(); return; }
    }

    private void addXp(int amount) {
        xp += amount;
        while (xp >= xpToNext) {
            xp -= xpToNext;
            xpLevel++;
            xpToNext = 20 + xpLevel * 10;
            if (xpLevel >= 5) unlock("level5");
        }
    }

    private int countItem(int id) {
        int n = 0;
        for (ItemStack s : inventory) if (s != null && s.id == id) n += s.count;
        return n;
    }

    private void addChatMessage(String text, Color color) {
        chatLog.add(new ChatMessage(text, color, System.currentTimeMillis()));
        if (chatLog.size() > 100) chatLog.remove(0);
    }

    private void startDigging(int wx, int wy) {
        int block = world.getBlock(wx, wy);
        if (block == World.AIR) return;
        double pdx = wx * TILE - player.x;
        double pdy = wy * TILE - player.y;
        if (pdx * pdx + pdy * pdy > (6 * TILE) * (6 * TILE)) return;
        if (block == World.CHEST || block == World.FURNACE || block == World.WORKBENCH || block == World.CRAFT_TABLE) return;
        digX = wx; digY = wy; digProgress = 0;
        ItemStack held = inventory[selectedSlot];
        int hardness = ToolUtil.getBlockHardness(block);
        int baseTicks = hardness * 10;
        int power = ToolUtil.getToolPower(held) + ToolUtil.getToolBonus(held, block);
        if (power < 1) power = 1;
        digRequired = Math.max(3, baseTicks / power);
        if (ToolUtil.isWrongTool(held, block)) digRequired *= 3;
    }

    private void continueDigging() {
        int block = world.getBlock(digX, digY);
        if (block == World.AIR) { digX = -1; digProgress = 0; return; }
        digProgress++;
        if (digProgress >= digRequired) {
            world.setBlock(digX, digY, World.AIR);
            ModManager.blockBreak(digX, digY, block);
            if (mode == GameMode.SURVIVAL) {
                if (block == World.GOLD) addToInventory(ItemStack.of(World.COIN_GOLD, 3));
                else if (block == World.DIAMOND) addToInventory(ItemStack.of(World.COIN_GOLD, 5));
                else if (block == World.IRON) addToInventory(ItemStack.of(World.COIN_GOLD, 1));
                addToInventory(ItemStack.of(block, 1));
                addXp(1);
            }
            if (mp != null) mp.sendBlockChange(digX, digY, World.AIR);
            Sound.play(Sound.MINE);
            unlock("first_block");
            digX = -1; digProgress = 0;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        switch (state) {
            case MENU: drawMenu(g2); break;
            case WORLD_SELECT: drawWorldSelect(g2); break;
            case WORLD_CREATE: drawWorldCreate(g2); break;
            case MULTIPLAYER: drawMultiplayer(g2); break;
            case LOADING: drawLoading(g2); break;
            case PLAYING: drawGame(g2); break;
            case PAUSED: drawGame(g2); drawPause(g2); break;
            case DEAD: drawGame(g2); drawDeath(g2); break;
            case ACHIEVEMENTS: drawAchievements(g2); break;
            case SETTINGS: drawSettings(g2); break;
            case CHANGELOG: drawChangelog(g2); break;
        }
    }

    private void drawPixelBackground(Graphics2D g) {
        GradientPaint gp = new GradientPaint(0, 0, new Color(15, 25, 60), 0, SCREEN_H, new Color(5, 8, 20));
        g.setPaint(gp);
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);
        long t = System.currentTimeMillis() / 300;
        Random rnd = new Random(12345);
        for (int i = 0; i < 100; i++) {
            int sx = rnd.nextInt(SCREEN_W);
            int sy = rnd.nextInt(SCREEN_H / 2);
            int bright = ((int) (t + i) % 4 == 0) ? 255 : 180;
            g.setColor(new Color(bright, bright, bright, 200));
            g.fillRect(sx, sy, 2, 2);
        }
    }

    private void drawPixelButton(Graphics2D g, String text, int x, int y, int w, int h, boolean hover) {
        drawPixelButtonWithIcon(g, text, x, y, w, h, hover, null);
    }

    private void drawPixelButtonWithIcon(Graphics2D g, String text, int x, int y, int w, int h, boolean hover, BufferedImage icon) {
        Color top = hover ? new Color(140, 180, 240) : new Color(80, 110, 170);
        Color bot = hover ? new Color(80, 120, 200) : new Color(40, 60, 120);
        Color border = hover ? new Color(255, 255, 200) : new Color(160, 180, 220);
        g.setColor(new Color(0, 0, 0, 220));
        g.fillRect(x - 3, y - 3, w + 6, h + 6);
        g.setColor(border);
        g.fillRect(x - 2, y - 2, w + 4, h + 4);
        g.setColor(new Color(20, 20, 30));
        g.fillRect(x - 1, y - 1, w + 2, h + 2);
        for (int i = 0; i < h; i++) {
            float f = i / (float) h;
            int r = (int) (top.getRed() * (1 - f) + bot.getRed() * f);
            int gr = (int) (top.getGreen() * (1 - f) + bot.getGreen() * f);
            int b = (int) (top.getBlue() * (1 - f) + bot.getBlue() * f);
            g.setColor(new Color(r, gr, b));
            g.fillRect(x, y + i, w, 1);
        }
        g.setColor(new Color(255, 255, 255, 80));
        g.fillRect(x, y, w, 2);
        int iconOffset = 0;
        if (icon != null) { g.drawImage(icon, x + 10, y + (h - 28) / 2, 28, 28, null); iconOffset = 30; }
        g.setFont(PixelFont.font(18, Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        int tx = x + iconOffset + (w - iconOffset - fm.stringWidth(text)) / 2;
        int ty = y + (h + fm.getAscent()) / 2 - 4;
        g.setColor(new Color(0, 0, 0, 220));
        g.drawString(text, tx + 2, ty + 2);
        g.setColor(Color.WHITE);
        g.drawString(text, tx, ty);
    }

    private void drawPixelPanel(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(0, 0, 0, 220));
        g.fillRect(x - 4, y - 4, w + 8, h + 8);
        g.setColor(new Color(140, 110, 80));
        g.fillRect(x - 3, y - 3, w + 6, h + 6);
        g.setColor(new Color(90, 65, 40));
        g.fillRect(x - 1, y - 1, w + 2, h + 2);
        g.setColor(new Color(45, 33, 20));
        g.fillRect(x, y, w, h);
    }
    private void drawChangelog(Graphics2D g) {
        drawPixelBackground(g);
        g.setFont(PixelFont.font(42, Font.BOLD));
        drawCenteredWithPixelShadow(g, "ЧТО НОВОГО", SCREEN_W / 2, 60, new Color(255, 220, 100));
        g.setFont(PixelFont.font(14, Font.PLAIN));
        int startY = 120;
        int lineH = 22;
        int visibleLines = (SCREEN_H - startY - 100) / lineH;
        int maxScroll = Math.max(0, CHANGELOG.length - visibleLines);
        changelogScroll = Math.max(0, Math.min(maxScroll, changelogScroll));
        for (int i = 0; i < visibleLines && i + changelogScroll < CHANGELOG.length; i++) {
            String line = CHANGELOG[i + changelogScroll];
            int y = startY + i * lineH;
            if (line.startsWith("===")) { g.setColor(new Color(255, 220, 100)); g.setFont(PixelFont.font(16, Font.BOLD)); }
            else if (line.startsWith("+")) { g.setColor(new Color(120, 255, 120)); g.setFont(PixelFont.font(14, Font.PLAIN)); }
            else { g.setColor(new Color(200, 200, 220)); g.setFont(PixelFont.font(14, Font.PLAIN)); }
            g.drawString(line, 80, y);
        }
        if (maxScroll > 0) {
            int barX = SCREEN_W - 30;
            int barY = startY;
            int barH = visibleLines * lineH;
            g.setColor(new Color(80, 80, 100));
            g.fillRect(barX, barY, 8, barH);
            int thumbH = Math.max(20, barH * visibleLines / CHANGELOG.length);
            int thumbY = barY + (barH - thumbH) * changelogScroll / maxScroll;
            g.setColor(new Color(200, 200, 220));
            g.fillRect(barX, thumbY, 8, thumbH);
        }
        drawPixelButton(g, "Назад [Esc]", SCREEN_W / 2 - 150, SCREEN_H - 70, 300, 50, mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150);
        g.setFont(PixelFont.font(11, Font.PLAIN));
        g.setColor(new Color(160, 160, 180));
    }

    private void drawMenu(Graphics2D g) {
        drawPixelBackground(g);
        g.setFont(PixelFont.font(72, Font.BOLD));
        drawCenteredWithPixelShadow(g, "TerrariaMini", SCREEN_W / 2, 110, new Color(255, 220, 80));
        g.setFont(PixelFont.font(20, Font.BOLD));
        drawCenteredWithPixelShadow(g, " beta 1.2.2 — Mods & Baggs Update", SCREEN_W / 2, 145, new Color(180, 220, 255));

        // ===== ГЕОМЕТРИЯ =====
        int btnW = 280;             // ширина одной нижней кнопки
        int btnH = 55;
        int gapX = 20;              // отступ между двумя кнопками в ряду
        int gapY = 20;              // отступ между рядами

        int totalRowW = btnW * 2 + gapX;        // ширина ряда = 580
        int rowStartX = (SCREEN_W - totalRowW) / 2;

        int topX = rowStartX;
        int topW = totalRowW;       // широкая = как две нижние + gapX
        int topY = 200;

        // ===== ВЕРХНЯЯ ШИРОКАЯ КНОПКА =====
        boolean topHover = mouseX > topX && mouseX < topX + topW && mouseY > topY && mouseY < topY + btnH;
        drawPixelButtonWithIcon(g, "Одиночная игра", topX, topY, topW, btnH, topHover, Textures.get(World.GRASS));

        // ===== НИЖНИЕ РЯДЫ (по 2 кнопки) =====
        String[][] rows = {
            {"Мультиплеер", "Датапаки"},
            {"Что нового",  "Достижения"},
            {"Подсказка",   "Выход"}
        };
        BufferedImage[][] rowIcons = {
            { Textures.get(World.CHEST),    Textures.get(World.BOOKSHELF) },
            { Textures.get(World.SIGN),     Textures.get(World.DIAMOND_GEM) },
            { Textures.get(World.FURNACE),  Textures.get(World.STONE) }
        };

        int firstRowY = topY + btnH + gapY + 20;
        for (int r = 0; r < rows.length; r++) {
            int rowY = firstRowY + r * (btnH + gapY);
            for (int c = 0; c < 2; c++) {
                int bx = rowStartX + c * (btnW + gapX);
                boolean hover = mouseX > bx && mouseX < bx + btnW && mouseY > rowY && mouseY < rowY + btnH;
                drawPixelButtonWithIcon(g, rows[r][c], bx, rowY, btnW, btnH, hover, rowIcons[r][c]);
            }
        }

        g.setColor(new Color(200, 200, 200));
        g.setFont(PixelFont.font(12, Font.PLAIN));
    }

    private void drawWorldSelect(Graphics2D g) {
        drawPixelBackground(g);
        g.setFont(PixelFont.font(42, Font.BOLD));
        drawCenteredWithPixelShadow(g, "Выбор мира", SCREEN_W / 2, 60, new Color(255, 220, 80));
        int listX = 60, listY = 120, itemH = 60, listW = 520;
        if (worldSaves.isEmpty()) {
            g.setColor(new Color(200, 200, 220));
            g.setFont(PixelFont.font(18, Font.PLAIN));
            g.drawString("Пока нет миров. Создай новый!", listX + 20, listY + 40);
        }
        for (int i = 0; i < Math.min(worldSaves.size(), 6); i++) {
            int y = listY + i * itemH;
            WorldSave ws = worldSaves.get(i);
            boolean sel = i == selectedWorldIndex;
            boolean hover = mouseY > y && mouseY < y + itemH - 6 && mouseX > listX && mouseX < listX + listW;
            g.setColor(sel ? new Color(120, 90, 40, 220) : (hover ? new Color(70, 60, 50, 200) : new Color(40, 35, 30, 200)));
            g.fillRect(listX, y, listW, itemH - 6);
            g.setColor(sel ? new Color(255, 220, 100) : new Color(120, 90, 60));
            g.drawRect(listX, y, listW, itemH - 6);
            g.setColor(sel ? new Color(255, 255, 200) : Color.WHITE);
            g.setFont(PixelFont.font(18, Font.BOLD));
            g.drawString((sel ? "> " : "  ") + ws.name, listX + 15, y + 24);
            g.setColor(new Color(180, 180, 200));
            g.setFont(PixelFont.font(12, Font.PLAIN));
            g.drawString("Размер: " + ws.sizeW + "×" + ws.sizeH + "  ·  " + ws.dateStr, listX + 20, y + 44);
        }
        drawPixelButton(g, "Создать [N]", 60, 520, 250, 50, mouseY > 520 && mouseY < 570 && mouseX > 60 && mouseX < 310);
        drawPixelButton(g, "Назад [Esc]", 60, 580, 200, 50, mouseY > 580 && mouseY < 630 && mouseX > 60 && mouseX < 260);
        if (!worldSaves.isEmpty()) {
            drawPixelButton(g, "Играть [Enter]", 330, 520, 250, 50, mouseY > 520 && mouseY < 570 && mouseX > 330 && mouseX < 580);
            drawPixelButton(g, "Удалить [Del]", 330, 580, 250, 50, mouseY > 580 && mouseY < 630 && mouseX > 330 && mouseX < 580);
        }
    }

    private void drawWorldCreate(Graphics2D g) {
        drawPixelBackground(g);
        g.setFont(PixelFont.font(42, Font.BOLD));
        drawCenteredWithPixelShadow(g, "Создание мира", SCREEN_W / 2, 100, new Color(255, 220, 80));
        g.setColor(Color.WHITE);
        g.setFont(PixelFont.font(18, Font.PLAIN));
        drawCentered(g, "Сид (пусто = случайный мир):", SCREEN_W / 2, 210);
        int bx = SCREEN_W / 2 - 250;
        int by = 240;
        g.setColor(new Color(0, 0, 0, 220));
        g.fillRect(bx - 3, by - 3, 506, 56);
        g.setColor(typingSeed ? new Color(255, 240, 100) : new Color(150, 150, 180));
        g.drawRect(bx - 2, by - 2, 504, 54);
        g.setColor(new Color(30, 30, 50));
        g.fillRect(bx, by, 500, 50);
        g.setColor(Color.WHITE);
        g.setFont(PixelFont.font(22, Font.BOLD));
        String shown = inputSeed.isEmpty() ? "_" : inputSeed + (typingSeed ? "_" : "");
        g.drawString(shown, bx + 15, by + 33);
        drawPixelButton(g, "Создать [Enter]", SCREEN_W / 2 - 220, 380, 440, 55, mouseY > 380 && mouseY < 435 && mouseX > SCREEN_W / 2 - 220 && mouseX < SCREEN_W / 2 + 220);
        drawPixelButton(g, "Отмена [Esc]", SCREEN_W / 2 - 150, 460, 300, 50, mouseY > 460 && mouseY < 510 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150);
        g.setColor(new Color(180, 200, 240));
        g.setFont(PixelFont.font(14, Font.PLAIN));
        drawCentered(g, "Мир 2400×400 блоков. Одинаковый сид = одинаковый мир.", SCREEN_W / 2, 570);
    }

    private void drawMultiplayer(Graphics2D g) {
        drawPixelBackground(g);
        g.setFont(PixelFont.font(42, Font.BOLD));
        drawCenteredWithPixelShadow(g, "Мультиплеер (LAN)", SCREEN_W / 2, 100, new Color(255, 220, 80));
        g.setFont(PixelFont.font(18, Font.PLAIN));
        g.setColor(Color.WHITE);
        drawCentered(g, "Адрес сервера (IP хоста):", SCREEN_W / 2, 200);
        int bx = SCREEN_W / 2 - 250;
        int by = 230;
        g.setColor(new Color(0, 0, 0, 220));
        g.fillRect(bx - 3, by - 3, 506, 56);
        g.setColor(typingMpAddress ? new Color(255, 240, 100) : new Color(150, 150, 180));
        g.drawRect(bx - 2, by - 2, 504, 54);
        g.setColor(new Color(30, 30, 50));
        g.fillRect(bx, by, 500, 50);
        g.setColor(Color.WHITE);
        g.setFont(PixelFont.font(22, Font.BOLD));
        String shown = mpAddress.isEmpty() ? "_" : mpAddress + (typingMpAddress ? "_" : "");
        g.drawString(shown, bx + 15, by + 33);
        drawPixelButton(g, "Создать сервер (хост)", SCREEN_W / 2 - 220, 350, 440, 55, mouseY > 350 && mouseY < 405 && mouseX > SCREEN_W / 2 - 220 && mouseX < SCREEN_W / 2 + 220);
        drawPixelButton(g, "Подключиться", SCREEN_W / 2 - 220, 420, 440, 55, mouseY > 420 && mouseY < 475 && mouseX > SCREEN_W / 2 - 220 && mouseX < SCREEN_W / 2 + 220);
        drawPixelButton(g, "Назад [Esc]", SCREEN_W / 2 - 150, 500, 300, 50, mouseY > 500 && mouseY < 550 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150);
        g.setColor(new Color(180, 200, 240));
        g.setFont(PixelFont.font(12, Font.PLAIN));
        drawCentered(g, "Порт 25565. Хост запускает сервер, клиенты вводят его IP.", SCREEN_W / 2, 590);
    }

    private void drawLoading(Graphics2D g) {
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);
        g.setColor(Color.WHITE);
        g.setFont(PixelFont.font(32, Font.BOLD));
        drawCentered(g, "Загрузка мира...", SCREEN_W / 2, SCREEN_H / 2 - 20);
        g.setFont(PixelFont.font(16, Font.PLAIN));
        g.setColor(new Color(180, 200, 240));
        drawCentered(g, "Ожидание мира от хоста " + mpAddress, SCREEN_W / 2, SCREEN_H / 2 + 30);
        int cx = SCREEN_W / 2, cy = SCREEN_H / 2 + 80;
        int dots = (int) (System.currentTimeMillis() / 300) % 4;
        for (int i = 0; i < 4; i++) { g.setColor(i <= dots ? Color.WHITE : new Color(80, 80, 100)); g.fillRect(cx - 60 + i * 30, cy, 20, 20); }
    }

    private void drawGame(Graphics2D g) {
        if (world == null || player == null) return;
        if (world.getWidth() <= 10) { state = GameState.LOADING; return; }
        int camX = (int) player.cameraX;
        int camY = (int) player.cameraY;
        double t = dayProgress();
        Color skyTop, skyBot;
        if (t < 0.25) { skyTop = new Color(255, 140, 80); skyBot = new Color(255, 200, 140); }
        else if (t < 0.5) { skyTop = new Color(100, 170, 240); skyBot = new Color(180, 220, 255); }
        else if (t < 0.75) { skyTop = new Color(240, 110, 70); skyBot = new Color(200, 130, 130); }
        else { skyTop = new Color(10, 10, 40); skyBot = new Color(25, 25, 60); }
        g.setPaint(new GradientPaint(0, 0, skyTop, 0, SCREEN_H, skyBot));
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);
        if (isNight()) {
            Random rnd = new Random(999);
            for (int i = 0; i < 60; i++) { g.setColor(new Color(255, 255, 255, 180)); g.fillRect(rnd.nextInt(SCREEN_W), rnd.nextInt(SCREEN_H / 2), 2, 2); }
        }
        int sunX = (int) (SCREEN_W * t * 2);
        if (sunX > SCREEN_W) sunX = SCREEN_W * 2 - sunX;
        int sunY = (int) (SCREEN_H * 0.15 + Math.sin(t * Math.PI * 2) * 100);
        if (isNight()) { g.setColor(new Color(240, 240, 220)); g.fillRect(sunX - 24, sunY, 48, 48); g.setColor(new Color(10, 10, 40)); g.fillRect(sunX - 8, sunY, 32, 48); }
        else { g.setColor(new Color(255, 240, 130)); g.fillRect(sunX - 24, sunY, 48, 48); g.setColor(new Color(255, 220, 80)); g.drawRect(sunX - 24, sunY, 48, 48); }

        int startX = Math.max(0, camX / TILE);
        int startY = Math.max(0, camY / TILE);
        int endX = Math.min(world.getWidth() - 1, (camX + SCREEN_W) / TILE + 1);
        int endY = Math.min(world.getHeight() - 1, (camY + SCREEN_H) / TILE + 1);

        // Фон
        for (int y = startY; y <= endY; y++) for (int x = startX; x <= endX; x++) {
            int bg = world.getBackground(x, y);
            if (bg == World.AIR || !Modules.SHOW_BACKGROUND) continue;
            g.drawImage(Textures.get(bg), x * TILE - camX, y * TILE - camY, null);
        }
        // Блоки
        for (int y = startY; y <= endY; y++) for (int x = startX; x <= endX; x++) {
            int b = world.getBlock(x, y);
            if (b == World.AIR) continue;
            g.drawImage(Textures.get(b), x * TILE - camX, y * TILE - camY, null);
        }
        // Жидкости
        for (int y = startY; y <= endY; y++) for (int x = startX; x <= endX; x++) {
            int lvl = world.getLiquid(x, y);
            if (lvl == 0) continue;
            int px = x * TILE - camX, py = y * TILE - camY;
            boolean isLava = lvl >= 8;
            int heightPx = (int) (TILE * (isLava ? 1.0 : lvl / 7.0));
            if (isLava) { g.setColor(new Color(255, 80, 20, 220)); g.fillRect(px, py + TILE - heightPx, TILE, heightPx); g.setColor(new Color(255, 180, 60, 180)); g.fillRect(px, py + TILE - heightPx, TILE, 3); }
            else { g.setColor(new Color(50, 120, 220, 180)); g.fillRect(px, py + TILE - heightPx, TILE, heightPx); g.setColor(new Color(150, 200, 255, 160)); g.fillRect(px, py + TILE - heightPx, TILE, 2); }
        }
        // Тени (ambient occlusion)
        for (int y = startY; y <= endY; y++) for (int x = startX; x <= endX; x++) {
            int b = world.getBlock(x, y);
            if (b == World.AIR) continue;
            int px = x * TILE - camX, py = y * TILE - camY;
            boolean airDown = world.getBlock(x, y + 1) == World.AIR;
            boolean airRight = world.getBlock(x + 1, y) == World.AIR;
            boolean airLeft = world.getBlock(x - 1, y) == World.AIR;
            boolean airUp = world.getBlock(x, y - 1) == World.AIR;
            if (airDown) for (int i = 0; i < 5; i++) { int a = 55 - i * 10; if (a <= 0) break; g.setColor(new Color(0, 0, 0, a)); g.fillRect(px, py + TILE - 5 + i, TILE, 1); }
            if (airRight) for (int i = 0; i < 4; i++) { int a = 45 - i * 10; if (a <= 0) break; g.setColor(new Color(0, 0, 0, a)); g.fillRect(px + TILE - 4 + i, py, 1, TILE); }
            if (airLeft) { g.setColor(new Color(0, 0, 0, 30)); g.fillRect(px, py, 3, TILE); }
            if (airUp) { g.setColor(new Color(0, 0, 0, 25)); g.fillRect(px, py, TILE, 3); }
        }
        boolean isNightTime = isNight();
        // Освещение
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int light = world.getLight(x, y);
                // Ночь: снаружи темно, в пещерах — всегда темно без света
                if (isNightTime && y < world.findSurface(x)) light = Math.max(0, light - 10);
                if (light >= 15) continue;
                int alpha = (15 - light) * 14; // 0..210
                if (alpha <= 0) continue;
                int px = x * TILE - camX;
                int py = y * TILE - camY;
                g.setColor(new Color(0, 0, 0, alpha));
                g.fillRect(px, py, TILE, TILE);
            }
        }

        for (PeacefulMob pm : peacefulMobs) pm.draw(g, camX, camY);
        for (Villager v : villagers) v.draw(g, camX, camY);
        for (RemotePlayer rp : remotePlayers) rp.draw(g, camX, camY);
        for (Monster m : monsters) m.draw(g, camX, camY);
        player.draw(g, camX, camY);

        if (digX >= 0 && digProgress > 0) {
            int px = digX * TILE - camX, py = digY * TILE - camY;
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect(px + 4, py - 10, TILE - 8, 6);
            g.setColor(new Color(200, 255, 100));
            double ratio = Math.min(1.0, digProgress / (double) digRequired);
            g.fillRect(px + 4, py - 10, (int) ((TILE - 8) * ratio), 6);
        }

        g.setFont(PixelFont.font(12, Font.BOLD));
        for (FloatingText ft : floaters) {
            g.setColor(new Color(0, 0, 0, 220));
            g.drawString(ft.text, (int) (ft.x - camX) + 1, (int) (ft.y - camY) + 1);
            g.setColor(ft.color);
            g.drawString(ft.text, (int) (ft.x - camX), (int) (ft.y - camY));
        }

        int wx = (int) ((mouseX + camX) / TILE);
        int wy = (int) ((mouseY + camY) / TILE);
        int cx = wx * TILE - camX, cy = wy * TILE - camY;
        g.setColor(new Color(255, 255, 255, 220));
        for (int i = 0; i < TILE; i += 4) { g.fillRect(cx + i, cy, 2, 2); g.fillRect(cx + i, cy + TILE - 2, 2, 2); g.fillRect(cx, cy + i, 2, 2); g.fillRect(cx + TILE - 2, cy + i, 2, 2); }

        drawHUD(g);
        if (inventoryOpen) drawInventory(g);
        if (activeContainer != null) drawContainer(g);
        if (activeVillager != null) drawTrade(g);
        drawChat(g);
        if (creativePaletteOpen && mode == GameMode.CREATIVE) drawCreativePalette(g);

        if (draggedStack != null) {
            BufferedImage ic = Textures.get(draggedStack.id);
            g.drawImage(ic, mouseX - 20, mouseY - 20, 40, 40, null);
            if (draggedStack.count > 1) {
                g.setFont(PixelFont.font(13, Font.BOLD));
                g.setColor(Color.BLACK);
                g.drawString("" + draggedStack.count, mouseX + 4, mouseY + 20);
                g.setColor(Color.WHITE);
                g.drawString("" + draggedStack.count, mouseX + 3, mouseY + 19);
            }
        }
    }
    private void drawHUD(Graphics2D g) {
        int effectY = SCREEN_H - 80;
        g.setFont(PixelFont.font(11, Font.BOLD));
        if (player.strengthTimer > 0) { g.setColor(new Color(255, 100, 100)); g.drawString("STR " + (player.strengthTimer / 60) + "s", 12, effectY); effectY -= 16; }
        if (player.speedTimer > 0) { g.setColor(new Color(100, 255, 255)); g.drawString("SPD " + (player.speedTimer / 60) + "s", 12, effectY); effectY -= 16; }
        if (player.fireResistTimer > 0) { g.setColor(new Color(255, 200, 100)); g.drawString("FIRE " + (player.fireResistTimer / 60) + "s", 12, effectY); effectY -= 16; }
        if (player.nightVisionTimer > 0) { g.setColor(new Color(150, 150, 255)); g.drawString("NIGHT " + (player.nightVisionTimer / 60) + "s", 12, effectY); }

        int panelW = 240, panelH = 82;
        int panelX = 10, panelY = 10;
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(panelX, panelY, panelW, panelH);
        g.setColor(new Color(200, 200, 220, 120));
        g.drawRect(panelX, panelY, panelW, panelH);

        int hearts = 10;
        double hpPerHeart = player.maxHp / (double) hearts;
        for (int i = 0; i < hearts; i++) {
            int hx = panelX + 10 + i * 21;
            int hy = panelY + 8;
            double hpInHeart = Math.max(0, Math.min(hpPerHeart, player.hp - i * hpPerHeart));
            drawPixelHeart(g, hx, hy, hpInHeart / hpPerHeart);
        }
        if (Modules.HUNGER_SYSTEM) {
            int hungerW = 200;
            int hungerX = panelX + 10;
            int hungerY = panelY + 34;
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect(hungerX - 1, hungerY - 1, hungerW + 2, 10);
            g.setColor(new Color(70, 45, 25));
            g.fillRect(hungerX, hungerY, hungerW, 8);
            g.setColor(new Color(210, 140, 50));
            int hFill = (int) (hungerW * (player.hunger / player.maxHunger));
            g.fillRect(hungerX, hungerY, hFill, 8);
            g.setColor(new Color(255, 200, 120));
            g.fillRect(hungerX, hungerY, hFill, 2);
        }
        if (activeBoss != null) {
            int bw = 400;
            int bx = (SCREEN_W - bw) / 2;
            int by = 80;
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(bx - 4, by - 4, bw + 8, 24);
            g.setColor(new Color(80, 0, 0));
            g.fillRect(bx, by, bw, 16);
            g.setColor(new Color(200, 30, 30));
            int fill = (int) (bw * (activeBoss.hp / activeBoss.maxHp));
            g.fillRect(bx, by, fill, 16);
            g.setColor(Color.WHITE);
            g.setFont(PixelFont.font(14, Font.BOLD));
            String bossName = activeBoss.bossType == 100 ? "Король Слизней" : activeBoss.bossType == 101 ? "Глаз Ктулху" : activeBoss.bossType == 102 ? "Скелетрон" : "Король Ада";
            drawCentered(g, bossName + "  " + (int) activeBoss.hp + " / " + (int) activeBoss.maxHp, SCREEN_W / 2, by + 14);
        }

        int xpW = 200;
        int xpX = panelX + 10;
        int xpY = panelY + 52;
        g.setColor(new Color(0, 0, 0));
        g.fillRect(xpX - 2, xpY - 2, xpW + 4, 12);
        g.setColor(new Color(50, 25, 80));
        g.fillRect(xpX, xpY, xpW, 8);
        g.setColor(new Color(140, 80, 220));
        int xpFill = (int) (xpW * (xp / (double) xpToNext));
        g.fillRect(xpX, xpY, xpFill, 8);
        g.setColor(new Color(210, 160, 255));
        g.fillRect(xpX, xpY, xpFill, 2);
        g.setFont(PixelFont.font(11, Font.BOLD));
        g.setColor(Color.WHITE);
        g.drawString("Lv " + xpLevel, xpX + xpW + 6, xpY + 8);

        String timeStr = isNight() ? "НОЧЬ" : "ДЕНЬ";
        g.setFont(PixelFont.font(14, Font.BOLD));
        int rx = SCREEN_W - 110;
        g.setColor(new Color(0, 0, 0));
        g.drawString(timeStr, rx + 1, 27);
        g.setColor(isNight() ? new Color(180, 180, 255) : new Color(255, 240, 100));
        g.drawString(timeStr, rx, 26);
        if (mode == GameMode.CREATIVE) {
            g.setColor(new Color(0, 0, 0));
            g.drawString("КРЕАТИВ", rx + 1, 47);
            g.setColor(new Color(100, 240, 240));
            g.drawString("КРЕАТИВ", rx, 46);
        }
        if (mp != null) {
            g.setColor(new Color(0, 0, 0));
            g.drawString(mp.isHost() ? "ХОСТ" : "КЛИЕНТ", rx + 1, 67);
            g.setColor(new Color(120, 240, 120));
            g.drawString(mp.isHost() ? "ХОСТ" : "КЛИЕНТ", rx, 66);
        }
        int gold = countItem(World.COIN_GOLD);
        if (gold > 0) {
            g.setFont(PixelFont.font(14, Font.BOLD));
            g.setColor(new Color(0, 0, 0));
            g.drawString("* " + gold, 13, SCREEN_H - 12);
            g.setColor(new Color(255, 220, 80));
            g.drawString("* " + gold, 12, SCREEN_H - 13);
        }
        g.setFont(PixelFont.font(11, Font.PLAIN));
        g.setColor(new Color(0, 0, 0));
        g.drawString(fps + " FPS", 13, SCREEN_H - 30);
        g.setColor(new Color(180, 255, 180));
        g.drawString(fps + " FPS", 12, SCREEN_H - 31);

        int totalW = INV_COLS * SLOT;
        int startX = (SCREEN_W - totalW) / 2;
        int startY = SCREEN_H - SLOT - 10;
        g.setColor(new Color(0, 0, 0, 170));
        g.fillRect(startX - 6, startY - 6, totalW + 12, SLOT + 12);
        g.setColor(new Color(160, 160, 180));
        g.drawRect(startX - 6, startY - 6, totalW + 12, SLOT + 12);
        for (int i = 0; i < INV_COLS; i++) {
            int sx = startX + i * SLOT;
            drawMinecraftSlot(g, sx, startY, i == selectedSlot);
            if (inventory[i] != null) drawSlotItem(g, inventory[i], sx, startY);
        }
        ItemStack hover = inventory[selectedSlot];
        if (hover != null) {
            String name = World.blockName(hover.id);
            String tip = ToolUtil.getTooltip(hover.id);
            g.setFont(PixelFont.font(12, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            int tw = Math.max(fm.stringWidth(name), tip.isEmpty() ? 0 : fm.stringWidth(tip)) + 16;
            int tx = SCREEN_W / 2 - tw / 2;
            int ty = startY - 44;
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(tx, ty, tw, tip.isEmpty() ? 24 : 40);
            g.setColor(new Color(220, 200, 100));
            g.drawRect(tx, ty, tw, 24);
            g.setColor(Color.WHITE);
            g.drawString(name, tx + 8, ty + 16);
            if (!tip.isEmpty()) { g.setFont(PixelFont.font(10, Font.PLAIN)); g.setColor(new Color(180, 200, 220)); g.drawString(tip, tx + 8, ty + 32); }
        }
    }

    private void drawCreativePalette(Graphics2D g) {
        int cols = 9, rows = 5;
        int panelW = cols * SLOT + 40, panelH = rows * SLOT + 80;
        int panelX = (SCREEN_W - panelW) / 2, panelY = (SCREEN_H - panelH) / 2;
        drawPixelPanel(g, panelX, panelY, panelW, panelH);
        g.setFont(PixelFont.font(22, Font.BOLD));
        g.setColor(new Color(0, 0, 0));
        g.drawString("КРЕАТИВ — ВЫБОР БЛОКА", panelX + 22, panelY + 32);
        g.setColor(new Color(100, 240, 240));
        g.drawString("КРЕАТИВ — ВЫБОР БЛОКА", panelX + 20, panelY + 30);
        int startIdx = creativePaletteScroll * cols;
        int startX = panelX + 20, startY = panelY + 55;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            int idx = startIdx + r * cols + c;
            if (idx >= CREATIVE_BLOCKS.length) break;
            int sx = startX + c * SLOT, sy = startY + r * SLOT;
            boolean hover = mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT;
            drawMinecraftSlot(g, sx, sy, hover);
            g.drawImage(Textures.get(CREATIVE_BLOCKS[idx]), sx + 8, sy + 8, SLOT - 18, SLOT - 18, null);
        }
        g.setFont(PixelFont.font(11, Font.PLAIN));
        g.setColor(new Color(160, 160, 180));
        g.drawString("ЛКМ — взять · Колесо — прокрутка · C/Esc — закрыть", panelX + 20, panelY + panelH - 12);
    }

    private void handleCreativePaletteClick(int mx, int my) {
        int cols = 9, rows = 5;
        int panelW = cols * SLOT + 40, panelH = rows * SLOT + 80;
        int panelX = (SCREEN_W - panelW) / 2, panelY = (SCREEN_H - panelH) / 2;
        int startX = panelX + 20, startY = panelY + 55;
        int startIdx = creativePaletteScroll * cols;
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            int idx = startIdx + r * cols + c;
            if (idx >= CREATIVE_BLOCKS.length) return;
            int sx = startX + c * SLOT, sy = startY + r * SLOT;
            if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                inventory[selectedSlot] = ItemStack.of(CREATIVE_BLOCKS[idx], 999);
                Sound.play(Sound.CLICK);
                return;
            }
        }
    }

    private void drawPixelHeart(Graphics2D g, int x, int y, double fill) {
        int px = 2;
        int[][] heart = {
            {0, 1, 1, 0, 1, 1, 0}, {1, 1, 1, 1, 1, 1, 1}, {1, 1, 1, 1, 1, 1, 1},
            {0, 1, 1, 1, 1, 1, 0}, {0, 0, 1, 1, 1, 0, 0}, {0, 0, 0, 1, 0, 0, 0}
        };
        for (int row = 0; row < heart.length; row++) for (int col = 0; col < heart[row].length; col++) if (heart[row][col] == 1) { g.setColor(new Color(30, 0, 0)); g.fillRect(x + col * px, y + row * px, px, px); }
        if (fill > 0) {
            int filledCols = (int) Math.ceil(heart[0].length * fill);
            for (int row = 0; row < heart.length; row++) for (int col = 0; col < heart[row].length; col++) if (heart[row][col] == 1 && col < filledCols) { int shade = row < 2 ? 220 : 180; g.setColor(new Color(shade, 20, 20)); g.fillRect(x + col * px, y + row * px, px, px); }
            g.setColor(new Color(255, 180, 180));
            g.fillRect(x + 2 * px, y + 1 * px, px, px);
        }
    }

    private void drawInventory(Graphics2D g) {
        int totalW = INV_COLS * SLOT, totalH = INV_ROWS * SLOT;
        int panelW = totalW + 240, panelH = totalH + 120;
        int panelX = (SCREEN_W - panelW) / 2, panelY = (SCREEN_H - panelH) / 2;
        drawPixelPanel(g, panelX, panelY, panelW, panelH);
        g.setFont(PixelFont.font(22, Font.BOLD));
        g.setColor(new Color(0, 0, 0));
        g.drawString("ИНВЕНТАРЬ", panelX + 22, panelY + 32);
        g.setColor(new Color(255, 220, 100));
        g.drawString("ИНВЕНТАРЬ", panelX + 20, panelY + 30);
        int startX = panelX + 20, startY = panelY + 50;
        for (int r = 0; r < INV_ROWS; r++) for (int c = 0; c < INV_COLS; c++) {
            int i = r * INV_COLS + c;
            int sx = startX + c * SLOT, sy = startY + r * SLOT;
            drawMinecraftSlot(g, sx, sy, i == selectedSlot);
            if (inventory[i] != null && i != draggedFromSlot) drawSlotItem(g, inventory[i], sx, sy);
        }
        int craftX = panelX + totalW + 40, craftY = startY;
        g.setFont(PixelFont.font(18, Font.BOLD));
        g.setColor(new Color(0, 0, 0)); g.drawString("КРАФТ", craftX + 2, craftY - 8);
        g.setColor(new Color(255, 220, 100)); g.drawString("КРАФТ", craftX, craftY - 10);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) {
            int i = r * 3 + c;
            int sx = craftX + c * SLOT, sy = craftY + r * SLOT;
            drawMinecraftSlot(g, sx, sy, false);
            if (craftGrid[i] != null) drawSlotItem(g, craftGrid[i], sx, sy);
        }
        int arrowX = craftX + 3 * SLOT + 15, arrowY = craftY + SLOT + 10;
        g.setColor(new Color(200, 200, 220));
        g.fillRect(arrowX, arrowY, 30, 4);
        Polygon p = new Polygon();
        p.addPoint(arrowX + 30, arrowY - 12); p.addPoint(arrowX + 30, arrowY + 16); p.addPoint(arrowX + 42, arrowY + 2);
        g.fillPolygon(p);
        int resX = arrowX + 50, resY = craftY + SLOT;
        drawMinecraftSlot(g, resX, resY, false);
        updateCraftResult();
        if (craftResult != null) drawSlotItem(g, craftResult, resX, resY);
        g.setFont(PixelFont.font(12, Font.BOLD));
        if (nearWorkbench) { g.setColor(new Color(120, 255, 120)); g.drawString("Верстак рядом", craftX, craftY + 3 * SLOT + 30); }
        else { g.setColor(new Color(180, 180, 200)); g.drawString("Нужен верстак", craftX, craftY + 3 * SLOT + 30); }
        g.setFont(PixelFont.font(11, Font.PLAIN));
        g.setColor(new Color(160, 160, 180));
        g.drawString("ЛКМ — взять/положить · ПКМ — половина · Esc — закрыть", panelX + 20, panelY + panelH - 12);
    }

    private void drawMinecraftSlot(Graphics2D g, int sx, int sy, boolean selected) {
        g.setColor(new Color(139, 139, 139)); g.fillRect(sx, sy, SLOT - 2, SLOT - 2);
        g.setColor(new Color(55, 55, 55)); g.fillRect(sx + 2, sy + 2, SLOT - 6, SLOT - 6);
        g.setColor(new Color(0, 0, 0, 100)); g.drawRect(sx, sy, SLOT - 2, SLOT - 2);
        g.setColor(new Color(255, 255, 255, 180));
        g.drawLine(sx + 2, sy + 2, sx + SLOT - 4, sy + 2);
        g.drawLine(sx + 2, sy + 2, sx + 2, sy + SLOT - 4);
        if (selected) { g.setColor(new Color(255, 255, 255)); g.drawRect(sx - 2, sy - 2, SLOT + 2, SLOT + 2); g.drawRect(sx - 3, sy - 3, SLOT + 4, SLOT + 4); }
    }

    private void updateCraftResult() {
        currentRecipe = Crafting.findRecipe(craftGrid, nearWorkbench);
        if (currentRecipe != null) craftResult = ItemStack.of(currentRecipe.resultId, currentRecipe.resultCount);
        else craftResult = null;
    }

    private void drawContainer(Graphics2D g) {
        if (containerSlots == null || activeContainer == null) return;
        boolean isFurnace = activeContainer.type == World.FURNACE;
        int cols = activeContainer.type == World.CHEST ? 9 : 3;
        int rows = 3;
        int totalW = cols * SLOT, totalH = rows * SLOT;
        int startX = (SCREEN_W - totalW) / 2;
        int startY = (SCREEN_H - totalH) / 2 - 200;
        drawPixelPanel(g, startX - 20, startY - 55, totalW + 40, totalH + 75);
        g.setFont(PixelFont.font(20, Font.BOLD));
        String title = activeContainer.type == World.CHEST ? "СУНДУК" : isFurnace ? "ПЕЧЬ" : "ВЕРСТАК";
        g.setColor(new Color(0, 0, 0)); g.drawString(title, startX + 2, startY - 30);
        g.setColor(new Color(255, 220, 100)); g.drawString(title, startX, startY - 32);
        for (int r = 0; r < rows; r++) for (int c = 0; c < cols; c++) {
            int i = r * cols + c;
            int sx = startX + c * SLOT, sy = startY + r * SLOT;
            drawMinecraftSlot(g, sx, sy, false);
            if (containerSlots[i] != null) drawSlotItem(g, containerSlots[i], sx, sy);
            if (isFurnace) {
                g.setColor(new Color(180, 180, 220)); g.setFont(PixelFont.font(10, Font.PLAIN));
                if (i == 0) g.drawString("РУДА", sx + 8, sy + 14);
                if (i == 1) g.drawString("ТОПЛ", sx + 8, sy + 14);
                if (i == 2) g.drawString("ТОПЛ", sx + 8, sy + 14);
                if (i == 8) g.drawString("РЕЗ.", sx + 10, sy + 14);
            }
        }
        int hotbarY = SCREEN_H - SLOT - 10;
        int hotbarX = (SCREEN_W - INV_COLS * SLOT) / 2;
        g.setColor(new Color(0, 0, 0, 170)); g.fillRect(hotbarX - 6, hotbarY - 6, INV_COLS * SLOT + 12, SLOT + 12);
        g.setColor(new Color(160, 160, 180)); g.drawRect(hotbarX - 6, hotbarY - 6, INV_COLS * SLOT + 12, SLOT + 12);
        for (int i = 0; i < INV_COLS; i++) {
            int sx = hotbarX + i * SLOT;
            drawMinecraftSlot(g, sx, hotbarY, i == selectedSlot);
            if (inventory[i] != null) drawSlotItem(g, inventory[i], sx, hotbarY);
        }
    }

    private void drawSlotItem(Graphics2D g, ItemStack stack, int sx, int sy) {
        BufferedImage icon = Textures.get(stack.id);
        g.drawImage(icon, sx + 8, sy + 8, SLOT - 18, SLOT - 18, null);
        if (stack.count > 1) {
            g.setFont(PixelFont.font(13, Font.BOLD));
            g.setColor(new Color(0, 0, 0)); g.drawString("" + stack.count, sx + SLOT - 20, sy + SLOT - 6);
            g.setColor(Color.WHITE); g.drawString("" + stack.count, sx + SLOT - 21, sy + SLOT - 7);
        }
    }

    private void drawTrade(Graphics2D g) {
        if (activeVillager == null) return;
        Villager v = activeVillager;
        int panelW = 600;
        int panelH = 100 + v.trades.length * 62;
        int panelX = (SCREEN_W - panelW) / 2, panelY = (SCREEN_H - panelH) / 2;
        drawPixelPanel(g, panelX, panelY, panelW, panelH);
        g.setFont(PixelFont.font(22, Font.BOLD));
        g.setColor(new Color(0, 0, 0)); g.drawString("ТОРГОВЛЯ · " + v.profession.toUpperCase(), panelX + 22, panelY + 32);
        g.setColor(new Color(255, 220, 100)); g.drawString("ТОРГОВЛЯ · " + v.profession.toUpperCase(), panelX + 20, panelY + 30);
        int rowY = panelY + 55;
        for (int i = 0; i < v.trades.length; i++) {
            int[] t = v.trades[i];
            int giveId = t[0], giveCnt = t[1], getId = t[2], getCnt = t[3];
            int rowX = panelX + 20, rowW = panelW - 40, rowH = 54;
            boolean hover = mouseX > rowX && mouseX < rowX + rowW && mouseY > rowY && mouseY < rowY + rowH - 8;
            boolean canTrade = v.canTrade(i, inventory);
            g.setColor(hover ? new Color(80, 70, 45, 220) : new Color(40, 35, 30, 200));
            g.fillRect(rowX, rowY, rowW, rowH - 8);
            g.setColor(canTrade ? new Color(150, 200, 120) : new Color(120, 90, 60));
            g.drawRect(rowX, rowY, rowW, rowH - 8);
            g.drawImage(Textures.get(giveId), rowX + 8, rowY + 6, 32, 32, null);
            g.setFont(PixelFont.font(14, Font.BOLD)); g.setColor(Color.WHITE);
            g.drawString("x" + giveCnt, rowX + 46, rowY + 26);
            g.setFont(PixelFont.font(10, Font.PLAIN)); g.setColor(new Color(200, 200, 220));
            g.drawString(World.blockName(giveId), rowX + 8, rowY + 44);
            g.setFont(PixelFont.font(20, Font.BOLD)); g.setColor(new Color(220, 220, 240));
            g.drawString("->", rowX + 150, rowY + 30);
            int gx = rowX + 200;
            g.drawImage(Textures.get(getId), gx, rowY + 6, 32, 32, null);
            g.setFont(PixelFont.font(14, Font.BOLD)); g.setColor(new Color(140, 255, 140));
            g.drawString("x" + getCnt, gx + 38, rowY + 26);
            g.setFont(PixelFont.font(10, Font.PLAIN)); g.setColor(new Color(200, 200, 220));
            g.drawString(World.blockName(getId), gx, rowY + 44);
            int have = 0;
            for (ItemStack s : inventory) if (s != null && s.id == giveId) have += s.count;
            g.setFont(PixelFont.font(12, Font.BOLD));
            if (have >= giveCnt) { g.setColor(new Color(140, 255, 140)); g.drawString("OK " + have + "/" + giveCnt, rowX + rowW - 90, rowY + 26); }
            else { g.setColor(new Color(255, 120, 120)); g.drawString("X " + have + "/" + giveCnt, rowX + rowW - 90, rowY + 26); }
            rowY += rowH;
        }
        g.setFont(PixelFont.font(11, Font.PLAIN)); g.setColor(new Color(160, 160, 180));
        g.drawString("ЛКМ по сделке — обмен · ПКМ / Esc — закрыть", panelX + 20, panelY + panelH - 10);
    }

    private void drawChat(Graphics2D g) {
        int startY = SCREEN_H - SLOT - 60;
        g.setFont(PixelFont.font(13, Font.PLAIN));
        int count = Math.min(chatVisibleCount, chatLog.size());
        for (int i = 0; i < count; i++) {
            ChatMessage msg = chatLog.get(chatLog.size() - count + i);
            int y = startY - (count - i) * 18;
            g.setColor(new Color(0, 0, 0, 120));
            FontMetrics fm = g.getFontMetrics();
            g.fillRect(10, y - 14, fm.stringWidth(msg.text) + 12, 18);
            g.setColor(msg.color);
            g.drawString(msg.text, 16, y);
        }
        if (chatOpen) {
            g.setColor(new Color(0, 0, 0, 200)); g.fillRect(10, startY, SCREEN_W - 20, 30);
            g.setColor(new Color(255, 255, 255)); g.drawRect(10, startY, SCREEN_W - 20, 30);
            g.setColor(Color.WHITE); g.setFont(PixelFont.font(16, Font.BOLD));
            g.drawString("> " + chatInput + "_", 20, startY + 22);
        }
    }

    private void drawPause(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 200)); g.fillRect(0, 0, SCREEN_W, SCREEN_H);
        g.setFont(PixelFont.font(58, Font.BOLD));
        drawCenteredWithPixelShadow(g, "ПАУЗА", SCREEN_W / 2, SCREEN_H / 2 - 120, new Color(255, 220, 100));
        drawPixelButton(g, "Продолжить [Esc]", SCREEN_W / 2 - 200, SCREEN_H / 2 - 40, 400, 55, mouseY > SCREEN_H / 2 - 40 && mouseY < SCREEN_H / 2 + 15 && mouseX > SCREEN_W / 2 - 200 && mouseX < SCREEN_W / 2 + 200);
        drawPixelButton(g, "Сохранить [S]", SCREEN_W / 2 - 200, SCREEN_H / 2 + 30, 400, 55, mouseY > SCREEN_H / 2 + 30 && mouseY < SCREEN_H / 2 + 85 && mouseX > SCREEN_W / 2 - 200 && mouseX < SCREEN_W / 2 + 200);
        drawPixelButton(g, "В меню [Q]", SCREEN_W / 2 - 200, SCREEN_H / 2 + 100, 400, 55, mouseY > SCREEN_H / 2 + 100 && mouseY < SCREEN_H / 2 + 155 && mouseX > SCREEN_W / 2 - 200 && mouseX < SCREEN_W / 2 + 200);
    }

    private void drawDeath(Graphics2D g) {
        g.setColor(new Color(100, 0, 0, 210)); g.fillRect(0, 0, SCREEN_W, SCREEN_H);
        g.setFont(PixelFont.font(58, Font.BOLD));
        drawCenteredWithPixelShadow(g, "ТЫ УМЕР", SCREEN_W / 2, SCREEN_H / 2 - 60, new Color(255, 100, 100));
        g.setFont(PixelFont.font(20, Font.PLAIN));
        g.setColor(Color.WHITE);
        drawCentered(g, "[ R ] Возродиться    [ Q ] В меню", SCREEN_W / 2, SCREEN_H / 2 + 30);
    }

    private void drawAchievements(Graphics2D g) {
        drawPixelBackground(g);
        g.setFont(PixelFont.font(42, Font.BOLD));
        drawCenteredWithPixelShadow(g, "ДОСТИЖЕНИЯ", SCREEN_W / 2, 70, new Color(255, 220, 100));
        int px = 90, py = 140, colW = 400;
        for (int i = 0; i < achievements.size(); i++) {
            Achievement a = achievements.get(i);
            int col = i / 7, row = i % 7;
            int x = px + col * colW, y = py + row * 55;
            g.setColor(a.unlocked ? new Color(40, 80, 40) : new Color(30, 30, 45));
            g.fillRect(x, y, colW - 20, 48);
            g.setColor(a.unlocked ? new Color(120, 220, 120) : new Color(90, 90, 120));
            g.drawRect(x, y, colW - 20, 48);
            g.setColor(a.unlocked ? new Color(255, 240, 100) : new Color(180, 180, 200));
            g.setFont(PixelFont.font(16, Font.BOLD));
            g.drawString((a.unlocked ? "* " : "- ") + a.title, x + 15, y + 22);
            g.setColor(Color.WHITE);
            g.setFont(PixelFont.font(12, Font.PLAIN));
            g.drawString(a.description, x + 15, y + 40);
        }
        int unlockedCount = 0;
        for (Achievement a : achievements) if (a.unlocked) unlockedCount++;
        g.setColor(new Color(255, 220, 100));
        g.setFont(PixelFont.font(20, Font.BOLD));
        drawCentered(g, "Открыто: " + unlockedCount + " / " + achievements.size(), SCREEN_W / 2, SCREEN_H - 80);
        drawPixelButton(g, "Назад [Esc]", SCREEN_W / 2 - 150, SCREEN_H - 70, 300, 50, mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150);
    }

    private void drawSettings(Graphics2D g) {
        drawPixelBackground(g);
        g.setFont(PixelFont.font(42, Font.BOLD));
        drawCenteredWithPixelShadow(g, "НАСТРОЙКИ", SCREEN_W / 2, 80, new Color(255, 220, 100));
        g.setFont(PixelFont.font(18, Font.BOLD));
        g.setColor(Color.WHITE);
        g.drawString("Имя игрока:", 100, 160);
        int nameBx = 260, nameBy = 140, nameW = 400, nameH = 36;
        g.setColor(new Color(0, 0, 0, 220)); g.fillRect(nameBx - 3, nameBy - 3, nameW + 6, nameH + 6);
        g.setColor(typingName ? new Color(255, 240, 100) : new Color(150, 150, 180));
        g.drawRect(nameBx - 2, nameBy - 2, nameW + 4, nameH + 4);
        g.setColor(new Color(30, 30, 50)); g.fillRect(nameBx, nameBy, nameW, nameH);
        g.setColor(Color.WHITE); g.setFont(PixelFont.font(16, Font.BOLD));
        g.drawString(playerName + (typingName ? "_" : ""), nameBx + 10, nameBy + 24);
        g.setFont(PixelFont.font(14, Font.PLAIN)); g.setColor(new Color(200, 200, 220));
        int y = 220;
        g.drawString("F11 — Полный экран", 100, y); y += 26;
        g.drawString("E — Инвентарь + крафт", 100, y); y += 26;
        g.drawString("T — Чат", 100, y); y += 26;
        g.drawString("M — Креатив / Выживание", 100, y); y += 26;
        g.drawString("C — Палитра блоков (креатив)", 100, y); y += 26;
        g.drawString("A/D — Движение · Space — Прыжок", 100, y); y += 26;
        g.drawString("ЛКМ — Копать/Атаковать · ПКМ — Ставить/Открыть", 100, y); y += 26;
        g.drawString("ПКМ по жителю — торговля", 100, y); y += 26;
        g.drawString("ПКМ по еде/зелью — использовать", 100, y); y += 26;
        drawPixelButton(g, "Назад [Esc]", SCREEN_W / 2 - 150, SCREEN_H - 70, 300, 50, mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150);
    }

    private void drawCentered(Graphics2D g, String s, int cx, int y) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, y);
    }

    private void drawCenteredWithPixelShadow(Graphics2D g, String s, int cx, int y, Color color) {
        FontMetrics fm = g.getFontMetrics();
        int x = cx - fm.stringWidth(s) / 2;
        g.setColor(new Color(0, 0, 0, 220));
        g.drawString(s, x + 4, y + 4);
        g.setColor(color);
        g.drawString(s, x, y);
    }
    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code < 256) keys[code] = true;
        if (typingName) {
            if (code == KeyEvent.VK_ENTER || code == KeyEvent.VK_ESCAPE) { typingName = false; return; }
            if (code == KeyEvent.VK_BACK_SPACE) { if (!playerName.isEmpty()) playerName = playerName.substring(0, playerName.length() - 1); return; }
            char ch = e.getKeyChar();
            if ((Character.isLetterOrDigit(ch) || ch == '_' || ch == ' ') && playerName.length() < 16) playerName += ch;
            return;
        }
        if (chatOpen) {
            if (code == KeyEvent.VK_ENTER) { if (!chatInput.trim().isEmpty()) executeChatCommand(chatInput); chatInput = ""; chatOpen = false; return; }
            if (code == KeyEvent.VK_ESCAPE) { chatOpen = false; chatInput = ""; return; }
            if (code == KeyEvent.VK_BACK_SPACE) { if (!chatInput.isEmpty()) chatInput = chatInput.substring(0, chatInput.length() - 1); return; }
            char ch = e.getKeyChar();
            if (ch >= 32 && ch < 127 && chatInput.length() < 100) chatInput += ch;
            return;
        }
        if (state == GameState.WORLD_CREATE && typingSeed) {
            if (code == KeyEvent.VK_ENTER) { createWorldWithSeed(); return; }
            if (code == KeyEvent.VK_ESCAPE) { state = GameState.WORLD_SELECT; typingSeed = false; return; }
            if (code == KeyEvent.VK_BACK_SPACE) { if (!inputSeed.isEmpty()) inputSeed = inputSeed.substring(0, inputSeed.length() - 1); return; }
            char ch = e.getKeyChar();
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') if (inputSeed.length() < 40) inputSeed += ch;
            return;
        }
        if (state == GameState.MULTIPLAYER && typingMpAddress) {
            if (code == KeyEvent.VK_ENTER) { typingMpAddress = false; return; }
            if (code == KeyEvent.VK_ESCAPE) { typingMpAddress = false; return; }
            if (code == KeyEvent.VK_BACK_SPACE) { if (!mpAddress.isEmpty()) mpAddress = mpAddress.substring(0, mpAddress.length() - 1); return; }
            char ch = e.getKeyChar();
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == ':') if (mpAddress.length() < 40) mpAddress += ch;
            return;
        }
        if (code == KeyEvent.VK_F11) { toggleFullscreen(); return; }
        switch (state) {
            case MENU: if (code == KeyEvent.VK_ESCAPE) System.exit(0); break;
            case WORLD_SELECT:
                if (code == KeyEvent.VK_ESCAPE) state = GameState.MENU;
                if (code == KeyEvent.VK_UP) selectedWorldIndex = Math.max(0, selectedWorldIndex - 1);
                if (code == KeyEvent.VK_DOWN) selectedWorldIndex = Math.min(Math.max(0, worldSaves.size() - 1), selectedWorldIndex + 1);
                if (code == KeyEvent.VK_N) { state = GameState.WORLD_CREATE; inputSeed = ""; typingSeed = true; }
                if (code == KeyEvent.VK_DELETE && !worldSaves.isEmpty()) deleteWorld();
                if (code == KeyEvent.VK_ENTER && !worldSaves.isEmpty()) loadSelectedWorld();
                break;
            case MULTIPLAYER: if (code == KeyEvent.VK_ESCAPE) { state = GameState.MENU; typingMpAddress = false; } break;
            case LOADING: if (code == KeyEvent.VK_ESCAPE) { if (mp != null) { mp.close(); mp = null; } state = GameState.MULTIPLAYER; } break;
            case PLAYING:
                if (code == KeyEvent.VK_ESCAPE && activeVillager != null) { activeVillager = null; return; }
                if (code == KeyEvent.VK_T) { chatOpen = true; chatInput = ""; return; }
                if (code == KeyEvent.VK_ESCAPE) {
                    if (activeContainer != null) { activeContainer = null; containerSlots = null; }
                    else if (inventoryOpen) { inventoryOpen = false; returnDragged(); returnCraftGrid(); }
                    else state = GameState.PAUSED;
                }
                if (code == KeyEvent.VK_E) { inventoryOpen = !inventoryOpen; if (!inventoryOpen) { returnDragged(); returnCraftGrid(); } }
                if (code == KeyEvent.VK_M) mode = mode == GameMode.SURVIVAL ? GameMode.CREATIVE : GameMode.SURVIVAL;
                if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) selectedSlot = code - KeyEvent.VK_1;
                if (code == KeyEvent.VK_F5) saveCurrentWorld();
                if (code == KeyEvent.VK_C && mode == GameMode.CREATIVE) { creativePaletteOpen = !creativePaletteOpen; return; }
                if (code == KeyEvent.VK_SHIFT && mode == GameMode.CREATIVE && player.flyMode) { player.flyDown = true; return; }
                // Двойной пробел = полёт (креатив)
                if (code == KeyEvent.VK_SPACE && mode == GameMode.CREATIVE) {
                    long now = System.currentTimeMillis();
                    if (now - lastSpacePress < 300) {
                        player.flyMode = !player.flyMode;
                        addChatMessage("Полёт: " + (player.flyMode ? "ВКЛ" : "ВЫКЛ"), new Color(120, 255, 120));
                    }
                    lastSpacePress = now;
                }
                break;
            case PAUSED:
                if (code == KeyEvent.VK_ESCAPE) state = GameState.PLAYING;
                if (code == KeyEvent.VK_S) saveCurrentWorld();
                if (code == KeyEvent.VK_Q) backToMenu();
                break;
            case DEAD:
                if (code == KeyEvent.VK_R) respawn();
                if (code == KeyEvent.VK_Q) backToMenu();
                break;
            case ACHIEVEMENTS: if (code == KeyEvent.VK_ESCAPE) state = GameState.MENU; break;
            case SETTINGS: if (code == KeyEvent.VK_ESCAPE) state = GameState.MENU; break;
            case WORLD_CREATE: if (code == KeyEvent.VK_ESCAPE) state = GameState.WORLD_SELECT; break;
            case CHANGELOG: if (code == KeyEvent.VK_ESCAPE) state = GameState.MENU; break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() < 256) keys[e.getKeyCode()] = false;
        if (e.getKeyCode() == KeyEvent.VK_SHIFT && player != null) player.flyDown = false;
        if (e.getKeyCode() == KeyEvent.VK_SPACE && player != null) player.flyUp = false;
    }
    @Override public void keyTyped(KeyEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        mouseX = e.getX(); mouseY = e.getY();
        boolean isLeft = SwingUtilities.isLeftMouseButton(e);
        boolean isRight = SwingUtilities.isRightMouseButton(e);
        if (isLeft) mouseDown = true;
        if (isRight) rightMouseDown = true;
        if (state == GameState.PLAYING && activeContainer != null) {
            if (handleContainerClick(mouseX, mouseY, isRight)) return;
            if (handleInventoryClickInContainer(mouseX, mouseY, isRight)) return;
            return;
        }
        if (state == GameState.PLAYING && inventoryOpen) { handleInventoryClick(mouseX, mouseY, isRight); return; }
        handleClick(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) mouseDown = false;
        if (SwingUtilities.isRightMouseButton(e)) rightMouseDown = false;
        if (state == GameState.PLAYING) digProgress = 0;
    }

    @Override public void mouseMoved(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mouseDragged(MouseEvent e) { mouseX = e.getX(); mouseY = e.getY(); }
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        if (state == GameState.CHANGELOG) { changelogScroll += e.getWheelRotation(); return; }
        if (state != GameState.PLAYING) return;
        if (creativePaletteOpen && mode == GameMode.CREATIVE) {
            int maxScroll = Math.max(0, (CREATIVE_BLOCKS.length + 44) / 9 - 5);
            creativePaletteScroll = Math.max(0, Math.min(maxScroll, creativePaletteScroll + e.getWheelRotation()));
            return;
        }
        int notches = e.getWheelRotation();
        selectedSlot = (selectedSlot + notches + INV_COLS) % INV_COLS;
    }

    private void handleClick(MouseEvent e) {
        if (state == GameState.PLAYING && chatOpen) return;
        Sound.play(Sound.CLICK);
        switch (state) {
            case MENU: handleMenuClick(); break;
            case WORLD_SELECT: handleWorldSelectClick(); break;
            case WORLD_CREATE: handleWorldCreateClick(); break;
            case MULTIPLAYER: handleMultiplayerClick(); break;
            case PLAYING: handleGameClick(e); break;
            case PAUSED: handlePauseClick(); break;
            case ACHIEVEMENTS: if (mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20) state = GameState.MENU; break;
            case SETTINGS:
                if (mouseX > 260 && mouseX < 660 && mouseY > 140 && mouseY < 176) { typingName = true; return; }
                typingName = false;
                if (mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20) state = GameState.MENU;
                break;
            case CHANGELOG: if (mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20) state = GameState.MENU; break;
        }
    }

    private void handleMenuClick() {
        int btnW = 280;
        int btnH = 55;
        int gapX = 20;
        int gapY = 20;

        int totalRowW = btnW * 2 + gapX;
        int rowStartX = (SCREEN_W - totalRowW) / 2;

        int topX = rowStartX;
        int topW = totalRowW;
        int topY = 200;

        // Верхняя широкая
        if (mouseX > topX && mouseX < topX + topW && mouseY > topY && mouseY < topY + btnH) {
            state = GameState.WORLD_SELECT;
            return;
        }

        // Нижние ряды
        GameState[][] targets = {
            { GameState.MULTIPLAYER, GameState.WORLD_SELECT },       // Мультиплеер, Датапаки
            { GameState.CHANGELOG,   GameState.ACHIEVEMENTS },       // Что нового, Достижения
            { GameState.SETTINGS,    null }                          // Подсказка, Выход
        };

        int firstRowY = topY + btnH + gapY + 20;
        for (int r = 0; r < 3; r++) {
            int rowY = firstRowY + r * (btnH + gapY);
            for (int c = 0; c < 2; c++) {
                int bx = rowStartX + c * (btnW + gapX);
                if (mouseX > bx && mouseX < bx + btnW && mouseY > rowY && mouseY < rowY + btnH) {
                    if (targets[r][c] == null) System.exit(0);
                    else state = targets[r][c];
                    return;
                }
            }
        }
    }

    private void handleWorldSelectClick() {
        int listX = 60, listY = 120, itemH = 60, listW = 520;
        for (int i = 0; i < Math.min(worldSaves.size(), 6); i++) {
            int y = listY + i * itemH;
            if (mouseY > y && mouseY < y + itemH - 6 && mouseX > listX && mouseX < listX + listW) { selectedWorldIndex = i; loadSelectedWorld(); return; }
        }
        if (mouseY > 520 && mouseY < 570) {
            if (mouseX > 60 && mouseX < 310) { state = GameState.WORLD_CREATE; inputSeed = ""; typingSeed = true; return; }
            if (mouseX > 330 && mouseX < 580 && !worldSaves.isEmpty()) { loadSelectedWorld(); return; }
        }
        if (mouseY > 580 && mouseY < 630) {
            if (mouseX > 60 && mouseX < 260) { state = GameState.MENU; return; }
            if (mouseX > 330 && mouseX < 580 && !worldSaves.isEmpty()) { deleteWorld(); return; }
        }
    }

    private void handleWorldCreateClick() {
        if (mouseY > 240 && mouseY < 290 && mouseX > SCREEN_W / 2 - 250 && mouseX < SCREEN_W / 2 + 250) { typingSeed = true; return; }
        if (mouseY > 380 && mouseY < 435 && mouseX > SCREEN_W / 2 - 220 && mouseX < SCREEN_W / 2 + 220) { createWorldWithSeed(); return; }
        if (mouseY > 460 && mouseY < 510 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150) { state = GameState.WORLD_SELECT; typingSeed = false; return; }
        typingSeed = false;
    }

    private void handleMultiplayerClick() {
        if (mouseY > 230 && mouseY < 280 && mouseX > SCREEN_W / 2 - 250 && mouseX < SCREEN_W / 2 + 250) { typingMpAddress = true; return; }
        if (mouseY > 350 && mouseY < 405 && mouseX > SCREEN_W / 2 - 220 && mouseX < SCREEN_W / 2 + 220) { startMultiplayerHost(); return; }
        if (mouseY > 420 && mouseY < 475 && mouseX > SCREEN_W / 2 - 220 && mouseX < SCREEN_W / 2 + 220) { startMultiplayerClient(); return; }
        if (mouseY > 500 && mouseY < 550 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150) { state = GameState.MENU; typingMpAddress = false; return; }
        typingMpAddress = false;
    }

    private void handlePauseClick() {
        if (mouseX < SCREEN_W / 2 - 200 || mouseX > SCREEN_W / 2 + 200) return;
        if (mouseY > SCREEN_H / 2 - 40 && mouseY < SCREEN_H / 2 + 15) state = GameState.PLAYING;
        else if (mouseY > SCREEN_H / 2 + 30 && mouseY < SCREEN_H / 2 + 85) saveCurrentWorld();
        else if (mouseY > SCREEN_H / 2 + 100 && mouseY < SCREEN_H / 2 + 155) backToMenu();
    }

    private void handleGameClick(MouseEvent e) {
        if (player == null || world == null) return;
        boolean isHostOrSolo = (mp == null) || mp.isHost();
        boolean isLeft = SwingUtilities.isLeftMouseButton(e);
        boolean isRight = SwingUtilities.isRightMouseButton(e);

        // ---- житель (торговля) ----
        if (activeVillager != null) {
            if (isRight) { activeVillager = null; return; }
            if (isLeft) { handleTradeClick(mouseX, mouseY); return; }
        }

        // ---- креативная палитра ----
        if (creativePaletteOpen && mode == GameMode.CREATIVE) {
            if (isRight) { creativePaletteOpen = false; return; }
            if (isLeft) { handleCreativePaletteClick(mouseX, mouseY); return; }
        }

        // ---- инвентарь / контейнер ----
        if (inventoryOpen && handleInventoryClick(mouseX, mouseY, isRight)) return;
        if (activeContainer != null && handleContainerClick(mouseX, mouseY, isRight)) return;

        ItemStack held = inventory[selectedSlot];

        // ---- клик по хотбару ----
        int totalW = INV_COLS * SLOT;
        int startX = (SCREEN_W - totalW) / 2;
        int startY = SCREEN_H - SLOT - 10;
        if (mouseY >= startY && mouseY < startY + SLOT) {
            int idx = (mouseX - startX) / SLOT;
            if (idx >= 0 && idx < INV_COLS) { selectedSlot = idx; return; }
        }

        // ---- мировые координаты ----
        int wx = (int) ((mouseX + player.cameraX) / TILE);
        int wy = (int) ((mouseY + player.cameraY) / TILE);
        if (!world.inBounds(wx, wy)) return;

        // ---- радиус взаимодействия ----
        double pdx = wx * TILE - player.x;
        double pdy = wy * TILE - player.y;
        if (pdx * pdx + pdy * pdy > (6 * TILE) * (6 * TILE)) return;

        // =====================================================================
        // ПКМ
        // =====================================================================
        if (isRight) {
            // 1) Житель — открыть торговлю
            for (Villager v : villagers) {
                if (wx * TILE >= v.x - 10 && wx * TILE <= v.x + Player.W + 10
                        && wy * TILE >= v.y - 10 && wy * TILE <= v.y + Player.H + 10) {
                    activeVillager = v;
                    return;
                }
            }

            int clickedBlock = world.getBlock(wx, wy);

            // 2) Дверь — открыть/закрыть
            if (clickedBlock == World.DOOR_CLOSED || clickedBlock == World.DOOR_OPEN) {
                int newState = clickedBlock == World.DOOR_CLOSED ? World.DOOR_OPEN : World.DOOR_CLOSED;
                world.setBlock(wx, wy, newState);
                if (mp != null) mp.sendBlockChange(wx, wy, newState);
                return;
            }

            // 3) Контейнеры — открыть
            if (clickedBlock == World.CHEST || clickedBlock == World.FURNACE
                    || clickedBlock == World.WORKBENCH || clickedBlock == World.CRAFT_TABLE) {
                openContainer(wx, wy, clickedBlock);
                return;
            }

            // 4) Установка фона (стены)
            ItemStack heldCheck = inventory[selectedSlot];
            if (heldCheck != null && isBgBlock(heldCheck.id)
                    && world.getBackground(wx, wy) == World.AIR) {
                world.setBackground(wx, wy, heldCheck.id);
                if (mode == GameMode.SURVIVAL) {
                    heldCheck.count--;
                    if (heldCheck.count <= 0) inventory[selectedSlot] = null;
                }
                if (mp != null) mp.sendBgChange(wx, wy, heldCheck.id);
                return;
            }

            // 5) Установка блока
            if (heldCheck != null && world.getBlock(wx, wy) == World.AIR
                    && !ToolUtil.isTool(heldCheck.id)) {
                int bx = (int) (player.x / TILE);
                int by = (int) (player.y / TILE);
                boolean inside = (wx == bx || wx == bx + (Player.W / TILE))
                        && (wy == by || wy == by + 1);
                if (!inside) {
                    if (mode == GameMode.CREATIVE) {
                        world.setBlock(wx, wy, heldCheck.id);
                        if (mp != null) mp.sendBlockChange(wx, wy, heldCheck.id);
                        ModManager.blockPlace(wx, wy, heldCheck.id);
                    } else if (heldCheck.count > 0) {
                        world.setBlock(wx, wy, heldCheck.id);
                        if (mp != null) mp.sendBlockChange(wx, wy, heldCheck.id);
                        ModManager.blockPlace(wx, wy, heldCheck.id);
                        heldCheck.count--;
                        if (heldCheck.count <= 0) inventory[selectedSlot] = null;
                    }
                }
            }
            return;
        }

        if (!isLeft) return;

        // =====================================================================
        // ЛКМ
        // =====================================================================

        // 1) Атака других игроков
        for (RemotePlayer rp : remotePlayers) {
            if (wx * TILE >= rp.x - 4 && wx * TILE <= rp.x + Player.W + 4
                    && wy * TILE >= rp.y - 4 && wy * TILE <= rp.y + Player.H + 4) {
                int dmg = ToolUtil.getSwordDamage(held);
                if (mp != null) mp.sendDamage(rp.id, dmg);
                floaters.add(new FloatingText("-" + dmg, rp.x, rp.y - 10, new Color(255, 100, 100)));
                return;
            }
        }

        // 2) Атака мирных мобов
        for (Iterator<PeacefulMob> it = peacefulMobs.iterator(); it.hasNext();) {
            PeacefulMob pm = it.next();
            if (wx * TILE >= pm.x - 4 && wx * TILE <= pm.x + PeacefulMob.W + 4
                    && wy * TILE >= pm.y - 4 && wy * TILE <= pm.y + PeacefulMob.H + 4) {
                it.remove();
                Random rnd = new Random();
                switch (pm.type) {
                    case 0: addToInventory(ItemStack.of(World.MEAT_RAW, 2 + rnd.nextInt(2))); break;
                    case 1: addToInventory(ItemStack.of(World.MEAT_RAW, 1)); break;
                    case 2: addToInventory(ItemStack.of(World.MEAT_RAW, 1)); break;
                    case 3: addToInventory(ItemStack.of(World.WOOL_ITEM, 2)); break;
                }
                addXp(2);
                Sound.play(Sound.HIT);
                return;
            }
        }

        // 3) Атака монстров
        Monster target = null;
        for (Monster m : monsters) {
            if (wx * TILE >= m.x - 4 && wx * TILE <= m.x + Monster.W + 4
                    && wy * TILE >= m.y - 4 && wy * TILE <= m.y + Monster.H + 4) {
                target = m;
                break;
            }
        }
        if (target != null && isHostOrSolo) {
            int dmg2 = ToolUtil.getSwordDamage(held);
            if (player.strengthTimer > 0) dmg2 = (int) (dmg2 * 1.5);
            target.hp -= dmg2;
            floaters.add(new FloatingText("-" + dmg2, target.x, target.y - 10, new Color(255, 100, 100)));
            Sound.play(Sound.HIT);
            if (target.hp <= 0) {
                target.dead = true;
                ModManager.monsterKilled(target.type);
                if (target.isBoss) {
                    defeatedBosses.add(target.bossType);
                    activeBoss = null;
                    if (target.bossType == 100) {
                        addToInventory(ItemStack.of(World.COIN_GOLD, 50));
                        addToInventory(ItemStack.of(World.IRON_INGOT, 10));
                        unlock("boss_slime");
                    } else if (target.bossType == 101) {
                        addToInventory(ItemStack.of(World.COIN_GOLD, 100));
                        addToInventory(ItemStack.of(World.GOLD_INGOT, 10));
                        unlock("boss_eye");
                    } else if (target.bossType == 102) {
                        addToInventory(ItemStack.of(World.COIN_GOLD, 200));
                        addToInventory(ItemStack.of(World.DIAMOND_GEM, 5));
                        unlock("boss_skeleton");
                    } else if (target.bossType == 103) {
                        addToInventory(ItemStack.of(World.COIN_GOLD, 1000));
                        addToInventory(ItemStack.of(World.HELL_CROWN, 1));
                        unlock("boss_hell");
                        addChatMessage("КОРОЛЬ АД ПОВЕРЖЕН! Игра пройдена!", new Color(255, 220, 100));
                    }
                    addChatMessage("Босс побеждён!", new Color(255, 220, 100));
                }
                Random rnd = new Random();
                addToInventory(ItemStack.of(World.COIN_GOLD, 1 + rnd.nextInt(3)));
                ItemStack loot = target.type == 0 ? ItemStack.of(World.STONE, 1 + rnd.nextInt(2))
                        : target.type == 1 ? ItemStack.of(World.DIRT, 1 + rnd.nextInt(2))
                        : ItemStack.of(World.GRASS, 1);
                addToInventory(loot);
                addXp(3 + rnd.nextInt(3));
                unlock("first_monster");
            }
            return;
        }

        // 4) Ведро — набрать жидкость
        if (held != null && held.id == World.BUCKET_EMPTY) {
            if (world.getLiquid(wx, wy) > 0) {
                int lvl = world.getLiquid(wx, wy);
                boolean isLava = lvl >= 8;
                world.setLiquid(wx, wy, 0);
                held.id = isLava ? World.BUCKET_LAVA : World.BUCKET_WATER;
                Sound.play(Sound.CLICK);
                return;
            }
        }
        if (held != null && (held.id == World.BUCKET_WATER || held.id == World.BUCKET_LAVA)) {
            if (world.getBlock(wx, wy) == World.AIR && world.getLiquid(wx, wy) == 0) {
                boolean isLava = held.id == World.BUCKET_LAVA;
                world.setLiquid(wx, wy, isLava ? 8 : 7);
                held.id = World.BUCKET_EMPTY;
                Sound.play(Sound.CLICK);
                return;
            }
        }

        // 5) Еда
        if (isRight && held != null) {
            // (эта ветка недостижима — isRight уже вернулся выше, оставлено для совместимости)
        }
        if (held != null) {
            int heal = 0, sat = 0;
            switch (held.id) {
                case World.BREAD: heal = 5; sat = 30; break;
                case World.APPLE: heal = 3; sat = 20; break;
                case World.MEAT_COOKED: heal = 10; sat = 50; break;
                case World.MEAT_RAW: heal = 3; sat = 15; break;
                case World.MUSHROOM_SOUP: heal = 8; sat = 40; break;
                case World.GOLDEN_APPLE: heal = 50; sat = 100; break;
                case World.CARROT: heal = 2; sat = 10; break;
            }
            if (heal > 0) {
                player.hp = Math.min(player.maxHp, player.hp + heal);
                player.satiety = Math.min(player.satietyMax, player.satiety + sat);
                player.hunger = Math.min(player.maxHunger, player.hunger + sat * 0.5);
                held.count--;
                if (held.count <= 0) inventory[selectedSlot] = null;
                Sound.play(Sound.TRADE);
                floaters.add(new FloatingText("+" + heal + " HP, +" + sat + " еды",
                        player.x, player.y - 30, new Color(120, 255, 120)));
                return;
            }
        }

        // 6) Зелья
        if (held != null) {
            int effect = 0;
            switch (held.id) {
                case World.POTION_HEAL:
                    player.hp = Math.min(player.maxHp, player.hp + 50);
                    floaters.add(new FloatingText("+50 HP", player.x, player.y - 30, new Color(255, 100, 200)));
                    break;
                case World.POTION_STRENGTH: player.strengthTimer = 600; break;
                case World.POTION_SPEED:    player.speedTimer = 600; break;
                case World.POTION_REGEN:    player.regenTimer = -100; break;
                case World.POTION_NIGHT:    player.nightVisionTimer = 1200; break;
                case World.POTION_FIRE:     player.fireResistTimer = 1200; break;
                default: effect = -1;
            }
            if (effect != -1) {
                held.count--;
                if (held.count <= 0) inventory[selectedSlot] = null;
                Sound.play(Sound.TRADE);
                return;
            }
        }

        // 7) Дверь — сломать (ЛКМ)
        int clickedBlock = world.getBlock(wx, wy);
        if (clickedBlock == World.DOOR_CLOSED || clickedBlock == World.DOOR_OPEN) {
            int otherY = (world.getBlock(wx, wy - 1) == World.DOOR_CLOSED
                    || world.getBlock(wx, wy - 1) == World.DOOR_OPEN) ? wy - 1
                    : (world.getBlock(wx, wy + 1) == World.DOOR_CLOSED
                    || world.getBlock(wx, wy + 1) == World.DOOR_OPEN) ? wy + 1
                    : wy;
            world.setBlock(wx, wy, World.AIR);
            world.setBlock(wx, otherY, World.AIR);
            if (mode == GameMode.SURVIVAL) addToInventory(ItemStack.of(World.DOOR_CLOSED, 1));
            if (mp != null) {
                mp.sendBlockChange(wx, wy, World.AIR);
                mp.sendBlockChange(wx, otherY, World.AIR);
            }
            Sound.play(Sound.MINE);
            return;
        }

        // 8) Копать
        startDigging(wx, wy);
    }

    private void handleTradeClick(int mx, int my) {
        if (activeVillager == null) return;
        Villager v = activeVillager;
        int panelW = 600;
        int panelH = 100 + v.trades.length * 62;
        int panelX = (SCREEN_W - panelW) / 2, panelY = (SCREEN_H - panelH) / 2;
        int rowY = panelY + 55;
        for (int i = 0; i < v.trades.length; i++) {
            int rowX = panelX + 20, rowW = panelW - 40, rowH = 54;
            if (mx > rowX && mx < rowX + rowW && my > rowY && my < rowY + rowH - 8) { performTrade(v, i); return; }
            rowY += rowH;
        }
    }

    private void performTrade(Villager v, int tradeIndex) {
        int[] t = v.trades[tradeIndex];
        int giveId = t[0], giveCnt = t[1], getId = t[2], getCnt = t[3];
        int have = 0;
        for (ItemStack s : inventory) if (s != null && s.id == giveId) have += s.count;
        if (have < giveCnt) { addChatMessage("Недостаточно: " + World.blockName(giveId) + " (" + have + "/" + giveCnt + ")", new Color(255, 120, 120)); Sound.play(Sound.HURT); return; }
        Villager.removeItems(inventory, giveId, giveCnt);
        addToInventory(ItemStack.of(getId, getCnt));
        floaters.add(new FloatingText("+" + getCnt + " " + World.blockName(getId), player.x, player.y - 40, new Color(140, 255, 140)));
        addChatMessage("Обмен: " + giveCnt + "x " + World.blockName(giveId) + " -> " + getCnt + "x " + World.blockName(getId), new Color(120, 255, 120));
        Sound.play(Sound.TRADE);
        unlock("trade");
    }

    private boolean handleInventoryClickInContainer(int mx, int my, boolean isRight) {
        int totalW = INV_COLS * SLOT;
        int startX = (SCREEN_W - totalW) / 2;
        int startY = SCREEN_H - SLOT - 10;
        if (mx < startX || mx >= startX + totalW) return false;
        if (my < startY || my >= startY + SLOT) return false;
        int idx = (mx - startX) / SLOT;
        if (idx < 0 || idx >= INV_COLS) return false;
        handleSlotClick(inventory, idx, isRight);
        return true;
    }

    private boolean handleInventoryClick(int mx, int my, boolean isRight) {
        int totalW = INV_COLS * SLOT, totalH = INV_ROWS * SLOT;
        int panelW = totalW + 240, panelH = totalH + 120;
        int panelX = (SCREEN_W - panelW) / 2, panelY = (SCREEN_H - panelH) / 2;
        int startX = panelX + 20, startY = panelY + 50;
        int craftX = panelX + totalW + 40, craftY = startY;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) {
            int i = r * 3 + c;
            int sx = craftX + c * SLOT, sy = craftY + r * SLOT;
            if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) { handleSlotClick(craftGrid, i, isRight); return true; }
        }
        int arrowX = craftX + 3 * SLOT + 15;
        int resX = arrowX + 50, resY = craftY + SLOT;
        if (mx >= resX && mx < resX + SLOT && my >= resY && my < resY + SLOT) {
            if (craftResult != null && currentRecipe != null) {
                addToInventory(craftResult.copy());
                Crafting.consumeRecipe(currentRecipe, craftGrid);
                Sound.play(Sound.MINE);
                unlock("craft");
                return true;
            }
        }
        for (int r = 0; r < INV_ROWS; r++) for (int c = 0; c < INV_COLS; c++) {
            int i = r * INV_COLS + c;
            int sx = startX + c * SLOT, sy = startY + r * SLOT;
            if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) { handleSlotClick(inventory, i, isRight); return true; }
        }
        return false;
    }

    private void handleSlotClick(ItemStack[] container, int i, boolean isRight) {
        if (container[i] == null) {
            if (draggedStack != null) {
                if (isRight) { container[i] = ItemStack.of(draggedStack.id, 1); draggedStack.count--; if (draggedStack.count <= 0) { draggedStack = null; draggedFromSlot = -1; } }
                else { container[i] = draggedStack; draggedStack = null; draggedFromSlot = -1; }
            }
            return;
        }
        if (draggedStack == null) {
            if (isRight) {
                int half = (container[i].count + 1) / 2;
                draggedStack = ItemStack.of(container[i].id, half);
                container[i].count -= half;
                draggedFromSlot = i;
                if (container[i].count <= 0) container[i] = null;
            } else { draggedStack = container[i]; draggedFromSlot = i; container[i] = null; }
            return;
        }
        if (container[i].id == draggedStack.id) {
            int maxStack = 999;
            int space = maxStack - container[i].count;
            if (space > 0) {
                int move = Math.min(space, draggedStack.count);
                container[i].count += move;
                draggedStack.count -= move;
                if (draggedStack.count <= 0) { draggedStack = null; draggedFromSlot = -1; }
            }
            return;
        }
        ItemStack tmp = container[i];
        container[i] = draggedStack;
        draggedStack = tmp;
        draggedFromSlot = i;
    }

    private boolean handleContainerClick(int mx, int my, boolean isRight) {
        if (containerSlots == null || activeContainer == null) return false;
        int cols = activeContainer.type == World.CHEST ? 9 : 3;
        int rows = 3;
        int totalW = cols * SLOT, totalH = rows * SLOT;
        int startX = (SCREEN_W - totalW) / 2;
        int startY = (SCREEN_H - totalH) / 2 - 200;
        if (mx < startX || my < startY || mx >= startX + totalW || my >= startY + totalH) {
            if (draggedStack != null) { returnDragged(); return true; }
            return false;
        }
        int c = (mx - startX) / SLOT;
        int r = (my - startY) / SLOT;
        int i = r * cols + c;
        if (i < 0 || i >= containerSlots.length) return false;
        if (activeContainer.type == World.FURNACE && i == 8) {
            ItemStack res = containerSlots[8];
            if (res != null) {
                if (draggedStack == null) { draggedStack = res; containerSlots[8] = null; draggedFromSlot = -1; }
                else if (draggedStack.id == res.id) { draggedStack.count += res.count; containerSlots[8] = null; }
            }
            return true;
        }
        handleSlotClick(containerSlots, i, isRight);
        return true;
    }

    private void returnDragged() { if (draggedStack != null) { addToInventory(draggedStack); draggedStack = null; } draggedFromSlot = -1; }
    private void returnCraftGrid() { for (int i = 0; i < craftGrid.length; i++) if (craftGrid[i] != null) { addToInventory(craftGrid[i]); craftGrid[i] = null; } }

    private void openContainer(int wx, int wy, int type) {
        for (WorkBlock wb : world.workBlocks) if (wb.x == wx && wb.y == wy) { activeContainer = wb; containerSlots = wb.slots; return; }
        int size = type == World.CHEST ? 27 : 9;
        WorkBlock wb = new WorkBlock(wx, wy, type, size);
        world.workBlocks.add(wb);
        activeContainer = wb;
        containerSlots = wb.slots;
    }

    private boolean isBgBlock(int id) { return id == World.BG_STONE || id == World.BG_DIRT || id == World.BG_WOOD || id == World.BG_PLANK; }
    private void executeChatCommand(String text) {
        addChatMessage("<" + playerName + "> " + text, Color.WHITE);
        if (!text.startsWith("/")) { if (mp != null) mp.sendChat("<" + playerName + "> " + text); return; }
        String[] parts = text.substring(1).split("\\s+");
        String cmd = parts[0].toLowerCase();
        try {
            switch (cmd) {
                case "help":
                    addChatMessage("/give <блок> [N] /heal /healall /god /godmode /fly /tp x y", Color.YELLOW);
                    addChatMessage("/time day|night /weather clear|rain /setspawn /spawnpoint", Color.YELLOW);
                    addChatMessage("/spawn zombie|slime|skeleton [N] /summon slime|eye|skeleton|hell", Color.YELLOW);
                    addChatMessage("/killall /clear /clearinv /giveall /clean /fill <блок> [R]", Color.YELLOW);
                    addChatMessage("/seed /coords /where /biome <имя> /save /xp N /unlock all", Color.YELLOW);
                    addChatMessage("/speed N /jump N /nightvision /bossreset /gamemode survival|creative", Color.YELLOW);
                    break;
                case "give": {
                    if (parts.length < 2) { addChatMessage("Использование: /give <блок> [N]", Color.RED); break; }
                    int id = parseBlockIdPublic(parts[1]);
                    int cnt = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;
                    if (id < 0) { addChatMessage("Неизвестный блок", Color.RED); break; }
                    if (id == World.WATER || id == World.LAVA) {
                        addChatMessage("Воду/лаву нельзя выдать как предмет — используй ведро", Color.RED);
                        break;
                    }
                    addToInventory(ItemStack.of(id, cnt));
                    addChatMessage("Выдано: " + cnt + "x " + World.blockName(id), Color.GREEN);
                    break;
                }
                case "heal": player.hp = player.maxHp; addChatMessage("HP восстановлено", Color.GREEN); break;
                case "god": player.godMode = !player.godMode; addChatMessage("Бессмертие: " + (player.godMode ? "ВКЛ" : "ВЫКЛ"), Color.GREEN); break;
                case "fly": player.flyMode = !player.flyMode; addChatMessage("Полёт: " + (player.flyMode ? "ВКЛ" : "ВЫКЛ"), Color.GREEN); break;
                case "tp": {
                    if (parts.length < 3) { addChatMessage("/tp <x> <y>", Color.RED); break; }
                    player.x = Double.parseDouble(parts[1]) * TILE;
                    player.y = Double.parseDouble(parts[2]) * TILE;
                    player.vx = 0; player.vy = 0;
                    player.wasInAir = false; 
                    player.fallStartY = player.y;  
                    addChatMessage("Телепорт", Color.GREEN);
                    break;
                }
                case "time": {
                    if (parts.length < 2) { addChatMessage("/time day|night", Color.RED); break; }
                    if (parts[1].equals("day")) gameTime = 0;
                    else if (parts[1].equals("night")) gameTime = DAY_LENGTH / 2;
                    addChatMessage("Время установлено", Color.GREEN);
                    break;
                }
                case "seed": addChatMessage("Сид: " + world.seed, Color.CYAN); break;
                case "coords": addChatMessage("X=" + (int) (player.x / TILE) + " Y=" + (int) (player.y / TILE), Color.CYAN); break;
                case "spawn": {
                    if (parts.length < 2) { addChatMessage("/spawn zombie|slime|skeleton [N]", Color.RED); break; }
                    int type = parts[1].equals("zombie") ? 0 : parts[1].equals("slime") ? 1 : 2;
                    int cnt = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;
                    for (int i = 0; i < cnt; i++) monsters.add(new Monster(player.x + (Math.random() * 10 - 5) * TILE, player.y - 2 * TILE, type));
                    addChatMessage("Спавн: " + cnt, Color.GREEN);
                    break;
                }
                case "killall": monsters.clear(); addChatMessage("Все монстры удалены", Color.GREEN); break;
                case "clear": Arrays.fill(inventory, null); addChatMessage("Инвентарь очищен", Color.GREEN); break;
                case "save": saveCurrentWorld(); addChatMessage("Мир сохранён", Color.GREEN); break;
                case "xp": { int n = parts.length >= 2 ? Integer.parseInt(parts[1]) : 100; addXp(n); addChatMessage("+" + n + " опыта", Color.GREEN); break; }
                case "unlock": if (parts.length >= 2 && parts[1].equals("all")) { for (Achievement a : achievements) a.unlocked = true; addChatMessage("Все достижения открыты", Color.GREEN); } break;
                case "gamemode": {
                    if (parts.length < 2) { addChatMessage("/gamemode survival|creative", Color.RED); break; }
                    if (parts[1].equalsIgnoreCase("creative") || parts[1].equals("1")) {
                        mode = GameMode.CREATIVE;
                        addChatMessage("Режим: КРЕАТИВ", Color.GREEN);
                    } else {
                        mode = GameMode.SURVIVAL;
                        addChatMessage("Режим: ВЫЖИВАНИЕ", Color.GREEN);
                    }
                    break;
                }
                case "weather": {
                    // заглушка — у нас нет погоды, но команда есть
                    if (parts.length < 2) { addChatMessage("/weather clear|rain", Color.RED); break; }
                    addChatMessage("Погода: " + parts[1] + " (пока без визуала)", Color.CYAN);
                    break;
                }
                case "setspawn": {
                    spawnX = (int) (player.x / TILE);
                    spawnY = (int) (player.y / TILE);
                    addChatMessage("Точка спавна: " + spawnX + ", " + spawnY, Color.GREEN);
                    break;
                }
                case "spawnpoint": {
                    // телепорт на точку спавна
                    int sx = (spawnX >= 0) ? spawnX : world.getWidth() / 2;
                    int sy = (spawnY >= 0) ? spawnY : world.findSurface(sx);
                    player.x = sx * TILE; player.y = (sy - 3) * TILE;
                    player.vx = 0; player.vy = 0;
                    addChatMessage("Телепорт на спавн", Color.GREEN);
                    break;
                }
                case "speed": {
                    if (parts.length < 2) { addChatMessage("/speed <множитель>", Color.RED); break; }
                    try {
                        double mult = Double.parseDouble(parts[1]);
                        if (mult <= 0 || mult > 10) { addChatMessage("Множитель 0.1–10", Color.RED); break; }
                        player.speedMultiplier = mult;
                        addChatMessage("Скорость: x" + mult, Color.GREEN);
                    } catch (Exception ex) { addChatMessage("Число нужно", Color.RED); }
                    break;
                }
                case "jump": {
                    if (parts.length < 2) { addChatMessage("/jump <высота>", Color.RED); break; }
                    try {
                        player.jumpMultiplier = Math.max(0.5, Math.min(5.0, Double.parseDouble(parts[1])));
                        addChatMessage("Прыжок: x" + player.jumpMultiplier, Color.GREEN);
                    } catch (Exception ex) { addChatMessage("Число нужно", Color.RED); }
                    break;
                }
                case "healall": {
                    player.hp = player.maxHp;
                    player.hunger = player.maxHunger;
                    player.satiety = player.satietyMax;
                    player.hurtCooldown = 0;
                    addChatMessage("Полное восстановление", Color.GREEN);
                    break;
                }
                case "clearinv": {
                    Arrays.fill(inventory, null);
                    addChatMessage("Инвентарь очищен", Color.GREEN);
                    break;
                }
                case "giveall": {
                    // выдать по 1 предмету всех блоков
                    for (int id = 1; id <= 90; id++) {
                        try { addToInventory(ItemStack.of(id, 1)); } catch (Exception ignored) {}
                    }
                    addChatMessage("Выдано по 1 каждого предмета", Color.GREEN);
                    break;
                }
                case "summon": {
                    if (parts.length < 2) {
                        addChatMessage("/summon <slime|eye|skeleton|hell>", Color.RED);
                        break;
                    }
                    int type;
                    switch (parts[1].toLowerCase()) {
                        case "slime": type = BOSS_SLIME; break;
                        case "eye": type = BOSS_EYE; break;
                        case "skeleton": type = BOSS_SKELETON; break;
                        case "hell": type = BOSS_HELL; break;
                        default: addChatMessage("Неизвестный босс", Color.RED); return;
                    }
                    spawnBoss(type);
                    addChatMessage("Босс призван!", new Color(255, 100, 100));
                    break;
                }
                case "bossreset": {
                    defeatedBosses.clear();
                    activeBoss = null;
                    addChatMessage("Все боссы снова доступны", Color.GREEN);
                    break;
                }
                case "nightvision": {
                    player.nightVisionTimer = 60 * 60 * 10;
                    addChatMessage("Ночное зрение 10 минут", Color.CYAN);
                    break;
                }
                case "godmode": {
                    player.godMode = !player.godMode;
                    addChatMessage("Бессмертие: " + (player.godMode ? "ВКЛ" : "ВЫКЛ"), Color.GREEN);
                    break;
                }
                case "where": {
                    addChatMessage("X=" + (int)(player.x / TILE) + " Y=" + (int)(player.y / TILE)
                            + " | Биом: " + biomeName(world.biomeAt((int)(player.x / TILE))), Color.CYAN);
                    break;
                }
                case "biome": {
                    if (parts.length < 2) { addChatMessage("/biome <forest|desert|snow|jungle|corruption|hell>", Color.RED); break; }
                    int b = parseBiome(parts[1]);
                    if (b < 0) { addChatMessage("Неизвестный биом", Color.RED); break; }
                    teleportToBiome(b);
                    break;
                }
                case "clean": {
                    // убрать все жидкости в радиусе 50 блоков
                    int px = (int)(player.x / TILE), py = (int)(player.y / TILE);
                    int removed = 0;
                    for (int x = px - 50; x <= px + 50; x++)
                        for (int y = py - 50; y <= py + 50; y++)
                            if (world.inBounds(x, y) && world.getLiquid(x, y) > 0) {
                                world.setLiquid(x, y, 0);
                                removed++;
                            }
                    addChatMessage("Удалено жидкости: " + removed, Color.GREEN);
                    break;
                }
                case "fill": {
                    // /fill <block> <radius>
                    if (parts.length < 2) { addChatMessage("/fill <block> [radius]", Color.RED); break; }
                    int id = parseBlockIdPublic(parts[1]);
                    if (id < 0) { addChatMessage("Неизвестный блок", Color.RED); break; }
                    int r = parts.length >= 3 ? Integer.parseInt(parts[2]) : 3;
                    r = Math.max(1, Math.min(20, r));
                    int px = (int)(player.x / TILE), py = (int)(player.y / TILE);
                    for (int x = px - r; x <= px + r; x++)
                        for (int y = py - r; y <= py + r; y++)
                            if (world.inBounds(x, y)) world.setBlock(x, y, id);
                    addChatMessage("Заполнено: " + id + " радиусом " + r, Color.GREEN);
                    break;
                }
                default:
                    if (!ModManager.dispatchCommand(text.substring(1).split("\\s+"))) {
                        addChatMessage("Неизвестная команда: /" + cmd, Color.RED);
                    }
            }
        } catch (Exception ex) { addChatMessage("Ошибка: " + ex.getMessage(), Color.RED); }
    }

    public static int parseBlockIdPublic(String name) {
        switch (name.toLowerCase()) {
            case "water": return World.WATER;
            case "lava": return World.LAVA;
            case "bread": return World.BREAD;
            case "apple": return World.APPLE;
            case "meat_raw": return World.MEAT_RAW;
            case "meat_cooked": return World.MEAT_COOKED;
            case "mushroom": return World.MUSHROOM;
            case "mushroom_soup": return World.MUSHROOM_SOUP;
            case "golden_apple": return World.GOLDEN_APPLE;
            case "carrot": return World.CARROT;
            case "wheat": return World.WHEAT_ITEM;
            case "potion_heal": return World.POTION_HEAL;
            case "potion_strength": return World.POTION_STRENGTH;
            case "potion_speed": return World.POTION_SPEED;
            case "potion_night": return World.POTION_NIGHT;
            case "potion_regen": return World.POTION_REGEN;
            case "potion_fire": return World.POTION_FIRE;
            case "flask": return World.FLASK;
            case "brewing_stand": return World.BREWING_STAND;
            case "hellstone": return World.HELLSTONE;
            case "obsidian": return World.OBSIDIAN;
            case "demonite": return World.DEMONITE;
            case "wool": return World.WOOL_ITEM;
            case "dirt": return World.DIRT;
            case "stone": return World.STONE;
            case "grass": return World.GRASS;
            case "sand": return World.SAND;
            case "wood": return World.WOOD;
            case "coal": return World.COAL;
            case "iron": return World.IRON;
            case "gold": return World.GOLD;
            case "diamond": return World.DIAMOND;
            case "iron_ingot": return World.IRON_INGOT;
            case "gold_ingot": return World.GOLD_INGOT;
            case "diamond_gem": return World.DIAMOND_GEM;
            case "coin": return World.COIN_GOLD;
            case "plank": return World.PLANK;
            case "torch": return World.TORCH;
            case "glass": return World.GLASS;
            case "chest": return World.CHEST;
            case "furnace": return World.FURNACE;
            case "workbench": return World.WORKBENCH;
            case "door": return World.DOOR_CLOSED;
            case "ladder": return World.LADDER;
            case "bookshelf": return World.BOOKSHELF;
            case "bed": return World.BED;
            case "sign": return World.SIGN;
            case "bucket": return World.BUCKET_EMPTY;
            default: return -1;
        }
    }

    private void startMultiplayerHost() {
        try {
            isMultiplayer = true;
            if (world == null || world.getWidth() <= 10) {
                long seed = new Random().nextLong();
                currentWorldName = "mp_host_" + System.currentTimeMillis();
                world = new World(WORLD_W, WORLD_H, seed);
                world.name = currentWorldName;
                world.seed = seed;
            }
            int sx = world.getWidth() / 2;
            int sy = world.findSurface(sx);
            player = new Player(sx * TILE, (sy - 3) * TILE);
            player.wasInAir = false;
            player.fallStartY = player.y;
            monsters.clear(); villagers.clear(); floaters.clear(); chatLog.clear(); remotePlayers.clear(); peacefulMobs.clear();
            Arrays.fill(inventory, null); Arrays.fill(craftGrid, null);
            Random rnd = new Random();
            for (int i = 0; i < world.villageX.size(); i++) villagers.add(new Villager(world.villageX.get(i) * TILE, world.villageY.get(i) * TILE, rnd.nextInt(12)));
            mp = new MultiplayerManager(this);
            mp.startHost(25565);
            mpHost = true;
            selectedSlot = 0; inventoryOpen = false; activeContainer = null; containerSlots = null; draggedStack = null;
            gameTime = 0; state = GameState.PLAYING; xp = 0; xpLevel = 0; xpToNext = 20;
            addChatMessage("Сервер запущен на порту 25565", new Color(120, 255, 120));
            addChatMessage("Другие игроки могут подключиться к вашему IP", new Color(255, 220, 100));
            unlock("multiplayer");
            requestFocusInWindow();
        } catch (Exception ex) { addChatMessage("Ошибка запуска сервера: " + ex.getMessage(), Color.RED); state = GameState.MULTIPLAYER; }
    }

    private void startMultiplayerClient() {
        System.out.println("[CLIENT] Клик по кнопке Подключиться"); 
        System.out.println("[CLIENT] Адрес: " + mpAddress);  
        try {
            isMultiplayer = true;
            mp = new MultiplayerManager(this);
            mpHost = false;
            mp.startClient(mpAddress, 25565);
            addChatMessage("Подключение к " + mpAddress + "...", Color.YELLOW);
            addChatMessage("Ожидаем мир от хоста...", Color.CYAN);
            unlock("multiplayer");
            world = null;
            player = null;
            state = GameState.LOADING;
            requestFocusInWindow();
        } catch (Exception ex) { addChatMessage("Ошибка подключения: " + ex.getMessage(), Color.RED); state = GameState.MULTIPLAYER; }
    }

    private void toggleFullscreen() {
        try {
            if (!fullscreen) {
                windowedBounds = frame.getBounds();
                frame.dispose();
                frame.setUndecorated(true);
                frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
                frame.setVisible(true);
                GraphicsConfiguration gc = frame.getGraphicsConfiguration();
                Rectangle screenBounds = gc.getBounds();
                frame.setBounds(screenBounds);
                fullscreen = true;
            } else {
                frame.dispose();
                frame.setUndecorated(false);
                frame.setExtendedState(JFrame.NORMAL);
                if (windowedBounds != null) frame.setBounds(windowedBounds);
                frame.setVisible(true);
                fullscreen = false;
            }
            revalidate();
            repaint();
            SwingUtilities.invokeLater(() -> { frame.toFront(); requestFocusInWindow(); });
        } catch (Exception ex) {
            ex.printStackTrace();
            try { frame.dispose(); frame.setUndecorated(false); if (windowedBounds != null) frame.setBounds(windowedBounds); frame.setVisible(true); } catch (Exception ignored) {}
            fullscreen = false;
        }
    }

    private void ensureSavesDir() { new File("saves").mkdirs(); }

    private void refreshWorldSaves() {
        worldSaves.clear();
        File saves = new File("saves");
        saves.mkdirs();
        for (File dir : WorldStorage.listWorlds(saves)) {
            WorldSave ws = new WorldSave();
            ws.folder = dir;
            ws.name = dir.getName();
            File region = new File(dir, WorldStorage.DATA_FILE);
            ws.size = region.length();
            ws.dateStr = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm")
                    .format(new Date(region.lastModified()));
            try {
                World w = WorldStorage.loadWorldFolder(dir);
                if (w != null) { ws.sizeW = w.getWidth(); ws.sizeH = w.getHeight(); }
            } catch (Exception ignored) { ws.sizeW = ws.sizeH = 0; }
            worldSaves.add(ws);
        }
    }

    private void refreshDataPacks() {
        File dir = new File("datapacks");
        dir.mkdirs();
        File defaultPack = new File("datapacks/default.json");
        if (!defaultPack.exists()) DataPack.saveDefault(defaultPack);
    }

    private DataPack getDataPackForWorld(String worldName) {
        File dir = new File(currentWorldDir != null ? currentWorldDir : new File("saves", worldName), "datapacks");
        File[] zips = dir.listFiles((d, n) -> n.endsWith(".zip"));
        if (zips != null && zips.length > 0) {
            try { return DataPackZip.load(zips[0]); } catch (Exception ignored) {}
        }
        return DataPack.load(new File("datapacks/default.json"));
    }

    private void createWorldWithSeed() {
        long seed = inputSeed.isEmpty() ? new Random().nextLong() : inputSeed.hashCode();
        String name = inputSeed.isEmpty()
                ? "world_" + System.currentTimeMillis()
                : "world_" + inputSeed;
        try {
            currentWorldDir = WorldStorage.createWorldFolder(new File("saves"), name);
            currentWorldName = name;
            DataPack dp = getDataPackForWorld(name);
            world = new World(WORLD_W, WORLD_H, seed, dp);
            world.name = name;
            world.seed = seed;
            world.dataPackName = dp.name;
            isMultiplayer = false;
            startPlaying();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void deleteWorld() {
        if (worldSaves.isEmpty()) return;
        WorldStorage.deleteRecursive(worldSaves.get(selectedWorldIndex).folder);
        refreshWorldSaves();
        if (selectedWorldIndex >= worldSaves.size())
            selectedWorldIndex = Math.max(0, worldSaves.size() - 1);
    }

    private void loadSelectedWorld() {
        if (worldSaves.isEmpty()) return;
        WorldSave ws = worldSaves.get(selectedWorldIndex);
        currentWorldDir = ws.folder;
        currentWorldName = ws.name;
        try {
            this.world = WorldStorage.loadWorldFolder(ws.folder);
            if (this.world == null) return;
            isMultiplayer = false;
            startPlaying();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void startPlaying() {
        int sx = world.getWidth() / 2;
        int sy = world.findSurface(sx);
        player = new Player(sx * TILE, (sy - 3) * TILE);
        // Загружаем прогресс
        if (!isMultiplayer) loadPlayerProgress();
        player.wasInAir = false;
        player.fallStartY = player.y;
        monsters.clear(); villagers.clear(); floaters.clear(); chatLog.clear(); remotePlayers.clear(); peacefulMobs.clear();
        Arrays.fill(inventory, null); Arrays.fill(craftGrid, null);
        if (mode == GameMode.CREATIVE) {
            addToInventory(ItemStack.of(World.WORKBENCH, 1));
            addToInventory(ItemStack.of(World.FURNACE, 1));
            addToInventory(ItemStack.of(World.CHEST, 1));
            addToInventory(ItemStack.of(World.COIN_GOLD, 999));
            addToInventory(ItemStack.of(World.DIRT, 999));
            addToInventory(ItemStack.of(World.STONE, 999));
            addToInventory(ItemStack.of(World.BG_STONE, 999));
        }
        Random rnd = new Random();
        for (int i = 0; i < world.villageX.size(); i++) {
            // Первый житель в деревне — всегда фермер (type 0)
            villagers.add(new Villager(world.villageX.get(i) * TILE, world.villageY.get(i) * TILE, 0));
            // Остальные — случайные
            for (int k = 0; k < 3; k++) {
                villagers.add(new Villager(world.villageX.get(i) * TILE, world.villageY.get(i) * TILE, rnd.nextInt(12)));
            }
        }
        if (!villagers.isEmpty()) unlock("village");
        for (int i = 0; i < 30; i++) {
            int mx = 50 + rnd.nextInt(world.getWidth() - 100);
            int my = world.findSurface(mx) - 2;
            if (world.getBlock(mx, my) == World.AIR && world.getBlock(mx, my - 1) == World.AIR) peacefulMobs.add(new PeacefulMob(mx * TILE, my * TILE, rnd.nextInt(4)));
        }
        if (world.dataPackName != null) {
            File dpFile = new File("datapacks/" + world.dataPackName + ".json");
            if (dpFile.exists()) world.dataPack = DataPack.load(dpFile);
        }
        selectedSlot = 0; inventoryOpen = false; activeContainer = null; containerSlots = null; draggedStack = null;
        gameTime = 0; state = GameState.PLAYING; xp = 0; xpLevel = 0; xpToNext = 20;
        addChatMessage("Добро пожаловать в TerrariaMini beta 1.2.2!", Color.YELLOW);
        addChatMessage("T — чат, E — инвентарь+крафт, ПКМ по жителю — торговля, F11 — fullscreen", Color.CYAN);
        world.ensureRuntimeState();
        if (world.liquid == null) world.liquid = new byte[world.getWidth()][world.getHeight()];
        if (world.surfaceMap == null) {
            world.surfaceMap = new double[world.getWidth()];
            for (int x = 0; x < world.getWidth(); x++) world.surfaceMap[x] = world.findSurface(x);
        }
        requestFocusInWindow();
    }

    private void respawn() {
        int sx = world.getWidth() / 2;
        int sy = world.findSurface(sx);
        player.x = sx * TILE; player.y = (sy - 3) * TILE; player.vx = 0; player.vy = 0;
        player.hp = player.maxHp;
        player.wasInAir = false;
        player.fallStartY = player.y;  
        state = GameState.PLAYING;
    }

    private void backToMenu() {
        if (world != null && world.getWidth() > 10) saveCurrentWorld(); savePlayerProgress();
        if (mp != null) { mp.close(); mp = null; }
        state = GameState.MENU;
        refreshWorldSaves();
        savePlayerProgress();
    }

    private void saveCurrentWorld() {
        if (world == null || world.getWidth() <= 10 || currentWorldDir == null) return;
        try {
            WorldStorage.saveWorldFolder(currentWorldDir, world);
            savePlayerProgress();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public World getWorld() { return world; }
    public Player getPlayer() { return player; }
    public List<RemotePlayer> getRemotePlayers() { return remotePlayers; }
    public void onRemoteChat(String text) {
        Object[] r = parseMcColor(text, new Color(100, 240, 255));
        addChatMessage((String) r[0], (Color) r[1]);
    }
    public void onRemoteBlockChange(int x, int y, int block) { if (world != null && world.getWidth() > 10) world.setBlock(x, y, block); }
    public void onRemoteBgChange(int x, int y, int block) { if (world != null && world.getWidth() > 10) world.setBackground(x, y, block); }
    public void onRemoteDamage(int dmg) { if (player != null && !player.godMode) { player.hp -= dmg; player.hurtCooldown = 30; Sound.play(Sound.HURT); } }

    public void onWorldReceived(World receivedWorld, double spawnX, double spawnY) {
        this.world = receivedWorld;
        this.world.ensureRuntimeState();
        int sx = (int) (spawnX / TILE) + 2;
        if (sx >= world.getWidth() - 2) sx = world.getWidth() / 2;
        int sy = world.findSurface(sx);
        player = new Player(sx * TILE, (sy - 3) * TILE);
        player.wasInAir = false;
        player.fallStartY = player.y;
        monsters.clear(); villagers.clear(); floaters.clear(); remotePlayers.clear(); peacefulMobs.clear();
        Arrays.fill(inventory, null); Arrays.fill(craftGrid, null);
        Random rnd = new Random();
        for (int i = 0; i < world.villageX.size(); i++) villagers.add(new Villager(world.villageX.get(i) * TILE, world.villageY.get(i) * TILE, rnd.nextInt(12)));
        for (int i = 0; i < 30; i++) {
            int mx = 50 + rnd.nextInt(world.getWidth() - 100);
            int my = world.findSurface(mx) - 2;
            if (world.getBlock(mx, my) == World.AIR) peacefulMobs.add(new PeacefulMob(mx * TILE, my * TILE, rnd.nextInt(4)));
        }
        selectedSlot = 0; inventoryOpen = false; activeContainer = null; containerSlots = null; draggedStack = null;
        gameTime = 0; state = GameState.PLAYING; xp = 0; xpLevel = 0; xpToNext = 20;
        addChatMessage("Мир получен от хоста!", new Color(120, 255, 120));
        addChatMessage("Ты играешь в общем мире с другими игроками.", new Color(255, 220, 100));
        requestFocusInWindow();
    }

    public void onConnectionFailed(String reason) {
        addChatMessage("Не удалось подключиться: " + reason, new Color(255, 100, 100));
        if (mp != null) { mp.close(); mp = null; }
        world = null; player = null;
        state = GameState.MULTIPLAYER;
    }

    static class Modules {
        public static final boolean SHOW_BACKGROUND = false;
        public static final boolean AUTO_GEN_BG = false;
        public static final boolean EXTENDED_CRAFTING = true;
        public static final boolean HUNGER_SYSTEM = true;
        public static final boolean TEMPERATURE = true;
        public static final boolean WATER_PHYSICS = true;
        public static final boolean DAY_NIGHT_MOBS = true;
        public static final boolean FALL_DAMAGE = true;
        public static final boolean TOOLS_WEAR = true;
        public static final boolean DURABILITY_SHOWN = true;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("TerrariaMini* beta 1.2.2");
            TerrariaMini game = new TerrariaMini(frame);
            frame.add(game);
            frame.pack();
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setResizable(true);
            frame.setVisible(true);
            game.requestFocusInWindow();
        });
    }
    // ==================== PIXEL FONT ====================
    static class PixelFont {
        private static final Map<String, Font> cache = new HashMap<>();
        public static Font font(int size, int style) {
            String key = size + "_" + style;
            Font f = cache.get(key);
            if (f != null) return f;
            String[] preferred = {"Press Start 2P", "Pixel", "Minecraft", "Fixedsys", "Terminal", "Courier New", "Monospaced"};
            String[] avail = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            Set<String> availSet = new HashSet<>(Arrays.asList(avail));
            String chosen = "Monospaced";
            for (String p : preferred) if (availSet.contains(p)) { chosen = p; break; }
            f = new Font(chosen, style, size);
            cache.put(key, f);
            return f;
        }
    }

    // ==================== TOOL UTIL ====================
    static class ToolUtil {
        static boolean isPickaxe(int id) { return id == World.PICK_WOOD || id == World.PICK_STONE || id == World.PICK_IRON || id == World.PICK_DIAMOND; }
        static boolean isAxe(int id) { return id == World.AXE_WOOD || id == World.AXE_STONE || id == World.AXE_IRON || id == World.AXE_DIAMOND; }
        static boolean isSword(int id) { return id == World.SWORD_WOOD || id == World.SWORD_STONE || id == World.SWORD_IRON || id == World.SWORD_DIAMOND; }
        static boolean isTool(int id) { return isPickaxe(id) || isAxe(id) || isSword(id); }

        static int getToolPower(ItemStack s) {
            if (s == null) return 1;
            switch (s.id) {
                case World.PICK_WOOD: return 2;
                case World.PICK_STONE: return 3;
                case World.PICK_IRON: return 5;
                case World.PICK_DIAMOND: return 8;
                case World.AXE_WOOD: return 2;
                case World.AXE_STONE: return 3;
                case World.AXE_IRON: return 5;
                case World.AXE_DIAMOND: return 8;
                default: return 1;
            }
        }

        static int getToolBonus(ItemStack s, int blockId) {
            if (s == null) return 0;
            if (isPickaxe(s.id)) if (blockId == World.STONE || blockId == World.COAL || blockId == World.IRON || blockId == World.GOLD || blockId == World.DIAMOND) return 4;
            if (isAxe(s.id)) if (blockId == World.WOOD || blockId == World.LEAVES || blockId == World.PLANK) return 4;
            return 0;
        }

        static boolean isWrongTool(ItemStack s, int blockId) {
            if (s == null) return false;
            if (isPickaxe(s.id)) return blockId == World.WOOD || blockId == World.LEAVES || blockId == World.PLANK;
            if (isAxe(s.id)) return blockId == World.STONE || blockId == World.COAL || blockId == World.IRON || blockId == World.GOLD || blockId == World.DIAMOND;
            return false;
        }

        static int getBlockHardness(int blockId) {
            switch (blockId) {
                case World.DIRT: case World.GRASS: case World.SAND: case World.SNOW: case World.LEAVES: return 1;
                case World.WOOD: case World.PLANK: case World.ICE: return 2;
                case World.STONE: case World.CHEST: return 3;
                case World.COAL: case World.WORKBENCH: return 4;
                case World.IRON: case World.FURNACE: return 5;
                case World.GOLD: return 6;
                case World.DIAMOND: return 8;
                case World.HELLSTONE: return 6;
                case World.OBSIDIAN: return 10;
                case World.DEMONITE: return 9;
                case World.BG_STONE: case World.BG_DIRT: case World.BG_WOOD: case World.BG_PLANK: return 1;
                case World.DOOR_CLOSED: return 2;
                default: return 2;
            }
        }

        static int getSwordDamage(ItemStack s) {
            if (s == null) return 3;
            switch (s.id) {
                case World.SWORD_WOOD: return 5;
                case World.SWORD_STONE: return 8;
                case World.SWORD_IRON: return 12;
                case World.SWORD_DIAMOND: return 18;
                case World.PICK_WOOD: return 3;
                case World.PICK_STONE: return 4;
                case World.PICK_IRON: return 6;
                case World.PICK_DIAMOND: return 9;
                case World.AXE_WOOD: return 4;
                case World.AXE_STONE: return 6;
                case World.AXE_IRON: return 9;
                case World.AXE_DIAMOND: return 13;
                default: return 3;
            }
        }

        static String getTooltip(int id) {
            switch (id) {
                case World.PICK_WOOD: return "Сила копания: 2";
                case World.PICK_STONE: return "Сила копания: 3";
                case World.PICK_IRON: return "Сила копания: 5";
                case World.PICK_DIAMOND: return "Сила копания: 8";
                case World.SWORD_WOOD: return "Урон: 5";
                case World.SWORD_STONE: return "Урон: 8";
                case World.SWORD_IRON: return "Урон: 12";
                case World.SWORD_DIAMOND: return "Урон: 18";
                case World.AXE_WOOD: return "Рубит дерево · урон 4";
                case World.AXE_STONE: return "Рубит дерево · урон 6";
                case World.AXE_IRON: return "Рубит дерево · урон 9";
                case World.AXE_DIAMOND: return "Рубит дерево · урон 13";
                case World.IRON_INGOT: return "Переплавь железную руду";
                case World.GOLD_INGOT: return "Ценно!";
                case World.DIAMOND_GEM: return "Редкий материал";
                case World.COIN_GOLD: return "Валюта для торговли";
                case World.TORCH: return "Освещает пещеры";
                case World.WORKBENCH: return "ПКМ — крафт";
                case World.FURNACE: return "ПКМ — плавка руды";
                case World.CHEST: return "ПКМ — хранилище";
                case World.BREAD: return "ПКМ — съесть (+5 HP)";
                case World.APPLE: return "ПКМ — съесть (+3 HP)";
                case World.MEAT_COOKED: return "ПКМ — съесть (+10 HP)";
                case World.GOLDEN_APPLE: return "ПКМ — съесть (+50 HP)";
                case World.POTION_HEAL: return "ПКМ — +50 HP";
                case World.POTION_STRENGTH: return "ПКМ — сила на 10с";
                case World.POTION_SPEED: return "ПКМ — скорость на 10с";
                case World.POTION_NIGHT: return "ПКМ — ночное зрение";
                case World.POTION_FIRE: return "ПКМ — огнестойкость";
                case World.BUCKET_EMPTY: return "ПКМ по воде/лаве — набрать";
                case World.BUCKET_WATER: return "ПКМ — вылить воду";
                case World.BUCKET_LAVA: return "ПКМ — вылить лаву";
                default: return "";
            }
        }
    }

    // ==================== CRAFTING ====================
    static class Crafting {
        static class Recipe {
            int resultId, resultCount;
            int[] ingredientIds, ingredientCounts;
            boolean needsTable;
            String category;
            Recipe(int rId, int rCount, int[] ids, int[] counts, boolean table, String cat) {
                resultId = rId; resultCount = rCount;
                ingredientIds = ids; ingredientCounts = counts;
                needsTable = table; category = cat;
            }
        }

        static final List<Recipe> recipes = new ArrayList<>();

        static {
            recipes.add(new Recipe(World.PLANK, 4, new int[]{World.WOOD}, new int[]{1}, false, "blocks"));
            recipes.add(new Recipe(World.BG_DIRT, 4, new int[]{World.DIRT}, new int[]{1}, false, "blocks"));
            recipes.add(new Recipe(World.BG_STONE, 4, new int[]{World.STONE}, new int[]{1}, false, "blocks"));
            recipes.add(new Recipe(World.BG_WOOD, 4, new int[]{World.WOOD}, new int[]{1}, false, "blocks"));
            recipes.add(new Recipe(World.BG_PLANK, 4, new int[]{World.PLANK}, new int[]{1}, false, "blocks"));
            recipes.add(new Recipe(World.WORKBENCH, 1, new int[]{World.PLANK}, new int[]{4}, false, "tools"));
            recipes.add(new Recipe(World.TORCH, 4, new int[]{World.COAL_ITEM, World.WOOD}, new int[]{1, 1}, false, "decor"));
            recipes.add(new Recipe(World.CHEST, 1, new int[]{World.PLANK}, new int[]{8}, true, "decor"));
            recipes.add(new Recipe(World.FURNACE, 1, new int[]{World.STONE}, new int[]{8}, true, "decor"));
            recipes.add(new Recipe(World.DOOR_CLOSED, 1, new int[]{World.PLANK, World.WOOD}, new int[]{6, 2}, true, "decor"));
            recipes.add(new Recipe(World.SWORD_WOOD, 1, new int[]{World.PLANK, World.WOOD}, new int[]{2, 1}, true, "tools"));
            recipes.add(new Recipe(World.PICK_WOOD, 1, new int[]{World.PLANK, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.AXE_WOOD, 1, new int[]{World.PLANK, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.SWORD_STONE, 1, new int[]{World.STONE, World.WOOD}, new int[]{2, 1}, true, "tools"));
            recipes.add(new Recipe(World.PICK_STONE, 1, new int[]{World.STONE, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.AXE_STONE, 1, new int[]{World.STONE, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.SWORD_IRON, 1, new int[]{World.IRON_INGOT, World.WOOD}, new int[]{2, 1}, true, "tools"));
            recipes.add(new Recipe(World.PICK_IRON, 1, new int[]{World.IRON_INGOT, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.AXE_IRON, 1, new int[]{World.IRON_INGOT, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.SWORD_DIAMOND, 1, new int[]{World.DIAMOND_GEM, World.WOOD}, new int[]{2, 1}, true, "tools"));
            recipes.add(new Recipe(World.PICK_DIAMOND, 1, new int[]{World.DIAMOND_GEM, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.AXE_DIAMOND, 1, new int[]{World.DIAMOND_GEM, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.STONE, 4, new int[]{World.DIRT}, new int[]{8}, true, "blocks"));
            recipes.add(new Recipe(World.DIRT, 4, new int[]{World.GRASS}, new int[]{4}, false, "blocks"));
            recipes.add(new Recipe(World.SNOW, 4, new int[]{World.ICE}, new int[]{2}, false, "blocks"));
            recipes.add(new Recipe(World.ICE, 1, new int[]{World.SNOW, World.STONE}, new int[]{4, 1}, true, "blocks"));
            recipes.add(new Recipe(World.GLASS, 1, new int[]{World.SAND}, new int[]{3}, true, "blocks"));
            recipes.add(new Recipe(World.BOOKSHELF, 1, new int[]{World.PLANK, World.WOOD}, new int[]{6, 3}, true, "decor"));
            recipes.add(new Recipe(World.LANTERN, 1, new int[]{World.TORCH, World.IRON_INGOT}, new int[]{4, 1}, true, "decor"));
            recipes.add(new Recipe(World.LADDER, 3, new int[]{World.PLANK, World.WOOD}, new int[]{3, 1}, true, "decor"));
            recipes.add(new Recipe(World.BED, 1, new int[]{World.PLANK, World.LEAVES}, new int[]{3, 3}, true, "decor"));
            recipes.add(new Recipe(World.SIGN, 1, new int[]{World.PLANK, World.WOOD}, new int[]{6, 2}, true, "decor"));
            recipes.add(new Recipe(World.BUCKET_EMPTY, 1, new int[]{World.IRON_INGOT}, new int[]{3}, true, "tools"));
            recipes.add(new Recipe(World.BREWING_STAND, 1, new int[]{World.WOOD, World.STONE}, new int[]{6, 4}, true, "decor"));
            recipes.add(new Recipe(World.FLASK, 4, new int[]{World.GLASS}, new int[]{1}, true, "tools"));
            recipes.add(new Recipe(World.POTION_HEAL, 1, new int[]{World.FLASK, World.MUSHROOM}, new int[]{1, 3}, true, "potion"));
            recipes.add(new Recipe(World.POTION_SPEED, 1, new int[]{World.FLASK, World.CARROT, World.GRASS}, new int[]{1, 2, 1}, true, "potion"));
            recipes.add(new Recipe(World.POTION_STRENGTH, 1, new int[]{World.FLASK, World.MEAT_RAW, World.COAL_ITEM}, new int[]{1, 2, 1}, true, "potion"));
            recipes.add(new Recipe(World.POTION_REGEN, 1, new int[]{World.FLASK, World.APPLE, World.MUSHROOM}, new int[]{1, 1, 2}, true, "potion"));
            recipes.add(new Recipe(World.POTION_NIGHT, 1, new int[]{World.FLASK, World.COAL_ITEM, World.TORCH}, new int[]{1, 1, 2}, true, "potion"));
            recipes.add(new Recipe(World.POTION_FIRE, 1, new int[]{World.FLASK, World.OBSIDIAN, World.LAVA}, new int[]{1, 1, 1}, true, "potion"));
            recipes.add(new Recipe(World.BREAD, 1, new int[]{World.WHEAT_ITEM}, new int[]{3}, false, "food"));
            recipes.add(new Recipe(World.MEAT_COOKED, 1, new int[]{World.MEAT_RAW, World.COAL_ITEM}, new int[]{1, 1}, true, "food"));
            recipes.add(new Recipe(World.MUSHROOM_SOUP, 1, new int[]{World.MUSHROOM, World.BREAD}, new int[]{3, 1}, true, "food"));
            recipes.add(new Recipe(World.GOLDEN_APPLE, 1, new int[]{World.APPLE, World.GOLD_INGOT}, new int[]{1, 3}, true, "food"));
        }

        static Recipe findRecipe(ItemStack[] grid, boolean hasTable) {
            for (Recipe r : recipes) {
                if (r.needsTable && !hasTable) continue;
                if (matchesRecipe(r, grid)) return r;
            }
            return null;
        }

        private static boolean matchesModRecipe(ModLoader.ModRecipe r, ItemStack[] grid) {
            Map<Integer, Integer> avail = new HashMap<>();
            int total = 0;
            for (ItemStack s : grid) { if (s == null) continue; avail.merge(s.id, s.count, Integer::sum); total += s.count; }
            int need = 0;
            for (int i = 0; i < r.ingredientIds.length; i++) need += r.ingredientCounts[i];
            if (total != need) return false;
            for (int i = 0; i < r.ingredientIds.length; i++) {
                Integer cnt = avail.get(r.ingredientIds[i]);
                if (cnt == null || cnt != r.ingredientCounts[i]) return false;
            }
            return true;
        }

        static List<Recipe> byCategory(String cat) {
            List<Recipe> list = new ArrayList<>();
            for (Recipe r : recipes) if (r.category.equals(cat)) list.add(r);
            return list;
        }

        private static boolean matchesRecipe(Recipe r, ItemStack[] grid) {
            Map<Integer, Integer> avail = new HashMap<>();
            int totalItems = 0;
            for (ItemStack s : grid) { if (s == null) continue; avail.merge(s.id, s.count, Integer::sum); totalItems += s.count; }
            int needTotal = 0;
            for (int i = 0; i < r.ingredientIds.length; i++) needTotal += r.ingredientCounts[i];
            if (totalItems != needTotal) return false;
            for (int i = 0; i < r.ingredientIds.length; i++) {
                Integer cnt = avail.get(r.ingredientIds[i]);
                if (cnt == null || cnt != r.ingredientCounts[i]) return false;
            }
            return true;
        }

        static void consumeRecipe(Recipe r, ItemStack[] grid) {
            for (int i = 0; i < r.ingredientIds.length; i++) {
                int need = r.ingredientCounts[i];
                for (int j = 0; j < grid.length && need > 0; j++) {
                    if (grid[j] != null && grid[j].id == r.ingredientIds[i]) {
                        int take = Math.min(need, grid[j].count);
                        grid[j].count -= take;
                        need -= take;
                        if (grid[j].count <= 0) grid[j] = null;
                    }
                }
            }
        }
    }

    // ==================== WORLD ====================
    static class World implements Serializable {
        private static final long serialVersionUID = 9L;

        public static final int AIR = 0;
        public static final int DIRT = 1;
        public static final int STONE = 2;
        public static final int GRASS = 3;
        public static final int SAND = 4;
        public static final int WOOD = 5;
        public static final int LEAVES = 6;
        public static final int COAL = 7;
        public static final int IRON = 8;
        public static final int GOLD = 9;
        public static final int DIAMOND = 10;
        public static final int CHEST = 11;
        public static final int FURNACE = 12;
        public static final int WORKBENCH = 13;
        public static final int TORCH = 14;
        public static final int IRON_INGOT = 15;
        public static final int GOLD_INGOT = 16;
        public static final int DIAMOND_GEM = 17;
        public static final int COAL_ITEM = 18;
        public static final int PLANK = 19;
        public static final int DOOR = 20;
        public static final int COIN_GOLD = 21;
        public static final int SNOW = 22;
        public static final int ICE = 23;
        public static final int SWORD_WOOD = 24;
        public static final int SWORD_STONE = 25;
        public static final int SWORD_IRON = 26;
        public static final int SWORD_DIAMOND = 27;
        public static final int PICK_WOOD = 28;
        public static final int PICK_STONE = 29;
        public static final int PICK_IRON = 30;
        public static final int PICK_DIAMOND = 31;
        public static final int AXE_WOOD = 32;
        public static final int AXE_STONE = 33;
        public static final int AXE_IRON = 34;
        public static final int AXE_DIAMOND = 35;
        public static final int DOOR_CLOSED = 36;
        public static final int DOOR_OPEN = 37;
        public static final int BG_STONE = 38;
        public static final int BG_DIRT = 39;
        public static final int BG_WOOD = 40;
        public static final int BG_PLANK = 41;
        public static final int CRAFT_TABLE = 42;
        public static final int BOOKSHELF = 50;
        public static final int GLASS = 51;
        public static final int LANTERN = 52;
        public static final int LADDER = 53;
        public static final int BED = 54;
        public static final int SIGN = 55;
        public static final int WATER = 56;
        public static final int LAVA = 57;
        public static final int BUCKET_EMPTY = 58;
        public static final int BUCKET_WATER = 59;
        public static final int BUCKET_LAVA = 60;
        public static final int WHEAT = 61;
        public static final int WHEAT_ITEM = 62;
        public static final int BREAD = 63;
        public static final int APPLE = 64;
        public static final int MEAT_RAW = 65;
        public static final int MEAT_COOKED = 66;
        public static final int MUSHROOM = 67;
        public static final int MUSHROOM_SOUP = 68;
        public static final int GOLDEN_APPLE = 69;
        public static final int CARROT = 70;
        public static final int POTION_HEAL = 71;
        public static final int POTION_STRENGTH = 72;
        public static final int POTION_SPEED = 73;
        public static final int POTION_NIGHT = 74;
        public static final int POTION_REGEN = 75;
        public static final int POTION_FIRE = 76;
        public static final int BREWING_STAND = 77;
        public static final int ALEMBIC = 78;
        public static final int FLASK = 79;
        public static final int HELLSTONE = 80;
        public static final int ASH = 81;
        public static final int OBSIDIAN = 82;
        public static final int DEMON_ALTAR = 83;
        public static final int DEMONITE = 84;
        public static final int HELL_PORTAL = 85;
        public static final int BOSS_SUMMON_SLIME = 86;
        public static final int BOSS_SUMMON_EYE = 87;
        public static final int BOSS_SUMMON_SKELETON = 88;
        public static final int BOSS_SUMMON_HELL = 89;
        public static final int HELL_CROWN = 90;
        public static final int WOOL_ITEM = 91;

        public static final int BIOME_FOREST = 0;
        public static final int BIOME_DESERT = 1;
        public static final int BIOME_SNOW = 2;
        public static final int BIOME_MOUNTAIN = 3;
        public static final int BIOME_JUNGLE = 4;
        public static final int BIOME_CORRUPTION = 5;
        public static final int BIOME_HELL = 6;

        private final int width, height;
        private final int[][] tiles;
        private final int[][] background;
        public String name = "world";
        public long seed = 0;
        public String dataPackName = "default";
        public final List<WorkBlock> workBlocks = new ArrayList<>();
        public final List<Integer> villageX = new ArrayList<>();
        public final List<Integer> villageY = new ArrayList<>();
        public transient double[] surfaceMap;
        public DataPack dataPack;
        public transient byte[][] liquid;
        // ---- активные регионы для оптимизации tick ----
        private static final int REGION = 32;
        private transient Set<Long> activeRegions = new HashSet<>();
        private transient int activeRegionAge = 0;
        public transient int[] biomeMap;
        private static long regionKey(int rx, int ry) {
            return ((long) rx << 32) | (ry & 0xFFFFFFFFL);
        }
        private int regionOf(int tile) { return tile / REGION; }

        /** Помечает регион (и соседние) как активный — там будет тикать песок/жидкость. */
        public void markActive(int x, int y) {
            ensureActiveRegions();
            int rx = regionOf(x), ry = regionOf(y);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    activeRegions.add(regionKey(rx + dx, ry + dy));
        }
        private void ensureActiveRegions() {
            if (activeRegions == null) activeRegions = new HashSet<>();
        }
        public transient byte[][] lightMap;
        public transient boolean needsLightUpdate = true;

        public World(int width, int height, long seed) { this(width, height, seed, null); }

        public World(int width, int height, long seed, DataPack dp) {
            this.width = width;
            this.height = height;
            this.tiles = new int[width][height];
            this.background = new int[width][height];
            this.liquid = new byte[width][height];
            this.lightMap = new byte[width][height];
            this.seed = seed;
            this.dataPack = dp;
            if (width > 10) generate(seed);
        }
        public int biomeAt(int x) {
            // у тебя биомы хранятся в generate() локально.
            // Проще: сохранять biome[] как transient-поле и заполнять в generate().
            if (biomeMap == null || x < 0 || x >= width) return BIOME_FOREST;
            return biomeMap[x];
        }

        private void generate(long seed) {
            Random rnd = new Random(seed);
            DataPack dp = this.dataPack != null ? this.dataPack : new DataPack();

            // ===== 1. БАЗОВЫЙ РЕЛЬЕФ (плоский) =====
            double[] surface = new double[width];
            int base = height / 3;
            for (int x = 0; x < width; x++) {
                surface[x] = base;
                surface[x] += Math.sin(x * 0.02) * 2.0 + Math.sin(x * 0.05) * 1.5;
            }

            // ===== 2. ГОРЫ (низкие, опционально) =====
            for (int m = 0; m < dp.mountainCount; m++) {
                int cx = 100 + rnd.nextInt(Math.max(1, width - 200));
                int peakHeight = 8 + rnd.nextInt(12);
                int radius = 40 + rnd.nextInt(60);
                for (int x = Math.max(0, cx - radius); x < Math.min(width, cx + radius); x++) {
                    double d = Math.abs(x - cx) / (double) radius;
                    surface[x] -= Math.cos(d * Math.PI / 2) * peakHeight;
                }
            }

            // ===== 3. РЕКИ =====
            // Каждая река — лента с синусоидальным изгибом, идущая по X.
            // Результат: surface[x] углублён, а ниже мы зальём русло водой.
            List<int[]> riverCenters = new ArrayList<>();   // [xCenter, halfWidth, waterLevel]
            for (int r = 0; r < dp.riverCount; r++) {
                double baseX = 100 + rnd.nextInt(Math.max(1, width - 200));
                double halfWidth = 8 + rnd.nextInt(6);      // половина ширины русла
                double amplitude = 20 + rnd.nextInt(40);    // амплитуда изгиба по X
                double period = 200 + rnd.nextInt(200);     // период синуса
                double phase = rnd.nextDouble() * Math.PI * 2;

                // Уровень воды = средний уровень поверхности в русле
                double waterLevel = 0;
                int samples = 0;
                for (int x = (int) (baseX - halfWidth * 3); x <= baseX + halfWidth * 3; x++) {
                    if (x < 0 || x >= width) continue;
                    waterLevel += surface[x];
                    samples++;
                }
                if (samples > 0) waterLevel /= samples;
                waterLevel += 1;   // чуть ниже поверхности

                for (int x = 0; x < width; x++) {
                    // насколько x близок к осевой линии реки (с изгибом)
                    double cx = baseX + Math.sin(x / period * Math.PI * 2 + phase) * amplitude;
                    double dist = Math.abs(x - cx);
                    if (dist > halfWidth) continue;
                    double t = dist / halfWidth;              // 0 в центре, 1 на краю
                    double depth = Math.cos(t * Math.PI / 2) * 4.0;  // до 4 блоков вглубь
                    surface[x] += depth;                       // углубляем рельеф
                }
                // запоминаем для заливки воды
                riverCenters.add(new int[]{(int) baseX, (int) halfWidth, (int) Math.round(waterLevel)});
            }

            // ===== 4. СГЛАЖИВАНИЕ =====
            for (int pass = 0; pass < 3; pass++) {
                double[] s2 = surface.clone();
                for (int x = 1; x < width - 1; x++) s2[x] = (surface[x - 1] + surface[x] * 2 + surface[x + 1]) / 4.0;
                surface = s2;
            }

            // ===== 5. БИОМЫ =====
            List<Integer> available = new ArrayList<>();
            available.add(BIOME_FOREST);
            if (dp.allowDesert) available.add(BIOME_DESERT);
            if (dp.allowSnow) available.add(BIOME_SNOW);
            if (dp.allowMountains) available.add(BIOME_MOUNTAIN);
            available.add(BIOME_JUNGLE);
            available.add(BIOME_CORRUPTION);

            int[] biome = new int[width];
            int biomeLen = 0;
            int currentBiome = available.get(rnd.nextInt(available.size()));
            int prevBiome = currentBiome;
            int transitionLeft = 0;
            for (int x = 0; x < width; x++) {
                if (biomeLen <= 0) {
                    prevBiome = currentBiome;
                    currentBiome = available.get(rnd.nextInt(available.size()));
                    biomeLen = 80 + rnd.nextInt(120);
                    transitionLeft = 16;
                }
                if (transitionLeft > 0) { biome[x] = (x % 2 == 0) ? prevBiome : currentBiome; transitionLeft--; }
                else biome[x] = currentBiome;
                biomeLen--;
            }

            // ===== 6. ЗАПОЛНЕНИЕ ТАЙЛОВ =====
            for (int x = 0; x < width; x++) {
                int top = (int) surface[x];
                top = Math.max(10, Math.min(height - 30, top));
                surface[x] = top;
                int surfaceBlock, soilBlock;
                switch (biome[x]) {
                    case BIOME_DESERT: surfaceBlock = SAND; soilBlock = SAND; break;
                    case BIOME_SNOW: surfaceBlock = SNOW; soilBlock = ICE; break;
                    case BIOME_MOUNTAIN: surfaceBlock = STONE; soilBlock = STONE; break;
                    case BIOME_JUNGLE: surfaceBlock = GRASS; soilBlock = DIRT; break;
                    case BIOME_CORRUPTION: surfaceBlock = DIRT; soilBlock = STONE; break;
                    default: surfaceBlock = GRASS; soilBlock = DIRT;
                }
                int dirtDepth = 4 + rnd.nextInt(4);
                for (int y = 0; y < height; y++) {
                    if (y < top) tiles[x][y] = AIR;
                    else if (y == top) tiles[x][y] = surfaceBlock;
                    else if (y < top + dirtDepth) tiles[x][y] = soilBlock;
                    else if (biome[x] == BIOME_SNOW && y < top + 12) tiles[x][y] = ICE;
                    else tiles[x][y] = STONE;
                }
            }

            // ===== 6.1. АД ВНИЗУ =====
            int hellStart = (int) (height * 0.85);
            for (int x = 0; x < width; x++) for (int y = hellStart; y < height; y++) {
                if (tiles[x][y] == STONE || tiles[x][y] == DIRT || tiles[x][y] == ICE)
                    tiles[x][y] = rnd.nextDouble() < 0.6 ? HELLSTONE : ASH;
            }
            for (int i = 0; i < 40; i++) {
                int cx = 10 + rnd.nextInt(Math.max(1, width - 20));
                int cy = hellStart + 5 + rnd.nextInt(Math.max(1, height - hellStart - 10));
                int size = 3 + rnd.nextInt(5);
                for (int k = 0; k < size; k++) {
                    int x = cx + rnd.nextInt(3) - 1;
                    int y = cy + rnd.nextInt(3) - 1;
                    if (inBounds(x, y) && tiles[x][y] == HELLSTONE) tiles[x][y] = DEMONITE;
                }
            }
            for (int i = 0; i < 6; i++) {
                int cx = 50 + rnd.nextInt(Math.max(1, width - 100));
                int cy = hellStart + 10 + rnd.nextInt(Math.max(1, height - hellStart - 20));
                if (inBounds(cx, cy)) tiles[cx][cy] = DEMON_ALTAR;
            }
            for (int i = 0; i < 15; i++) {
                int cx = 20 + rnd.nextInt(Math.max(1, width - 40));
                int cy = hellStart + 5 + rnd.nextInt(Math.max(1, height - hellStart - 10));
                int rx = 4 + rnd.nextInt(8), ry = 2 + rnd.nextInt(3);
                for (int dx = -rx; dx <= rx; dx++) for (int dy = -ry; dy <= ry; dy++) {
                    if ((dx * dx) / (double) (rx * rx) + (dy * dy) / (double) (ry * ry) > 1.0) continue;
                    int x = cx + dx, y = cy + dy;
                    if (inBounds(x, y) && tiles[x][y] == HELLSTONE) tiles[x][y] = LAVA;
                }
            }

            // ===== 7. ПЕЩЕРЫ =====
            for (int i = 0; i < dp.caveCount + rnd.nextInt(10); i++) {
                carveWorm(rnd.nextInt(width), (int) (height * (0.35 + rnd.nextDouble() * 0.55)), 60 + rnd.nextInt(200), 3 + rnd.nextInt(5), rnd);
            }
            for (int i = 0; i < 8 + rnd.nextInt(10); i++) {
                carveEllipse(20 + rnd.nextInt(Math.max(1, width - 40)), (int) (height * (0.4 + rnd.nextDouble() * 0.5)), 8 + rnd.nextInt(15), 6 + rnd.nextInt(10));
            }
            for (int i = 0; i < 4 + rnd.nextInt(6); i++) {
                int cx = 50 + rnd.nextInt(Math.max(1, width - 100));
                int topY = findSurface(cx) + 3;
                int depth = 40 + rnd.nextInt(60);
                int r = 2 + rnd.nextInt(2);
                for (int y = topY; y < topY + depth; y++) for (int dx = -r; dx <= r; dx++)
                    if (inBounds(cx + dx, y)) tiles[cx + dx][y] = AIR;
            }

            // ===== 7.1. ВОДА В ПЕЩЕРАХ =====
            if (liquid == null) liquid = new byte[width][height];
            for (int i = 0; i < 60; i++) {
                int cx = 20 + rnd.nextInt(Math.max(1, width - 40));
                int cy = (int) (height * 0.5 + rnd.nextDouble() * height * 0.3);
                for (int dx = -5; dx <= 5; dx++) for (int dy = -3; dy <= 3; dy++) {
                    int x = cx + dx, y = cy + dy;
                    if (inBounds(x, y) && tiles[x][y] == AIR && getLiquid(x, y) == 0) setLiquid(x, y, 7);
                }
            }
            // ===== 7.2. ЗАЛИВКА РЕК =====
            for (int[] rc : riverCenters) {
                int centerX = rc[0];
                int halfW = rc[1];
                int waterLevel = rc[2];
                for (int x = Math.max(0, centerX - halfW * 4); x < Math.min(width, centerX + halfW * 4); x++) {
                    // осевая линия с тем же изгибом — восстановим
                    double cx = centerX;
                    // (если хочешь точно — вынеси генерацию изгиба в функцию и переиспользуй)
                    // Упрощённо: заливаем там, где surface[x] ниже waterLevel и это русло
                    int top = findSurface(x);
                    if (top <= waterLevel) continue;         // не в русле
                    if (top - waterLevel > 6) continue;      // слишком глубоко — не река
                    for (int y = waterLevel; y < top; y++) {
                        if (inBounds(x, y) && tiles[x][y] == AIR) {
                            setLiquid(x, y, 7);              // полный уровень воды
                        }
                    }
                }
            }

            // ===== 8. РУДЫ =====
            this.surfaceMap = surface;
            generateOreVeins(COAL, 12, rnd);
            generateOreVeins(IRON, 8, rnd);
            generateOreVeins(GOLD, 5, rnd);
            generateOreVeins(DIAMOND, 3, rnd);

            // ===== 9. РАСТИТЕЛЬНОСТЬ =====
            for (int x = 4; x < width - 4; x++) {
                int top = (int) surface[x];
                int ground = tiles[x][top];
                if (biome[x] == BIOME_FOREST && ground == GRASS && rnd.nextDouble() < dp.treeDensity) buildTree(x, top, rnd.nextInt(2), rnd);
                else if (biome[x] == BIOME_JUNGLE && ground == GRASS && rnd.nextDouble() < 0.15) {
                    buildTree(x, top, 3, rnd);
                    for (int i = 1; i < 3 + rnd.nextInt(4); i++) if (inBounds(x, top + i) && tiles[x][top + i] == AIR) tiles[x][top + i] = LEAVES;
                } else if (biome[x] == BIOME_SNOW && ground == SNOW && rnd.nextDouble() < 0.07) buildTree(x, top, 1, rnd);
                else if (biome[x] == BIOME_CORRUPTION && ground == DIRT && rnd.nextDouble() < 0.05) {
                    int h = 4 + rnd.nextInt(3);
                    for (int i = 1; i <= h; i++) if (inBounds(x, top - i)) tiles[x][top - i] = WOOD;
                }
                if (ground == GRASS && rnd.nextDouble() < 0.02) if (inBounds(x, top - 1) && tiles[x][top - 1] == AIR) tiles[x][top - 1] = WHEAT;
            }

            // ===== 10. КАКТУСЫ В ПУСТЫНЕ =====
            for (int x = 4; x < width - 4; x++) {
                int top = (int) surface[x];
                if (tiles[x][top] != SAND) continue;
                if (rnd.nextDouble() < 0.04) {
                    int h = 2 + rnd.nextInt(3);
                    for (int i = 1; i <= h; i++) if (inBounds(x, top - i)) tiles[x][top - i] = WOOD;
                }
            }

            // ===== 11. ФОН ПЕЩЕР =====
            for (int x = 0; x < width; x++) {
                int top = (int) surface[x];
                for (int y = top; y < height; y++) {
                    if (tiles[x][y] == AIR) background[x][y] = (y < top + 6) ? BG_DIRT : BG_STONE;
                }
            }

            // ===== 12. СТРУКТУРЫ =====
            for (int v = 0; v < dp.villageCount; v++) {
                int vx = 80 + rnd.nextInt(Math.max(1, width - 160));
                int vy = (int) surface[vx];
                if (vy < 20 || vy > height - 30) continue;
                buildVillage(vx, vy, rnd);
            }
            for (int m = 0; m < dp.mineCount; m++) {
                buildMine(60 + rnd.nextInt(Math.max(1, width - 120)), (int) (height * 0.55 + rnd.nextDouble() * 0.3), rnd);
            }
            for (int t = 0; t < dp.templeCount; t++) {
                buildTemple(100 + rnd.nextInt(Math.max(1, width - 200)), (int) (height * 0.65 + rnd.nextDouble() * 0.25), rnd);
            }
            for (int r = 0; r < dp.ruinCount; r++) {
                int rx = 60 + rnd.nextInt(Math.max(1, width - 120));
                int ry = (int) surface[rx];
                if (ry < 20 || ry > height - 30) continue;
                buildRuins(rx, ry, rnd);
            }

            // ===== 13. ФИНАЛЬНАЯ КАРТА =====
            this.surfaceMap = surface;
            this.needsLightUpdate = true;
            this.biomeMap = biome;
        }
        public void ensureRuntimeState() {
            if (liquid == null || liquid.length != width || liquid[0].length != height)
                liquid = new byte[width][height];
            if (lightMap == null || lightMap.length != width || lightMap[0].length != height)
                lightMap = new byte[width][height];
            if (surfaceMap == null || surfaceMap.length != width) {
                surfaceMap = new double[width];
                for (int x = 0; x < width; x++) surfaceMap[x] = findSurface(x);
            }
            if (biomeMap == null || biomeMap.length != width) {
                biomeMap = new int[width];
            }
            needsLightUpdate = true;
            ensureActiveRegions();
        }

        private void generateOreVeins(int oreBlock, int numVeins, Random rnd) {
            for (int i = 0; i < numVeins * 10; i++) {
                int cx = 5 + rnd.nextInt(Math.max(1, width - 10));
                int cy = 20 + rnd.nextInt(Math.max(1, height - 30));
                if (oreBlock == DIAMOND && cy < height * 0.75) continue;
                if (oreBlock == GOLD && cy < height * 0.6) continue;
                if (oreBlock == IRON && cy < height * 0.4) continue;
                int veinSize = 4 + rnd.nextInt(8);
                for (int k = 0; k < veinSize; k++) {
                    int x = cx + rnd.nextInt(5) - 2;
                    int y = cy + rnd.nextInt(5) - 2;
                    if (inBounds(x, y) && tiles[x][y] == STONE) tiles[x][y] = oreBlock;
                }
            }
        }

        private void carveEllipse(int cx, int cy, int rx, int ry) {
            for (int dx = -rx; dx <= rx; dx++) for (int dy = -ry; dy <= ry; dy++) {
                if ((dx * dx) / (double) (rx * rx) + (dy * dy) / (double) (ry * ry) > 1.0) continue;
                int x = cx + dx, y = cy + dy;
                if (!inBounds(x, y)) continue;
                int b = tiles[x][y];
                if (b == STONE || b == DIRT || b == GRASS || b == SAND || b == SNOW || b == ICE || b == COAL || b == IRON || b == GOLD || b == DIAMOND) tiles[x][y] = AIR;
            }
        }

        private void carveWorm(int cx, int cy, int length, int radius, Random rnd) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            for (int step = 0; step < length; step++) {
                angle += (rnd.nextDouble() - 0.5) * 0.5;
                cx += (int) (Math.cos(angle) * 1.5);
                cy += (int) (Math.sin(angle) * 1.5);
                int r = radius - step * radius / (length + 1) / 4;
                if (r < 1) r = 1;
                for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) {
                    if (dx * dx + dy * dy > r * r) continue;
                    int tx = cx + dx, ty = cy + dy;
                    if (!inBounds(tx, ty)) continue;
                    int b = tiles[tx][ty];
                    if (b == STONE || b == DIRT || b == GRASS || b == SAND || b == SNOW || b == ICE || b == COAL || b == IRON || b == GOLD || b == DIAMOND) tiles[tx][ty] = AIR;
                }
            }
        }

        private void buildTree(int x, int groundY, int type, Random rnd) {
            int h, crownRadius;
            switch (type) {
                case 0: h = 5 + rnd.nextInt(3); crownRadius = 3; break;
                case 1: h = 8 + rnd.nextInt(4); crownRadius = 2; break;
                case 2: h = 4 + rnd.nextInt(3); crownRadius = 2; break;
                case 3: h = 7 + rnd.nextInt(4); crownRadius = 4; break;
                default: h = 5; crownRadius = 3;
            }
            for (int i = 1; i <= h; i++) if (inBounds(x, groundY - i)) tiles[x][groundY - i] = WOOD;
            int crownY = groundY - h;
            if (type == 1) {
                for (int layer = 0; layer < 4; layer++) {
                    int r = 3 - layer;
                    for (int dx = -r; dx <= r; dx++) for (int dy = 0; dy < 2; dy++) {
                        int tx = x + dx, ty = crownY + layer * 2 + dy;
                        if (inBounds(tx, ty) && tiles[tx][ty] == AIR) tiles[tx][ty] = LEAVES;
                    }
                }
            } else if (type == 2) {
                for (int dx = -3; dx <= 3; dx++) for (int dy = -1; dy <= 1; dy++) {
                    if (Math.abs(dx) + Math.abs(dy) > 3) continue;
                    int tx = x + dx, ty = crownY + dy;
                    if (inBounds(tx, ty) && tiles[tx][ty] == AIR) tiles[tx][ty] = LEAVES;
                }
            } else {
                for (int dx = -crownRadius; dx <= crownRadius; dx++) for (int dy = -crownRadius; dy <= crownRadius; dy++) {
                    if (dx * dx + dy * dy > crownRadius * crownRadius) continue;
                    int tx = x + dx, ty = crownY + dy;
                    if (inBounds(tx, ty) && tiles[tx][ty] == AIR) tiles[tx][ty] = LEAVES;
                }
            }
        }

        private void buildVillage(int cx, int cy, Random rnd) {
            int numHouses = 5 + rnd.nextInt(4);
            List<int[]> housePositions = new ArrayList<>();
            for (int i = 0; i < numHouses; i++) {
                int hx = cx + (i - numHouses / 2) * 14 + rnd.nextInt(6) - 3;
                if (hx < 10 || hx > width - 10) continue;
                int hy = findSurface(hx);
                if (Math.abs(hy - cy) > 6) hy = cy;
                housePositions.add(new int[]{hx, hy});
            }
            for (int i = 0; i < housePositions.size() - 1; i++) {
                int[] a = housePositions.get(i), b = housePositions.get(i + 1);
                int y = Math.min(a[1], b[1]);
                for (int x = Math.min(a[0], b[0]); x <= Math.max(a[0], b[0]); x++) if (inBounds(x, y)) tiles[x][y] = PLANK;
            }
            for (int[] pos : housePositions) {
                buildHouse(pos[0], pos[1], rnd);
                villageX.add(pos[0]);
                villageY.add(pos[1] - 1);
            }
        }

        private void buildHouse(int cx, int cy, Random rnd) {
            int w = 7 + rnd.nextInt(3), h = 5 + rnd.nextInt(2);
            for (int x = -w / 2 - 1; x <= w / 2 + 1; x++) for (int y = 0; y < h + 3; y++) {
                int tx = cx + x, ty = cy - y;
                if (inBounds(tx, ty) && y > 0) tiles[tx][ty] = AIR;
            }
            for (int x = -w / 2; x <= w / 2; x++) for (int y = 0; y < h; y++) {
                int tx = cx + x, ty = cy - y;
                if (!inBounds(tx, ty)) continue;
                if (y == 0 || y == h - 1 || x == -w / 2 || x == w / 2) tiles[tx][ty] = PLANK;
                else if (y == h - 2 && (x == -w / 2 + 1 || x == w / 2 - 1)) tiles[tx][ty] = BG_PLANK;
                else if (y == 1 && (x == -2 || x == 2)) tiles[tx][ty] = TORCH;
                else tiles[tx][ty] = AIR;
            }
            int doorX = cx + (rnd.nextBoolean() ? -w / 2 : w / 2);
            int doorY = cy - 1;
            if (inBounds(doorX, doorY)) {
                tiles[doorX][doorY] = DOOR_CLOSED;
                if (inBounds(doorX, doorY - 1)) tiles[doorX][doorY - 1] = DOOR_CLOSED;
            }
            int chestX = cx + rnd.nextInt(Math.max(1, w - 2)) - (w - 2) / 2;
            int chestY = cy - 1;
            if (inBounds(chestX, chestY) && tiles[chestX][chestY] == AIR) tiles[chestX][chestY] = CHEST;
            int tableX = cx + rnd.nextInt(Math.max(1, w - 2)) - (w - 2) / 2;
            int tableY = cy - 1;
            if (inBounds(tableX, tableY) && tiles[tableX][tableY] == AIR) tiles[tableX][tableY] = WORKBENCH;
        }

        private void buildMine(int cx, int cy, Random rnd) {
            int numTunnels = 3 + rnd.nextInt(3);
            int dir = rnd.nextBoolean() ? 1 : -1;
            for (int t = 0; t < numTunnels; t++) {
                int startX = cx + rnd.nextInt(40) - 20;
                int startY = cy + rnd.nextInt(30) - 15;
                int length = 30 + rnd.nextInt(50);
                for (int i = 0; i < length; i++) {
                    int x = startX + i * dir;
                    if (!inBounds(x, startY)) break;
                    for (int dy = 0; dy < 3; dy++) if (inBounds(x, startY - dy)) tiles[x][startY - dy] = AIR;
                    if (i % 8 == 0 && inBounds(x, startY - 2)) {
                        tiles[x][startY - 2] = WOOD; tiles[x][startY - 1] = WOOD; tiles[x][startY] = WOOD; tiles[x][startY + 1] = PLANK;
                    }
                }
                int chestX = startX + (length / 2) * dir;
                if (inBounds(chestX, startY)) {
                    tiles[chestX][startY] = CHEST;
                    WorkBlock wb = new WorkBlock(chestX, startY, CHEST, 27);
                    wb.slots[0] = ItemStack.of(COIN_GOLD, 2 + rnd.nextInt(8));
                    wb.slots[1] = ItemStack.of(IRON_INGOT, 1 + rnd.nextInt(4));
                    if (rnd.nextDouble() < 0.4) wb.slots[2] = ItemStack.of(GOLD_INGOT, 1 + rnd.nextInt(2));
                    if (rnd.nextDouble() < 0.15) wb.slots[3] = ItemStack.of(DIAMOND_GEM, 1);
                    workBlocks.add(wb);
                }
            }
        }

        private void buildTemple(int cx, int cy, Random rnd) {
            int w = 20 + rnd.nextInt(10), h = 12 + rnd.nextInt(6);
            for (int x = -w / 2; x <= w / 2; x++) for (int y = -h; y <= 0; y++) {
                int tx = cx + x, ty = cy + y;
                if (inBounds(tx, ty)) tiles[tx][ty] = AIR;
            }
            for (int x = -w / 2; x <= w / 2; x++) for (int y = -h; y <= 0; y++) {
                int tx = cx + x, ty = cy + y;
                if (!inBounds(tx, ty)) continue;
                boolean edge = (x == -w / 2 || x == w / 2 || y == -h || y == 0);
                if (edge) tiles[tx][ty] = PLANK;
                else background[tx][ty] = BG_PLANK;
            }
            for (int col = -w / 4; col <= w / 4; col += w / 4) {
                if (col == 0) continue;
                for (int y = -h + 1; y < 0; y++) if (inBounds(cx + col, cy + y)) tiles[cx + col][cy + y] = STONE;
            }
            if (inBounds(cx, cy - 1)) tiles[cx][cy - 1] = GOLD_INGOT;
            int chestX = cx + w / 2 - 3, chestY = cy - 1;
            if (inBounds(chestX, chestY)) {
                tiles[chestX][chestY] = CHEST;
                WorkBlock wb = new WorkBlock(chestX, chestY, CHEST, 27);
                wb.slots[0] = ItemStack.of(DIAMOND_GEM, 1 + rnd.nextInt(2));
                wb.slots[1] = ItemStack.of(GOLD_INGOT, 3 + rnd.nextInt(5));
                wb.slots[2] = ItemStack.of(COIN_GOLD, 10 + rnd.nextInt(20));
                workBlocks.add(wb);
            }
            for (int fx = -w / 2 + 2; fx <= w / 2 - 2; fx += 6) if (inBounds(cx + fx, cy - h + 1)) tiles[cx + fx][cy - h + 1] = TORCH;
        }

        private void buildRuins(int cx, int cy, Random rnd) {
            int w = 6 + rnd.nextInt(6), h = 3 + rnd.nextInt(3);
            for (int x = -w / 2; x <= w / 2; x++) for (int y = 0; y < h; y++) {
                int tx = cx + x, ty = cy - y;
                if (!inBounds(tx, ty)) continue;
                boolean isWall = (x == -w / 2 || x == w / 2 || y == h - 1);
                if (isWall && rnd.nextDouble() < 0.7) tiles[tx][ty] = PLANK;
                else if (isWall) tiles[tx][ty] = AIR;
            }
            if (inBounds(cx, cy - 1) && tiles[cx][cy - 1] == AIR) {
                tiles[cx][cy - 1] = CHEST;
                WorkBlock wb = new WorkBlock(cx, cy - 1, CHEST, 27);
                wb.slots[0] = ItemStack.of(COIN_GOLD, 1 + rnd.nextInt(5));
                wb.slots[1] = ItemStack.of(TORCH, 4 + rnd.nextInt(8));
                workBlocks.add(wb);
            }
        }

        public int getLiquid(int x, int y) {
            if (!inBounds(x, y)) return 0;
            if (liquid == null) liquid = new byte[width][height];
            return liquid[x][y] & 0xFF;
        }

        public void setLiquid(int x, int y, int level) {
            if (liquid == null) liquid = new byte[width][height];
            if (inBounds(x, y)) {
                liquid[x][y] = (byte) level;
                markActive(x, y);
            }
        }

        public void tickLiquids() {
            if (liquid == null) liquid = new byte[width][height];
            for (int y = height - 2; y >= 0; y--) for (int x = 0; x < width; x++) {
                int lvl = getLiquid(x, y);
                if (lvl == 0) continue;
                boolean isLava = lvl >= 8;
                int maxLevel = isLava ? 8 : 7;
                int currentLevel = lvl;
                if (y + 1 < height && !isSolid(x, y + 1) && getLiquid(x, y + 1) < maxLevel) {
                    int space = maxLevel - getLiquid(x, y + 1);
                    int move = Math.min(currentLevel, space);
                    setLiquid(x, y + 1, getLiquid(x, y + 1) + move);
                    currentLevel -= move;
                    setLiquid(x, y, currentLevel <= 0 ? 0 : currentLevel);
                    if (currentLevel <= 0) continue;
                }
                if (currentLevel > 0) {
                    for (int dir = -1; dir <= 1; dir += 2) {
                        int nx = x + dir;
                        if (nx < 0 || nx >= width) continue;
                        if (isSolid(nx, y)) continue;
                        int nLvl = getLiquid(nx, y);
                        if (nLvl < maxLevel && currentLevel > 0) { int move = Math.min(1, currentLevel); setLiquid(nx, y, nLvl + move); currentLevel -= move; }
                    }
                    setLiquid(x, y, currentLevel <= 0 ? 0 : currentLevel);
                }
                if (isLava) {
                    for (int dir = -1; dir <= 1; dir += 2) {
                        int nx = x + dir;
                        if (inBounds(nx, y) && getLiquid(nx, y) > 0 && getLiquid(nx, y) < 8) { setBlock(x, y, OBSIDIAN); setLiquid(x, y, 0); setLiquid(nx, y, 0); }
                    }
                    if (inBounds(x, y - 1) && getLiquid(x, y - 1) > 0 && getLiquid(x, y - 1) < 8) { setBlock(x, y, OBSIDIAN); setLiquid(x, y, 0); setLiquid(x, y - 1, 0); }
                }
            }
        }

        public void tick() {
            // песок — только в активных регионах
            for (long rk : new ArrayList<>(activeRegions)) {
                int rx = (int) (rk >> 32);
                int ry = (int) rk;
                int x0 = rx * REGION, y0 = ry * REGION;
                int x1 = Math.min(width, x0 + REGION), y1 = Math.min(height, y0 + REGION);
                for (int x = x0; x < x1; x++)
                    for (int y = y1 - 2; y >= y0; y--)
                        if (tiles[x][y] == SAND && y + 1 < height && tiles[x][y + 1] == AIR) {
                            tiles[x][y + 1] = SAND; tiles[x][y] = AIR;
                            markActive(x, y); markActive(x, y + 1);
                        }
            }
            // жидкости — только в активных регионах
            tickLiquidsActive();
            if (needsLightUpdate || lightMap == null) {
                recalculateLight();
                needsLightUpdate = false;
            }
            // «остывание» регионов: если ничего не менялось N тиков — убрать
            ensureActiveRegions();
            activeRegionAge++;
            if (activeRegionAge > 120) { activeRegions.clear(); activeRegionAge = 0; }
        }
        private void tickLiquidsActive() {
            ensureRuntimeState();          // на случай null после десериализации
            if (liquid == null) liquid = new byte[width][height];
            for (long rk : new ArrayList<>(activeRegions)) {
                int rx = (int) (rk >> 32);
                int ry = (int) rk;
                int x0 = Math.max(0, rx * REGION), y0 = Math.max(0, ry * REGION);
                int x1 = Math.min(width, x0 + REGION), y1 = Math.min(height, y0 + REGION);
                for (int y = y1 - 1; y >= y0; y--)
                    for (int x = x0; x < x1; x++)
                        tickLiquidCell(x, y);
            }
        }
        private void tickLiquidCell(int x, int y) {
            int lvl = getLiquid(x, y);
            if (lvl == 0) return;
            boolean isLava = lvl >= 8;
            int maxLevel = isLava ? 8 : 7;
            int currentLevel = lvl;

            if (y + 1 < height && !isSolid(x, y + 1) && getLiquid(x, y + 1) < maxLevel) {
                int space = maxLevel - getLiquid(x, y + 1);
                int move = Math.min(currentLevel, space);
                setLiquid(x, y + 1, getLiquid(x, y + 1) + move);
                currentLevel -= move;
                setLiquid(x, y, currentLevel <= 0 ? 0 : currentLevel);
                if (currentLevel <= 0) return;
            }
            if (currentLevel > 0) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    int nx = x + dir;
                    if (nx < 0 || nx >= width) continue;
                    if (isSolid(nx, y)) continue;
                    int nLvl = getLiquid(nx, y);
                    if (nLvl < maxLevel && currentLevel > 0) {
                        int move = Math.min(1, currentLevel);
                        setLiquid(nx, y, nLvl + move);
                        currentLevel -= move;
                    }
                }
                setLiquid(x, y, currentLevel <= 0 ? 0 : currentLevel);
            }
            if (isLava) {
                for (int dir = -1; dir <= 1; dir += 2) {
                    int nx = x + dir;
                    if (inBounds(nx, y) && getLiquid(nx, y) > 0 && getLiquid(nx, y) < 8) {
                        setBlock(x, y, OBSIDIAN);
                        setLiquid(x, y, 0);
                        setLiquid(nx, y, 0);
                    }
                }
                if (inBounds(x, y - 1) && getLiquid(x, y - 1) > 0 && getLiquid(x, y - 1) < 8) {
                    setBlock(x, y, OBSIDIAN);
                    setLiquid(x, y, 0);
                    setLiquid(x, y - 1, 0);
                }
            }
        }

        public void recalculateLight() {
            if (lightMap == null || lightMap.length != width) lightMap = new byte[width][height];
            for (int x = 0; x < width; x++) {
                if (lightMap[x] == null || lightMap[x].length != height) lightMap[x] = new byte[height];
                Arrays.fill(lightMap[x], (byte) 0);
            }

            // Небесный свет
            for (int x = 0; x < width; x++) {
                int top = findSurface(x);
                for (int y = 0; y < top; y++) lightMap[x][y] = 15;  // небо
            }

            // Источники света
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    int b = tiles[x][y];
                    int light = 0;
                    if (b == TORCH) light = 14;
                    else if (b == LANTERN) light = 13;
                    else if (b == LAVA) light = 12;
                    else if (b == FURNACE) light = 8;
                    else if (b == HELLSTONE) light = 6;
                    if (light > 0) lightMap[x][y] = (byte) light;
                }
            }

            // Распространение BFS
            for (int pass = 0; pass < 3; pass++) {
                for (int x = 0; x < width; x++) {
                    for (int y = 0; y < height; y++) {
                        int cur = lightMap[x][y] & 0xFF;
                        if (cur <= 1) continue;
                        int newLight = cur - 1;
                        if (x > 0 && (lightMap[x - 1][y] & 0xFF) < newLight) lightMap[x - 1][y] = (byte) newLight;
                        if (x < width - 1 && (lightMap[x + 1][y] & 0xFF) < newLight) lightMap[x + 1][y] = (byte) newLight;
                        if (y > 0 && (lightMap[x][y - 1] & 0xFF) < newLight) lightMap[x][y - 1] = (byte) newLight;
                        if (y < height - 1 && (lightMap[x][y + 1] & 0xFF) < newLight) lightMap[x][y + 1] = (byte) newLight;
                    }
                }
            }
        }

        public int getLight(int x, int y) {
            if (!inBounds(x, y)) return 0;
            if (lightMap == null) return 15;
            return lightMap[x][y] & 0xFF;
        }

        public int findSurface(int x) {
            if (x < 0 || x >= width) return height / 3;
            for (int y = 0; y < height; y++) if (tiles[x][y] != AIR) return y;
            return height / 3;
        }

        public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }
        public int getBlock(int x, int y) { if (!inBounds(x, y)) return STONE; return tiles[x][y]; }
        public void setBlock(int x, int y, int b) {
            if (inBounds(x, y)) {
                tiles[x][y] = b;
                needsLightUpdate = true;
                markActive(x, y);
            }
        }
        public int getBackground(int x, int y) { if (!inBounds(x, y)) return AIR; return background[x][y]; }
        public void setBackground(int x, int y, int b) {
            if (inBounds(x, y)) {
                background[x][y] = b;
                markActive(x, y);
            }
        }

        public boolean isSolid(int x, int y) {
            int b = getBlock(x, y);
            if (b == AIR || b == TORCH || b == DOOR_OPEN) return false;
            if (b == WATER || b == LAVA) return false;
            if (b == WHEAT) return false;
            if (b >= BG_STONE && b <= BG_PLANK) return false;
            return true;
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }

        public static String blockName(int b) {
            switch (b) {
                case DIRT: return "Грязь";
                case STONE: return "Камень";
                case GRASS: return "Трава";
                case SAND: return "Песок";
                case WOOD: return "Дерево";
                case LEAVES: return "Листва";
                case COAL: return "Угольная руда";
                case IRON: return "Железная руда";
                case GOLD: return "Золотая руда";
                case DIAMOND: return "Алмазная руда";
                case CHEST: return "Сундук";
                case FURNACE: return "Печь";
                case WORKBENCH: return "Верстак";
                case TORCH: return "Факел";
                case IRON_INGOT: return "Железный слиток";
                case GOLD_INGOT: return "Золотой слиток";
                case DIAMOND_GEM: return "Алмаз";
                case COAL_ITEM: return "Уголь";
                case PLANK: return "Доски";
                case DOOR: return "Дверь";
                case COIN_GOLD: return "Золотая монета";
                case SNOW: return "Снег";
                case ICE: return "Лёд";
                case SWORD_WOOD: return "Деревянный меч";
                case SWORD_STONE: return "Каменный меч";
                case SWORD_IRON: return "Железный меч";
                case SWORD_DIAMOND: return "Алмазный меч";
                case PICK_WOOD: return "Деревянная кирка";
                case PICK_STONE: return "Каменная кирка";
                case PICK_IRON: return "Железная кирка";
                case PICK_DIAMOND: return "Алмазная кирка";
                case AXE_WOOD: return "Деревянный топор";
                case AXE_STONE: return "Каменный топор";
                case AXE_IRON: return "Железный топор";
                case AXE_DIAMOND: return "Алмазный топор";
                case DOOR_CLOSED: return "Дверь (закрыта)";
                case DOOR_OPEN: return "Дверь (открыта)";
                case BG_STONE: return "Каменная стена";
                case BG_DIRT: return "Земляная стена";
                case BG_WOOD: return "Деревянная стена";
                case BG_PLANK: return "Дощатая стена";
                case CRAFT_TABLE: return "Верстак";
                case BOOKSHELF: return "Книжная полка";
                case GLASS: return "Стекло";
                case LANTERN: return "Фонарь";
                case LADDER: return "Лестница";
                case BED: return "Кровать";
                case SIGN: return "Табличка";
                case WATER: return "Вода";
                case LAVA: return "Лава";
                case BUCKET_EMPTY: return "Ведро";
                case BUCKET_WATER: return "Ведро с водой";
                case BUCKET_LAVA: return "Ведро с лавой";
                case WHEAT: return "Пшеница (росток)";
                case WHEAT_ITEM: return "Пшеница";
                case BREAD: return "Хлеб";
                case APPLE: return "Яблоко";
                case MEAT_RAW: return "Сырое мясо";
                case MEAT_COOKED: return "Жареное мясо";
                case MUSHROOM: return "Гриб";
                case MUSHROOM_SOUP: return "Грибной суп";
                case GOLDEN_APPLE: return "Золотое яблоко";
                case CARROT: return "Морковь";
                case POTION_HEAL: return "Зелье лечения";
                case POTION_STRENGTH: return "Зелье силы";
                case POTION_SPEED: return "Зелье скорости";
                case POTION_NIGHT: return "Зелье ночного зрения";
                case POTION_REGEN: return "Зелье регенерации";
                case POTION_FIRE: return "Зелье огнестойкости";
                case BREWING_STAND: return "Зельеварка";
                case ALEMBIC: return "Аламбик";
                case FLASK: return "Колба";
                case HELLSTONE: return "Адский камень";
                case ASH: return "Пепел";
                case OBSIDIAN: return "Обсидиан";
                case DEMON_ALTAR: return "Демонический алтарь";
                case DEMONITE: return "Демонит";
                case HELL_PORTAL: return "Адский портал";
                case BOSS_SUMMON_SLIME: return "Призыв: Король Слизней";
                case BOSS_SUMMON_EYE: return "Призыв: Глаз Ктулху";
                case BOSS_SUMMON_SKELETON: return "Призыв: Скелетрон";
                case BOSS_SUMMON_HELL: return "Призыв: Король Ада";
                case HELL_CROWN: return "Адская корона";
                case WOOL_ITEM: return "Шерсть";
                default: return "Воздух";
            }
        }
    }

    // ==================== WORKBLOCK ====================
    static class WorkBlock implements Serializable {
        private static final long serialVersionUID = 8L;
        public static final int COOK_TIME = 180;
        int x, y, type;
        ItemStack[] slots;
        int cookTime = 0;
        int currentRecipe = -1;
        WorkBlock(int x, int y, int type, int size) { this.x = x; this.y = y; this.type = type; this.slots = new ItemStack[size]; }
        int getResultCount() { return slots.length > 8 && slots[8] != null ? slots[8].count : 0; }
        void tickFurnace() {
            if (type != World.FURNACE || slots.length < 9) return;
            ItemStack ore = slots[0];
            ItemStack fuel = slots[1] != null ? slots[1] : slots[2];
            ItemStack result = slots[8];
            int recipe = (ore != null) ? getRecipe(ore.id) : -1;
            if (cookTime > 0 && (recipe == -1 || recipe != currentRecipe)) { cookTime = 0; currentRecipe = -1; }
            if (cookTime > 0) {
                cookTime--;
                if (cookTime <= 0) {
                    if (ore != null && recipe != -1) {
                        if (result == null) slots[8] = ItemStack.of(recipe, 1);
                        else if (result.id == recipe && result.count < 999) result.count++;
                        ore.count--;
                        if (ore.count <= 0) slots[0] = null;
                    }
                    cookTime = 0; currentRecipe = -1;
                }
                return;
            }
            if (ore == null || recipe == -1) return;
            if (result != null && (result.id != recipe || result.count >= 999)) return;
            if (fuel == null || !isFuel(fuel.id)) return;
            fuel.count--;
            if (fuel.count <= 0) { if (slots[1] == fuel) slots[1] = null; else slots[2] = null; }
            cookTime = COOK_TIME;
            currentRecipe = recipe;
        }
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
        }
        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
            if (slots == null) slots = new ItemStack[type == World.CHEST ? 27 : 9];
        }
        static int getRecipe(int oreId) {
            switch (oreId) {
                case World.IRON: return World.IRON_INGOT;
                case World.GOLD: return World.GOLD_INGOT;
                case World.DIAMOND: return World.DIAMOND_GEM;
                case World.COAL: return World.COAL_ITEM;
                case World.SAND: return World.STONE;
                default: return -1;
            }
        }
        static boolean isFuel(int id) { return id == World.COAL || id == World.COAL_ITEM || id == World.WOOD || id == World.LEAVES || id == World.PLANK; }
    }

    // ==================== ITEMSTACK ====================
    static class ItemStack implements Serializable {
        private static final long serialVersionUID = 1L;
        int id, count;
        ItemStack(int id, int count) { this.id = id; this.count = count; }
        static ItemStack of(int id, int count) { return new ItemStack(id, count); }
        ItemStack copy() { return new ItemStack(id, count); }
    }

    // ==================== PLAYER ====================
    static class Player {
        public static final int W = 24;
        public static final int H = 34;
        public double x, y, vx, vy;
        public double cameraX, cameraY;
        public double hp = 100, maxHp = 100;
        public double hunger = 100, maxHunger = 100;
        public int hungerTimer = 0;
        public int hurtCooldown = 0;
        public int animTick = 0;
        public int facing = 1;
        public boolean onGround = false;
        public boolean wasInAir = false;
        public double fallStartY = 0;
        public boolean godMode = false;
        public boolean flyMode = false;
        public int satiety = 100, satietyMax = 100;
        public int regenTimer = 0;
        public int strengthTimer = 0;
        public int speedTimer = 0;
        public int nightVisionTimer = 0;
        public int fireResistTimer = 0;
        private static final double GRAVITY = 0.7;
        private static final double MOVE_SPEED = 4.5;
        private static final double JUMP_POWER = -12.0;
        private static final double MAX_FALL = 18.0;
        private static final double FRICTION = 0.78;
        public boolean flyUp = false;
        public boolean flyDown = false;
        public double speedMultiplier = 1.0;
        public double jumpMultiplier = 1.0;

        public Player(double x, double y) { this.x = x; this.y = y; }

        public void update(double inputX, boolean jump, World world) {
            if (inputX > 0) facing = 1; else if (inputX < 0) facing = -1;
            if (inputX != 0) vx += inputX * 1.3 * speedMultiplier;
            if (speedTimer > 0 && inputX != 0) vx += inputX * 0.5;
            vx *= FRICTION;
            if (Math.abs(vx) > MOVE_SPEED) vx = Math.signum(vx) * MOVE_SPEED;
            if (flyMode) { vy = 0; if (jump) vy = -6; if (flyDown) vy = 6; }
            else { if (jump && onGround) { vy = JUMP_POWER; onGround = false; } vy += GRAVITY; if (vy > MAX_FALL) vy = MAX_FALL; }
            moveX(vx, world);
            moveY(vy, world);
            double targetCamX = x + W / 2.0 - SCREEN_W / 2.0;
            double targetCamY = y + H / 2.0 - SCREEN_H / 2.0;
            cameraX += (targetCamX - cameraX) * 0.15;
            cameraY += (targetCamY - cameraY) * 0.15;
            cameraX = clamp(cameraX, 0, world.getWidth() * TILE - SCREEN_W);
            cameraY = clamp(cameraY, 0, world.getHeight() * TILE - SCREEN_H);
            if (hurtCooldown > 0) hurtCooldown--;
            if (onGround && Math.abs(vx) > 0.5) animTick++;
            if (Modules.HUNGER_SYSTEM) {
                hungerTimer++;
                if (hungerTimer >= 60 * 20) {
                    hungerTimer = 0;
                    hunger -= 1;
                    if (hunger < 0) hunger = 0;
                    if (hunger <= 0 && hurtCooldown == 0) { hp -= 1; hurtCooldown = 60; }
                }
            }
            regenTimer++;
            if (regenTimer >= 200) {  // регенерация раз в ~3 сек
                regenTimer = 0;
                if (satiety > 30 && hp < maxHp) {
                    hp = Math.min(maxHp, hp + 2);
                    satiety = Math.max(0, satiety - 2);
                }
                // Голод от времени
                satiety = Math.max(0, satiety - 1);
            }
            if (strengthTimer > 0) strengthTimer--;
            if (speedTimer > 0) speedTimer--;
            if (nightVisionTimer > 0) nightVisionTimer--;
            if (fireResistTimer > 0) fireResistTimer--;
            if (Math.abs(vx) > MOVE_SPEED * speedMultiplier) vx = Math.signum(vx) * MOVE_SPEED * speedMultiplier;
            // прыжок:
            if (jump && onGround) { vy = JUMP_POWER * jumpMultiplier; onGround = false; }
        }

        private void moveX(double dx, World world) {
            x += dx;
            int top = (int) (y / TILE);
            int bottom = (int) ((y + H - 1) / TILE);
            if (dx > 0) {
                int right = (int) ((x + W - 1) / TILE);
                for (int ty = top; ty <= bottom; ty++) if (world.isSolid(right, ty)) { x = right * TILE - W; vx = 0; break; }
            } else if (dx < 0) {
                int left = (int) (x / TILE);
                for (int ty = top; ty <= bottom; ty++) if (world.isSolid(left, ty)) { x = (left + 1) * TILE; vx = 0; break; }
            }
        }

        private void moveY(double dy, World world) {
            y += dy;
            int left = (int) (x / TILE);
            int right = (int) ((x + W - 1) / TILE);
            if (dy > 0) {
                int bottom = (int) ((y + H - 1) / TILE);
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, bottom)) { y = bottom * TILE - H; vy = 0; onGround = true; break; }
            } else if (dy < 0) {
                int top = (int) (y / TILE);
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, top)) { y = (top + 1) * TILE; vy = 0; break; }
            }
            if (dy >= 0) {
                int bottom = (int) ((y + H) / TILE);
                boolean grounded = false;
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, bottom)) { grounded = true; break; }
                onGround = grounded;
            }
        }

        public void hurt(double dmg) {
            if (godMode || hurtCooldown > 0) return;
            hp -= dmg;
            hurtCooldown = 30;
            Sound.play(Sound.HURT);
        }

        public void draw(Graphics2D g, int camX, int camY) {
            int px = (int) (x - camX);
            int py = (int) (y - camY);
            if (hurtCooldown > 0 && (hurtCooldown / 4) % 2 == 0) return;
            g.setColor(new Color(0, 0, 0, 80));
            g.fillRect(px + 2, py + H - 3, W - 4, 3);
            int legPhase = (animTick / 5) % 4;
            int legOff = legPhase == 0 ? 0 : (legPhase == 1 ? 3 : (legPhase == 2 ? 0 : -3));
            g.setColor(new Color(60, 40, 30));
            g.fillRect(px + 5, py + H - 10 + Math.abs(legOff) / 2, 5, 10 - Math.abs(legOff));
            g.fillRect(px + W - 10, py + H - 10 + Math.abs(-legOff) / 2, 5, 10 - Math.abs(-legOff));
            g.setColor(new Color(80, 110, 200));
            g.fillRect(px + 3, py + 14, W - 6, H - 22);
            g.setColor(new Color(50, 30, 20));
            g.fillRect(px + 3, py + H - 14, W - 6, 3);
            g.setColor(new Color(240, 200, 150));
            int armOff = (animTick / 5) % 4;
            int armY = py + 16 + (armOff == 1 || armOff == 3 ? 1 : 0);
            g.fillRect(px - 1, armY, 4, 12);
            g.fillRect(px + W - 3, armY, 4, 12);
            g.setColor(new Color(250, 210, 160));
            g.fillRect(px + 3, py, W - 6, 14);
            g.setColor(new Color(80, 50, 30));
            g.fillRect(px + 3, py, W - 6, 4);
            g.fillRect(px + 3, py, 3, 8);
            g.fillRect(px + W - 6, py, 3, 8);
            g.setColor(Color.WHITE);
            int eyeY = py + 6;
            if (facing == 1) {
                g.fillRect(px + 9, eyeY, 4, 4); g.fillRect(px + 15, eyeY, 4, 4);
                g.setColor(new Color(30, 60, 130)); g.fillRect(px + 11, eyeY + 1, 2, 3); g.fillRect(px + 17, eyeY + 1, 2, 3);
            } else {
                g.fillRect(px + 5, eyeY, 4, 4); g.fillRect(px + 11, eyeY, 4, 4);
                g.setColor(new Color(30, 60, 130)); g.fillRect(px + 5, eyeY + 1, 2, 3); g.fillRect(px + 11, eyeY + 1, 2, 3);
            }
        }

        private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    }

    // ==================== MONSTER ====================
    static class Monster {
        public boolean isBoss = false;
        public int bossType = 0;
        public static final int W = 24;
        public static final int H = 30;
        double x, y, vx, vy;
        int type;
        double hp, maxHp;
        boolean dead = false;
        boolean onGround = false;
        int direction = 1;
        int jumpCooldown = 0;
        int attackCooldown = 0;
        int animTick = 0;
        private static final double GRAVITY = 0.6;

        Monster(double x, double y, int type) {
            this.x = x; this.y = y; this.type = type;
            if (type == 100) { hp = maxHp = 300; isBoss = true; bossType = 100; }
            else if (type == 101) { hp = maxHp = 500; isBoss = true; bossType = 101; }
            else if (type == 102) { hp = maxHp = 800; isBoss = true; bossType = 102; }
            else if (type == 103) { hp = maxHp = 2000; isBoss = true; bossType = 103; }
            else if (type == 0) hp = maxHp = 30;
            else if (type == 1) hp = maxHp = 20;
            else hp = maxHp = 40;
        }

        void update(Player player, World world, List<Monster> others) {
            if (dead) return;
            animTick++;
            if (isBoss) {
                if (bossType == 100) {
                    double dx = player.x - x;
                    if (onGround && Math.abs(dx) > 5 && animTick % 40 == 0) { vy = -15; vx = Math.signum(dx) * 3; }
                } else if (bossType == 101) {
                    double dx = player.x - x, dy = player.y - y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist > 1) { vx += dx / dist * 0.4; vy += dy / dist * 0.4; }
                    vx *= 0.92; vy *= 0.92;
                    if (Math.abs(vx) > 4) vx = Math.signum(vx) * 4;
                    if (Math.abs(vy) > 4) vy = Math.signum(vy) * 4;
                } else if (bossType == 102) {
                    double dx = player.x - x;
                    direction = dx > 0 ? 1 : -1;
                    vx += direction * 0.15;
                } else if (bossType == 103) {
                    double dx = player.x - x, dy = player.y - y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    if (dist < 200 && animTick % 150 == 0) { x = player.x + (new Random().nextInt(300) - 150); y = player.y - 100; }
                    vx += dx / Math.max(1, dist) * 0.3; vy += dy / Math.max(1, dist) * 0.3;
                    vx *= 0.9; vy *= 0.9;
                }
                if (bossType == 100 || bossType == 102) { vy += 0.6; if (vy > 15) vy = 15; }
                moveX(vx, world); moveY(vy, world);
                if (attackCooldown > 0) attackCooldown--;
                if (attackCooldown == 0) {
                    Rectangle mr = new Rectangle((int) x - W / 2, (int) y - H / 2, W * 3, H * 3);  // босс больше
                    Rectangle pr = new Rectangle((int) player.x, (int) player.y, Player.W, Player.H);
                    if (mr.intersects(pr)) {
                        int dmg = bossType == 103 ? 20 : bossType == 102 ? 12 : bossType == 101 ? 10 : 8;
                        player.hurt(dmg);
                        attackCooldown = bossType == 103 ? 50 : 60;  // раз в 0.8–1 сек
                    }
                }
                return;
            }
            // Горим на солнце
            if (!isBoss && isDaytime() && world.findSurface((int) (x / TILE)) > (int) (y / TILE)) {
                if (animTick % 30 == 0) { hp -= 3; if (hp <= 0) { dead = true; return; } }
            }
            double dx = player.x - x;
            direction = dx > 0 ? 1 : -1;
            vx += direction * 0.3;
            vx *= 0.85;
            if (Math.abs(vx) > 2.2) vx = Math.signum(vx) * 2.2;
            if (onGround && jumpCooldown <= 0) {
                int aheadX = (int) ((x + (direction > 0 ? W + 2 : -2)) / TILE);
                int belowY = (int) ((y + H + 2) / TILE);
                int aheadY = (int) ((y + H - 4) / TILE);
                if (world.isSolid(aheadX, aheadY) || !world.isSolid(aheadX, belowY)) { vy = -8; onGround = false; }
                jumpCooldown = 30;
            }
            if (jumpCooldown > 0) jumpCooldown--;
            vy += GRAVITY;
            if (vy > 15) vy = 15;
            moveX(vx, world);
            moveY(vy, world);
            if (attackCooldown > 0) attackCooldown--;
            if (attackCooldown == 0) {
                Rectangle mr = new Rectangle((int) x, (int) y, W, H);
                Rectangle pr = new Rectangle((int) player.x, (int) player.y, Player.W, Player.H);
                if (mr.intersects(pr)) { player.hurt(type == 2 ? 12 : 8); attackCooldown = 40; }
            }
        }

        private boolean isDaytime() {
            return TerrariaMini.currentGameTime % (60 * 60 * 3) < (60 * 60 * 3) / 2;
        }

        void moveX(double dx, World world) {
            x += dx;
            int top = (int) (y / TILE);
            int bottom = (int) ((y + H - 1) / TILE);
            if (dx > 0) {
                int right = (int) ((x + W - 1) / TILE);
                for (int ty = top; ty <= bottom; ty++) if (world.isSolid(right, ty)) { x = right * TILE - W; vx = 0; break; }
            } else if (dx < 0) {
                int left = (int) (x / TILE);
                for (int ty = top; ty <= bottom; ty++) if (world.isSolid(left, ty)) { x = (left + 1) * TILE; vx = 0; break; }
            }
        }

        void moveY(double dy, World world) {
            y += dy;
            int left = (int) (x / TILE);
            int right = (int) ((x + W - 1) / TILE);
            if (dy > 0) {
                int bottom = (int) ((y + H - 1) / TILE);
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, bottom)) { y = bottom * TILE - H; vy = 0; onGround = true; break; }
            } else if (dy < 0) {
                int top = (int) (y / TILE);
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, top)) { y = (top + 1) * TILE; vy = 0; break; }
            }
            if (dy >= 0) {
                int bottom = (int) ((y + H) / TILE);
                boolean grounded = false;
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, bottom)) { grounded = true; break; }
                onGround = grounded;
            }
        }

        void draw(Graphics2D g, int camX, int camY) {
            int px = (int) (x - camX), py = (int) (y - camY);
            int size = isBoss ? 2 : 1;
            if (isBoss) { px = (int) (x - camX) - W / 2; py = (int) (y - camY) - H / 2; }
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(px + 2, py + H * size - 2, W * size - 4, 6);
            if (isBoss) {
                Color c = bossType == 100 ? new Color(80, 140, 80) : bossType == 101 ? new Color(200, 200, 255) : bossType == 102 ? new Color(230, 230, 220) : new Color(255, 80, 20);
                g.setColor(c);
                g.fillOval(px, py, W * 2, H * 2);
                g.setColor(Color.WHITE);
                g.fillRect(px + W / 2, py + H / 2, 6, 6);
                g.fillRect(px + W * 3 / 2, py + H / 2, 6, 6);
            } else if (type == 0) {
                g.setColor(new Color(80, 140, 80));
                g.fillRect(px + 3, py, W - 6, H - 4);
                g.setColor(new Color(60, 100, 60));
                g.fillRect(px + 2, py + 8, 3, 12); g.fillRect(px + W - 5, py + 8, 3, 12);
                g.setColor(new Color(40, 20, 20));
                g.fillRect(px + 6, py + 6, 4, 4); g.fillRect(px + W - 10, py + 6, 4, 4);
            } else if (type == 1) {
                g.setColor(new Color(120, 200, 200, 200));
                int squash = (int) (Math.sin(animTick * 0.15) * 3);
                g.fillOval(px, py + squash, W, H - squash);
                g.setColor(new Color(80, 160, 160));
                g.fillOval(px + 5, py + 6 + squash, W - 10, H - 14);
                g.setColor(Color.BLACK);
                g.fillRect(px + 7, py + 10, 3, 3); g.fillRect(px + W - 10, py + 10, 3, 3);
            } else {
                g.setColor(new Color(230, 230, 220));
                g.fillRect(px + 4, py + 4, W - 8, H - 6);
                g.setColor(new Color(200, 200, 190));
                g.fillRect(px + W / 2 - 1, py + 10, 2, H - 14);
                g.fillRect(px + 4, py + 14, W - 8, 2);
                g.fillRect(px + 4, py + 20, W - 8, 2);
                g.setColor(new Color(60, 60, 60));
                g.fillRect(px + 6, py + 6, 4, 5); g.fillRect(px + W - 10, py + 6, 4, 5);
            }
            if (hp < maxHp) {
                int bw = W * size;
                g.setColor(Color.BLACK); g.fillRect(px, py - 8, bw, 4);
                g.setColor(Color.RED); g.fillRect(px, py - 8, (int) (bw * (hp / maxHp)), 4);
            }
        }
    }

    // ==================== PEACEFUL MOB ====================
    static class PeacefulMob {
        double x, y, vx, vy;
        int type;
        boolean onGround = false;
        int animTick = 0;
        int direction = 1;
        double homeX;
        int fleeTimer = 0;
        private static final double GRAVITY = 0.6;
        public static final int W = 28, H = 24;

        PeacefulMob(double x, double y, int type) { this.x = x; this.y = y; this.type = type; this.homeX = x; }

        void update(Player player, World world) {
            animTick++;
            double dx = player.x - x;
            double dist2 = dx * dx + (player.y - y) * (player.y - y);
            if (dist2 < (5 * TILE) * (5 * TILE)) { direction = dx > 0 ? -1 : 1; vx += direction * 0.4; fleeTimer = 60; }
            else if (fleeTimer > 0) fleeTimer--;
            else {
                if (animTick % 120 == 0) vx = (new Random().nextBoolean() ? 1 : -1) * 0.6;
                if (Math.abs(x - homeX) > TILE * 6) vx = Math.signum(homeX - x) * 0.6;
            }
            vx *= 0.9;
            if (Math.abs(vx) > 2.5) vx = Math.signum(vx) * 2.5;
            vy += GRAVITY;
            if (vy > 15) vy = 15;
            moveX(vx, world);
            moveY(vy, world);
        }

        private void moveX(double dx, World world) {
            x += dx;
            int top = (int) (y / TILE), bottom = (int) ((y + H - 1) / TILE);
            if (dx > 0) { int right = (int) ((x + W - 1) / TILE); for (int ty = top; ty <= bottom; ty++) if (world.isSolid(right, ty)) { x = right * TILE - W; vx = 0; break; } }
            else if (dx < 0) { int left = (int) (x / TILE); for (int ty = top; ty <= bottom; ty++) if (world.isSolid(left, ty)) { x = (left + 1) * TILE; vx = 0; break; } }
        }

        private void moveY(double dy, World world) {
            y += dy;
            int left = (int) (x / TILE), right = (int) ((x + W - 1) / TILE);
            if (dy > 0) { int bottom = (int) ((y + H - 1) / TILE); for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, bottom)) { y = bottom * TILE - H; vy = 0; onGround = true; break; } }
            else if (dy < 0) { int top = (int) (y / TILE); for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, top)) { y = (top + 1) * TILE; vy = 0; break; } }
        }

        void draw(Graphics2D g, int camX, int camY) {
            int px = (int) (x - camX), py = (int) (y - camY);
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(px + 2, py + H - 3, W - 4, 5);
            switch (type) {
                case 0:
                    g.setColor(new Color(240, 240, 240)); g.fillRect(px + 2, py + 6, W - 4, H - 10);
                    g.setColor(new Color(30, 30, 30));
                    g.fillRect(px + 5, py + 10, 4, 4); g.fillRect(px + W - 10, py + 14, 3, 3);
                    g.fillRect(px, py + 4, 6, 8);
                    g.setColor(new Color(200, 100, 100)); g.fillRect(px + 6, py + H - 5, 4, 4);
                    break;
                case 1:
                    g.setColor(Color.WHITE); g.fillOval(px + 4, py + 4, W - 8, H - 6);
                    g.setColor(new Color(255, 200, 60));
                    g.fillRect(px + W - 8, py + 6, 4, 4);
                    g.setColor(new Color(220, 30, 30)); g.fillRect(px + W - 6, py + 4, 3, 3);
                    g.setColor(new Color(255, 200, 60));
                    g.fillRect(px + 6, py + H - 4, 2, 4); g.fillRect(px + W - 10, py + H - 4, 2, 4);
                    break;
                case 2:
                    g.setColor(new Color(200, 180, 160)); g.fillOval(px + 4, py + 6, W - 8, H - 8);
                    g.fillRect(px + 6, py, 3, 8); g.fillRect(px + 12, py, 3, 8);
                    g.setColor(Color.WHITE); g.fillRect(px + 8, py + 12, 2, 2); g.fillRect(px + 14, py + 12, 2, 2);
                    break;
                case 3:
                    g.setColor(new Color(240, 240, 230)); g.fillOval(px, py + 4, W, H - 6);
                    g.setColor(new Color(200, 180, 160)); g.fillRect(px + W - 8, py + 8, 6, 6);
                    g.setColor(Color.BLACK); g.fillRect(px + W - 4, py + 10, 2, 2);
                    break;
            }
        }
    }

    // ==================== VILLAGER ====================
    static class Villager {
        double x, y, vx, vy;
        int type;
        int direction = 1;
        int animTick = 0;
        boolean onGround = false;
        double homeX;
        private static final double GRAVITY = 0.6;
        int[][] trades;
        int tradeRefreshTimer = 0;
        String profession;

        Villager(double x, double y, int type) { this.x = x; this.y = y; this.type = type; this.homeX = x; initTrades(); }

        private void initTrades() {
            switch (type) {
                case 0: profession = "Фермер";
                    trades = new int[][]{
                        {World.COIN_GOLD, 1, World.BREAD, 2}, {World.COIN_GOLD, 1, World.APPLE, 3},
                        {World.COIN_GOLD, 2, World.MEAT_COOKED, 2}, {World.COIN_GOLD, 2, World.CARROT, 4},
                        {World.COIN_GOLD, 3, World.MUSHROOM_SOUP, 1}, {World.COIN_GOLD, 10, World.GOLDEN_APPLE, 1},
                        {World.WHEAT_ITEM, 3, World.COIN_GOLD, 1}, {World.MEAT_RAW, 2, World.COIN_GOLD, 1},
                    }; break;
                case 1: profession = "Кузнец";
                    trades = new int[][]{
                        {World.COIN_GOLD, 5, World.IRON_INGOT, 1}, {World.COIN_GOLD, 12, World.GOLD_INGOT, 1},
                        {World.IRON_INGOT, 3, World.PICK_IRON, 1}, {World.IRON_INGOT, 3, World.SWORD_IRON, 1},
                        {World.IRON_INGOT, 3, World.AXE_IRON, 1}, {World.GOLD_INGOT, 2, World.COIN_GOLD, 15},
                        {World.COIN_GOLD, 8, World.FURNACE, 1},
                    }; break;
                case 2: profession = "Антиквар";
                    trades = new int[][]{
                        {World.COIN_GOLD, 25, World.DIAMOND_GEM, 1}, {World.COIN_GOLD, 10, World.LANTERN, 2},
                        {World.COIN_GOLD, 8, World.TORCH, 16}, {World.COIN_GOLD, 15, World.CHEST, 1},
                        {World.DIAMOND_GEM, 1, World.COIN_GOLD, 30}, {World.GOLD_INGOT, 2, World.COIN_GOLD, 15},
                    }; break;
                case 3: profession = "Оружейник";
                    trades = new int[][]{
                        {World.COIN_GOLD, 20, World.SWORD_DIAMOND, 1}, {World.COIN_GOLD, 6, World.SWORD_STONE, 1},
                        {World.GOLD_INGOT, 3, World.SWORD_IRON, 1}, {World.COIN_GOLD, 30, World.BED, 1},
                    }; break;
                case 4: profession = "Алхимик";
                    trades = new int[][]{
                        {World.COIN_GOLD, 5, World.POTION_HEAL, 1}, {World.COIN_GOLD, 8, World.POTION_SPEED, 1},
                        {World.COIN_GOLD, 10, World.POTION_STRENGTH, 1}, {World.COIN_GOLD, 12, World.POTION_REGEN, 1},
                        {World.COIN_GOLD, 15, World.POTION_FIRE, 1}, {World.COIN_GOLD, 3, World.FLASK, 4},
                        {World.MUSHROOM, 5, World.COIN_GOLD, 2}, {World.COIN_GOLD, 20, World.BREWING_STAND, 1},
                    }; break;
                case 5: profession = "Плотник";
                    trades = new int[][]{
                        {World.COIN_GOLD, 1, World.PLANK, 8}, {World.COIN_GOLD, 2, World.WOOD, 16},
                        {World.PLANK, 8, World.COIN_GOLD, 1}, {World.COIN_GOLD, 4, World.WORKBENCH, 1},
                        {World.COIN_GOLD, 3, World.LADDER, 8}, {World.COIN_GOLD, 6, World.BOOKSHELF, 1},
                    }; break;
                case 6: profession = "Шахтёр";
                    trades = new int[][]{
                        {World.COIN_GOLD, 3, World.COAL, 8}, {World.COIN_GOLD, 8, World.IRON, 4},
                        {World.COIN_GOLD, 20, World.GOLD, 4}, {World.COIN_GOLD, 50, World.DIAMOND, 4},
                        {World.COIN_GOLD, 4, World.PICK_STONE, 1}, {World.COIN_GOLD, 10, World.PICK_IRON, 1},
                    }; break;
                case 7: profession = "Рыбак";
                    trades = new int[][]{
                        {World.COIN_GOLD, 2, World.SAND, 16}, {World.COIN_GOLD, 1, World.DIRT, 10},
                        {World.COIN_GOLD, 6, World.ICE, 4}, {World.SAND, 16, World.COIN_GOLD, 1},
                    }; break;
                case 8: profession = "Строитель";
                    trades = new int[][]{
                        {World.COIN_GOLD, 1, World.BG_DIRT, 16}, {World.COIN_GOLD, 1, World.BG_STONE, 16},
                        {World.COIN_GOLD, 2, World.BG_WOOD, 16}, {World.COIN_GOLD, 3, World.BG_PLANK, 16},
                        {World.COIN_GOLD, 2, World.PLANK, 16}, {World.COIN_GOLD, 4, World.DOOR_CLOSED, 1},
                    }; break;
                case 9: profession = "Ювелир";
                    trades = new int[][]{
                        {World.COIN_GOLD, 30, World.DIAMOND_GEM, 1}, {World.COIN_GOLD, 12, World.GOLD_INGOT, 1},
                        {World.DIAMOND_GEM, 1, World.COIN_GOLD, 40}, {World.GOLD_INGOT, 1, World.COIN_GOLD, 18},
                    }; break;
                case 10: profession = "Стражник";
                    trades = new int[][]{
                        {World.COIN_GOLD, 15, World.SWORD_IRON, 1}, {World.COIN_GOLD, 40, World.SWORD_DIAMOND, 1},
                        {World.COIN_GOLD, 10, World.PICK_IRON, 1}, {World.COIN_GOLD, 5, World.IRON_INGOT, 1},
                    }; break;
                case 11: profession = "Повар";
                    trades = new int[][]{
                        {World.COIN_GOLD, 1, World.GRASS, 8}, {World.COIN_GOLD, 2, World.LEAVES, 16},
                        {World.COIN_GOLD, 3, World.COIN_GOLD, 1}, {World.COAL_ITEM, 4, World.COIN_GOLD, 2},
                    }; break;
                default: profession = "Житель";
                    trades = new int[][]{{World.COIN_GOLD, 1, World.DIRT, 5}}; break;
            }
        }

        void update(World world) {
            animTick++;
            vx *= 0.9;
            if (Math.abs(vx) < 0.3 && animTick % 60 == 0) vx = (new Random().nextBoolean() ? 1 : -1) * 0.8;
            if (Math.abs(x - homeX) > TILE * 5) vx = Math.signum(homeX - x) * 0.8;
            if (vx > 0.1) direction = 1;
            else if (vx < -0.1) direction = -1;
            vy += GRAVITY;
            if (vy > 15) vy = 15;
            moveX(vx, world);
            moveY(vy, world);
            if (tradeRefreshTimer > 0) tradeRefreshTimer--;
        }

        private void moveX(double dx, World world) {
            x += dx;
            int top = (int) (y / TILE), bottom = (int) ((y + Player.H - 1) / TILE);
            if (dx > 0) { int right = (int) ((x + Player.W - 1) / TILE); for (int ty = top; ty <= bottom; ty++) if (world.isSolid(right, ty)) { x = right * TILE - Player.W; vx = 0; break; } }
            else if (dx < 0) { int left = (int) (x / TILE); for (int ty = top; ty <= bottom; ty++) if (world.isSolid(left, ty)) { x = (left + 1) * TILE; vx = 0; break; } }
        }

        private void moveY(double dy, World world) {
            y += dy;
            int left = (int) (x / TILE), right = (int) ((x + Player.W - 1) / TILE);
            if (dy > 0) { int bottom = (int) ((y + Player.H - 1) / TILE); for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, bottom)) { y = bottom * TILE - Player.H; vy = 0; onGround = true; break; } }
            else if (dy < 0) { int top = (int) (y / TILE); for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, top)) { y = (top + 1) * TILE; vy = 0; break; } }
            if (dy >= 0) {
                int bottom = (int) ((y + Player.H) / TILE);
                boolean grounded = false;
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, bottom)) { grounded = true; break; }
                onGround = grounded;
            }
        }

        void draw(Graphics2D g, int camX, int camY) {
            int px = (int) (x - camX), py = (int) (y - camY);
            g.setColor(new Color(0, 0, 0, 70));
            g.fillOval(px + 2, py + Player.H - 3, Player.W - 4, 6);
            Color[] colors = {
                new Color(200, 140, 80), new Color(110, 150, 200), new Color(170, 110, 180),
                new Color(180, 80, 80), new Color(80, 180, 120), new Color(160, 120, 80),
                new Color(90, 90, 100), new Color(100, 160, 200), new Color(200, 180, 100),
                new Color(255, 215, 0), new Color(140, 40, 40), new Color(220, 200, 160)
            };
            Color body = type >= 0 && type < colors.length ? colors[type] : new Color(170, 110, 180);
            int legPhase = (animTick / 6) % 4;
            int legOff = legPhase == 0 ? 0 : (legPhase == 1 ? 2 : (legPhase == 2 ? 0 : -2));
            g.setColor(new Color(70, 45, 30));
            g.fillRect(px + 5, py + Player.H - 10 + Math.abs(legOff), 5, 10 - Math.abs(legOff));
            g.fillRect(px + Player.W - 10, py + Player.H - 10 + Math.abs(-legOff), 5, 10 - Math.abs(-legOff));
            g.setColor(body); g.fillRect(px + 3, py + 14, Player.W - 6, Player.H - 24);
            g.setColor(new Color(60, 40, 25)); g.fillRect(px + 3, py + Player.H - 16, Player.W - 6, 3);
            g.setColor(new Color(240, 200, 160));
            g.fillRect(px - 1, py + 17, 4, 11); g.fillRect(px + Player.W - 3, py + 17, 4, 11);
            g.setColor(new Color(245, 205, 165)); g.fillRect(px + 3, py, Player.W - 6, 14);
            if (type == 0 || type == 11) { g.setColor(new Color(230, 200, 90)); g.fillRect(px + 1, py - 2, Player.W - 2, 4); g.fillRect(px + 6, py - 6, Player.W - 12, 5); }
            else if (type == 1 || type == 3 || type == 10) { g.setColor(new Color(50, 35, 25)); g.fillRect(px + 3, py, Player.W - 6, 4); g.fillRect(px + 3, py, 3, 7); g.fillRect(px + Player.W - 6, py, 3, 7); }
            else if (type == 2 || type == 9) { g.setColor(new Color(200, 200, 210)); g.fillRect(px + 3, py, Player.W - 6, 4); g.fillRect(px + 3, py, 3, 8); g.fillRect(px + Player.W - 6, py, 3, 8); }
            else if (type == 5 || type == 8) { g.setColor(new Color(180, 140, 80)); g.fillRect(px + 2, py - 1, Player.W - 4, 5); }
            else if (type == 6) { g.setColor(new Color(255, 200, 60)); g.fillRect(px + 4, py - 3, Player.W - 8, 3); }
            else { g.setColor(new Color(80, 50, 30)); g.fillRect(px + 3, py, Player.W - 6, 4); }
            g.setColor(Color.WHITE);
            int eyeY = py + 6;
            if (direction == 1) {
                g.fillRect(px + 9, eyeY, 4, 4); g.fillRect(px + 15, eyeY, 4, 4);
                g.setColor(new Color(30, 40, 80)); g.fillRect(px + 11, eyeY + 1, 2, 3); g.fillRect(px + 17, eyeY + 1, 2, 3);
            } else {
                g.fillRect(px + 5, eyeY, 4, 4); g.fillRect(px + 11, eyeY, 4, 4);
                g.setColor(new Color(30, 40, 80)); g.fillRect(px + 5, eyeY + 1, 2, 3); g.fillRect(px + 11, eyeY + 1, 2, 3);
            }
            int bob = (int) (Math.sin(animTick * 0.08) * 2);
            g.setColor(new Color(255, 220, 80));
            g.fillOval(px + Player.W / 2 - 5, py - 14 + bob, 10, 10);
            g.setColor(new Color(190, 150, 20));
            g.drawOval(px + Player.W / 2 - 5, py - 14 + bob, 10, 10);
            g.setColor(new Color(255, 250, 180));
            g.fillRect(px + Player.W / 2 - 2, py - 11 + bob, 3, 3);
            g.setFont(PixelFont.font(10, Font.BOLD));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(profession);
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(px + Player.W / 2 - tw / 2 - 4, py - 26 + bob, tw + 8, 12);
            g.setColor(new Color(255, 230, 140));
            g.drawString(profession, px + Player.W / 2 - tw / 2, py - 17 + bob);
        }

        boolean canTrade(int tradeIndex, ItemStack[] inventory) {
            if (tradeIndex < 0 || tradeIndex >= trades.length) return false;
            int[] t = trades[tradeIndex];
            return countItems(inventory, t[0]) >= t[1];
        }

        private static int countItems(ItemStack[] inventory, int id) {
            int n = 0;
            for (ItemStack s : inventory) if (s != null && s.id == id) n += s.count;
            return n;
        }

        static boolean removeItems(ItemStack[] inventory, int id, int count) {
            int need = count;
            for (int i = 0; i < inventory.length && need > 0; i++) {
                if (inventory[i] != null && inventory[i].id == id) {
                    int take = Math.min(need, inventory[i].count);
                    inventory[i].count -= take;
                    need -= take;
                    if (inventory[i].count <= 0) inventory[i] = null;
                }
            }
            return need == 0;
        }
    }

    // ==================== REMOTE PLAYER ====================
    static class RemotePlayer {
        int id;
        double x, y;
        int facing = 1;
        String name;
        long lastUpdate = System.currentTimeMillis();
        double targetX, targetY;
        RemotePlayer(int id, double x, double y) {
            this.id = id; this.x = x; this.y = y;
            this.targetX = x; this.targetY = y;
            this.name = "Игрок #" + id;
        }
        void updatePosition(double nx, double ny, int facing) { this.targetX = nx; this.targetY = ny; this.facing = facing; this.lastUpdate = System.currentTimeMillis(); }
        void tick() { x += (targetX - x) * 0.25; y += (targetY - y) * 0.25; }
        void draw(Graphics2D g, int camX, int camY) {
            int px = (int) (x - camX), py = (int) (y - camY);
            g.setColor(new Color(0, 0, 0, 80)); g.fillRect(px + 2, py + Player.H - 3, Player.W - 4, 3);
            g.setColor(new Color(180, 80, 200)); g.fillRect(px + 3, py + 14, Player.W - 6, Player.H - 22);
            g.setColor(new Color(50, 30, 20)); g.fillRect(px + 3, py + Player.H - 14, Player.W - 6, 3);
            g.setColor(new Color(240, 200, 150)); g.fillRect(px - 1, py + 16, 4, 12); g.fillRect(px + Player.W - 3, py + 16, 4, 12);
            g.setColor(new Color(250, 210, 160)); g.fillRect(px + 3, py, Player.W - 6, 14);
            g.setColor(new Color(180, 80, 30)); g.fillRect(px + 3, py, Player.W - 6, 4);
            g.setColor(Color.WHITE);
            int eyeY = py + 6;
            if (facing == 1) { g.fillRect(px + 9, eyeY, 4, 4); g.fillRect(px + 15, eyeY, 4, 4); }
            else { g.fillRect(px + 5, eyeY, 4, 4); g.fillRect(px + 11, eyeY, 4, 4); }
            g.setFont(PixelFont.font(11, Font.BOLD));
            g.setColor(new Color(0, 0, 0, 180));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(name);
            g.fillRect(px + Player.W / 2 - tw / 2 - 3, py - 18, tw + 6, 14);
            g.setColor(new Color(255, 200, 255));
            g.drawString(name, px + Player.W / 2 - tw / 2, py - 8);
        }
    }

    // ==================== MULTIPLAYER MANAGER ====================
    static class MultiplayerManager {
        private final TerrariaMini game;
        private ServerSocket serverSocket;
        private Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private volatile boolean host = false;
        private volatile boolean running = false;
        private final Map<Integer, Socket> clients = new ConcurrentHashMap<>();
        private final Map<Integer, ObjectOutputStream> clientOuts = new ConcurrentHashMap<>();
        private final Map<Integer, ObjectInputStream> clientIns = new ConcurrentHashMap<>();
        private int nextClientId = 1;

        MultiplayerManager(TerrariaMini game) { this.game = game; }
        boolean isHost() { return host; }

        void startHost(int port) throws IOException {
            host = true; running = true;
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            new Thread(() -> {
                while (running) {
                    try {
                        Socket s = serverSocket.accept();
                        s.setTcpNoDelay(true);
                        int id = nextClientId++;
                        clients.put(id, s);
                        ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream());
                        oos.flush();
                        clientOuts.put(id, oos);
                        new Thread(() -> initClientInput(id, s), "MP-InitIn-" + id).start();
                    } catch (Exception e) { if (running) e.printStackTrace(); }
                }
            }, "MP-Server").start();
        }

        private void initClientInput(int id, Socket s) {
            try {
                ObjectInputStream ois = new ObjectInputStream(s.getInputStream());
                clientIns.put(id, ois);
                ObjectOutputStream oos = clientOuts.get(id);
                if (oos != null && game.getWorld() != null) {
                    WorldSyncPacket wsp = new WorldSyncPacket();
                    AssignIdPacket aid = new AssignIdPacket();
                    aid.playerId = id;
                    oos.writeObject(aid); oos.flush();
                    wsp.world = game.getWorld();
                    wsp.spawnX = game.getPlayer().x;
                    wsp.spawnY = game.getPlayer().y;
                    oos.writeObject(wsp);
                    oos.flush();

                    PlayerJoinPacket join = new PlayerJoinPacket();
                    join.playerId = id;
                    join.name = "Игрок #" + id;
                    oos.writeObject(join);
                    oos.flush();

                    for (Map.Entry<Integer, Socket> e : clients.entrySet()) {
                        if (e.getKey() == id) continue;
                        PlayerJoinPacket other = new PlayerJoinPacket();
                        other.playerId = e.getKey();
                        other.name = "Игрок #" + e.getKey();
                        oos.writeObject(other);
                        oos.flush();
                    }

                    PlayerJoinPacket broadcast = new PlayerJoinPacket();
                    broadcast.playerId = id;
                    broadcast.name = "Игрок #" + id;
                    broadcastToAll(broadcast, id);

                    final int cid = id;
                    SwingUtilities.invokeLater(() -> game.onRemoteChat("[MP] Игрок #" + cid + " подключился"));
                }
                while (running && !s.isClosed()) {
                    Object obj = ois.readObject();
                    processPacket(obj, id);
                }
            } catch (Exception e) {
                clients.remove(id); clientOuts.remove(id); clientIns.remove(id);
                final int cid = id;
                PlayerLeavePacket leave = new PlayerLeavePacket();
                leave.playerId = cid;
                broadcastToAll(leave, cid);
                SwingUtilities.invokeLater(() -> game.onRemoteChat("[MP] Игрок #" + cid + " отключился"));
            }
        }

        void startClient(String address, int port) {
            host = false; running = true;
            new Thread(() -> {
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(address, port), 5000);
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(15000);
                    out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    in = new ObjectInputStream(socket.getInputStream());
                    socket.setSoTimeout(0);
                    new Thread(this::clientLoop, "MP-Client").start();
                } catch (Exception e) {
                    final String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    SwingUtilities.invokeLater(() -> game.onConnectionFailed(msg));
                    close();
                }
            }, "MP-Connect").start();
        }

        private void clientLoop() {
            try {
                while (running && socket != null && !socket.isClosed()) {
                    Object obj = in.readObject();
                    processPacket(obj, -1);
                }
            } catch (Exception e) { if (running) SwingUtilities.invokeLater(() -> game.onRemoteChat("[MP] Соединение потеряно")); }
        }

        private void processPacket(Object obj, int fromClient) {
            if (obj instanceof WorldSyncPacket) {
                WorldSyncPacket wsp = (WorldSyncPacket) obj;
                SwingUtilities.invokeLater(() -> game.onWorldReceived(wsp.world, wsp.spawnX, wsp.spawnY));
                return;
            }
            if (obj instanceof PlayerUpdatePacket) {
                PlayerUpdatePacket p = (PlayerUpdatePacket) obj;
                SwingUtilities.invokeLater(() -> {
                    RemotePlayer rp = null;
                    for (RemotePlayer r : game.getRemotePlayers()) if (r.id == p.playerId) { rp = r; break; }
                    if (rp == null) { rp = new RemotePlayer(p.playerId, p.x, p.y); game.getRemotePlayers().add(rp); }
                    rp.updatePosition(p.x, p.y, p.facing);
                    if (host && fromClient != -1) broadcast(p, fromClient);
                });
            } else if (obj instanceof ChatPacket) {
                ChatPacket c = (ChatPacket) obj;
                SwingUtilities.invokeLater(() -> game.onRemoteChat(c.text));
                if (host && fromClient != -1) broadcast(c, fromClient);
            } else if (obj instanceof BlockChangePacket) {
                BlockChangePacket b = (BlockChangePacket) obj;
                SwingUtilities.invokeLater(() -> game.onRemoteBlockChange(b.x, b.y, b.block));
                if (host && fromClient != -1) broadcast(b, fromClient);
            } else if (obj instanceof BgChangePacket) {
                BgChangePacket b = (BgChangePacket) obj;
                SwingUtilities.invokeLater(() -> game.onRemoteBgChange(b.x, b.y, b.block));
                if (host && fromClient != -1) broadcast(b, fromClient);
            } else if (obj instanceof DamagePacket) {
                if (host && fromClient != -1) broadcast(obj, fromClient);
            }
            if (obj instanceof PlayerJoinPacket) {
                PlayerJoinPacket j = (PlayerJoinPacket) obj;
                SwingUtilities.invokeLater(() -> {
                    boolean exists = false;
                    for (RemotePlayer r : game.getRemotePlayers()) if (r.id == j.playerId) { exists = true; break; }
                    if (!exists) {
                        RemotePlayer rp = new RemotePlayer(j.playerId, game.getPlayer().x, game.getPlayer().y);
                        rp.name = j.name;
                        game.getRemotePlayers().add(rp);
                    }
                });
                return;
            }
            if (obj instanceof PlayerLeavePacket) {
                PlayerLeavePacket l = (PlayerLeavePacket) obj;
                SwingUtilities.invokeLater(() -> {
                    game.getRemotePlayers().removeIf(r -> r.id == l.playerId);
                });
                return;
            }
            if (obj instanceof AssignIdPacket) {
                AssignIdPacket a = (AssignIdPacket) obj;
                SwingUtilities.invokeLater(() -> game.myPlayerId = a.playerId);
                return;
            }
        }

        void tick(Player player) {
            if (!running || player == null) return;
            PlayerUpdatePacket p = new PlayerUpdatePacket();
            p.playerId = host ? 0 : game.myPlayerId;   // было: host ? 0 : 999
            p.x = player.x; p.y = player.y; p.facing = player.facing;
            send(p);
        }
        void updateRemotePlayers(List<RemotePlayer> rps) { for (RemotePlayer rp : rps) rp.tick(); }
        void sendChat(String text) { ChatPacket c = new ChatPacket(); c.text = text; send(c); }
        void sendBlockChange(int x, int y, int block) { BlockChangePacket b = new BlockChangePacket(); b.x = x; b.y = y; b.block = block; send(b); }
        void sendBgChange(int x, int y, int block) { BgChangePacket b = new BgChangePacket(); b.x = x; b.y = y; b.block = block; send(b); }
        void sendDamage(int targetId, int dmg) { DamagePacket d = new DamagePacket(); d.targetId = targetId; d.dmg = dmg; send(d); }

        private void send(Object packet) {
            if (!running) return;
            try {
                if (host) {
                    for (ObjectOutputStream oos : clientOuts.values()) {
                        try { synchronized (oos) { oos.writeObject(packet); oos.flush(); oos.reset(); } } catch (Exception ignored) {}
                    }
                } else if (out != null) {
                    synchronized (out) { out.writeObject(packet); out.flush(); out.reset(); }
                }
            } catch (Exception e) {}
        }

        private void broadcast(Object packet, int exceptClientId) {
            for (Map.Entry<Integer, ObjectOutputStream> e : clientOuts.entrySet()) {
                if (e.getKey() == exceptClientId) continue;
                try {
                    ObjectOutputStream oos = e.getValue();
                    synchronized (oos) { oos.writeObject(packet); oos.flush(); oos.reset(); }
                } catch (Exception ignored) {}
            }
        }

        void close() {
            running = false;
            try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            for (Socket s : clients.values()) try { s.close(); } catch (Exception ignored) {}
            clients.clear(); clientOuts.clear(); clientIns.clear();
        }

        private void broadcastToAll(Object packet, int exceptId) {
            for (Map.Entry<Integer, ObjectOutputStream> e : clientOuts.entrySet()) {
                if (e.getKey() == exceptId) continue;
                try {
                    ObjectOutputStream oos = e.getValue();
                    synchronized (oos) { oos.writeObject(packet); oos.flush(); oos.reset(); }
                } catch (Exception ignored) {}
            }
        }
    }

    // ==================== ПАКЕТЫ ====================
    static class WorldSyncPacket implements Serializable { private static final long serialVersionUID = 1L; World world; double spawnX, spawnY; }
    static class PlayerUpdatePacket implements Serializable { private static final long serialVersionUID = 1L; int playerId; double x, y; int facing; }
    static class ChatPacket implements Serializable { private static final long serialVersionUID = 1L; String text; }
    static class BlockChangePacket implements Serializable { private static final long serialVersionUID = 1L; int x, y, block; }
    static class BgChangePacket implements Serializable { private static final long serialVersionUID = 1L; int x, y, block; }
    static class DamagePacket implements Serializable { private static final long serialVersionUID = 1L; int targetId, dmg; }
    static class PlayerJoinPacket implements Serializable {
        private static final long serialVersionUID = 1L;
        int playerId;
        String name;
    }
    static class PlayerLeavePacket implements Serializable {
        private static final long serialVersionUID = 1L;
        int playerId;
    }
    static class AssignIdPacket implements Serializable {
        private static final long serialVersionUID = 1L;
        int playerId;
    }

    // ==================== ACHIEVEMENT / FLOATINGTEXT / CHATMESSAGE / WORLDSAVE ====================
    static class Achievement { String id, title, description; boolean unlocked = false; Achievement(String id, String title, String desc) { this.id = id; this.title = title; this.description = desc; } }
    static class FloatingText { String text; double x, y; Color color; int life = 70; FloatingText(String t, double x, double y, Color c) { this.text = t; this.x = x; this.y = y; this.color = c; } }
    static class ChatMessage { String text; Color color; long time; ChatMessage(String t, Color c, long time) { this.text = t; this.color = c; this.time = time; } }
    static class WorldSave { File file; String name; long size; String dateStr; int sizeW, sizeH; File folder; }

    // ==================== DATAPACK ====================
    static class DataPack {
        String name;
        DataPackZip.Gamerules gamerules;
        File file;
        double treeDensity = 0.09;
        double oreDensity = 1.0;
        int caveCount = 18;
        int villageCount = 2;
        int mineCount = 2;
        int templeCount = 1;
        int ruinCount = 2;
        int mountainCount = 3;
        int riverCount = 2;
        boolean allowSnow = true;
        boolean allowDesert = true;
        boolean allowMountains = true;

        static DataPack load(File f) {
            DataPack dp = new DataPack();
            dp.file = f;
            dp.name = f.getName().replace(".json", "");
            try {
                String content = new String(java.nio.file.Files.readAllBytes(f.toPath()));
                dp.treeDensity = parseDouble(content, "treeDensity", dp.treeDensity);
                dp.oreDensity = parseDouble(content, "oreDensity", dp.oreDensity);
                dp.caveCount = (int) parseDouble(content, "caveCount", dp.caveCount);
                dp.villageCount = (int) parseDouble(content, "villageCount", dp.villageCount);
                dp.mineCount = (int) parseDouble(content, "mineCount", dp.mineCount);
                dp.templeCount = (int) parseDouble(content, "templeCount", dp.templeCount);
                dp.ruinCount = (int) parseDouble(content, "ruinCount", dp.ruinCount);
                dp.mountainCount = (int) parseDouble(content, "mountainCount", dp.mountainCount);
                dp.riverCount = (int) parseDouble(content, "riverCount", dp.riverCount);
                dp.allowSnow = parseBool(content, "allowSnow", dp.allowSnow);
                dp.allowDesert = parseBool(content, "allowDesert", dp.allowDesert);
                dp.allowMountains = parseBool(content, "allowMountains", dp.allowMountains);
            } catch (Exception e) { e.printStackTrace(); }
            return dp;
        }

        private static double parseDouble(String s, String key, double def) {
            try {
                int i = s.indexOf("\"" + key + "\"");
                if (i < 0) return def;
                int colon = s.indexOf(':', i);
                int end = s.indexOf(',', colon);
                if (end < 0) end = s.indexOf('}', colon);
                return Double.parseDouble(s.substring(colon + 1, end).trim());
            } catch (Exception e) { return def; }
        }

        private static boolean parseBool(String s, String key, boolean def) {
            try {
                int i = s.indexOf("\"" + key + "\"");
                if (i < 0) return def;
                int colon = s.indexOf(':', i);
                int end = s.indexOf(',', colon);
                if (end < 0) end = s.indexOf('}', colon);
                return Boolean.parseBoolean(s.substring(colon + 1, end).trim());
            } catch (Exception e) { return def; }
        }

        static void saveDefault(File f) {
            try {
                f.getParentFile().mkdirs();
                String json = "{\n"
                    + "  \"treeDensity\": 0.09,\n  \"oreDensity\": 1.0,\n  \"caveCount\": 18,\n"
                    + "  \"villageCount\": 2,\n  \"mineCount\": 2,\n  \"templeCount\": 1,\n"
                    + "  \"ruinCount\": 2,\n  \"mountainCount\": 3,\n  \"riverCount\": 2,\n"
                    + "  \"allowSnow\": true,\n  \"allowDesert\": true,\n  \"allowMountains\": true\n}\n";
                java.nio.file.Files.write(f.toPath(), json.getBytes());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // ==================== SOUND ====================
    static class Sound {
        public static final int CLICK = 0, MINE = 1, HIT = 2, HURT = 3, TRADE = 4, ACHIEVEMENT = 5, DEATH = 6, START = 7;
        private static boolean enabled = true;
        private static Map<Integer, byte[]> cache = new HashMap<>();
        static void init() {
            try {
                AudioFormat fmt = new AudioFormat(44100, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                if (!AudioSystem.isLineSupported(info)) enabled = false;
            } catch (Exception e) { enabled = false; }
        }
        public static void play(final int soundId) {
            if (!enabled) return;
            new Thread(() -> {
                try {
                    byte[] data = cache.get(soundId);
                    if (data == null) { data = generate(soundId); cache.put(soundId, data); }
                    AudioFormat fmt = new AudioFormat(44100, 16, 1, true, false);
                    SourceDataLine line = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt));
                    line.open(fmt); line.start();
                    line.write(data, 0, data.length);
                    line.drain(); line.close();
                } catch (Exception ignored) {}
            }).start();
        }
        private static byte[] generate(int soundId) {
            int sampleRate = 44100;
            double duration, freq; int waveform;
            switch (soundId) {
                case CLICK: duration = 0.04; freq = 800; waveform = 0; break;
                case MINE: duration = 0.08; freq = 200; waveform = 1; break;
                case HIT: duration = 0.06; freq = 150; waveform = 1; break;
                case HURT: duration = 0.20; freq = 100; waveform = 2; break;
                case TRADE: duration = 0.30; freq = 660; waveform = 0; break;
                case ACHIEVEMENT: duration = 0.50; freq = 880; waveform = 0; break;
                case DEATH: duration = 0.80; freq = 80; waveform = 2; break;
                case START: duration = 0.40; freq = 440; waveform = 0; break;
                default: duration = 0.1; freq = 440; waveform = 0; break;
            }
            int n = (int) (sampleRate * duration);
            byte[] buf = new byte[n * 2];
            for (int i = 0; i < n; i++) {
                double t = i / (double) sampleRate;
                double env = Math.exp(-4 * t / duration);
                double v;
                if (waveform == 0) v = Math.sin(2 * Math.PI * freq * t);
                else if (waveform == 1) v = (Math.random() * 2 - 1);
                else { double f = freq * (1 - t * 1.5); v = Math.signum(Math.sin(2 * Math.PI * f * t)); }
                short s = (short) (v * env * 8000);
                buf[i * 2] = (byte) (s & 0xFF);
                buf[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }
            return buf;
        }
    }

    // ==================== MUSIC ====================
    static class Music {
        public static final int TRACK_MENU  = 0;
        public static final int TRACK_DAY   = 1;
        public static final int TRACK_NIGHT = 2;
        public static final int TRACK_BOSS  = 3;
        public static final int TRACK_HELL  = 4;

        private static volatile boolean playing = false;
        private static volatile boolean loop = false;
        private static volatile int currentTrack = -1;
        private static volatile float volume = 0.55f;
        private static Thread musicThread;
        private static SourceDataLine currentLine;

        public static void playMenuMusic() { play(TRACK_MENU); }

        public static synchronized void play(int track) {
            if (playing && currentTrack == track) return;
            stop();
            playing = true; loop = true; currentTrack = track;
            musicThread = new Thread(() -> {
                try {
                    AudioFormat fmt = new AudioFormat(44100, 16, 1, true, false);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                    if (!AudioSystem.isLineSupported(info)) { playing = false; return; }
                    byte[] melody = generate(track);
                    while (loop && !Thread.currentThread().isInterrupted()) {
                        currentLine = (SourceDataLine) AudioSystem.getLine(info);
                        currentLine.open(fmt); currentLine.start();
                        int chunk = 4096;
                        for (int i = 0; i < melody.length && loop; i += chunk) {
                            int len = Math.min(chunk, melody.length - i);
                            currentLine.write(melody, i, len);
                        }
                        currentLine.drain(); currentLine.stop(); currentLine.close();
                        currentLine = null;
                    }
                } catch (Exception ignored) {}
                playing = false;
            }, "Music-Thread");
            musicThread.setDaemon(true);
            musicThread.start();
        }

        public static synchronized void stop() {
            loop = false;
            if (currentLine != null) {
                try { currentLine.stop(); } catch (Exception ignored) {}
                try { currentLine.flush(); } catch (Exception ignored) {}
            }
            if (musicThread != null) { musicThread.interrupt(); musicThread = null; }
            playing = false; currentTrack = -1;
        }

        public static void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }

        // ==================== ГЕНЕРАЦИЯ ====================
        private static byte[] generate(int track) {
            final int SR = 44100;
            double[][] chords;
            double[] melody;
            double bpm;
            switch (track) {
                case TRACK_NIGHT:
                    chords = new double[][]{
                        {220.00, 261.63, 329.63}, {174.61, 220.00, 261.63},
                        {261.63, 329.63, 392.00}, {196.00, 246.94, 293.66}
                    };
                    melody = new double[]{
                        440, 523.25, 587.33, 523.25, 440, 392, 349.23, 392,
                        523.25, 659.25, 587.33, 523.25, 440, 392, 349.23, 329.63,
                        440, 523.25, 587.33, 659.25, 587.33, 523.25, 440, 392,
                        349.23, 392, 440, 392, 349.23, 329.63, 293.66, 261.63
                    };
                    bpm = 70; break;
                case TRACK_BOSS:
                    chords = new double[][]{
                        {146.83, 174.61, 220.00}, {174.61, 220.00, 261.63},
                        {130.81, 164.81, 196.00}, {146.83, 174.61, 220.00}
                    };
                    melody = new double[]{
                        293.66, 349.23, 440, 349.23, 293.66, 261.63, 220, 261.63,
                        349.23, 440, 523.25, 440, 349.23, 293.66, 261.63, 220,
                        293.66, 349.23, 440, 523.25, 440, 349.23, 293.66, 261.63,
                        220, 261.63, 293.66, 261.63, 220, 196, 174.61, 164.81
                    };
                    bpm = 140; break;
                case TRACK_HELL:
                    chords = new double[][]{
                        {110.00, 130.81, 155.56}, {116.54, 138.59, 164.81},
                        {103.83, 123.47, 146.83}, {98.00, 116.54, 138.59}
                    };
                    melody = new double[]{
                        220, 233.08, 246.94, 233.08, 220, 207.65, 196, 207.65,
                        233.08, 246.94, 261.63, 246.94, 233.08, 220, 207.65, 196,
                        220, 233.08, 246.94, 261.63, 246.94, 233.08, 220, 207.65,
                        196, 207.65, 220, 207.65, 196, 185, 174.61, 164.81
                    };
                    bpm = 100; break;
                case TRACK_DAY:
                    chords = new double[][]{
                        {261.63, 329.63, 392.00}, {196.00, 246.94, 293.66},
                        {220.00, 261.63, 329.63}, {174.61, 220.00, 261.63}
                    };
                    melody = new double[]{
                        523.25, 587.33, 659.25, 587.33, 523.25, 440, 392, 440,
                        587.33, 659.25, 783.99, 659.25, 587.33, 523.25, 440, 392,
                        523.25, 659.25, 783.99, 659.25, 523.25, 440, 392, 440,
                        392, 440, 523.25, 440, 392, 349.23, 329.63, 293.66
                    };
                    bpm = 110; break;
                default: // TRACK_MENU
                    chords = new double[][]{
                        {261.63, 329.63, 392.00}, {196.00, 246.94, 293.66},
                        {220.00, 261.63, 329.63}, {174.61, 220.00, 261.63}
                    };
                    melody = new double[]{
                        523.25, 587.33, 659.25, 587.33, 523.25, 440, 392, 440,
                        659.25, 783.99, 880.00, 783.99, 659.25, 523.25, 440, 523.25,
                        523.25, 587.33, 659.25, 783.99, 659.25, 587.33, 523.25, 440,
                        440, 523.25, 587.33, 523.25, 440, 392, 349.23, 329.63
                    };
                    bpm = 90; break;
            }

            double beat = 60.0 / bpm;
            double chordDur = beat * 4;
            double noteDur = chordDur / 8;
            int chordSamples = (int) (SR * chordDur);
            int noteSamples = (int) (SR * noteDur);
            int total = chordSamples * chords.length;
            float[] mix = new float[total];

            // pad + арпеджио
            for (int c = 0; c < chords.length; c++) {
                int base = c * chordSamples;
                for (int n = 0; n < chords[c].length; n++) {
                    double f = chords[c][n];
                    double amp = 0.08 / (n + 1);
                    for (int i = 0; i < chordSamples; i++) {
                        double t = i / (double) SR;
                        double env = Math.min(1.0, i / (SR * 0.6)) * Math.min(1.0, (chordSamples - i) / (SR * 0.8));
                        double trem = 0.85 + 0.15 * Math.sin(2 * Math.PI * 2.0 * t);
                        double v = Math.sin(2 * Math.PI * f * t) + 0.2 * Math.sin(2 * Math.PI * 2 * f * t);
                        mix[base + i] += (float) (v * amp * env * trem);
                    }
                }
                int[] arp = {0, 1, 2, 1};
                for (int a = 0; a < 4; a++) {
                    double f = chords[c][arp[a]];
                    int start = base + a * (chordSamples / 4);
                    int len = chordSamples / 4;
                    for (int i = 0; i < len; i++) {
                        double t = i / (double) SR;
                        double env = Math.exp(-3.0 * i / (double) len);
                        double v = Math.sin(2 * Math.PI * f * t) + 0.3 * Math.sin(2 * Math.PI * 2 * f * t);
                        mix[start + i] += (float) (v * 0.07 * env);
                    }
                }
            }

            // мелодия-колокольчик
            for (int m = 0; m < melody.length; m++) {
                double f = melody[m];
                int c = m / 8, idx = m % 8;
                int base = c * chordSamples + idx * noteSamples;
                for (int i = 0; i < noteSamples && base + i < total; i++) {
                    double t = i / (double) SR;
                    double attack = Math.min(1.0, i / (SR * 0.005));
                    double decay = Math.exp(-6.0 * t);
                    double vib = 1.0 + 0.004 * Math.sin(2 * Math.PI * 5 * t);
                    double v = Math.sin(2 * Math.PI * f * vib * t)
                            + 0.35 * Math.sin(2 * Math.PI * 2 * f * vib * t)
                            + 0.12 * Math.sin(2 * Math.PI * 3 * f * vib * t);
                    mix[base + i] += (float) (v * 0.10 * attack * decay);
                }
            }

            // бас
            for (int c = 0; c < chords.length; c++) {
                double f = chords[c][0] * 0.5;
                int base = c * chordSamples;
                for (int i = 0; i < chordSamples; i++) {
                    double t = i / (double) SR;
                    double env = Math.min(1.0, i / (SR * 0.1)) * Math.min(1.0, (chordSamples - i) / (SR * 1.0));
                    double v = Math.sin(2 * Math.PI * f * t);
                    mix[base + i] += (float) (v * 0.13 * env);
                }
            }

            // хорус + реверб
            int chorusDelay = (int) (SR * 0.018);
            int rev1 = (int) (SR * 0.12);
            int rev2 = (int) (SR * 0.23);
            float[] wet = new float[total];
            float[] rev = new float[total];
            for (int i = 0; i < total; i++) {
                float v = mix[i];
                if (i >= chorusDelay) v += wet[i - chorusDelay] * 0.25f;
                wet[i] = v;
                float r = v;
                if (i >= rev1) r += rev[i - rev1] * 0.35f;
                if (i >= rev2) r += rev[i - rev2] * 0.20f;
                rev[i] = r;
            }

            byte[] out = new byte[total * 2];
            int fadeIn = (int) (SR * 1.5);
            int fadeOut = (int) (SR * 2.5);
            for (int i = 0; i < total; i++) {
                double s = rev[i];
                if (i < fadeIn) s *= i / (double) fadeIn;
                if (i > total - fadeOut) s *= (total - i) / (double) fadeOut;
                s = Math.tanh(s * 1.2) * volume;
                short sh = (short) (s * 32767 * 0.55);
                out[i * 2] = (byte) (sh & 0xFF);
                out[i * 2 + 1] = (byte) ((sh >> 8) & 0xFF);
            }
            return out;
        }
    }

    // ==================== TEXTURES ====================
    static class Textures {
        private static final Map<Integer, BufferedImage> cache = new HashMap<>();
        private static final int SIZE = TILE * 2;
        public static BufferedImage get(int block) {
            BufferedImage img = cache.get(block);
            if (img != null) return img;
            img = generate(block);
            cache.put(block, img);
            return img;
        }
        private static BufferedImage generate(int block) {
            BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            int P = SIZE / 16;
            Random rnd = new Random(block * 1000L + 42);
            switch (block) {
                case World.DIRT: fillNoise(g, rnd, P, new Color(120, 80, 45), new Color(85, 55, 30), new Color(150, 105, 65), 40); break;
                case World.STONE: fillNoise(g, rnd, P, new Color(125, 125, 135), new Color(95, 95, 105), new Color(155, 155, 165), 30); break;
                case World.GRASS: drawGrass(g, rnd, P); break;
                case World.SAND: fillNoise(g, rnd, P, new Color(225, 205, 130), new Color(195, 175, 100), new Color(245, 225, 160), 25); break;
                case World.WOOD: drawWood(g, rnd, P); break;
                case World.LEAVES: drawLeaves(g, rnd, P); break;
                case World.COAL: drawOre(g, P, new Color(40, 40, 45), new Color(15, 15, 20), new Color(70, 70, 75)); break;
                case World.IRON: drawOre(g, P, new Color(180, 160, 140), new Color(140, 120, 100), new Color(220, 200, 180)); break;
                case World.GOLD: drawOre(g, P, new Color(230, 200, 70), new Color(180, 150, 30), new Color(255, 230, 120)); break;
                case World.DIAMOND: drawOre(g, P, new Color(130, 220, 230), new Color(80, 170, 190), new Color(200, 245, 255)); break;
                case World.CHEST: drawChest(g, P); break;
                case World.FURNACE: drawFurnace(g, P); break;
                case World.WORKBENCH: case World.CRAFT_TABLE: drawWorkbench(g, P); break;
                case World.TORCH: drawTorch(g, P); break;
                case World.PLANK: drawPlank(g, rnd, P); break;
                case World.COIN_GOLD: drawCoin(g, P); break;
                case World.IRON_INGOT: drawIngot(g, P, new Color(200, 200, 210), new Color(240, 240, 250), new Color(140, 140, 150)); break;
                case World.GOLD_INGOT: drawIngot(g, P, new Color(255, 215, 60), new Color(255, 240, 130), new Color(190, 150, 20)); break;
                case World.DIAMOND_GEM: drawGem(g, P); break;
                case World.COAL_ITEM: drawCoal(g, P); break;
                case World.SNOW: fillNoise(g, rnd, P, new Color(245, 250, 255), new Color(220, 230, 245), new Color(255, 255, 255), 20); break;
                case World.ICE: fillNoise(g, rnd, P, new Color(150, 200, 240), new Color(110, 170, 220), new Color(200, 230, 250), 25); break;
                case World.SWORD_WOOD: case World.SWORD_STONE: case World.SWORD_IRON: case World.SWORD_DIAMOND:
                    drawSword(g, P, getToolBase(), getToolHead(block)); break;
                case World.PICK_WOOD: case World.PICK_STONE: case World.PICK_IRON: case World.PICK_DIAMOND:
                    drawPickaxe(g, P, getToolBase(), getToolHead(block)); break;
                case World.AXE_WOOD: case World.AXE_STONE: case World.AXE_IRON: case World.AXE_DIAMOND:
                    drawAxe(g, P, getToolBase(), getToolHead(block)); break;
                case World.DOOR_CLOSED: drawDoor(g, P, true); break;
                case World.DOOR_OPEN: drawDoor(g, P, false); break;
                case World.BG_STONE: drawBgBlock(g, P, new Color(70, 70, 80, 180), new Color(45, 45, 55, 180)); break;
                case World.BG_DIRT: drawBgBlock(g, P, new Color(85, 60, 35, 180), new Color(55, 40, 25, 180)); break;
                case World.BG_WOOD: drawBgBlock(g, P, new Color(100, 70, 40, 180), new Color(70, 50, 30, 180)); break;
                case World.BG_PLANK: drawBgBlock(g, P, new Color(130, 95, 60, 180), new Color(90, 65, 40, 180)); break;
                case World.BOOKSHELF: drawBookshelf(g, P); break;
                case World.GLASS: drawGlass(g, P); break;
                case World.LANTERN: drawLantern(g, P); break;
                case World.LADDER: drawLadder(g, P); break;
                case World.BED: drawBed(g, P); break;
                case World.SIGN: drawSign(g, P); break;
                case World.HELLSTONE: fillNoise(g, rnd, P, new Color(180, 40, 30), new Color(120, 20, 10), new Color(230, 80, 40), 35); break;
                case World.ASH: fillNoise(g, rnd, P, new Color(60, 60, 60), new Color(40, 40, 40), new Color(90, 90, 90), 30); break;
                case World.OBSIDIAN: fillNoise(g, rnd, P, new Color(30, 20, 50), new Color(15, 10, 30), new Color(60, 40, 90), 30); break;
                case World.DEMONITE: drawOre(g, P, new Color(180, 50, 200), new Color(120, 20, 140), new Color(230, 120, 250)); break;
                case World.WOOL_ITEM: fillNoise(g, rnd, P, new Color(240, 240, 230), new Color(200, 200, 190), new Color(255, 255, 255), 20); break;
                case World.WHEAT: drawWheat(g, P); break;
                case World.WHEAT_ITEM: drawWheatItem(g, P); break;
                case World.BREAD: drawBread(g, P); break;
                case World.APPLE: drawApple(g, P); break;
                case World.CARROT: drawCarrot(g, P); break;
                case World.MEAT_RAW: drawMeat(g, P, new Color(200, 100, 100)); break;
                case World.MEAT_COOKED: drawMeat(g, P, new Color(140, 80, 40)); break;
                case World.MUSHROOM: drawMushroom(g, P); break;
                case World.MUSHROOM_SOUP: drawBowl(g, P); break;
                case World.GOLDEN_APPLE: drawApple(g, P); break;
                case World.POTION_HEAL: drawPotion(g, P, new Color(255, 60, 60)); break;
                case World.POTION_STRENGTH: drawPotion(g, P, new Color(255, 140, 60)); break;
                case World.POTION_SPEED: drawPotion(g, P, new Color(60, 180, 255)); break;
                case World.POTION_NIGHT: drawPotion(g, P, new Color(120, 60, 200)); break;
                case World.POTION_REGEN: drawPotion(g, P, new Color(120, 255, 120)); break;
                case World.POTION_FIRE: drawPotion(g, P, new Color(255, 100, 20)); break;
                case World.BREWING_STAND: drawWorkbench(g, P); break;
                case World.FLASK: drawFlask(g, P); break;
                case World.BUCKET_EMPTY: drawBucket(g, P, null); break;
                case World.BUCKET_WATER: drawBucket(g, P, new Color(50, 120, 220)); break;
                case World.BUCKET_LAVA: drawBucket(g, P, new Color(255, 80, 20)); break;
                default: fillNoise(g, rnd, P, new Color(125, 125, 135), new Color(95, 95, 105), new Color(155, 155, 165), 30); break;
            }
            g.dispose();
            return img;
        }
        private static Color getToolBase() { return new Color(100, 65, 35); }
        private static Color getToolHead(int id) {
            switch (id) {
                case World.SWORD_WOOD: case World.PICK_WOOD: case World.AXE_WOOD: return new Color(170, 120, 75);
                case World.SWORD_STONE: case World.PICK_STONE: case World.AXE_STONE: return new Color(140, 140, 150);
                case World.SWORD_IRON: case World.PICK_IRON: case World.AXE_IRON: return new Color(200, 200, 210);
                case World.SWORD_DIAMOND: case World.PICK_DIAMOND: case World.AXE_DIAMOND: return new Color(150, 240, 250);
                default: return new Color(170, 120, 75);
            }
        }
        private static void fillNoise(Graphics2D g, Random rnd, int P, Color base, Color dark, Color light, int noiseLevel) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int v = rnd.nextInt(100);
                Color c = base;
                if (v < noiseLevel / 2) c = dark; else if (v > 100 - noiseLevel / 2) c = light;
                g.setColor(c); g.fillRect(x * P, y * P, P, P);
            }
            g.setColor(dark.darker());
            for (int i = 0; i < 5; i++) g.fillRect(rnd.nextInt(16) * P, rnd.nextInt(16) * P, P, P);
            g.setColor(new Color(255, 255, 255, 35)); g.fillRect(0, 0, SIZE, 2); g.fillRect(0, 0, 2, SIZE);
            g.setColor(new Color(0, 0, 0, 55)); g.fillRect(0, SIZE - 2, SIZE, 2); g.fillRect(SIZE - 2, 0, 2, SIZE);
            g.setColor(new Color(0, 0, 0, 70)); g.drawRect(0, 0, SIZE - 1, SIZE - 1);
        }
        private static void drawGrass(Graphics2D g, Random rnd, int P) {
            fillNoise(g, rnd, P, new Color(120, 80, 45), new Color(85, 55, 30), new Color(150, 105, 65), 30);
            for (int x = 0; x < 16; x++) {
                int h = 2 + rnd.nextInt(4);
                for (int y = 0; y < h; y++) {
                    int shade = rnd.nextInt(3);
                    g.setColor(shade == 0 ? new Color(50, 140, 50) : shade == 1 ? new Color(80, 180, 80) : new Color(110, 210, 110));
                    g.fillRect(x * P, y * P, P, P);
                }
            }
        }
        private static void drawWood(Graphics2D g, Random rnd, int P) {
            for (int x = 0; x < 16; x++) {
                int baseVar = rnd.nextInt(20);
                Color c = new Color(140 + baseVar, 95 + baseVar / 2, 55 + baseVar / 2);
                for (int y = 0; y < 16; y++) { g.setColor(c); g.fillRect(x * P, y * P, P, P); }
            }
            g.setColor(new Color(80, 50, 25));
            for (int x = 0; x < 16; x += 4) g.fillRect(x * P, 0, 1, SIZE);
        }
        private static void drawLeaves(Graphics2D g, Random rnd, int P) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int v = rnd.nextInt(100);
                g.setColor(v < 25 ? new Color(30, 90, 30) : v < 60 ? new Color(50, 130, 50) : new Color(80, 170, 80));
                g.fillRect(x * P, y * P, P, P);
            }
        }
        private static void drawOre(Graphics2D g, int P, Color base, Color dark, Color light) {
            Random rnd = new Random(999);
            fillNoise(g, rnd, P, new Color(120, 120, 130), new Color(90, 90, 100), new Color(155, 155, 165), 25);
            Random r2 = new Random(555);
            int clusters = 5 + r2.nextInt(4);
            for (int i = 0; i < clusters; i++) {
                int cx = 1 + r2.nextInt(14), cy = 1 + r2.nextInt(14), size = 1 + r2.nextInt(2);
                for (int dy = -size; dy <= size; dy++) for (int dx = -size; dx <= size; dx++) {
                    if (dx * dx + dy * dy > size * size + 1) continue;
                    int x = cx + dx, y = cy + dy;
                    if (x < 0 || x > 15 || y < 0 || y > 15) continue;
                    g.setColor(base); g.fillRect(x * P, y * P, P, P);
                    if (dx == 0 && dy == 0) { g.setColor(light); g.fillRect(x * P + P / 3, y * P + P / 3, P / 3, P / 3); }
                }
            }
        }
        private static void drawChest(Graphics2D g, int P) {
            g.setColor(new Color(160, 100, 45)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(190, 130, 70)); g.fillRect(0, 0, SIZE, 3 * P);
            g.setColor(new Color(90, 55, 20));
            g.fillRect(0, 0, SIZE, P); g.fillRect(0, 3 * P, SIZE, P);
            g.fillRect(0, 0, P, SIZE); g.fillRect(SIZE - P, 0, P, SIZE); g.fillRect(0, SIZE - P, SIZE, P);
            g.setColor(new Color(255, 220, 80)); g.fillRect(7 * P, 5 * P, 2 * P, 3 * P);
        }
        private static void drawFurnace(Graphics2D g, int P) {
            Random rnd = new Random(777);
            fillNoise(g, rnd, P, new Color(90, 90, 95), new Color(60, 60, 65), new Color(120, 120, 130), 25);
            g.setColor(new Color(30, 30, 30)); g.fillRect(4 * P, 8 * P, 8 * P, 6 * P);
            g.setColor(new Color(255, 140, 40)); g.fillRect(5 * P, 11 * P, 6 * P, 3 * P);
            g.setColor(new Color(255, 220, 80)); g.fillRect(5 * P, 12 * P, 6 * P, 2 * P);
        }
        private static void drawWorkbench(Graphics2D g, int P) {
            g.setColor(new Color(180, 130, 80)); g.fillRect(0, 0, SIZE, 3 * P);
            g.setColor(new Color(140, 95, 55)); g.fillRect(0, 0, SIZE, P);
            g.setColor(new Color(150, 100, 55));
            g.fillRect(P, 3 * P, 3 * P, 13 * P); g.fillRect(SIZE - 4 * P, 3 * P, 3 * P, 13 * P);
            g.setColor(new Color(120, 80, 45)); g.fillRect(0, 8 * P, SIZE, 2 * P);
        }
        private static void drawTorch(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(120, 80, 45)); g.fillRect(7 * P, 6 * P, 2 * P, 10 * P);
            g.setColor(new Color(255, 140, 40)); g.fillRect(6 * P, 3 * P, 4 * P, 5 * P);
            g.setColor(new Color(255, 220, 80)); g.fillRect(7 * P, 2 * P, 2 * P, 4 * P);
        }
        private static void drawPlank(Graphics2D g, Random rnd, int P) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int v = rnd.nextInt(20);
                g.setColor(new Color(180 + v, 130 + v, 80 + v)); g.fillRect(x * P, y * P, P, P);
            }
            g.setColor(new Color(100, 65, 35));
            for (int y = 4; y < 16; y += 4) g.fillRect(0, y * P, SIZE, P / 2);
        }
        private static void drawCoin(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int dx = x - 7, dy = y - 7, d2 = dx * dx + dy * dy;
                if (d2 <= 30) { g.setColor(d2 <= 8 ? new Color(255, 240, 130) : d2 <= 20 ? new Color(255, 215, 60) : new Color(190, 150, 20)); g.fillRect(x * P, y * P, P, P); }
            }
        }
        private static void drawIngot(Graphics2D g, int P, Color base, Color light, Color dark) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(dark); g.fillRect(2 * P, 9 * P, 12 * P, 3 * P);
            g.setColor(base); g.fillRect(3 * P, 6 * P, 10 * P, 4 * P);
            g.setColor(light); g.fillRect(4 * P, 5 * P, 8 * P, 2 * P);
        }
        private static void drawGem(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            int[][] shape = {
                {0, 0, 0, 1, 2, 1, 0, 0, 0}, {0, 0, 1, 2, 3, 2, 1, 0, 0},
                {0, 1, 2, 3, 3, 3, 2, 1, 0}, {1, 2, 3, 3, 3, 3, 3, 2, 1},
                {0, 1, 2, 3, 3, 3, 2, 1, 0}, {0, 0, 1, 2, 3, 2, 1, 0, 0},
                {0, 0, 0, 1, 2, 1, 0, 0, 0}
            };
            for (int y = 0; y < shape.length; y++) for (int x = 0; x < shape[y].length; x++) {
                int v = shape[y][x];
                if (v == 0) continue;
                g.setColor(v == 1 ? new Color(80, 180, 200) : v == 2 ? new Color(150, 240, 250) : new Color(220, 255, 255));
                g.fillRect((x + 3) * P, (y + 4) * P, P, P);
            }
        }
        private static void drawCoal(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(40, 40, 45)); g.fillOval(3 * P, 4 * P, 10 * P, 9 * P);
        }
        private static void drawSword(Graphics2D g, int P, Color base, Color head) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(base); g.fillRect(2 * P, 12 * P, 6 * P, 3 * P);
            g.setColor(new Color(80, 50, 25)); g.fillRect(6 * P, 10 * P, 4 * P, 2 * P);
            g.setColor(head);
            for (int i = 0; i < 8; i++) { g.fillRect((7 - i / 2) * P, (9 - i) * P, P, P); g.fillRect((8 - i / 2) * P, (9 - i) * P, P, P); }
        }
        private static void drawPickaxe(Graphics2D g, int P, Color base, Color head) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(base);
            for (int i = 0; i < 10; i++) { g.fillRect((4 + i / 2) * P, (13 - i) * P, P, P); g.fillRect((5 + i / 2) * P, (13 - i) * P, P, P); }
            g.setColor(head);
            g.fillRect(5 * P, 2 * P, 2 * P, P); g.fillRect(7 * P, P, 3 * P, P); g.fillRect(10 * P, 2 * P, 2 * P, P);
        }
        private static void drawAxe(Graphics2D g, int P, Color base, Color head) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(base);
            for (int i = 0; i < 10; i++) { g.fillRect((4 + i / 2) * P, (13 - i) * P, P, P); g.fillRect((5 + i / 2) * P, (13 - i) * P, P, P); }
            g.setColor(head); g.fillRect(7 * P, 2 * P, 5 * P, 5 * P);
        }
        private static void drawDoor(Graphics2D g, int P, boolean closed) {
            if (closed) {
                g.setColor(new Color(150, 90, 40)); g.fillRect(0, 0, SIZE, SIZE);
                g.setColor(new Color(90, 55, 20)); g.drawRect(0, 0, SIZE - 1, SIZE - 1);
                g.drawRect(P, P, SIZE - 2 * P, SIZE - 2 * P);
                g.setColor(new Color(255, 220, 80)); g.fillRect(SIZE - 3 * P, SIZE / 2 - P, P, P);
            } else {
                g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
                g.setColor(new Color(150, 90, 40)); g.fillRect(0, 0, 3 * P, SIZE);
            }
        }
        private static void drawBgBlock(Graphics2D g, int P, Color base, Color dark) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                g.setColor((x + y) % 2 == 0 ? base : dark); g.fillRect(x * P, y * P, P, P);
            }
        }
        private static void drawBookshelf(Graphics2D g, int P) {
            g.setColor(new Color(140, 95, 55)); g.fillRect(0, 0, SIZE, SIZE);
            Color[] bookColors = { new Color(180, 60, 60), new Color(60, 120, 180), new Color(60, 160, 80), new Color(200, 160, 60), new Color(140, 80, 180) };
            for (int row = 0; row < 4; row++) for (int i = 0; i < 5; i++) {
                g.setColor(bookColors[(row * 5 + i) % bookColors.length]);
                g.fillRect((i * 3 + 1) * P, (row * 4 + 1) * P, 2 * P, 3 * P);
            }
        }
        private static void drawGlass(Graphics2D g, int P) {
            g.setColor(new Color(180, 220, 240, 80)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(255, 255, 255, 160)); g.fillRect(0, 0, SIZE, 2); g.fillRect(0, 0, 2, SIZE);
        }
        private static void drawLantern(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(120, 80, 45)); g.fillRect(7 * P, 0, 2 * P, 2 * P);
            g.setColor(new Color(255, 220, 80)); g.fillRect(5 * P, 3 * P, 6 * P, 8 * P);
            g.setColor(new Color(255, 250, 200)); g.fillRect(6 * P, 4 * P, 4 * P, 6 * P);
        }
        private static void drawLadder(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(140, 95, 55)); g.fillRect(3 * P, 0, 2 * P, SIZE); g.fillRect(11 * P, 0, 2 * P, SIZE);
            for (int i = 2; i < 16; i += 4) g.fillRect(3 * P, i * P, 10 * P, P);
        }
        private static void drawBed(Graphics2D g, int P) {
            g.setColor(new Color(140, 95, 55)); g.fillRect(0, 8 * P, SIZE, 6 * P);
            g.setColor(new Color(200, 60, 60)); g.fillRect(P, 6 * P, SIZE - 2 * P, 3 * P);
            g.setColor(new Color(240, 240, 240)); g.fillRect(3 * P, 3 * P, 4 * P, 4 * P);
        }
        private static void drawSign(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(140, 95, 55)); g.fillRect(7 * P, 8 * P, 2 * P, 8 * P);
            g.setColor(new Color(180, 130, 80)); g.fillRect(2 * P, 3 * P, 12 * P, 6 * P);
        }
        private static void drawWheat(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(200, 180, 60)); g.fillRect(7 * P, 2 * P, 2 * P, 14 * P);
            g.setColor(new Color(240, 220, 100));
            for (int i = 0; i < 4; i++) g.fillRect(7 * P, (2 + i * 3) * P, P, P);
        }
        private static void drawWheatItem(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(220, 190, 80)); g.fillRect(6 * P, 4 * P, 4 * P, 8 * P);
            g.setColor(new Color(240, 220, 120));
            for (int i = 0; i < 4; i++) g.fillRect(6 * P, (4 + i * 2) * P, 4 * P, P);
        }
        private static void drawBread(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(200, 140, 70)); g.fillOval(2 * P, 5 * P, 12 * P, 7 * P);
            g.setColor(new Color(230, 180, 100)); g.fillOval(4 * P, 6 * P, 8 * P, 4 * P);
        }
        private static void drawApple(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(220, 40, 40)); g.fillOval(3 * P, 4 * P, 10 * P, 10 * P);
            g.setColor(new Color(80, 50, 20)); g.fillRect(7 * P, P, P, 4 * P);
            g.setColor(new Color(50, 160, 50)); g.fillRect(8 * P, 2 * P, 3 * P, P);
        }
        private static void drawCarrot(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(255, 140, 40)); g.fillOval(5 * P, 5 * P, 6 * P, 9 * P);
            g.setColor(new Color(50, 160, 50)); g.fillRect(7 * P, P, P, 4 * P);
        }
        private static void drawMeat(Graphics2D g, int P, Color color) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(color); g.fillOval(3 * P, 4 * P, 10 * P, 8 * P);
            g.setColor(new Color(240, 240, 240)); g.fillRect(5 * P, 6 * P, 2 * P, 4 * P);
        }
        private static void drawMushroom(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(200, 60, 60)); g.fillOval(3 * P, 3 * P, 10 * P, 7 * P);
            g.setColor(new Color(240, 240, 240)); g.fillRect(7 * P, 9 * P, 2 * P, 5 * P);
            g.setColor(new Color(255, 200, 200)); g.fillRect(5 * P, 5 * P, P, P); g.fillRect(9 * P, 6 * P, P, P);
        }
        private static void drawBowl(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(180, 120, 60)); g.fillOval(2 * P, 5 * P, 12 * P, 8 * P);
            g.setColor(new Color(230, 180, 120)); g.fillOval(3 * P, 6 * P, 10 * P, 3 * P);
        }
        private static void drawPotion(Graphics2D g, int P, Color color) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(200, 200, 220)); g.fillRect(6 * P, P, 4 * P, 3 * P);
            g.setColor(color); g.fillOval(3 * P, 4 * P, 10 * P, 10 * P);
            g.setColor(new Color(255, 255, 255, 100)); g.fillRect(5 * P, 6 * P, 2 * P, 3 * P);
        }
        private static void drawFlask(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(200, 200, 220)); g.fillRect(6 * P, 2 * P, 4 * P, 12 * P);
            g.setColor(new Color(240, 240, 255, 120)); g.fillRect(7 * P, 4 * P, 2 * P, 8 * P);
        }
        private static void drawBucket(Graphics2D g, int P, Color liquid) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(120, 120, 130));
            g.fillRect(3 * P, 3 * P, 10 * P, 10 * P);
            g.setColor(new Color(60, 60, 70)); g.drawRect(3 * P, 3 * P, 10 * P, 10 * P);
            g.drawLine(P, 3 * P, 2 * P, 8 * P); g.drawLine(15 * P, 3 * P, 14 * P, 8 * P);
            if (liquid != null) { g.setColor(liquid); g.fillRect(4 * P, 5 * P, 8 * P, 6 * P); }
        }
    }

    // ==================== MOD LOADER ====================
    static class ModLoader {
        static class ModRecipe {
            int resultId, resultCount;
            int[] ingredientIds, ingredientCounts;
            boolean needsTable;
            String modName;
        }

        static final List<ModRecipe> loadedRecipes = new ArrayList<>();
        static final List<String> loadedMods = new ArrayList<>();

        static void loadAll() {
            loadedRecipes.clear();
            loadedMods.clear();
            File modsDir = new File("mods");
            modsDir.mkdirs();
            File[] files = modsDir.listFiles((d, n) -> n.endsWith(".json"));
            if (files == null) return;
            for (File f : files) {
                try { loadMod(f); } catch (Exception e) { System.out.println("Ошибка мода " + f.getName() + ": " + e.getMessage()); }
            }
            System.out.println("Загружено модов: " + loadedMods.size() + ", рецептов: " + loadedRecipes.size());
        }

        static void loadMod(File f) throws Exception {
            String content = new String(java.nio.file.Files.readAllBytes(f.toPath()));
            String modName = f.getName().replace(".json", "");
            int recipesStart = content.indexOf("\"recipes\"");
            if (recipesStart < 0) return;
            int arrStart = content.indexOf('[', recipesStart);
            int arrEnd = content.lastIndexOf(']');
            if (arrStart < 0 || arrEnd < 0) return;
            String arr = content.substring(arrStart + 1, arrEnd);
            String[] recipeStrings = arr.split("\\}\\s*,\\s*\\{");
            for (String rs : recipeStrings) {
                try {
                    rs = rs.replace("{", "").replace("}", "");
                    String resultStr = extractStr(rs, "result");
                    int resultCount = (int) extractNum(rs, "count", 1);
                    boolean needsTable = extractBool(rs, "needsTable", true);
                    int resultId = TerrariaMini.parseBlockIdPublic(resultStr);
                    if (resultId < 0) continue;
                    int ingStart = rs.indexOf("\"ingredients\"");
                    if (ingStart < 0) continue;
                    int ingObjStart = rs.indexOf('{', ingStart);
                    int ingObjEnd = rs.indexOf('}', ingObjStart);
                    if (ingObjStart < 0 || ingObjEnd < 0) continue;
                    String ingStr = rs.substring(ingObjStart + 1, ingObjEnd);
                    String[] ingPairs = ingStr.split(",");
                    List<Integer> ids = new ArrayList<>();
                    List<Integer> counts = new ArrayList<>();
                    for (String pair : ingPairs) {
                        String[] kv = pair.split(":");
                        if (kv.length != 2) continue;
                        String ingName = kv[0].replace("\"", "").trim();
                        int ingCount = Integer.parseInt(kv[1].trim());
                        int ingId = TerrariaMini.parseBlockIdPublic(ingName);
                        if (ingId >= 0) { ids.add(ingId); counts.add(ingCount); }
                    }
                    if (ids.isEmpty()) continue;
                    ModRecipe mr = new ModRecipe();
                    mr.resultId = resultId;
                    mr.resultCount = resultCount;
                    mr.needsTable = needsTable;
                    mr.modName = modName;
                    mr.ingredientIds = ids.stream().mapToInt(Integer::intValue).toArray();
                    mr.ingredientCounts = counts.stream().mapToInt(Integer::intValue).toArray();
                    loadedRecipes.add(mr);
                } catch (Exception ignored) {}
            }
            loadedMods.add(modName);
        }

        private static String extractStr(String s, String key) {
            int i = s.indexOf("\"" + key + "\"");
            if (i < 0) return null;
            int colon = s.indexOf(':', i);
            int start = s.indexOf('"', colon) + 1;
            int end = s.indexOf('"', start);
            return s.substring(start, end);
        }
        private static double extractNum(String s, String key, double def) {
            try {
                int i = s.indexOf("\"" + key + "\"");
                if (i < 0) return def;
                int colon = s.indexOf(':', i);
                int end = s.indexOf(',', colon);
                if (end < 0) end = s.length();
                return Double.parseDouble(s.substring(colon + 1, end).trim());
            } catch (Exception e) { return def; }
        }
        private static boolean extractBool(String s, String key, boolean def) {
            try {
                int i = s.indexOf("\"" + key + "\"");
                if (i < 0) return def;
                int colon = s.indexOf(':', i);
                int end = s.indexOf(',', colon);
                if (end < 0) end = s.length();
                return Boolean.parseBoolean(s.substring(colon + 1, end).trim());
            } catch (Exception e) { return def; }
        }
    }

    static class PlayerSave implements Serializable {
        double x, y, hp, maxHp, hunger;
        int satiety;
        int xp, xpLevel, xpToNext;
        int selectedSlot;
        ItemStack[] inventory;
        List<String> unlockedAchievements = new ArrayList<>();
        String name;
    }
    static class DataPackZip {
        static class LootEntry { int itemId, min, max; double chance; }
        static class Gamerules {
            boolean keepInventory = false;
            boolean pvp = false;
            int maxPlayers = 8;
            boolean spawnMonsters = true;
            double hungerRate = 1.0;
        }

        static DataPack load(File zipFile) throws IOException {
            DataPack dp = new DataPack();
            dp.name = zipFile.getName().replace(".zip", "");
            dp.gamerules = new Gamerules();
            try (java.util.zip.ZipInputStream zis =
                        new java.util.zip.ZipInputStream(new FileInputStream(zipFile))) {
                java.util.zip.ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    if (e.isDirectory()) continue;
                    String path = e.getName().replace('\\', '/');
                    if (!path.startsWith("data/terrariamini/")) continue;
                    String content = new String(zis.readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    if (path.startsWith("data/terrariamini/gamerules/")) parseGamerules(dp, content);
                    // commands / loots — сюда можно добавить парсеры
                }
            }
            return dp;
        }

        private static void parseGamerules(DataPack dp, String json) {
            dp.gamerules.keepInventory = bool(json, "keepInventory", false);
            dp.gamerules.pvp = bool(json, "pvp", false);
            dp.gamerules.maxPlayers = (int) num(json, "maxPlayers", 8);
            dp.gamerules.spawnMonsters = bool(json, "spawnMonsters", true);
            dp.gamerules.hungerRate = num(json, "hungerRate", 1.0);
        }

        static boolean bool(String s, String k, boolean def) {
            try {
                int i = s.indexOf("\"" + k + "\"");
                if (i < 0) return def;
                int c = s.indexOf(':', i);
                int e = s.indexOf(',', c);
                if (e < 0) e = s.indexOf('}', c);
                return Boolean.parseBoolean(s.substring(c + 1, e).trim());
            } catch (Exception ex) { return def; }
        }
        static double num(String s, String k, double def) {
            try {
                int i = s.indexOf("\"" + k + "\"");
                if (i < 0) return def;
                int c = s.indexOf(':', i);
                int e = s.indexOf(',', c);
                if (e < 0) e = s.indexOf('}', c);
                return Double.parseDouble(s.substring(c + 1, e).trim());
            } catch (Exception ex) { return def; }
        }
    }
}