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

    public static final int TILE = 32;
    public static final int VIEW_W = 30;
    public static final int VIEW_H = 20;
    public static final int SCREEN_W = TILE * VIEW_W;
    public static final int SCREEN_H = TILE * VIEW_H;
    public static final int INV_COLS = 9;
    public static final int INV_ROWS = 4;
    public static final int SLOT = 44;
    public static final int WORLD_W = 800;
    public static final int WORLD_H = 200;

    enum GameState { MENU, WORLD_SELECT, WORLD_CREATE, PLAYING, PAUSED, DEAD, ACHIEVEMENTS, SETTINGS, MULTIPLAYER, LOADING }
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

    // Торговля
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
        Sound.init();

        timer = new javax.swing.Timer(16, this);
        timer.start();
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
    }

    private void unlock(String id) {
        for (Achievement a : achievements) {
            if (a.id.equals(id) && !a.unlocked) {
                a.unlocked = true;
                if (player != null) {
                    floaters.add(new FloatingText("★ " + a.title, player.x, player.y - 50, new Color(255, 220, 100)));
                }
                addChatMessage("★ Достижение: " + a.title, new Color(255, 220, 100));
                Sound.play(Sound.ACHIEVEMENT);
            }
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == GameState.MENU) {
            Music.playMenuMusic();
        } else {
            Music.stop();
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
                if (b == World.WORKBENCH || b == World.CRAFT_TABLE) {
                    nearWorkbench = true;
                    break;
                }
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

        boolean night = isNight();
        if (night) {
            monsterSpawnCooldown--;
            if (monsterSpawnCooldown <= 0 && monsters.size() < 15) {
                spawnMonsterNearPlayer();
                monsterSpawnCooldown = 60 + new Random().nextInt(120);
            }
            if (gameTime > DAY_LENGTH / 2) unlock("night");
        }

        for (Iterator<FloatingText> it = floaters.iterator(); it.hasNext();) {
            FloatingText ft = it.next();
            ft.y -= 0.5;
            ft.life--;
            if (ft.life <= 0) it.remove();
        }

        if (mouseDown && !inventoryOpen && digX >= 0) {
            continueDigging();
        } else {
            digProgress = 0;
            digX = -1;
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
    }

    private void checkFallDamage() {
        if (!Modules.FALL_DAMAGE) return;
        if (player == null) return;
        if (!player.onGround) {
            if (!player.wasInAir) {
                player.fallStartY = player.y;
                player.wasInAir = true;
            }
            return;
        }
        if (player.wasInAir) {
            double fallDist = (player.fallStartY - player.y) / TILE;
            if (fallDist > 4.0) {
                int dmg = (int) ((fallDist - 4.0) * 8);
                if (dmg > 0 && !player.godMode) {
                    player.hp -= dmg;
                    player.hurtCooldown = 30;
                    floaters.add(new FloatingText("-" + dmg + " HP", player.x, player.y - 20, new Color(255, 80, 80)));
                    addChatMessage("Урон от падения: " + dmg, new Color(255, 100, 100));
                    Sound.play(Sound.HURT);
                }
            }
            player.wasInAir = false;
        }
    }

    private boolean isNight() {
        return (gameTime % DAY_LENGTH) / (double) DAY_LENGTH > 0.5;
    }

    private double dayProgress() {
        return (gameTime % DAY_LENGTH) / (double) DAY_LENGTH;
    }

    private void spawnMonsterNearPlayer() {
        Random rnd = new Random();
        int side = rnd.nextBoolean() ? 1 : -1;
        int dist = 15 + rnd.nextInt(10);
        double mx = player.x + side * dist * TILE;
        double my = player.y - rnd.nextInt(3) * TILE;
        int tx = (int) (mx / TILE);
        int ty = (int) (my / TILE);
        for (int i = 0; i < 20; i++) {
            if (ty + i < world.getHeight() && world.isSolid(tx, ty + i)) {
                my = (ty + i) * TILE - Monster.H;
                break;
            }
        }
        if (tx < 2 || tx > world.getWidth() - 2) return;
        monsters.add(new Monster(mx, my, rnd.nextInt(3)));
    }

    private void addToInventory(ItemStack stack) {
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] != null && inventory[i].id == stack.id && inventory[i].count < 999) {
                inventory[i].count += stack.count;
                return;
            }
        }
        for (int i = 0; i < inventory.length; i++) {
            if (inventory[i] == null) {
                inventory[i] = stack.copy();
                return;
            }
        }
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
        for (ItemStack s : inventory) {
            if (s != null && s.id == id) n += s.count;
        }
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
        if (block == World.DOOR_CLOSED || block == World.DOOR_OPEN) return;

        digX = wx;
        digY = wy;
        digProgress = 0;

        ItemStack held = inventory[selectedSlot];
        int hardness = ToolUtil.getBlockHardness(block);
        int toolPower = ToolUtil.getToolPower(held);
        int toolBonus = ToolUtil.getToolBonus(held, block);

        int baseTicks = hardness * 10;
        int power = toolPower + toolBonus;
        if (power < 1) power = 1;
        digRequired = Math.max(3, baseTicks / power);
        if (ToolUtil.isWrongTool(held, block)) digRequired *= 3;
    }

    private void continueDigging() {
        int block = world.getBlock(digX, digY);
        if (block == World.AIR) {
            digX = -1;
            digProgress = 0;
            return;
        }

        digProgress++;
        if (digProgress >= digRequired) {
            world.setBlock(digX, digY, World.AIR);
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
            digX = -1;
            digProgress = 0;
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
        if (icon != null) {
            g.drawImage(icon, x + 10, y + (h - 28) / 2, 28, 28, null);
            iconOffset = 30;
        }

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

    private void drawMenu(Graphics2D g) {
        drawPixelBackground(g);
        g.setFont(PixelFont.font(72, Font.BOLD));
        drawCenteredWithPixelShadow(g, "TerrariaMini", SCREEN_W / 2, 110, new Color(255, 220, 80));
        g.setFont(PixelFont.font(20, Font.BOLD));
        drawCenteredWithPixelShadow(g, " alpha 1.7.1 — Cave & Craft", SCREEN_W / 2, 145, new Color(180, 220, 255));

        String[] items = {"Одиночная игра", "Мультиплеер (LAN)", "Достижения", "Подсказка", "Выход"};
        BufferedImage[] icons = {Textures.get(World.GRASS), Textures.get(World.CHEST),
            Textures.get(World.DIAMOND_GEM), Textures.get(World.FURNACE), Textures.get(World.STONE)};
        int startY = 200;
        for (int i = 0; i < items.length; i++) {
            int y = startY + i * 70;
            int bx = SCREEN_W / 2 - 220;
            boolean hover = mouseY > y - 25 && mouseY < y + 30 && mouseX > bx && mouseX < bx + 440;
            drawPixelButtonWithIcon(g, items[i], bx, y - 25, 440, 55, hover, icons[i]);
        }

        g.setColor(new Color(200, 200, 200));
        g.setFont(PixelFont.font(12, Font.PLAIN));
        drawCentered(g, "A/D — движение · Space — прыжок · ЛКМ/ПКМ — копать/ставить · T — чат · F11 — fullscreen · E — инвентарь",
                SCREEN_W / 2, SCREEN_H - 30);
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

        drawPixelButton(g, "Создать [Enter]", SCREEN_W / 2 - 220, 380, 440, 55,
                mouseY > 380 && mouseY < 435 && mouseX > SCREEN_W / 2 - 220 && mouseX < SCREEN_W / 2 + 220);
        drawPixelButton(g, "Отмена [Esc]", SCREEN_W / 2 - 150, 460, 300, 50,
                mouseY > 460 && mouseY < 510 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150);
        g.setColor(new Color(180, 200, 240));
        g.setFont(PixelFont.font(14, Font.PLAIN));
        drawCentered(g, "Мир 800×200 блоков. Одинаковый сид = одинаковый мир.", SCREEN_W / 2, 570);
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

        drawPixelButton(g, "Создать сервер (хост)", SCREEN_W / 2 - 220, 350, 440, 55,
                mouseY > 350 && mouseY < 405 && mouseX > SCREEN_W / 2 - 220 && mouseX < SCREEN_W / 2 + 220);
        drawPixelButton(g, "Подключиться", SCREEN_W / 2 - 220, 420, 440, 55,
                mouseY > 420 && mouseY < 475 && mouseX > SCREEN_W / 2 - 220 && mouseX < SCREEN_W / 2 + 220);
        drawPixelButton(g, "Назад [Esc]", SCREEN_W / 2 - 150, 500, 300, 50,
                mouseY > 500 && mouseY < 550 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150);

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

        int cx = SCREEN_W / 2;
        int cy = SCREEN_H / 2 + 80;
        int dots = (int) (System.currentTimeMillis() / 300) % 4;
        for (int i = 0; i < 4; i++) {
            g.setColor(i <= dots ? Color.WHITE : new Color(80, 80, 100));
            g.fillRect(cx - 60 + i * 30, cy, 20, 20);
        }
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
            for (int i = 0; i < 60; i++) {
                int sx = rnd.nextInt(SCREEN_W);
                int sy = rnd.nextInt(SCREEN_H / 2);
                g.setColor(new Color(255, 255, 255, 180));
                g.fillRect(sx, sy, 2, 2);
            }
        }

        int sunX = (int) (SCREEN_W * t * 2);
        if (sunX > SCREEN_W) sunX = SCREEN_W * 2 - sunX;
        int sunY = (int) (SCREEN_H * 0.15 + Math.sin(t * Math.PI * 2) * 100);
        if (isNight()) {
            g.setColor(new Color(240, 240, 220));
            g.fillRect(sunX - 24, sunY, 48, 48);
            g.setColor(new Color(10, 10, 40));
            g.fillRect(sunX - 8, sunY, 32, 48);
        } else {
            g.setColor(new Color(255, 240, 130));
            g.fillRect(sunX - 24, sunY, 48, 48);
            g.setColor(new Color(255, 220, 80));
            g.drawRect(sunX - 24, sunY, 48, 48);
        }

        int startX = Math.max(0, camX / TILE);
        int startY = Math.max(0, camY / TILE);
        int endX = Math.min(world.getWidth() - 1, (camX + SCREEN_W) / TILE + 1);
        int endY = Math.min(world.getHeight() - 1, (camY + SCREEN_H) / TILE + 1);

        // Фон
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int bg = world.getBackground(x, y);
                if (bg == World.AIR) continue;
                int px = x * TILE - camX;
                int py = y * TILE - camY;
                if (Modules.SHOW_BACKGROUND) {
                    g.drawImage(Textures.get(bg), px, py, null);
                }
            }
        }

        // Блоки
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int b = world.getBlock(x, y);
                if (b == World.AIR) continue;
                int px = x * TILE - camX;
                int py = y * TILE - camY;
                g.drawImage(Textures.get(b), px, py, null);
            }
        }

        // === ИСПРАВЛЕННЫЕ ТЕНИ (ambient occlusion) ===
        for (int y = startY; y <= endY; y++) {
            for (int x = startX; x <= endX; x++) {
                int b = world.getBlock(x, y);
                if (b == World.AIR) continue;
                int px = x * TILE - camX;
                int py = y * TILE - camY;
                boolean airDown = world.getBlock(x, y + 1) == World.AIR;
                boolean airRight = world.getBlock(x + 1, y) == World.AIR;
                boolean airLeft = world.getBlock(x - 1, y) == World.AIR;
                boolean airUp = world.getBlock(x, y - 1) == World.AIR;
                if (airDown) {
                    for (int i = 0; i < 5; i++) {
                        int a = 55 - i * 10;
                        if (a <= 0) break;
                        g.setColor(new Color(0, 0, 0, a));
                        g.fillRect(px, py + TILE - 5 + i, TILE, 1);
                    }
                }
                if (airRight) {
                    for (int i = 0; i < 4; i++) {
                        int a = 45 - i * 10;
                        if (a <= 0) break;
                        g.setColor(new Color(0, 0, 0, a));
                        g.fillRect(px + TILE - 4 + i, py, 1, TILE);
                    }
                }
                if (airLeft) {
                    g.setColor(new Color(0, 0, 0, 30));
                    g.fillRect(px, py, 3, TILE);
                }
                if (airUp) {
                    g.setColor(new Color(0, 0, 0, 25));
                    g.fillRect(px, py, TILE, 3);
                }
            }
        }

        for (Villager v : villagers) v.draw(g, camX, camY);
        for (RemotePlayer rp : remotePlayers) rp.draw(g, camX, camY);
        for (Monster m : monsters) m.draw(g, camX, camY);
        player.draw(g, camX, camY);

        if (digX >= 0 && digProgress > 0) {
            int px = digX * TILE - camX;
            int py = digY * TILE - camY;
            g.setColor(new Color(0, 0, 0, 180));
            g.fillRect(px + 4, py - 10, TILE - 8, 6);
            g.setColor(new Color(200, 255, 100));
            double ratio = Math.min(1.0, digProgress / (double) digRequired);
            int w = (int) ((TILE - 8) * ratio);
            g.fillRect(px + 4, py - 10, w, 6);
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
        int cx = wx * TILE - camX;
        int cy = wy * TILE - camY;
        g.setColor(new Color(255, 255, 255, 220));
        for (int i = 0; i < TILE; i += 4) {
            g.fillRect(cx + i, cy, 2, 2);
            g.fillRect(cx + i, cy + TILE - 2, 2, 2);
            g.fillRect(cx, cy + i, 2, 2);
            g.fillRect(cx + TILE - 2, cy + i, 2, 2);
        }

        drawHUD(g);
        if (inventoryOpen) drawInventory(g);
        if (activeContainer != null) drawContainer(g);
        if (activeVillager != null) drawTrade(g);
        drawChat(g);

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

        String timeStr = isNight() ? "☾ НОЧЬ" : "☀ ДЕНЬ";
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
            g.drawString("★ " + gold, 13, SCREEN_H - 12);
            g.setColor(new Color(255, 220, 80));
            g.drawString("★ " + gold, 12, SCREEN_H - 13);
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
            if (!tip.isEmpty()) {
                g.setFont(PixelFont.font(10, Font.PLAIN));
                g.setColor(new Color(180, 200, 220));
                g.drawString(tip, tx + 8, ty + 32);
            }
        }
    }

    private void drawPixelHeart(Graphics2D g, int x, int y, double fill) {
        int px = 2;
        int[][] heart = {
            {0, 1, 1, 0, 1, 1, 0},
            {1, 1, 1, 1, 1, 1, 1},
            {1, 1, 1, 1, 1, 1, 1},
            {0, 1, 1, 1, 1, 1, 0},
            {0, 0, 1, 1, 1, 0, 0},
            {0, 0, 0, 1, 0, 0, 0}
        };
        for (int row = 0; row < heart.length; row++) {
            for (int col = 0; col < heart[row].length; col++) {
                if (heart[row][col] == 1) {
                    g.setColor(new Color(30, 0, 0));
                    g.fillRect(x + col * px, y + row * px, px, px);
                }
            }
        }
        if (fill > 0) {
            int filledCols = (int) Math.ceil(heart[0].length * fill);
            for (int row = 0; row < heart.length; row++) {
                for (int col = 0; col < heart[row].length; col++) {
                    if (heart[row][col] == 1 && col < filledCols) {
                        int shade = row < 2 ? 220 : 180;
                        g.setColor(new Color(shade, 20, 20));
                        g.fillRect(x + col * px, y + row * px, px, px);
                    }
                }
            }
            g.setColor(new Color(255, 180, 180));
            g.fillRect(x + 2 * px, y + 1 * px, px, px);
        }
    }

    private void drawInventory(Graphics2D g) {
        int totalW = INV_COLS * SLOT;
        int totalH = INV_ROWS * SLOT;
        int panelW = totalW + 240;
        int panelH = totalH + 120;
        int panelX = (SCREEN_W - panelW) / 2;
        int panelY = (SCREEN_H - panelH) / 2;
        drawPixelPanel(g, panelX, panelY, panelW, panelH);

        g.setFont(PixelFont.font(22, Font.BOLD));
        g.setColor(new Color(0, 0, 0));
        g.drawString("ИНВЕНТАРЬ", panelX + 22, panelY + 32);
        g.setColor(new Color(255, 220, 100));
        g.drawString("ИНВЕНТАРЬ", panelX + 20, panelY + 30);

        int startX = panelX + 20;
        int startY = panelY + 50;

        for (int r = 0; r < INV_ROWS; r++) {
            for (int c = 0; c < INV_COLS; c++) {
                int i = r * INV_COLS + c;
                int sx = startX + c * SLOT;
                int sy = startY + r * SLOT;
                drawMinecraftSlot(g, sx, sy, i == selectedSlot);
                if (inventory[i] != null && i != draggedFromSlot) {
                    drawSlotItem(g, inventory[i], sx, sy);
                }
            }
        }

        int craftX = panelX + totalW + 40;
        int craftY = startY;

        g.setFont(PixelFont.font(18, Font.BOLD));
        g.setColor(new Color(0, 0, 0));
        g.drawString("КРАФТ", craftX + 2, craftY - 8);
        g.setColor(new Color(255, 220, 100));
        g.drawString("КРАФТ", craftX, craftY - 10);

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int i = r * 3 + c;
                int sx = craftX + c * SLOT;
                int sy = craftY + r * SLOT;
                drawMinecraftSlot(g, sx, sy, false);
                if (craftGrid[i] != null) drawSlotItem(g, craftGrid[i], sx, sy);
            }
        }

        int arrowX = craftX + 3 * SLOT + 15;
        int arrowY = craftY + SLOT + 10;
        g.setColor(new Color(200, 200, 220));
        g.fillRect(arrowX, arrowY, 30, 4);
        Polygon p = new Polygon();
        p.addPoint(arrowX + 30, arrowY - 12);
        p.addPoint(arrowX + 30, arrowY + 16);
        p.addPoint(arrowX + 42, arrowY + 2);
        g.fillPolygon(p);

        int resX = arrowX + 50;
        int resY = craftY + SLOT;
        drawMinecraftSlot(g, resX, resY, false);
        updateCraftResult();
        if (craftResult != null) drawSlotItem(g, craftResult, resX, resY);

        g.setFont(PixelFont.font(12, Font.BOLD));
        if (nearWorkbench) {
            g.setColor(new Color(120, 255, 120));
            g.drawString("✔ Верстак рядом — полный крафт", craftX, craftY + 3 * SLOT + 30);
        } else {
            g.setColor(new Color(180, 180, 200));
            g.drawString("Нужен верстак для инструментов", craftX, craftY + 3 * SLOT + 30);
        }

        g.setFont(PixelFont.font(11, Font.PLAIN));
        g.setColor(new Color(160, 160, 180));
        g.drawString("ЛКМ — взять/положить · ПКМ — половина · Esc — закрыть", panelX + 20, panelY + panelH - 12);
    }

    private void drawMinecraftSlot(Graphics2D g, int sx, int sy, boolean selected) {
        g.setColor(new Color(139, 139, 139));
        g.fillRect(sx, sy, SLOT - 2, SLOT - 2);
        g.setColor(new Color(55, 55, 55));
        g.fillRect(sx + 2, sy + 2, SLOT - 6, SLOT - 6);
        g.setColor(new Color(0, 0, 0, 100));
        g.drawRect(sx, sy, SLOT - 2, SLOT - 2);
        g.setColor(new Color(255, 255, 255, 180));
        g.drawLine(sx + 2, sy + 2, sx + SLOT - 4, sy + 2);
        g.drawLine(sx + 2, sy + 2, sx + 2, sy + SLOT - 4);
        if (selected) {
            g.setColor(new Color(255, 255, 255));
            g.drawRect(sx - 2, sy - 2, SLOT + 2, SLOT + 2);
            g.drawRect(sx - 3, sy - 3, SLOT + 4, SLOT + 4);
        }
    }

    private void updateCraftResult() {
        currentRecipe = Crafting.findRecipe(craftGrid, nearWorkbench);
        if (currentRecipe != null) {
            craftResult = ItemStack.of(currentRecipe.resultId, currentRecipe.resultCount);
        } else {
            craftResult = null;
        }
    }

    private void drawContainer(Graphics2D g) {
        if (containerSlots == null) return;
        boolean isFurnace = activeContainer.type == World.FURNACE;
        int cols = activeContainer.type == World.CHEST ? 9 : 3;
        int rows = 3;
        int totalW = cols * SLOT;
        int totalH = rows * SLOT;
        int startX = (SCREEN_W - totalW) / 2;
        int startY = (SCREEN_H - totalH) / 2 - 200;
        drawPixelPanel(g, startX - 20, startY - 55, totalW + 40, totalH + 75);

        g.setFont(PixelFont.font(20, Font.BOLD));
        String title = activeContainer.type == World.CHEST ? "СУНДУК" : isFurnace ? "ПЕЧЬ" : "ВЕРСТАК";
        g.setColor(new Color(0, 0, 0));
        g.drawString(title, startX + 2, startY - 30);
        g.setColor(new Color(255, 220, 100));
        g.drawString(title, startX, startY - 32);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                int sx = startX + c * SLOT;
                int sy = startY + r * SLOT;
                drawMinecraftSlot(g, sx, sy, false);
                if (containerSlots[i] != null) drawSlotItem(g, containerSlots[i], sx, sy);
                if (isFurnace) {
                    g.setColor(new Color(180, 180, 220));
                    g.setFont(PixelFont.font(10, Font.PLAIN));
                    if (i == 0) g.drawString("РУДА", sx + 8, sy + 14);
                    if (i == 1) g.drawString("ТОПЛ", sx + 8, sy + 14);
                    if (i == 2) g.drawString("ТОПЛ", sx + 8, sy + 14);
                    if (i == 8) g.drawString("РЕЗ.", sx + 10, sy + 14);
                }
            }
        }
    }

    private void drawSlotItem(Graphics2D g, ItemStack stack, int sx, int sy) {
        BufferedImage icon = Textures.get(stack.id);
        g.drawImage(icon, sx + 8, sy + 8, SLOT - 18, SLOT - 18, null);
        if (stack.count > 1) {
            g.setFont(PixelFont.font(13, Font.BOLD));
            g.setColor(new Color(0, 0, 0));
            g.drawString("" + stack.count, sx + SLOT - 20, sy + SLOT - 6);
            g.setColor(Color.WHITE);
            g.drawString("" + stack.count, sx + SLOT - 21, sy + SLOT - 7);
        }
    }

    private void drawTrade(Graphics2D g) {
        if (activeVillager == null) return;
        Villager v = activeVillager;

        int panelW = 600;
        int panelH = 100 + v.trades.length * 62;
        int panelX = (SCREEN_W - panelW) / 2;
        int panelY = (SCREEN_H - panelH) / 2;
        drawPixelPanel(g, panelX, panelY, panelW, panelH);

        g.setFont(PixelFont.font(22, Font.BOLD));
        g.setColor(new Color(0, 0, 0));
        g.drawString("ТОРГОВЛЯ · " + v.profession.toUpperCase(), panelX + 22, panelY + 32);
        g.setColor(new Color(255, 220, 100));
        g.drawString("ТОРГОВЛЯ · " + v.profession.toUpperCase(), panelX + 20, panelY + 30);

        int rowY = panelY + 55;
        for (int i = 0; i < v.trades.length; i++) {
            int[] t = v.trades[i];
            int giveId = t[0], giveCnt = t[1], getId = t[2], getCnt = t[3];

            int rowX = panelX + 20;
            int rowW = panelW - 40;
            int rowH = 54;

            boolean hover = mouseX > rowX && mouseX < rowX + rowW && mouseY > rowY && mouseY < rowY + rowH - 8;
            boolean canTrade = v.canTrade(i, inventory);

            g.setColor(hover ? new Color(80, 70, 45, 220) : new Color(40, 35, 30, 200));
            g.fillRect(rowX, rowY, rowW, rowH - 8);
            g.setColor(canTrade ? new Color(150, 200, 120) : new Color(120, 90, 60));
            g.drawRect(rowX, rowY, rowW, rowH - 8);

            BufferedImage giveImg = Textures.get(giveId);
            g.drawImage(giveImg, rowX + 8, rowY + 6, 32, 32, null);
            g.setFont(PixelFont.font(14, Font.BOLD));
            g.setColor(Color.WHITE);
            g.drawString("×" + giveCnt, rowX + 46, rowY + 26);
            g.setFont(PixelFont.font(10, Font.PLAIN));
            g.setColor(new Color(200, 200, 220));
            g.drawString(World.blockName(giveId), rowX + 8, rowY + 44);

            g.setFont(PixelFont.font(20, Font.BOLD));
            g.setColor(new Color(220, 220, 240));
            g.drawString("→", rowX + 150, rowY + 30);

            BufferedImage getImg = Textures.get(getId);
            int gx = rowX + 200;
            g.drawImage(getImg, gx, rowY + 6, 32, 32, null);
            g.setFont(PixelFont.font(14, Font.BOLD));
            g.setColor(new Color(140, 255, 140));
            g.drawString("×" + getCnt, gx + 38, rowY + 26);
            g.setFont(PixelFont.font(10, Font.PLAIN));
            g.setColor(new Color(200, 200, 220));
            g.drawString(World.blockName(getId), gx, rowY + 44);

            int have = 0;
            for (ItemStack s : inventory) {
                if (s != null && s.id == giveId) have += s.count;
            }
            g.setFont(PixelFont.font(12, Font.BOLD));
            if (have >= giveCnt) {
                g.setColor(new Color(140, 255, 140));
                g.drawString("✔ " + have + "/" + giveCnt, rowX + rowW - 90, rowY + 26);
            } else {
                g.setColor(new Color(255, 120, 120));
                g.drawString("✘ " + have + "/" + giveCnt, rowX + rowW - 90, rowY + 26);
            }

            rowY += rowH;
        }

        g.setFont(PixelFont.font(11, Font.PLAIN));
        g.setColor(new Color(160, 160, 180));
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
            g.setColor(new Color(0, 0, 0, 200));
            g.fillRect(10, startY, SCREEN_W - 20, 30);
            g.setColor(new Color(255, 255, 255));
            g.drawRect(10, startY, SCREEN_W - 20, 30);
            g.setColor(Color.WHITE);
            g.setFont(PixelFont.font(16, Font.BOLD));
            g.drawString("> " + chatInput + "_", 20, startY + 22);
        }
    }

    private void drawPause(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 200));
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);
        g.setFont(PixelFont.font(58, Font.BOLD));
        drawCenteredWithPixelShadow(g, "ПАУЗА", SCREEN_W / 2, SCREEN_H / 2 - 120, new Color(255, 220, 100));
        drawPixelButton(g, "Продолжить [Esc]", SCREEN_W / 2 - 200, SCREEN_H / 2 - 40, 400, 55,
                mouseY > SCREEN_H / 2 - 40 && mouseY < SCREEN_H / 2 + 15 && mouseX > SCREEN_W / 2 - 200 && mouseX < SCREEN_W / 2 + 200);
        drawPixelButton(g, "Сохранить [S]", SCREEN_W / 2 - 200, SCREEN_H / 2 + 30, 400, 55,
                mouseY > SCREEN_H / 2 + 30 && mouseY < SCREEN_H / 2 + 85 && mouseX > SCREEN_W / 2 - 200 && mouseX < SCREEN_W / 2 + 200);
        drawPixelButton(g, "В меню [Q]", SCREEN_W / 2 - 200, SCREEN_H / 2 + 100, 400, 55,
                mouseY > SCREEN_H / 2 + 100 && mouseY < SCREEN_H / 2 + 155 && mouseX > SCREEN_W / 2 - 200 && mouseX < SCREEN_W / 2 + 200);
    }

    private void drawDeath(Graphics2D g) {
        g.setColor(new Color(100, 0, 0, 210));
        g.fillRect(0, 0, SCREEN_W, SCREEN_H);
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
            g.drawString((a.unlocked ? "★ " : "☆ ") + a.title, x + 15, y + 22);
            g.setColor(Color.WHITE);
            g.setFont(PixelFont.font(12, Font.PLAIN));
            g.drawString(a.description, x + 15, y + 40);
        }
        int unlockedCount = 0;
        for (Achievement a : achievements) if (a.unlocked) unlockedCount++;
        g.setColor(new Color(255, 220, 100));
        g.setFont(PixelFont.font(20, Font.BOLD));
        drawCentered(g, "Открыто: " + unlockedCount + " / " + achievements.size(), SCREEN_W / 2, SCREEN_H - 80);
        drawPixelButton(g, "Назад [Esc]", SCREEN_W / 2 - 150, SCREEN_H - 70, 300, 50,
                mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150);
    }

    private void drawSettings(Graphics2D g) {
        drawPixelBackground(g);
        g.setFont(PixelFont.font(42, Font.BOLD));
        drawCenteredWithPixelShadow(g, "НАСТРОЙКИ", SCREEN_W / 2, 80, new Color(255, 220, 100));
        g.setFont(PixelFont.font(18, Font.PLAIN));
        g.setColor(Color.WHITE);
        g.drawString("F11 — Полный экран", 100, 200);
        g.drawString("E — Инвентарь + крафт", 100, 240);
        g.drawString("T — Чат", 100, 280);
        g.drawString("M — Креатив / Выживание", 100, 320);
        g.drawString("A/D — Движение · Space — Прыжок", 100, 360);
        g.drawString("ЛКМ — Копать/Атаковать · ПКМ — Ставить/Открыть/Торговать", 100, 400);
        g.drawString("ПКМ по двери — открыть/закрыть", 100, 440);
        g.drawString("ПКМ по жителю — торговля", 100, 480);
        drawPixelButton(g, "Назад [Esc]", SCREEN_W / 2 - 150, SCREEN_H - 70, 300, 50,
                mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20 && mouseX > SCREEN_W / 2 - 150 && mouseX < SCREEN_W / 2 + 150);
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

        if (chatOpen) {
            if (code == KeyEvent.VK_ENTER) {
                if (!chatInput.trim().isEmpty()) executeChatCommand(chatInput);
                chatInput = "";
                chatOpen = false;
                return;
            }
            if (code == KeyEvent.VK_ESCAPE) { chatOpen = false; chatInput = ""; return; }
            if (code == KeyEvent.VK_BACK_SPACE) {
                if (!chatInput.isEmpty()) chatInput = chatInput.substring(0, chatInput.length() - 1);
                return;
            }
            char ch = e.getKeyChar();
            if (ch >= 32 && ch < 127 && chatInput.length() < 100) chatInput += ch;
            return;
        }

        if (state == GameState.WORLD_CREATE && typingSeed) {
            if (code == KeyEvent.VK_ENTER) { createWorldWithSeed(); return; }
            if (code == KeyEvent.VK_ESCAPE) { state = GameState.WORLD_SELECT; typingSeed = false; return; }
            if (code == KeyEvent.VK_BACK_SPACE) {
                if (!inputSeed.isEmpty()) inputSeed = inputSeed.substring(0, inputSeed.length() - 1);
                return;
            }
            char ch = e.getKeyChar();
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '-') {
                if (inputSeed.length() < 40) inputSeed += ch;
            }
            return;
        }

        if (state == GameState.MULTIPLAYER && typingMpAddress) {
            if (code == KeyEvent.VK_ENTER) { typingMpAddress = false; return; }
            if (code == KeyEvent.VK_ESCAPE) { typingMpAddress = false; return; }
            if (code == KeyEvent.VK_BACK_SPACE) {
                if (!mpAddress.isEmpty()) mpAddress = mpAddress.substring(0, mpAddress.length() - 1);
                return;
            }
            char ch = e.getKeyChar();
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == ':') {
                if (mpAddress.length() < 40) mpAddress += ch;
            }
            return;
        }

        if (code == KeyEvent.VK_F11) { toggleFullscreen(); return; }

        switch (state) {
            case MENU:
                if (code == KeyEvent.VK_ESCAPE) System.exit(0);
                break;
            case WORLD_SELECT:
                if (code == KeyEvent.VK_ESCAPE) state = GameState.MENU;
                if (code == KeyEvent.VK_UP) selectedWorldIndex = Math.max(0, selectedWorldIndex - 1);
                if (code == KeyEvent.VK_DOWN) selectedWorldIndex = Math.min(Math.max(0, worldSaves.size() - 1), selectedWorldIndex + 1);
                if (code == KeyEvent.VK_N) { state = GameState.WORLD_CREATE; inputSeed = ""; typingSeed = true; }
                if (code == KeyEvent.VK_DELETE && !worldSaves.isEmpty()) deleteWorld();
                if (code == KeyEvent.VK_ENTER && !worldSaves.isEmpty()) loadSelectedWorld();
                break;
            case MULTIPLAYER:
                if (code == KeyEvent.VK_ESCAPE) { state = GameState.MENU; typingMpAddress = false; }
                break;
            case LOADING:
                if (code == KeyEvent.VK_ESCAPE) {
                    if (mp != null) { mp.close(); mp = null; }
                    state = GameState.MULTIPLAYER;
                }
                break;
            case PLAYING:
                if (code == KeyEvent.VK_ESCAPE && activeVillager != null) { activeVillager = null; return; }
                if (code == KeyEvent.VK_T) { chatOpen = true; chatInput = ""; return; }
                if (code == KeyEvent.VK_ESCAPE) {
                    if (activeContainer != null) { activeContainer = null; containerSlots = null; }
                    else if (inventoryOpen) { inventoryOpen = false; returnDragged(); returnCraftGrid(); }
                    else state = GameState.PAUSED;
                }
                if (code == KeyEvent.VK_E) {
                    inventoryOpen = !inventoryOpen;
                    if (!inventoryOpen) { returnDragged(); returnCraftGrid(); }
                }
                if (code == KeyEvent.VK_M) mode = mode == GameMode.SURVIVAL ? GameMode.CREATIVE : GameMode.SURVIVAL;
                if (code >= KeyEvent.VK_1 && code <= KeyEvent.VK_9) selectedSlot = code - KeyEvent.VK_1;
                if (code == KeyEvent.VK_F5) saveCurrentWorld();
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
            case ACHIEVEMENTS:
                if (code == KeyEvent.VK_ESCAPE) state = GameState.MENU;
                break;
            case SETTINGS:
                if (code == KeyEvent.VK_ESCAPE) state = GameState.MENU;
                break;
            case WORLD_CREATE:
                if (code == KeyEvent.VK_ESCAPE) state = GameState.WORLD_SELECT;
                break;
        }
    }

    @Override public void keyReleased(KeyEvent e) { if (e.getKeyCode() < 256) keys[e.getKeyCode()] = false; }
    @Override public void keyTyped(KeyEvent e) {}

    @Override
    public void mousePressed(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        boolean isLeft = SwingUtilities.isLeftMouseButton(e);
        boolean isRight = SwingUtilities.isRightMouseButton(e);
        if (isLeft) mouseDown = true;
        if (isRight) rightMouseDown = true;

        if (state == GameState.PLAYING && inventoryOpen) { handleInventoryClick(mouseX, mouseY, isRight); return; }
        if (state == GameState.PLAYING && activeContainer != null) { handleContainerClick(mouseX, mouseY, isRight); return; }

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
        if (state != GameState.PLAYING) return;
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
            case ACHIEVEMENTS:
                if (mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20) state = GameState.MENU;
                break;
            case SETTINGS:
                if (mouseY > SCREEN_H - 70 && mouseY < SCREEN_H - 20) state = GameState.MENU;
                break;
        }
    }

    private void handleMenuClick() {
        int bx = SCREEN_W / 2 - 220;
        if (mouseX < bx || mouseX > bx + 440) return;
        int startY = 200;
        for (int i = 0; i < 5; i++) {
            int y = startY + i * 70;
            if (mouseY > y - 25 && mouseY < y + 30) {
                if (i == 0) state = GameState.WORLD_SELECT;
                else if (i == 1) state = GameState.MULTIPLAYER;
                else if (i == 2) state = GameState.ACHIEVEMENTS;
                else if (i == 3) state = GameState.SETTINGS;
                else System.exit(0);
                return;
            }
        }
    }

    private void handleWorldSelectClick() {
        int listX = 60, listY = 120, itemH = 60, listW = 520;
        for (int i = 0; i < Math.min(worldSaves.size(), 6); i++) {
            int y = listY + i * itemH;
            if (mouseY > y && mouseY < y + itemH - 6 && mouseX > listX && mouseX < listX + listW) {
                selectedWorldIndex = i;
                loadSelectedWorld();
                return;
            }
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
        if (player == null) return;

        // Торговля открыта
        if (activeVillager != null) {
            if (SwingUtilities.isRightMouseButton(e)) { activeVillager = null; return; }
            if (SwingUtilities.isLeftMouseButton(e)) { handleTradeClick(mouseX, mouseY); return; }
        }

        boolean isLeft = SwingUtilities.isLeftMouseButton(e);
        boolean isRight = SwingUtilities.isRightMouseButton(e);

        if (inventoryOpen && handleInventoryClick(mouseX, mouseY, isRight)) return;
        if (activeContainer != null && handleContainerClick(mouseX, mouseY, isRight)) return;

        int totalW = INV_COLS * SLOT;
        int startX = (SCREEN_W - totalW) / 2;
        int startY = SCREEN_H - SLOT - 10;
        if (mouseY >= startY && mouseY < startY + SLOT) {
            int idx = (mouseX - startX) / SLOT;
            if (idx >= 0 && idx < INV_COLS) { selectedSlot = idx; return; }
        }

        int wx = (int) ((mouseX + player.cameraX) / TILE);
        int wy = (int) ((mouseY + player.cameraY) / TILE);
        if (!world.inBounds(wx, wy)) return;
        double pdx = wx * TILE - player.x;
        double pdy = wy * TILE - player.y;
        if (pdx * pdx + pdy * pdy > (6 * TILE) * (6 * TILE)) return;

        // === ТОРГОВЛЯ: ПКМ по жителю ===
        if (isRight) {
            for (Villager v : villagers) {
                if (wx * TILE >= v.x - 10 && wx * TILE <= v.x + Player.W + 10
                        && wy * TILE >= v.y - 10 && wy * TILE <= v.y + Player.H + 10) {
                    activeVillager = v;
                    return;
                }
            }
        }

        if (isRight) {
            int clickedBlock = world.getBlock(wx, wy);
            if (clickedBlock == World.DOOR_CLOSED) {
                world.setBlock(wx, wy, World.DOOR_OPEN);
                if (mp != null) mp.sendBlockChange(wx, wy, World.DOOR_OPEN);
                return;
            }
            if (clickedBlock == World.DOOR_OPEN) {
                world.setBlock(wx, wy, World.DOOR_CLOSED);
                if (mp != null) mp.sendBlockChange(wx, wy, World.DOOR_CLOSED);
                return;
            }
            if (clickedBlock == World.CHEST || clickedBlock == World.FURNACE
                    || clickedBlock == World.WORKBENCH || clickedBlock == World.CRAFT_TABLE) {
                openContainer(wx, wy, clickedBlock);
                return;
            }
            ItemStack heldCheck = inventory[selectedSlot];
            if (heldCheck != null && isBgBlock(heldCheck.id) && world.getBackground(wx, wy) == World.AIR) {
                world.setBackground(wx, wy, heldCheck.id);
                if (mode == GameMode.SURVIVAL) {
                    heldCheck.count--;
                    if (heldCheck.count <= 0) inventory[selectedSlot] = null;
                }
                if (mp != null) mp.sendBgChange(wx, wy, heldCheck.id);
                return;
            }
            if (heldCheck != null && world.getBlock(wx, wy) == World.AIR && !ToolUtil.isTool(heldCheck.id)) {
                int bx = (int) (player.x / TILE);
                int by = (int) (player.y / TILE);
                boolean inside = (wx == bx || wx == bx + (Player.W / TILE)) && (wy == by || wy == by + 1);
                if (!inside) {
                    if (mode == GameMode.CREATIVE || heldCheck.count > 0) {
                        world.setBlock(wx, wy, heldCheck.id);
                        if (mp != null) mp.sendBlockChange(wx, wy, heldCheck.id);
                        if (mode == GameMode.SURVIVAL) {
                            heldCheck.count--;
                            if (heldCheck.count <= 0) inventory[selectedSlot] = null;
                        }
                    }
                }
            }
            return;
        }

        if (!isLeft) return;

        for (RemotePlayer rp : remotePlayers) {
            if (wx * TILE >= rp.x - 4 && wx * TILE <= rp.x + Player.W + 4
                    && wy * TILE >= rp.y - 4 && wy * TILE <= rp.y + Player.H + 4) {
                ItemStack held = inventory[selectedSlot];
                int dmg = ToolUtil.getSwordDamage(held);
                if (mp != null) mp.sendDamage(rp.id, dmg);
                floaters.add(new FloatingText("-" + dmg, rp.x, rp.y - 10, new Color(255, 100, 100)));
                return;
            }
        }

        Monster target = null;
        for (Monster m : monsters) {
            if (wx * TILE >= m.x - 4 && wx * TILE <= m.x + Monster.W + 4
                    && wy * TILE >= m.y - 4 && wy * TILE <= m.y + Monster.H + 4) {
                target = m;
                break;
            }
        }
        if (target != null) {
            ItemStack held = inventory[selectedSlot];
            int dmg = ToolUtil.getSwordDamage(held);
            target.hp -= dmg;
            floaters.add(new FloatingText("-" + dmg, target.x, target.y - 10, new Color(255, 100, 100)));
            Sound.play(Sound.HIT);
            if (target.hp <= 0) {
                target.dead = true;
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

        startDigging(wx, wy);
    }

    private void handleTradeClick(int mx, int my) {
        if (activeVillager == null) return;
        Villager v = activeVillager;
        int panelW = 600;
        int panelH = 100 + v.trades.length * 62;
        int panelX = (SCREEN_W - panelW) / 2;
        int panelY = (SCREEN_H - panelH) / 2;
        int rowY = panelY + 55;
        for (int i = 0; i < v.trades.length; i++) {
            int rowX = panelX + 20;
            int rowW = panelW - 40;
            int rowH = 54;
            if (mx > rowX && mx < rowX + rowW && my > rowY && my < rowY + rowH - 8) {
                performTrade(v, i);
                return;
            }
            rowY += rowH;
        }
    }

    private void performTrade(Villager v, int tradeIndex) {
        int[] t = v.trades[tradeIndex];
        int giveId = t[0], giveCnt = t[1], getId = t[2], getCnt = t[3];

        int have = 0;
        for (ItemStack s : inventory) if (s != null && s.id == giveId) have += s.count;
        if (have < giveCnt) {
            addChatMessage("Недостаточно: " + World.blockName(giveId) + " (" + have + "/" + giveCnt + ")", new Color(255, 120, 120));
            Sound.play(Sound.HURT);
            return;
        }

        Villager.removeItems(inventory, giveId, giveCnt);
        addToInventory(ItemStack.of(getId, getCnt));

        floaters.add(new FloatingText("+" + getCnt + " " + World.blockName(getId), player.x, player.y - 40, new Color(140, 255, 140)));
        addChatMessage("Обмен: " + giveCnt + "× " + World.blockName(giveId) + " → " + getCnt + "× " + World.blockName(getId), new Color(120, 255, 120));
        Sound.play(Sound.TRADE);
        unlock("trade");
    }

    private boolean handleInventoryClick(int mx, int my, boolean isRight) {
        int totalW = INV_COLS * SLOT;
        int totalH = INV_ROWS * SLOT;
        int panelW = totalW + 240;
        int panelH = totalH + 120;
        int panelX = (SCREEN_W - panelW) / 2;
        int panelY = (SCREEN_H - panelH) / 2;
        int startX = panelX + 20;
        int startY = panelY + 50;

        int craftX = panelX + totalW + 40;
        int craftY = startY;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                int i = r * 3 + c;
                int sx = craftX + c * SLOT;
                int sy = craftY + r * SLOT;
                if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                    handleSlotClick(craftGrid, i, isRight);
                    return true;
                }
            }
        }
        int arrowX = craftX + 3 * SLOT + 15;
        int resX = arrowX + 50;
        int resY = craftY + SLOT;
        if (mx >= resX && mx < resX + SLOT && my >= resY && my < resY + SLOT) {
            if (craftResult != null && currentRecipe != null) {
                addToInventory(craftResult.copy());
                Crafting.consumeRecipe(currentRecipe, craftGrid);
                Sound.play(Sound.MINE);
                unlock("craft");
                return true;
            }
        }

        for (int r = 0; r < INV_ROWS; r++) {
            for (int c = 0; c < INV_COLS; c++) {
                int i = r * INV_COLS + c;
                int sx = startX + c * SLOT;
                int sy = startY + r * SLOT;
                if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                    handleSlotClick(inventory, i, isRight);
                    return true;
                }
            }
        }
        return false;
    }

    private void handleSlotClick(ItemStack[] container, int i, boolean isRight) {
        if (container[i] == null) {
            if (draggedStack != null) {
                if (isRight) {
                    container[i] = ItemStack.of(draggedStack.id, 1);
                    draggedStack.count--;
                    if (draggedStack.count <= 0) { draggedStack = null; draggedFromSlot = -1; }
                } else {
                    container[i] = draggedStack;
                    draggedStack = null;
                    draggedFromSlot = -1;
                }
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
            } else {
                draggedStack = container[i];
                draggedFromSlot = i;
                container[i] = null;
            }
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
        if (containerSlots == null) return false;
        int cols = activeContainer.type == World.CHEST ? 9 : 3;
        int rows = 3;
        int totalW = cols * SLOT;
        int totalH = rows * SLOT;
        int startX = (SCREEN_W - totalW) / 2;
        int startY = (SCREEN_H - totalH) / 2 - 200;
        if (mx < startX || my < startY || mx >= startX + totalW || my >= startY + totalH) return false;

        int c = (mx - startX) / SLOT;
        int r = (my - startY) / SLOT;
        int i = r * cols + c;
        if (i < 0 || i >= containerSlots.length) return false;

        if (activeContainer.type == World.FURNACE && i == 8) {
            ItemStack res = containerSlots[8];
            if (res != null) {
                if (draggedStack == null) { draggedStack = res; containerSlots[8] = null; }
                else if (draggedStack.id == res.id) { draggedStack.count += res.count; containerSlots[8] = null; }
            }
            return true;
        }

        handleSlotClick(containerSlots, i, isRight);
        return true;
    }

    private void returnDragged() {
        if (draggedStack != null) { addToInventory(draggedStack); draggedStack = null; }
        draggedFromSlot = -1;
    }

    private void returnCraftGrid() {
        for (int i = 0; i < craftGrid.length; i++) {
            if (craftGrid[i] != null) { addToInventory(craftGrid[i]); craftGrid[i] = null; }
        }
    }

    private void openContainer(int wx, int wy, int type) {
        for (WorkBlock wb : world.workBlocks) {
            if (wb.x == wx && wb.y == wy) {
                activeContainer = wb;
                containerSlots = wb.slots;
                return;
            }
        }
        int size = type == World.CHEST ? 27 : 9;
        WorkBlock wb = new WorkBlock(wx, wy, type, size);
        world.workBlocks.add(wb);
        activeContainer = wb;
        containerSlots = wb.slots;
    }

    private boolean isBgBlock(int id) {
        return id == World.BG_STONE || id == World.BG_DIRT || id == World.BG_WOOD || id == World.BG_PLANK;
    }

    private void executeChatCommand(String text) {
        addChatMessage("<Игрок> " + text, Color.WHITE);
        if (!text.startsWith("/")) {
            if (mp != null) mp.sendChat("<Игрок> " + text);
            return;
        }
        String[] parts = text.substring(1).split("\\s+");
        String cmd = parts[0].toLowerCase();
        try {
            switch (cmd) {
                case "help":
                    addChatMessage("/give <блок> [N] /heal /god /fly /tp x y", Color.YELLOW);
                    addChatMessage("/time day|night /spawn zombie|slime|skeleton /killall", Color.YELLOW);
                    addChatMessage("/seed /coords /clear /save /xp N /unlock all", Color.YELLOW);
                    break;
                case "give": {
                    if (parts.length < 2) { addChatMessage("Использование: /give <блок> [N]", Color.RED); break; }
                    int id = parseBlockId(parts[1]);
                    int cnt = parts.length >= 3 ? Integer.parseInt(parts[2]) : 1;
                    if (id < 0) { addChatMessage("Неизвестный блок", Color.RED); break; }
                    addToInventory(ItemStack.of(id, cnt));
                    addChatMessage("Выдано: " + cnt + "× " + World.blockName(id), Color.GREEN);
                    break;
                }
                case "heal": player.hp = player.maxHp; addChatMessage("Здоровье восстановлено", Color.GREEN); break;
                case "god": player.godMode = !player.godMode; addChatMessage("Бессмертие: " + (player.godMode ? "ВКЛ" : "ВЫКЛ"), Color.GREEN); break;
                case "fly": player.flyMode = !player.flyMode; addChatMessage("Полёт: " + (player.flyMode ? "ВКЛ" : "ВЫКЛ"), Color.GREEN); break;
                case "tp": {
                    if (parts.length < 3) { addChatMessage("/tp <x> <y>", Color.RED); break; }
                    player.x = Double.parseDouble(parts[1]) * TILE;
                    player.y = Double.parseDouble(parts[2]) * TILE;
                    player.vx = 0; player.vy = 0;
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
                case "xp": {
                    int n = parts.length >= 2 ? Integer.parseInt(parts[1]) : 100;
                    addXp(n);
                    addChatMessage("+" + n + " опыта", Color.GREEN);
                    break;
                }
                case "unlock":
                    if (parts.length >= 2 && parts[1].equals("all")) {
                        for (Achievement a : achievements) a.unlocked = true;
                        addChatMessage("Все достижения открыты", Color.GREEN);
                    }
                    break;
                default: addChatMessage("Неизвестная команда: /" + cmd, Color.RED);
            }
        } catch (Exception ex) { addChatMessage("Ошибка: " + ex.getMessage(), Color.RED); }
    }

    private int parseBlockId(String name) {
        switch (name.toLowerCase()) {
            case "dirt": return World.DIRT;
            case "stone": return World.STONE;
            case "grass": return World.GRASS;
            case "sand": return World.SAND;
            case "wood": return World.WOOD;
            case "leaves": return World.LEAVES;
            case "coal": return World.COAL;
            case "iron": return World.IRON;
            case "gold": return World.GOLD;
            case "diamond": return World.DIAMOND;
            case "chest": return World.CHEST;
            case "furnace": return World.FURNACE;
            case "workbench": return World.WORKBENCH;
            case "torch": return World.TORCH;
            case "iron_ingot": return World.IRON_INGOT;
            case "gold_ingot": return World.GOLD_INGOT;
            case "diamond_gem": return World.DIAMOND_GEM;
            case "coin": return World.COIN_GOLD;
            case "sword_wood": return World.SWORD_WOOD;
            case "sword_stone": return World.SWORD_STONE;
            case "sword_iron": return World.SWORD_IRON;
            case "sword_diamond": return World.SWORD_DIAMOND;
            case "pick_wood": return World.PICK_WOOD;
            case "pick_stone": return World.PICK_STONE;
            case "pick_iron": return World.PICK_IRON;
            case "pick_diamond": return World.PICK_DIAMOND;
            case "axe_wood": return World.AXE_WOOD;
            case "axe_stone": return World.AXE_STONE;
            case "axe_iron": return World.AXE_IRON;
            case "axe_diamond": return World.AXE_DIAMOND;
            case "door": return World.DOOR_CLOSED;
            case "bg_stone": return World.BG_STONE;
            case "bg_dirt": return World.BG_DIRT;
            case "bg_wood": return World.BG_WOOD;
            case "bg_plank": return World.BG_PLANK;
            default: return -1;
        }
    }

    private void startMultiplayerHost() {
        try {
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
            monsters.clear(); villagers.clear(); floaters.clear(); chatLog.clear(); remotePlayers.clear();
            Arrays.fill(inventory, null); Arrays.fill(craftGrid, null);
            Random rnd = new Random();
            for (int i = 0; i < world.villageX.size(); i++) {
                villagers.add(new Villager(world.villageX.get(i) * TILE, world.villageY.get(i) * TILE, rnd.nextInt(3)));
            }
            mp = new MultiplayerManager(this);
            mp.startHost(25565);
            mpHost = true;
            selectedSlot = 0; inventoryOpen = false; activeContainer = null; containerSlots = null; draggedStack = null;
            gameTime = 0; state = GameState.PLAYING; xp = 0; xpLevel = 0; xpToNext = 20;
            addChatMessage("Сервер запущен на порту 25565", new Color(120, 255, 120));
            addChatMessage("Другие игроки могут подключиться к вашему IP", new Color(255, 220, 100));
            unlock("multiplayer");
            requestFocusInWindow();
        } catch (Exception ex) {
            addChatMessage("Ошибка запуска сервера: " + ex.getMessage(), Color.RED);
            state = GameState.MULTIPLAYER;
        }
    }

    private void startMultiplayerClient() {
        try {
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
        } catch (Exception ex) {
            addChatMessage("Ошибка подключения: " + ex.getMessage(), Color.RED);
            state = GameState.MULTIPLAYER;
        }
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
            try {
                frame.dispose();
                frame.setUndecorated(false);
                if (windowedBounds != null) frame.setBounds(windowedBounds);
                frame.setVisible(true);
            } catch (Exception ignored) {}
            fullscreen = false;
        }
    }

    private void ensureSavesDir() { new File("saves").mkdirs(); }

    private void refreshWorldSaves() {
        worldSaves.clear();
        File dir = new File("saves");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".world"));
        if (files != null) {
            for (File f : files) {
                try {
                    WorldSave ws = new WorldSave();
                    ws.file = f;
                    ws.name = f.getName().replace(".world", "");
                    ws.size = f.length();
                    long lastMod = f.lastModified();
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
                    ws.dateStr = sdf.format(new Date(lastMod));
                    try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(f))) {
                        World w = (World) in.readObject();
                        ws.sizeW = w.getWidth();
                        ws.sizeH = w.getHeight();
                    } catch (Exception e) { ws.sizeW = 0; ws.sizeH = 0; }
                    worldSaves.add(ws);
                } catch (Exception ignored) {}
            }
        }
        worldSaves.sort((a, b) -> Long.compare(b.file.lastModified(), a.file.lastModified()));
    }

    private void createWorldWithSeed() {
        long seed = inputSeed.isEmpty() ? new Random().nextLong() : inputSeed.hashCode();
        currentWorldName = inputSeed.isEmpty() ? "world_" + System.currentTimeMillis() : "world_" + inputSeed;
        world = new World(WORLD_W, WORLD_H, seed);
        world.name = currentWorldName;
        world.seed = seed;
        startPlaying();
    }

    private void deleteWorld() {
        if (worldSaves.isEmpty()) return;
        WorldSave ws = worldSaves.get(selectedWorldIndex);
        ws.file.delete();
        refreshWorldSaves();
        if (selectedWorldIndex >= worldSaves.size()) selectedWorldIndex = Math.max(0, worldSaves.size() - 1);
    }

    private void loadSelectedWorld() {
        if (worldSaves.isEmpty()) return;
        WorldSave ws = worldSaves.get(selectedWorldIndex);
        currentWorldName = ws.name;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(ws.file))) {
            this.world = (World) in.readObject();
            startPlaying();
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    private void startPlaying() {
        int sx = world.getWidth() / 2;
        int sy = world.findSurface(sx);
        player = new Player(sx * TILE, (sy - 3) * TILE);
        monsters.clear(); villagers.clear(); floaters.clear(); chatLog.clear(); remotePlayers.clear();
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
            villagers.add(new Villager(world.villageX.get(i) * TILE, world.villageY.get(i) * TILE, rnd.nextInt(3)));
        }
        if (!villagers.isEmpty()) unlock("village");

        selectedSlot = 0; inventoryOpen = false; activeContainer = null; containerSlots = null; draggedStack = null;
        gameTime = 0; state = GameState.PLAYING; xp = 0; xpLevel = 0; xpToNext = 20;
        addChatMessage("Добро пожаловать в TerraPixel alpha 1.7.1!", Color.YELLOW);
        addChatMessage("T — чат, E — инвентарь+крафт, ПКМ по жителю — торговля, F11 — fullscreen", Color.CYAN);
        requestFocusInWindow();
    }

    private void respawn() {
        int sx = world.getWidth() / 2;
        int sy = world.findSurface(sx);
        player.x = sx * TILE;
        player.y = (sy - 3) * TILE;
        player.vx = 0; player.vy = 0;
        player.hp = player.maxHp;
        state = GameState.PLAYING;
    }

    private void backToMenu() {
        if (world != null && world.getWidth() > 10) saveCurrentWorld();
        if (mp != null) { mp.close(); mp = null; }
        state = GameState.MENU;
        refreshWorldSaves();
    }

    private void saveCurrentWorld() {
        if (world == null || world.getWidth() <= 10) return;
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("saves/" + currentWorldName + ".world"))) {
            out.writeObject(world);
        } catch (Exception ex) { ex.printStackTrace(); }
    }

    public World getWorld() { return world; }
    public Player getPlayer() { return player; }
    public List<RemotePlayer> getRemotePlayers() { return remotePlayers; }
    public void onRemoteChat(String text) { addChatMessage(text, new Color(100, 240, 255)); }
    public void onRemoteBlockChange(int x, int y, int block) { if (world != null && world.getWidth() > 10) world.setBlock(x, y, block); }
    public void onRemoteBgChange(int x, int y, int block) { if (world != null && world.getWidth() > 10) world.setBackground(x, y, block); }
    public void onRemoteDamage(int dmg) {
        if (player != null && !player.godMode) {
            player.hp -= dmg;
            player.hurtCooldown = 30;
            Sound.play(Sound.HURT);
        }
    }
    public void onWorldReceived(World receivedWorld, double spawnX, double spawnY) {
        this.world = receivedWorld;
        int sx = (int) (spawnX / TILE) + 2;
        if (sx >= world.getWidth() - 2) sx = world.getWidth() / 2;
        int sy = world.findSurface(sx);
        player = new Player(sx * TILE, (sy - 3) * TILE);
        monsters.clear(); villagers.clear(); floaters.clear(); remotePlayers.clear();
        Arrays.fill(inventory, null); Arrays.fill(craftGrid, null);
        Random rnd = new Random();
        for (int i = 0; i < world.villageX.size(); i++) {
            villagers.add(new Villager(world.villageX.get(i) * TILE, world.villageY.get(i) * TILE, rnd.nextInt(3)));
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
            JFrame frame = new JFrame("TerraPixel* alpha 1.7.1");
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
            for (String p : preferred) {
                if (availSet.contains(p)) { chosen = p; break; }
            }
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
            if (isPickaxe(s.id)) {
                if (blockId == World.STONE || blockId == World.COAL || blockId == World.IRON
                        || blockId == World.GOLD || blockId == World.DIAMOND) return 4;
            }
            if (isAxe(s.id)) {
                if (blockId == World.WOOD || blockId == World.LEAVES || blockId == World.PLANK) return 4;
            }
            return 0;
        }

        static boolean isWrongTool(ItemStack s, int blockId) {
            if (s == null) return false;
            if (isPickaxe(s.id)) return blockId == World.WOOD || blockId == World.LEAVES || blockId == World.PLANK;
            if (isAxe(s.id)) return blockId == World.STONE || blockId == World.COAL || blockId == World.IRON
                    || blockId == World.GOLD || blockId == World.DIAMOND;
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
                case World.PICK_WOOD: return "Сила копания: 2 · дерево";
                case World.PICK_STONE: return "Сила копания: 3 · камень";
                case World.PICK_IRON: return "Сила копания: 5 · железо";
                case World.PICK_DIAMOND: return "Сила копания: 8 · алмаз";
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

        private static final List<Recipe> recipes = new ArrayList<>();

        static {
            // Блоки (без верстака)
            recipes.add(new Recipe(World.PLANK, 4, new int[]{World.WOOD}, new int[]{1}, false, "blocks"));
            recipes.add(new Recipe(World.BG_DIRT, 4, new int[]{World.DIRT}, new int[]{1}, false, "blocks"));
            recipes.add(new Recipe(World.BG_STONE, 4, new int[]{World.STONE}, new int[]{1}, false, "blocks"));
            recipes.add(new Recipe(World.BG_WOOD, 4, new int[]{World.WOOD}, new int[]{1}, false, "blocks"));
            recipes.add(new Recipe(World.BG_PLANK, 4, new int[]{World.PLANK}, new int[]{1}, false, "blocks"));

            // База
            recipes.add(new Recipe(World.WORKBENCH, 1, new int[]{World.PLANK}, new int[]{4}, false, "tools"));
            recipes.add(new Recipe(World.TORCH, 4, new int[]{World.COAL_ITEM, World.WOOD}, new int[]{1, 1}, false, "decor"));
            recipes.add(new Recipe(World.CHEST, 1, new int[]{World.PLANK}, new int[]{8}, true, "decor"));
            recipes.add(new Recipe(World.FURNACE, 1, new int[]{World.STONE}, new int[]{8}, true, "decor"));
            recipes.add(new Recipe(World.DOOR_CLOSED, 1, new int[]{World.PLANK, World.WOOD}, new int[]{6, 2}, true, "decor"));

            // Деревянные инструменты
            recipes.add(new Recipe(World.SWORD_WOOD, 1, new int[]{World.PLANK, World.WOOD}, new int[]{2, 1}, true, "tools"));
            recipes.add(new Recipe(World.PICK_WOOD, 1, new int[]{World.PLANK, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.AXE_WOOD, 1, new int[]{World.PLANK, World.WOOD}, new int[]{3, 2}, true, "tools"));

            // Каменные
            recipes.add(new Recipe(World.SWORD_STONE, 1, new int[]{World.STONE, World.WOOD}, new int[]{2, 1}, true, "tools"));
            recipes.add(new Recipe(World.PICK_STONE, 1, new int[]{World.STONE, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.AXE_STONE, 1, new int[]{World.STONE, World.WOOD}, new int[]{3, 2}, true, "tools"));

            // Железные
            recipes.add(new Recipe(World.SWORD_IRON, 1, new int[]{World.IRON_INGOT, World.WOOD}, new int[]{2, 1}, true, "tools"));
            recipes.add(new Recipe(World.PICK_IRON, 1, new int[]{World.IRON_INGOT, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.AXE_IRON, 1, new int[]{World.IRON_INGOT, World.WOOD}, new int[]{3, 2}, true, "tools"));

            // Алмазные
            recipes.add(new Recipe(World.SWORD_DIAMOND, 1, new int[]{World.DIAMOND_GEM, World.WOOD}, new int[]{2, 1}, true, "tools"));
            recipes.add(new Recipe(World.PICK_DIAMOND, 1, new int[]{World.DIAMOND_GEM, World.WOOD}, new int[]{3, 2}, true, "tools"));
            recipes.add(new Recipe(World.AXE_DIAMOND, 1, new int[]{World.DIAMOND_GEM, World.WOOD}, new int[]{3, 2}, true, "tools"));

            // Дополнительные блоки
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
        }

        static Recipe findRecipe(ItemStack[] grid, boolean hasTable) {
            for (Recipe r : recipes) {
                if (r.needsTable && !hasTable) continue;
                if (matchesRecipe(r, grid)) return r;
            }
            return null;
        }

        static List<Recipe> byCategory(String cat) {
            List<Recipe> list = new ArrayList<>();
            for (Recipe r : recipes) if (r.category.equals(cat)) list.add(r);
            return list;
        }

        private static boolean matchesRecipe(Recipe r, ItemStack[] grid) {
            Map<Integer, Integer> avail = new HashMap<>();
            int totalItems = 0;
            for (ItemStack s : grid) {
                if (s == null) continue;
                avail.merge(s.id, s.count, Integer::sum);
                totalItems += s.count;
            }
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

        private final int width, height;
        private final int[][] tiles;
        private final int[][] background;
        public String name = "world";
        public long seed = 0;
        public final List<WorkBlock> workBlocks = new ArrayList<>();
        public final List<Integer> villageX = new ArrayList<>();
        public final List<Integer> villageY = new ArrayList<>();

        public World(int width, int height, long seed) {
            this.width = width;
            this.height = height;
            this.tiles = new int[width][height];
            this.background = new int[width][height];
            this.seed = seed;
            if (width > 10) generate(seed);
        }

        private void generate(long seed) {
            Random rnd = new Random(seed);
            double[] surface = new double[width];
            int base = height / 3;
            int octaves = 5;
            double[] freqs = new double[octaves];
            double[] amps = new double[octaves];
            double[] phases = new double[octaves];
            for (int i = 0; i < octaves; i++) {
                freqs[i] = Math.pow(2, i) * 0.006;
                amps[i] = 18.0 / (i + 1);
                phases[i] = rnd.nextDouble() * Math.PI * 2;
            }
            for (int x = 0; x < width; x++) {
                double h = 0;
                for (int i = 0; i < octaves; i++) h += Math.sin(x * freqs[i] + phases[i]) * amps[i];
                h += (rnd.nextDouble() - 0.5) * 1.5;
                surface[x] = base + h;
            }
            for (int pass = 0; pass < 3; pass++) {
                double[] s2 = surface.clone();
                for (int x = 1; x < width - 1; x++) s2[x] = (surface[x - 1] + surface[x] * 2 + surface[x + 1]) / 4.0;
                surface = s2;
            }

            int[] biome = new int[width];
            int biomeLen = 0;
            int currentBiome = rnd.nextInt(3);
            for (int x = 0; x < width; x++) {
                if (biomeLen <= 0) { currentBiome = rnd.nextInt(3); biomeLen = 40 + rnd.nextInt(60); }
                biome[x] = currentBiome;
                biomeLen--;
            }
            for (int x = 0; x < width; x++) {
                int top = (int) surface[x];
                top = Math.max(10, Math.min(height - 30, top));
                surface[x] = top;
                int surfaceBlock = (biome[x] == 1) ? SAND : (biome[x] == 2 ? SNOW : GRASS);
                int dirtDepth = 4 + rnd.nextInt(3);
                for (int y = 0; y < height; y++) {
                    if (y < top) tiles[x][y] = AIR;
                    else if (y == top) tiles[x][y] = surfaceBlock;
                    else if (y < top + dirtDepth) tiles[x][y] = DIRT;
                    else if (biome[x] == 2 && y < top + 10) tiles[x][y] = ICE;
                    else tiles[x][y] = STONE;
                }
            }

            if (Modules.AUTO_GEN_BG) {
                for (int x = 0; x < width; x++) {
                    int top = (int) surface[x];
                    for (int y = top; y < height; y++) {
                        if (tiles[x][y] == AIR) {
                            if (y < top + 6) background[x][y] = BG_DIRT;
                            else background[x][y] = BG_STONE;
                        }
                    }
                }
            }

            int numCaves = 15 + rnd.nextInt(20);
            for (int i = 0; i < numCaves; i++) {
                int startX = rnd.nextInt(width);
                int startY = (int) (height * (0.35 + rnd.nextDouble() * 0.55));
                int length = 50 + rnd.nextInt(180);
                int radius = 3 + rnd.nextInt(5);
                carveWorm(startX, startY, length, radius, rnd);
            }

            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (tiles[x][y] != STONE) continue;
                    int depth = y - (int) surface[x];
                    double roll = rnd.nextDouble();
                    if (roll < 0.015) { tiles[x][y] = COAL; continue; }
                    if (depth > 6 && roll < 0.028) { tiles[x][y] = IRON; continue; }
                    if (depth > 18 && roll < 0.034) { tiles[x][y] = GOLD; continue; }
                    if (depth > 30 && roll < 0.036) { tiles[x][y] = DIAMOND; continue; }
                }
            }

            for (int x = 4; x < width - 4; x++) {
                if (biome[x] == 1) continue;
                if (rnd.nextDouble() < 0.07) {
                    int top = (int) surface[x];
                    int g = tiles[x][top];
                    if (g != GRASS && g != SNOW) continue;
                    int h = 5 + rnd.nextInt(4);
                    for (int i = 1; i <= h; i++) if (inBounds(x, top - i)) tiles[x][top - i] = WOOD;
                    int leafTop = top - h;
                    for (int dy = -2; dy <= 1; dy++) {
                        for (int dx = -2; dx <= 2; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            if (Math.abs(dx) + Math.abs(dy) > 3) continue;
                            int tx = x + dx, ty = leafTop + dy;
                            if (inBounds(tx, ty) && tiles[tx][ty] == AIR) tiles[tx][ty] = LEAVES;
                        }
                    }
                }
            }

            for (int x = 4; x < width - 4; x++) {
                if (biome[x] != 1) continue;
                if (rnd.nextDouble() < 0.03) {
                    int top = (int) surface[x];
                    if (tiles[x][top] != SAND) continue;
                    int h = 2 + rnd.nextInt(3);
                    for (int i = 1; i <= h; i++) if (inBounds(x, top - i)) tiles[x][top - i] = WOOD;
                }
            }

            for (int x = 0; x < width; x++) {
                int top = (int) surface[x];
                for (int y = top; y < height; y++) {
                    if (tiles[x][y] == AIR) {
                        if (y < top + 6) background[x][y] = BG_DIRT;
                        else background[x][y] = BG_STONE;
                    }
                }
            }

            int numVillages = 1 + rnd.nextInt(3);
            for (int v = 0; v < numVillages; v++) {
                int vx = 80 + rnd.nextInt(width - 160);
                int vy = (int) surface[vx];
                if (vy < 20 || vy > height - 30) continue;
                buildVillage(vx, vy, rnd);
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
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        if (dx * dx + dy * dy > r * r) continue;
                        int tx = cx + dx, ty = cy + dy;
                        if (!inBounds(tx, ty)) continue;
                        int b = tiles[tx][ty];
                        if (b == STONE || b == DIRT || b == GRASS || b == SAND
                                || b == SNOW || b == ICE || b == COAL || b == IRON
                                || b == GOLD || b == DIAMOND) tiles[tx][ty] = AIR;
                    }
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
                int[] a = housePositions.get(i);
                int[] b = housePositions.get(i + 1);
                int y = Math.min(a[1], b[1]);
                for (int x = Math.min(a[0], b[0]); x <= Math.max(a[0], b[0]); x++) {
                    if (inBounds(x, y)) tiles[x][y] = PLANK;
                }
            }
            for (int[] pos : housePositions) {
                buildHouse(pos[0], pos[1], rnd);
                villageX.add(pos[0]);
                villageY.add(pos[1] - 1);
            }
        }

        private void buildHouse(int cx, int cy, Random rnd) {
            int w = 7 + rnd.nextInt(3);
            int h = 5 + rnd.nextInt(2);
            for (int x = -w / 2 - 1; x <= w / 2 + 1; x++) {
                for (int y = 0; y < h + 3; y++) {
                    int tx = cx + x, ty = cy - y;
                    if (inBounds(tx, ty) && y > 0) tiles[tx][ty] = AIR;
                }
            }
            for (int x = -w / 2; x <= w / 2; x++) {
                for (int y = 0; y < h; y++) {
                    int tx = cx + x, ty = cy - y;
                    if (!inBounds(tx, ty)) continue;
                    if (y == 0) tiles[tx][ty] = PLANK;
                    else if (y == h - 1) tiles[tx][ty] = PLANK;
                    else if (x == -w / 2 || x == w / 2) tiles[tx][ty] = PLANK;
                    else if (y == h - 2 && (x == -w / 2 + 1 || x == w / 2 - 1)) tiles[tx][ty] = BG_PLANK;
                    else if (y == 1 && (x == -2 || x == 2)) tiles[tx][ty] = TORCH;
                    else tiles[tx][ty] = AIR;
                }
            }
            int doorX = cx + (rnd.nextBoolean() ? -w / 2 : w / 2);
            int doorY = cy - 1;
            if (inBounds(doorX, doorY)) {
                tiles[doorX][doorY] = DOOR_CLOSED;
                if (inBounds(doorX, doorY - 1)) tiles[doorX][doorY - 1] = DOOR_CLOSED;
            }
            int chestX = cx + rnd.nextInt(w - 2) - (w - 2) / 2;
            int chestY = cy - 1;
            if (inBounds(chestX, chestY) && tiles[chestX][chestY] == AIR) tiles[chestX][chestY] = CHEST;
            int tableX = cx + rnd.nextInt(w - 2) - (w - 2) / 2;
            int tableY = cy - 1;
            if (inBounds(tableX, tableY) && tiles[tableX][tableY] == AIR) tiles[tableX][tableY] = WORKBENCH;
        }

        public void tick() {
            for (int x = 0; x < width; x++) {
                for (int y = height - 2; y >= 0; y--) {
                    if (tiles[x][y] == SAND && tiles[x][y + 1] == AIR) {
                        tiles[x][y + 1] = SAND;
                        tiles[x][y] = AIR;
                    }
                }
            }
        }

        public int findSurface(int x) {
            if (x < 0 || x >= width) return height / 3;
            for (int y = 0; y < height; y++) if (tiles[x][y] != AIR) return y;
            return height / 3;
        }

        public boolean inBounds(int x, int y) { return x >= 0 && y >= 0 && x < width && y < height; }
        public int getBlock(int x, int y) { if (!inBounds(x, y)) return STONE; return tiles[x][y]; }
        public void setBlock(int x, int y, int b) { if (inBounds(x, y)) tiles[x][y] = b; }
        public int getBackground(int x, int y) { if (!inBounds(x, y)) return AIR; return background[x][y]; }
        public void setBackground(int x, int y, int b) { if (inBounds(x, y)) background[x][y] = b; }

        public boolean isSolid(int x, int y) {
            int b = getBlock(x, y);
            if (b == AIR || b == TORCH || b == DOOR_OPEN) return false;
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
        WorkBlock(int x, int y, int type, int size) {
            this.x = x; this.y = y; this.type = type;
            this.slots = new ItemStack[size];
        }
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
                    cookTime = 0;
                    currentRecipe = -1;
                }
                return;
            }
            if (ore == null || recipe == -1) return;
            if (result != null && (result.id != recipe || result.count >= 999)) return;
            if (fuel == null || !isFuel(fuel.id)) return;
            fuel.count--;
            if (fuel.count <= 0) {
                if (slots[1] == fuel) slots[1] = null;
                else slots[2] = null;
            }
            cookTime = COOK_TIME;
            currentRecipe = recipe;
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
        static boolean isFuel(int id) {
            return id == World.COAL || id == World.COAL_ITEM
                    || id == World.WOOD || id == World.LEAVES || id == World.PLANK;
        }
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
        private static final double GRAVITY = 0.7;
        private static final double MOVE_SPEED = 4.5;
        private static final double JUMP_POWER = -12.0;
        private static final double MAX_FALL = 18.0;
        private static final double FRICTION = 0.78;
        public Player(double x, double y) { this.x = x; this.y = y; }
        public void update(double inputX, boolean jump, World world) {
            if (inputX > 0) facing = 1;
            else if (inputX < 0) facing = -1;
            if (inputX != 0) vx += inputX * 1.3;
            vx *= FRICTION;
            if (Math.abs(vx) > MOVE_SPEED) vx = Math.signum(vx) * MOVE_SPEED;
            if (flyMode) { vy = 0; if (jump) vy = -6; }
            else {
                if (jump && onGround) { vy = JUMP_POWER; onGround = false; }
                vy += GRAVITY;
                if (vy > MAX_FALL) vy = MAX_FALL;
            }
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
        }
        private void moveX(double dx, World world) {
            x += dx;
            int top = (int) (y / TILE);
            int bottom = (int) ((y + H - 1) / TILE);
            if (dx > 0) {
                int right = (int) ((x + W - 1) / TILE);
                for (int ty = top; ty <= bottom; ty++) {
                    if (world.isSolid(right, ty)) { x = right * TILE - W; vx = 0; break; }
                }
            } else if (dx < 0) {
                int left = (int) (x / TILE);
                for (int ty = top; ty <= bottom; ty++) {
                    if (world.isSolid(left, ty)) { x = (left + 1) * TILE; vx = 0; break; }
                }
            }
        }
        private void moveY(double dy, World world) {
            y += dy;
            int left = (int) (x / TILE);
            int right = (int) ((x + W - 1) / TILE);
            if (dy > 0) {
                int bottom = (int) ((y + H - 1) / TILE);
                for (int tx = left; tx <= right; tx++) {
                    if (world.isSolid(tx, bottom)) { y = bottom * TILE - H; vy = 0; onGround = true; break; }
                }
            } else if (dy < 0) {
                int top = (int) (y / TILE);
                for (int tx = left; tx <= right; tx++) {
                    if (world.isSolid(tx, top)) { y = (top + 1) * TILE; vy = 0; break; }
                }
            }
            if (dy >= 0) {
                int bottom = (int) ((y + H) / TILE);
                boolean grounded = false;
                for (int tx = left; tx <= right; tx++) {
                    if (world.isSolid(tx, bottom)) { grounded = true; break; }
                }
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
                g.fillRect(px + 9, eyeY, 4, 4);
                g.fillRect(px + 15, eyeY, 4, 4);
                g.setColor(new Color(30, 60, 130));
                g.fillRect(px + 11, eyeY + 1, 2, 3);
                g.fillRect(px + 17, eyeY + 1, 2, 3);
            } else {
                g.fillRect(px + 5, eyeY, 4, 4);
                g.fillRect(px + 11, eyeY, 4, 4);
                g.setColor(new Color(30, 60, 130));
                g.fillRect(px + 5, eyeY + 1, 2, 3);
                g.fillRect(px + 11, eyeY + 1, 2, 3);
            }
        }
        private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    }

    // ==================== MONSTER ====================
    static class Monster {
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
            if (type == 0) hp = maxHp = 30;
            else if (type == 1) hp = maxHp = 20;
            else hp = maxHp = 40;
        }
        void update(Player player, World world, List<Monster> others) {
            if (dead) return;
            animTick++;
            double dx = player.x - x;
            direction = dx > 0 ? 1 : -1;
            vx += direction * 0.3;
            vx *= 0.85;
            if (Math.abs(vx) > 2.2) vx = Math.signum(vx) * 2.2;
            if (onGround && jumpCooldown <= 0) {
                int aheadX = (int) ((x + (direction > 0 ? W + 2 : -2)) / TILE);
                int belowY = (int) ((y + H + 2) / TILE);
                int aheadY = (int) ((y + H - 4) / TILE);
                if (world.isSolid(aheadX, aheadY) || !world.isSolid(aheadX, belowY)) {
                    vy = -8;
                    onGround = false;
                }
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
                if (mr.intersects(pr)) {
                    player.hurt(type == 2 ? 12 : 8);
                    attackCooldown = 40;
                }
            }
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
            int px = (int) (x - camX);
            int py = (int) (y - camY);
            g.setColor(new Color(0, 0, 0, 60));
            g.fillOval(px + 2, py + H - 2, W - 4, 6);
            if (type == 0) {
                g.setColor(new Color(80, 140, 80));
                g.fillRect(px + 3, py, W - 6, H - 4);
                g.setColor(new Color(60, 100, 60));
                g.fillRect(px + 2, py + 8, 3, 12);
                g.fillRect(px + W - 5, py + 8, 3, 12);
                g.setColor(new Color(40, 20, 20));
                g.fillRect(px + 6, py + 6, 4, 4);
                g.fillRect(px + W - 10, py + 6, 4, 4);
            } else if (type == 1) {
                g.setColor(new Color(120, 200, 200, 200));
                int squash = (int) (Math.sin(animTick * 0.15) * 3);
                g.fillOval(px, py + squash, W, H - squash);
                g.setColor(new Color(80, 160, 160));
                g.fillOval(px + 5, py + 6 + squash, W - 10, H - 14);
                g.setColor(Color.BLACK);
                g.fillRect(px + 7, py + 10, 3, 3);
                g.fillRect(px + W - 10, py + 10, 3, 3);
            } else {
                g.setColor(new Color(230, 230, 220));
                g.fillRect(px + 4, py + 4, W - 8, H - 6);
                g.setColor(new Color(200, 200, 190));
                g.fillRect(px + W / 2 - 1, py + 10, 2, H - 14);
                g.fillRect(px + 4, py + 14, W - 8, 2);
                g.fillRect(px + 4, py + 20, W - 8, 2);
                g.setColor(new Color(60, 60, 60));
                g.fillRect(px + 6, py + 6, 4, 5);
                g.fillRect(px + W - 10, py + 6, 4, 5);
            }
            if (hp < maxHp) {
                g.setColor(Color.BLACK);
                g.fillRect(px, py - 8, W, 4);
                g.setColor(Color.RED);
                g.fillRect(px, py - 8, (int) (W * (hp / maxHp)), 4);
            }
        }
    }

    // ==================== VILLAGER (с торговлей) ====================
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

        Villager(double x, double y, int type) {
            this.x = x; this.y = y; this.type = type;
            this.homeX = x;
            initTrades();
        }

        private void initTrades() {
            switch (type) {
                case 0:
                    profession = "Фермер";
                    trades = new int[][]{
                        {World.COIN_GOLD, 1, World.SAND, 8},
                        {World.COIN_GOLD, 2, World.DIRT, 20},
                        {World.COIN_GOLD, 3, World.GRASS, 10},
                        {World.COIN_GOLD, 2, World.WOOD, 12},
                        {World.IRON_INGOT, 1, World.COIN_GOLD, 4},
                        {World.COAL_ITEM, 4, World.COIN_GOLD, 2},
                    };
                    break;
                case 1:
                    profession = "Кузнец";
                    trades = new int[][]{
                        {World.COIN_GOLD, 5, World.IRON_INGOT, 1},
                        {World.COIN_GOLD, 12, World.GOLD_INGOT, 1},
                        {World.IRON_INGOT, 3, World.PICK_IRON, 1},
                        {World.IRON_INGOT, 3, World.SWORD_IRON, 1},
                        {World.IRON_INGOT, 3, World.AXE_IRON, 1},
                        {World.GOLD_INGOT, 2, World.COIN_GOLD, 15},
                        {World.COIN_GOLD, 8, World.FURNACE, 1},
                    };
                    break;
                case 2:
                    profession = "Антиквар";
                    trades = new int[][]{
                        {World.COIN_GOLD, 25, World.DIAMOND_GEM, 1},
                        {World.COIN_GOLD, 10, World.LANTERN, 2},
                        {World.COIN_GOLD, 8, World.TORCH, 16},
                        {World.COIN_GOLD, 15, World.CHEST, 1},
                        {World.DIAMOND_GEM, 1, World.COIN_GOLD, 30},
                        {World.GOLD_INGOT, 2, World.COIN_GOLD, 15},
                    };
                    break;
                default:
                    profession = "Житель";
                    trades = new int[][]{{World.COIN_GOLD, 1, World.DIRT, 5}};
                    break;
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
            int top = (int) (y / TILE);
            int bottom = (int) ((y + Player.H - 1) / TILE);
            if (dx > 0) {
                int right = (int) ((x + Player.W - 1) / TILE);
                for (int ty = top; ty <= bottom; ty++) if (world.isSolid(right, ty)) { x = right * TILE - Player.W; vx = 0; break; }
            } else if (dx < 0) {
                int left = (int) (x / TILE);
                for (int ty = top; ty <= bottom; ty++) if (world.isSolid(left, ty)) { x = (left + 1) * TILE; vx = 0; break; }
            }
        }

        private void moveY(double dy, World world) {
            y += dy;
            int left = (int) (x / TILE);
            int right = (int) ((x + Player.W - 1) / TILE);
            if (dy > 0) {
                int bottom = (int) ((y + Player.H - 1) / TILE);
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, bottom)) { y = bottom * TILE - Player.H; vy = 0; onGround = true; break; }
            } else if (dy < 0) {
                int top = (int) (y / TILE);
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, top)) { y = (top + 1) * TILE; vy = 0; break; }
            }
            if (dy >= 0) {
                int bottom = (int) ((y + Player.H) / TILE);
                boolean grounded = false;
                for (int tx = left; tx <= right; tx++) if (world.isSolid(tx, bottom)) { grounded = true; break; }
                onGround = grounded;
            }
        }

        void draw(Graphics2D g, int camX, int camY) {
            int px = (int) (x - camX);
            int py = (int) (y - camY);
            g.setColor(new Color(0, 0, 0, 70));
            g.fillOval(px + 2, py + Player.H - 3, Player.W - 4, 6);
            Color body = type == 0 ? new Color(200, 140, 80) : type == 1 ? new Color(110, 150, 200) : new Color(170, 110, 180);
            int legPhase = (animTick / 6) % 4;
            int legOff = legPhase == 0 ? 0 : (legPhase == 1 ? 2 : (legPhase == 2 ? 0 : -2));
            g.setColor(new Color(70, 45, 30));
            g.fillRect(px + 5, py + Player.H - 10 + Math.abs(legOff), 5, 10 - Math.abs(legOff));
            g.fillRect(px + Player.W - 10, py + Player.H - 10 + Math.abs(-legOff), 5, 10 - Math.abs(-legOff));
            g.setColor(body);
            g.fillRect(px + 3, py + 14, Player.W - 6, Player.H - 24);
            g.setColor(new Color(60, 40, 25));
            g.fillRect(px + 3, py + Player.H - 16, Player.W - 6, 3);
            g.setColor(new Color(240, 200, 160));
            g.fillRect(px - 1, py + 17, 4, 11);
            g.fillRect(px + Player.W - 3, py + 17, 4, 11);
            g.setColor(new Color(245, 205, 165));
            g.fillRect(px + 3, py, Player.W - 6, 14);
            if (type == 0) {
                g.setColor(new Color(230, 200, 90));
                g.fillRect(px + 1, py - 2, Player.W - 2, 4);
                g.fillRect(px + 6, py - 6, Player.W - 12, 5);
                g.setColor(new Color(200, 170, 60));
                g.fillRect(px + 6, py - 3, Player.W - 12, 1);
            } else if (type == 1) {
                g.setColor(new Color(50, 35, 25));
                g.fillRect(px + 3, py, Player.W - 6, 4);
                g.fillRect(px + 3, py, 3, 7);
                g.fillRect(px + Player.W - 6, py, 3, 7);
            } else {
                g.setColor(new Color(200, 200, 210));
                g.fillRect(px + 3, py, Player.W - 6, 4);
                g.fillRect(px + 3, py, 3, 8);
                g.fillRect(px + Player.W - 6, py, 3, 8);
            }
            g.setColor(Color.WHITE);
            int eyeY = py + 6;
            if (direction == 1) {
                g.fillRect(px + 9, eyeY, 4, 4);
                g.fillRect(px + 15, eyeY, 4, 4);
                g.setColor(new Color(30, 40, 80));
                g.fillRect(px + 11, eyeY + 1, 2, 3);
                g.fillRect(px + 17, eyeY + 1, 2, 3);
            } else {
                g.fillRect(px + 5, eyeY, 4, 4);
                g.fillRect(px + 11, eyeY, 4, 4);
                g.setColor(new Color(30, 40, 80));
                g.fillRect(px + 5, eyeY + 1, 2, 3);
                g.fillRect(px + 11, eyeY + 1, 2, 3);
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
        String name = "Игрок";
        long lastUpdate = System.currentTimeMillis();
        double targetX, targetY;
        RemotePlayer(int id, double x, double y) {
            this.id = id; this.x = x; this.y = y;
            this.targetX = x; this.targetY = y;
            this.name = "Игрок #" + id;
        }
        void updatePosition(double nx, double ny, int facing) {
            this.targetX = nx; this.targetY = ny; this.facing = facing;
            this.lastUpdate = System.currentTimeMillis();
        }
        void tick() {
            x += (targetX - x) * 0.25;
            y += (targetY - y) * 0.25;
        }
        void draw(Graphics2D g, int camX, int camY) {
            int px = (int) (x - camX);
            int py = (int) (y - camY);
            g.setColor(new Color(0, 0, 0, 80));
            g.fillRect(px + 2, py + Player.H - 3, Player.W - 4, 3);
            g.setColor(new Color(180, 80, 200));
            g.fillRect(px + 3, py + 14, Player.W - 6, Player.H - 22);
            g.setColor(new Color(50, 30, 20));
            g.fillRect(px + 3, py + Player.H - 14, Player.W - 6, 3);
            g.setColor(new Color(240, 200, 150));
            g.fillRect(px - 1, py + 16, 4, 12);
            g.fillRect(px + Player.W - 3, py + 16, 4, 12);
            g.setColor(new Color(250, 210, 160));
            g.fillRect(px + 3, py, Player.W - 6, 14);
            g.setColor(new Color(180, 80, 30));
            g.fillRect(px + 3, py, Player.W - 6, 4);
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
                    wsp.world = game.getWorld();
                    wsp.spawnX = game.getPlayer().x;
                    wsp.spawnY = game.getPlayer().y;
                    oos.writeObject(wsp);
                    oos.flush();
                    final int cid = id;
                    SwingUtilities.invokeLater(() -> game.onRemoteChat("§eИгрок #" + cid + " подключился"));
                }
                while (running && !s.isClosed()) {
                    Object obj = ois.readObject();
                    processPacket(obj, id);
                }
            } catch (Exception e) {
                clients.remove(id); clientOuts.remove(id); clientIns.remove(id);
                final int cid = id;
                SwingUtilities.invokeLater(() -> game.onRemoteChat("§cИгрок #" + cid + " отключился"));
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
            } catch (Exception e) {
                if (running) SwingUtilities.invokeLater(() -> game.onRemoteChat("§cСоединение с сервером потеряно"));
            }
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
        }

        void tick(Player player) {
            if (!running || player == null) return;
            PlayerUpdatePacket p = new PlayerUpdatePacket();
            p.playerId = host ? 0 : 999;
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
                        try {
                            synchronized (oos) { oos.writeObject(packet); oos.flush(); oos.reset(); }
                        } catch (Exception ignored) {}
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
    }

    // ==================== ПАКЕТЫ ====================
    static class WorldSyncPacket implements Serializable {
        private static final long serialVersionUID = 1L;
        World world; double spawnX, spawnY;
    }
    static class PlayerUpdatePacket implements Serializable {
        private static final long serialVersionUID = 1L;
        int playerId; double x, y; int facing;
    }
    static class ChatPacket implements Serializable {
        private static final long serialVersionUID = 1L;
        String text;
    }
    static class BlockChangePacket implements Serializable {
        private static final long serialVersionUID = 1L;
        int x, y, block;
    }
    static class BgChangePacket implements Serializable {
        private static final long serialVersionUID = 1L;
        int x, y, block;
    }
    static class DamagePacket implements Serializable {
        private static final long serialVersionUID = 1L;
        int targetId, dmg;
    }

    // ==================== ACHIEVEMENT / FLOATINGTEXT / CHATMESSAGE / WORLDSAVE ====================
    static class Achievement {
        String id, title, description;
        boolean unlocked = false;
        Achievement(String id, String title, String desc) { this.id = id; this.title = title; this.description = desc; }
    }
    static class FloatingText {
        String text; double x, y; Color color; int life = 70;
        FloatingText(String t, double x, double y, Color c) { this.text = t; this.x = x; this.y = y; this.color = c; }
    }
    static class ChatMessage {
        String text; Color color; long time;
        ChatMessage(String t, Color c, long time) { this.text = t; this.color = c; this.time = time; }
    }
    static class WorldSave {
        File file; String name; long size; String dateStr; int sizeW, sizeH;
    }

    // ==================== SOUND ====================
    static class Sound {
        public static final int CLICK = 0;
        public static final int MINE = 1;
        public static final int HIT = 2;
        public static final int HURT = 3;
        public static final int TRADE = 4;
        public static final int ACHIEVEMENT = 5;
        public static final int DEATH = 6;
        public static final int START = 7;
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
        private static volatile boolean playing = false;
        private static volatile boolean loop = false;
        private static Thread musicThread;
        private static SourceDataLine currentLine;
        private static volatile float volume = 0.55f;
        public static void playMenuMusic() {
            if (playing) return;
            playing = true; loop = true;
            musicThread = new Thread(() -> {
                try {
                    AudioFormat fmt = new AudioFormat(44100, 16, 1, true, false);
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                    if (!AudioSystem.isLineSupported(info)) { playing = false; return; }
                    byte[] melody = generateSoftMelody();
                    while (loop && !Thread.currentThread().isInterrupted()) {
                        currentLine = (SourceDataLine) AudioSystem.getLine(info);
                        currentLine.open(fmt); currentLine.start();
                        int chunkSize = 4096;
                        for (int i = 0; i < melody.length && loop; i += chunkSize) {
                            int len = Math.min(chunkSize, melody.length - i);
                            currentLine.write(melody, i, len);
                        }
                        currentLine.drain(); currentLine.stop(); currentLine.close();
                        currentLine = null;
                    }
                } catch (Exception e) {}
                playing = false;
            }, "Music-Thread");
            musicThread.setDaemon(true);
            musicThread.start();
        }
        public static void stop() {
            loop = false;
            if (currentLine != null) { try { currentLine.stop(); } catch (Exception ignored) {} try { currentLine.flush(); } catch (Exception ignored) {} }
            if (musicThread != null) { musicThread.interrupt(); musicThread = null; }
            playing = false;
        }
        public static void setVolume(float v) { volume = Math.max(0f, Math.min(1f, v)); }
        private static byte[] generateSoftMelody() {
            int sampleRate = 44100;
            double[][] chords = {
                {261.63, 329.63, 392.00, 493.88},
                {220.00, 261.63, 329.63, 392.00},
                {174.61, 220.00, 261.63, 329.63},
                {196.00, 246.94, 293.66, 349.23},
                {164.81, 196.00, 246.94, 293.66},
                {220.00, 261.63, 329.63, 392.00},
                {146.83, 174.61, 220.00, 261.63},
                {196.00, 246.94, 293.66, 349.23}
            };
            double[] melody = {
                523.25, 587.33, 659.25, 587.33,
                440.00, 523.25, 587.33, 523.25,
                349.23, 392.00, 440.00, 392.00,
                392.00, 440.00, 493.88, 440.00,
                329.63, 392.00, 493.88, 392.00,
                440.00, 523.25, 659.25, 523.25,
                293.66, 349.23, 440.00, 349.23,
                392.00, 440.00, 493.88, 523.25
            };
            double chordDuration = 4.0;
            int chordSamples = (int) (sampleRate * chordDuration);
            int totalSamples = chordSamples * chords.length;
            int melodyNoteSamples = chordSamples / 4;
            float[] mixBuf = new float[totalSamples];
            for (int c = 0; c < chords.length; c++) {
                int baseIdx = c * chordSamples;
                for (int n = 0; n < chords[c].length; n++) {
                    double freq = chords[c][n];
                    double noteAmp = 0.22 / (n + 1);
                    double phase = (n * 0.7) % (2 * Math.PI);
                    for (int i = 0; i < chordSamples; i++) {
                        double t = i / (double) sampleRate;
                        double attack = Math.min(1.0, i / (sampleRate * 0.4));
                        double release = Math.min(1.0, (chordSamples - i) / (sampleRate * 1.2));
                        double env = Math.pow(attack * release, 1.5);
                        double tremolo = 0.9 + 0.1 * Math.sin(2 * Math.PI * 3 * t);
                        double v = Math.sin(2 * Math.PI * freq * t + phase) + 0.3 * Math.sin(4 * Math.PI * freq * t + phase);
                        mixBuf[baseIdx + i] += (float) (v * noteAmp * env * tremolo);
                    }
                }
            }
            for (int m = 0; m < melody.length; m++) {
                double freq = melody[m];
                int chordIdx = m / 4;
                int noteIdx = m % 4;
                int baseIdx = chordIdx * chordSamples + noteIdx * melodyNoteSamples;
                double phase = (m * 0.3) % (2 * Math.PI);
                for (int i = 0; i < melodyNoteSamples; i++) {
                    double t = i / (double) sampleRate;
                    double attack = Math.min(1.0, i / (sampleRate * 0.15));
                    double release = Math.min(1.0, (melodyNoteSamples - i) / (sampleRate * 0.5));
                    double env = Math.pow(attack * release, 1.2);
                    double vibrato = 1.0 + 0.006 * Math.sin(2 * Math.PI * 5 * t);
                    double v = Math.sin(2 * Math.PI * freq * vibrato * t + phase);
                    v += 0.25 * Math.sin(4 * Math.PI * freq * vibrato * t + phase);
                    mixBuf[baseIdx + i] += (float) (v * 0.10 * env);
                }
            }
            double[] bassNotes = {130.81, 110.00, 87.31, 98.00, 82.41, 110.00, 73.42, 98.00};
            for (int c = 0; c < bassNotes.length; c++) {
                double freq = bassNotes[c];
                int baseIdx = c * chordSamples;
                for (int i = 0; i < chordSamples; i++) {
                    double t = i / (double) sampleRate;
                    double attack = Math.min(1.0, i / (sampleRate * 0.5));
                    double release = Math.min(1.0, (chordSamples - i) / (sampleRate * 1.5));
                    double env = attack * release;
                    double v = Math.sin(2 * Math.PI * freq * t);
                    mixBuf[baseIdx + i] += (float) (v * 0.15 * env);
                }
            }
            float[] reverb = new float[totalSamples];
            int delay1 = (int) (sampleRate * 0.12);
            int delay2 = (int) (sampleRate * 0.23);
            for (int i = 0; i < totalSamples; i++) {
                float v = mixBuf[i];
                if (i >= delay1) v += reverb[i - delay1] * 0.35f;
                if (i >= delay2) v += reverb[i - delay2] * 0.20f;
                reverb[i] = v;
            }
            byte[] out = new byte[totalSamples * 2];
            int fadeInSamples = (int) (sampleRate * 2.0);
            int fadeOutSamples = (int) (sampleRate * 3.0);
            for (int i = 0; i < totalSamples; i++) {
                double sample = reverb[i];
                if (i < fadeInSamples) sample *= (i / (double) fadeInSamples);
                if (i > totalSamples - fadeOutSamples) {
                    double f = (totalSamples - i) / (double) fadeOutSamples;
                    sample *= f;
                }
                sample = Math.tanh(sample * 1.2) * volume;
                short s = (short) (sample * 32767 * 0.55);
                out[i * 2] = (byte) (s & 0xFF);
                out[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
            }
            return out;
        }
    }

    // ==================== TEXTURES (улучшенные) ====================
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
                case World.DIRT:
                    fillNoise(g, rnd, P, new Color(120, 80, 45), new Color(85, 55, 30), new Color(150, 105, 65), 40); break;
                case World.STONE:
                    fillNoise(g, rnd, P, new Color(125, 125, 135), new Color(95, 95, 105), new Color(155, 155, 165), 30); break;
                case World.GRASS:
                    drawGrass(g, rnd, P); break;
                case World.SAND:
                    fillNoise(g, rnd, P, new Color(225, 205, 130), new Color(195, 175, 100), new Color(245, 225, 160), 25); break;
                case World.WOOD:
                    drawWood(g, rnd, P); break;
                case World.LEAVES:
                    drawLeaves(g, rnd, P); break;
                case World.COAL:
                    drawOre(g, P, new Color(40, 40, 45), new Color(15, 15, 20), new Color(70, 70, 75)); break;
                case World.IRON:
                    drawOre(g, P, new Color(180, 160, 140), new Color(140, 120, 100), new Color(220, 200, 180)); break;
                case World.GOLD:
                    drawOre(g, P, new Color(230, 200, 70), new Color(180, 150, 30), new Color(255, 230, 120)); break;
                case World.DIAMOND:
                    drawOre(g, P, new Color(130, 220, 230), new Color(80, 170, 190), new Color(200, 245, 255)); break;
                case World.CHEST:
                    drawChest(g, P); break;
                case World.FURNACE:
                    drawFurnace(g, P); break;
                case World.WORKBENCH:
                case World.CRAFT_TABLE:
                    drawWorkbench(g, P); break;
                case World.TORCH:
                    drawTorch(g, P); break;
                case World.PLANK:
                    drawPlank(g, rnd, P); break;
                case World.COIN_GOLD:
                    drawCoin(g, P); break;
                case World.IRON_INGOT:
                    drawIngot(g, P, new Color(200, 200, 210), new Color(240, 240, 250), new Color(140, 140, 150)); break;
                case World.GOLD_INGOT:
                    drawIngot(g, P, new Color(255, 215, 60), new Color(255, 240, 130), new Color(190, 150, 20)); break;
                case World.DIAMOND_GEM:
                    drawGem(g, P); break;
                case World.COAL_ITEM:
                    drawCoal(g, P); break;
                case World.SNOW:
                    fillNoise(g, rnd, P, new Color(245, 250, 255), new Color(220, 230, 245), new Color(255, 255, 255), 20); break;
                case World.ICE:
                    fillNoise(g, rnd, P, new Color(150, 200, 240), new Color(110, 170, 220), new Color(200, 230, 250), 25); break;
                case World.SWORD_WOOD: case World.SWORD_STONE: case World.SWORD_IRON: case World.SWORD_DIAMOND:
                    drawSword(g, P, getToolBase(block), getToolHead(block)); break;
                case World.PICK_WOOD: case World.PICK_STONE: case World.PICK_IRON: case World.PICK_DIAMOND:
                    drawPickaxe(g, P, getToolBase(block), getToolHead(block)); break;
                case World.AXE_WOOD: case World.AXE_STONE: case World.AXE_IRON: case World.AXE_DIAMOND:
                    drawAxe(g, P, getToolBase(block), getToolHead(block)); break;
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
                default:
                    fillNoise(g, rnd, P, new Color(125, 125, 135), new Color(95, 95, 105), new Color(155, 155, 165), 30); break;
            }
            g.dispose();
            return img;
        }
        private static Color getToolBase(int id) { return new Color(100, 65, 35); }
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
                if (v < noiseLevel / 2) c = dark;
                else if (v > 100 - noiseLevel / 2) c = light;
                g.setColor(c);
                g.fillRect(x * P, y * P, P, P);
            }
            g.setColor(dark.darker());
            for (int i = 0; i < 5; i++) { int x = rnd.nextInt(16), y = rnd.nextInt(16); g.fillRect(x * P, y * P, P, P); }
            g.setColor(new Color(255, 255, 255, 35));
            g.fillRect(0, 0, SIZE, 2); g.fillRect(0, 0, 2, SIZE);
            g.setColor(new Color(0, 0, 0, 55));
            g.fillRect(0, SIZE - 2, SIZE, 2); g.fillRect(SIZE - 2, 0, 2, SIZE);
            g.setColor(new Color(0, 0, 0, 70));
            g.drawRect(0, 0, SIZE - 1, SIZE - 1);
        }
        private static void drawGrass(Graphics2D g, Random rnd, int P) {
            fillNoise(g, rnd, P, new Color(120, 80, 45), new Color(85, 55, 30), new Color(150, 105, 65), 30);
            for (int x = 0; x < 16; x++) {
                int h = 2 + rnd.nextInt(4);
                for (int y = 0; y < h; y++) {
                    int shade = rnd.nextInt(3);
                    Color c = shade == 0 ? new Color(50, 140, 50) : shade == 1 ? new Color(80, 180, 80) : new Color(110, 210, 110);
                    g.setColor(c);
                    g.fillRect(x * P, y * P, P, P);
                }
                if (rnd.nextBoolean()) { g.setColor(new Color(40, 160, 40)); g.fillRect(x * P + P / 2, h * P, P / 2, P); }
            }
            g.setColor(new Color(0, 0, 0, 40));
            g.fillRect(0, SIZE - 2, SIZE, 2);
        }
        private static void drawWood(Graphics2D g, Random rnd, int P) {
            for (int x = 0; x < 16; x++) {
                int baseVar = rnd.nextInt(20);
                Color c = new Color(140 + baseVar, 95 + baseVar / 2, 55 + baseVar / 2);
                for (int y = 0; y < 16; y++) { g.setColor(c); g.fillRect(x * P, y * P, P, P); }
            }
            g.setColor(new Color(80, 50, 25));
            for (int x = 0; x < 16; x += 4) g.fillRect(x * P, 0, 1, SIZE);
            g.setColor(new Color(255, 255, 255, 25));
            g.fillRect(0, 0, SIZE, 2);
        }
        private static void drawLeaves(Graphics2D g, Random rnd, int P) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int v = rnd.nextInt(100);
                Color c = v < 25 ? new Color(30, 90, 30) : v < 60 ? new Color(50, 130, 50) : new Color(80, 170, 80);
                g.setColor(c);
                g.fillRect(x * P, y * P, P, P);
            }
            g.setColor(new Color(20, 70, 20));
            for (int i = 0; i < 12; i++) { int x = rnd.nextInt(16), y = rnd.nextInt(16); g.fillRect(x * P, y * P, P, P); }
        }
        private static void drawOre(Graphics2D g, int P, Color base, Color dark, Color light) {
            Random rnd = new Random(999);
            fillNoise(g, rnd, P, new Color(120, 120, 130), new Color(90, 90, 100), new Color(155, 155, 165), 25);
            Random r2 = new Random(555);
            int clusters = 5 + r2.nextInt(4);
            for (int i = 0; i < clusters; i++) {
                int cx = 1 + r2.nextInt(14);
                int cy = 1 + r2.nextInt(14);
                int size = 1 + r2.nextInt(2);
                for (int dy = -size; dy <= size; dy++) for (int dx = -size; dx <= size; dx++) {
                    if (dx * dx + dy * dy > size * size + 1) continue;
                    int x = cx + dx, y = cy + dy;
                    if (x < 0 || x > 15 || y < 0 || y > 15) continue;
                    g.setColor(base);
                    g.fillRect(x * P, y * P, P, P);
                    if (dx == 0 && dy == 0) { g.setColor(light); g.fillRect(x * P + P / 3, y * P + P / 3, P / 3, P / 3); }
                }
            }
        }
        private static void drawChest(Graphics2D g, int P) {
            g.setColor(new Color(160, 100, 45)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(190, 130, 70)); g.fillRect(0, 0, SIZE, 3 * P);
            g.setColor(new Color(90, 55, 20));
            g.fillRect(0, 0, SIZE, P); g.fillRect(0, 3 * P, SIZE, P);
            g.fillRect(0, 0, P, SIZE); g.fillRect(SIZE - P, 0, P, SIZE);
            g.fillRect(0, SIZE - P, SIZE, P);
            g.setColor(new Color(130, 80, 35));
            for (int x = 3; x < 16; x += 4) g.fillRect(x * P, 4 * P, P / 2, 11 * P);
            g.setColor(new Color(255, 220, 80)); g.fillRect(7 * P, 5 * P, 2 * P, 3 * P);
            g.setColor(new Color(200, 150, 30)); g.fillRect(7 * P + P / 2, 6 * P + P / 2, P, P);
            g.setColor(new Color(255, 255, 255, 60)); g.fillRect(P, P, 2 * P, P);
        }
        private static void drawFurnace(Graphics2D g, int P) {
            Random rnd = new Random(777);
            fillNoise(g, rnd, P, new Color(90, 90, 95), new Color(60, 60, 65), new Color(120, 120, 130), 25);
            g.setColor(new Color(30, 30, 30)); g.fillRect(4 * P, 8 * P, 8 * P, 6 * P);
            g.setColor(new Color(255, 140, 40)); g.fillRect(5 * P, 11 * P, 6 * P, 3 * P);
            g.setColor(new Color(255, 220, 80)); g.fillRect(5 * P, 12 * P, 6 * P, 2 * P);
            g.setColor(new Color(255, 240, 150)); g.fillRect(6 * P, 12 * P, P, P); g.fillRect(9 * P, 12 * P, P, P);
            g.setColor(new Color(180, 180, 190)); g.drawRect(4 * P, 8 * P, 8 * P, 6 * P);
        }
        private static void drawWorkbench(Graphics2D g, int P) {
            g.setColor(new Color(180, 130, 80)); g.fillRect(0, 0, SIZE, 3 * P);
            g.setColor(new Color(140, 95, 55)); g.fillRect(0, 0, SIZE, P);
            g.setColor(new Color(150, 100, 55));
            g.fillRect(P, 3 * P, 3 * P, 13 * P);
            g.fillRect(SIZE - 4 * P, 3 * P, 3 * P, 13 * P);
            g.setColor(new Color(120, 80, 45)); g.fillRect(0, 8 * P, SIZE, 2 * P);
            g.setColor(new Color(200, 200, 210)); g.fillRect(6 * P, P, P, 2 * P);
            g.setColor(new Color(140, 95, 55)); g.fillRect(6 * P, 3 * P, P, P);
        }
        private static void drawTorch(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(120, 80, 45)); g.fillRect(7 * P, 6 * P, 2 * P, 10 * P);
            g.setColor(new Color(255, 140, 40)); g.fillRect(6 * P, 3 * P, 4 * P, 5 * P);
            g.setColor(new Color(255, 220, 80)); g.fillRect(7 * P, 2 * P, 2 * P, 4 * P);
            g.setColor(new Color(255, 250, 200)); g.fillRect(7 * P, 3 * P, P, 2 * P);
        }
        private static void drawPlank(Graphics2D g, Random rnd, int P) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int v = rnd.nextInt(20);
                g.setColor(new Color(180 + v, 130 + v, 80 + v));
                g.fillRect(x * P, y * P, P, P);
            }
            g.setColor(new Color(100, 65, 35));
            for (int y = 4; y < 16; y += 4) g.fillRect(0, y * P, SIZE, P / 2);
            g.setColor(new Color(255, 255, 255, 25)); g.fillRect(0, 0, SIZE, 2);
        }
        private static void drawCoin(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                int dx = x - 7, dy = y - 7;
                int d2 = dx * dx + dy * dy;
                if (d2 <= 30) {
                    Color c = d2 <= 8 ? new Color(255, 240, 130) : d2 <= 20 ? new Color(255, 215, 60) : new Color(190, 150, 20);
                    g.setColor(c); g.fillRect(x * P, y * P, P, P);
                }
            }
            g.setColor(new Color(140, 100, 10));
            g.fillRect(7 * P, 6 * P, P, 4 * P);
            g.fillRect(8 * P, 6 * P, 2 * P, P);
            g.fillRect(8 * P, 9 * P, 2 * P, P);
        }
        private static void drawIngot(Graphics2D g, int P, Color base, Color light, Color dark) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(dark); g.fillRect(2 * P, 9 * P, 12 * P, 3 * P);
            g.setColor(base); g.fillRect(3 * P, 6 * P, 10 * P, 4 * P);
            g.setColor(light); g.fillRect(4 * P, 5 * P, 8 * P, 2 * P);
            g.setColor(new Color(255, 255, 255, 180)); g.fillRect(5 * P, 5 * P, 2 * P, P);
            g.setColor(dark); g.drawRect(3 * P, 6 * P, 10 * P, 4 * P); g.drawRect(2 * P, 9 * P, 12 * P, 3 * P);
        }
        private static void drawGem(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            int[][] shape = {
                {0, 0, 0, 1, 2, 1, 0, 0, 0},
                {0, 0, 1, 2, 3, 2, 1, 0, 0},
                {0, 1, 2, 3, 3, 3, 2, 1, 0},
                {1, 2, 3, 3, 3, 3, 3, 2, 1},
                {0, 1, 2, 3, 3, 3, 2, 1, 0},
                {0, 0, 1, 2, 3, 2, 1, 0, 0},
                {0, 0, 0, 1, 2, 1, 0, 0, 0}
            };
            for (int y = 0; y < shape.length; y++) for (int x = 0; x < shape[y].length; x++) {
                int v = shape[y][x];
                if (v == 0) continue;
                Color c = v == 1 ? new Color(80, 180, 200) : v == 2 ? new Color(150, 240, 250) : new Color(220, 255, 255);
                g.setColor(c); g.fillRect((x + 3) * P, (y + 4) * P, P, P);
            }
            g.setColor(new Color(255, 255, 255, 220)); g.fillRect(7 * P, 5 * P, P, P);
        }
        private static void drawCoal(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(40, 40, 45)); g.fillOval(3 * P, 4 * P, 10 * P, 9 * P);
            g.setColor(new Color(70, 70, 75)); g.fillOval(4 * P, 5 * P, 5 * P, 4 * P);
            g.setColor(new Color(90, 90, 95)); g.fillRect(5 * P, 6 * P, P, P);
        }
        private static void drawSword(Graphics2D g, int P, Color base, Color head) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(base); g.fillRect(2 * P, 12 * P, 6 * P, 3 * P);
            g.setColor(base.darker()); g.fillRect(2 * P, 12 * P, P, 3 * P); g.fillRect(7 * P, 12 * P, P, 3 * P);
            g.setColor(new Color(80, 50, 25)); g.fillRect(6 * P, 10 * P, 4 * P, 2 * P);
            g.setColor(head);
            for (int i = 0; i < 8; i++) { g.fillRect((7 - i / 2) * P, (9 - i) * P, P, P); g.fillRect((8 - i / 2) * P, (9 - i) * P, P, P); }
            g.setColor(head.brighter()); g.fillRect(6 * P, P, P, P); g.fillRect(8 * P, P, P, P);
        }
        private static void drawPickaxe(Graphics2D g, int P, Color base, Color head) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(base);
            for (int i = 0; i < 10; i++) { g.fillRect((4 + i / 2) * P, (13 - i) * P, P, P); g.fillRect((5 + i / 2) * P, (13 - i) * P, P, P); }
            g.setColor(head);
            g.fillRect(5 * P, 2 * P, 2 * P, P); g.fillRect(7 * P, P, 3 * P, P);
            g.fillRect(10 * P, 2 * P, 2 * P, P); g.fillRect(11 * P, 3 * P, P, P); g.fillRect(4 * P, 3 * P, P, P);
            g.setColor(head.brighter()); g.fillRect(6 * P, 2 * P, P, P); g.fillRect(8 * P, 2 * P, P, P);
        }
        private static void drawAxe(Graphics2D g, int P, Color base, Color head) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(base);
            for (int i = 0; i < 10; i++) { g.fillRect((4 + i / 2) * P, (13 - i) * P, P, P); g.fillRect((5 + i / 2) * P, (13 - i) * P, P, P); }
            g.setColor(head); g.fillRect(7 * P, 2 * P, 5 * P, 5 * P);
            g.setColor(head.brighter()); g.fillRect(8 * P, 3 * P, 3 * P, 3 * P);
            g.setColor(head.darker()); g.drawRect(7 * P, 2 * P, 5 * P, 5 * P);
        }
        private static void drawDoor(Graphics2D g, int P, boolean closed) {
            if (closed) {
                g.setColor(new Color(150, 90, 40)); g.fillRect(0, 0, SIZE, SIZE);
                g.setColor(new Color(90, 55, 20)); g.drawRect(0, 0, SIZE - 1, SIZE - 1);
                g.drawRect(P, P, SIZE - 2 * P, SIZE - 2 * P);
                g.fillRect(SIZE / 2 - P / 2, P, P, SIZE - 2 * P);
                g.setColor(new Color(255, 220, 80)); g.fillRect(SIZE - 3 * P, SIZE / 2 - P, P, P);
            } else {
                g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
                g.setColor(new Color(150, 90, 40)); g.fillRect(0, 0, 3 * P, SIZE);
                g.setColor(new Color(90, 55, 20)); g.drawRect(0, 0, 3 * P, SIZE - 1);
            }
        }
        private static void drawBgBlock(Graphics2D g, int P, Color base, Color dark) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                g.setColor((x + y) % 2 == 0 ? base : dark);
                g.fillRect(x * P, y * P, P, P);
            }
        }
        private static void drawBookshelf(Graphics2D g, int P) {
            g.setColor(new Color(140, 95, 55)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(100, 65, 35));
            for (int y = 0; y < 16; y += 2) for (int x = 0; x < 16; x += 4) g.fillRect(x * P, y * P, 2 * P, P);
            Color[] bookColors = { new Color(180, 60, 60), new Color(60, 120, 180), new Color(60, 160, 80), new Color(200, 160, 60), new Color(140, 80, 180) };
            for (int row = 0; row < 4; row++) for (int i = 0; i < 5; i++) {
                g.setColor(bookColors[(row * 5 + i) % bookColors.length]);
                g.fillRect((i * 3 + 1) * P, (row * 4 + 1) * P, 2 * P, 3 * P);
            }
        }
        private static void drawGlass(Graphics2D g, int P) {
            g.setColor(new Color(180, 220, 240, 80)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(255, 255, 255, 160)); g.fillRect(0, 0, SIZE, 2); g.fillRect(0, 0, 2, SIZE);
            g.setColor(new Color(200, 240, 255, 120)); g.fillRect(P, P, SIZE - 2 * P, 2); g.fillRect(P, P, 2, SIZE - 2 * P);
            g.setColor(new Color(100, 160, 200, 180)); g.drawRect(0, 0, SIZE - 1, SIZE - 1);
        }
        private static void drawLantern(Graphics2D g, int P) {
            g.setColor(new Color(0, 0, 0, 0)); g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(new Color(120, 80, 45)); g.fillRect(7 * P, 0, 2 * P, 2 * P);
            g.setColor(new Color(255, 220, 80)); g.fillRect(5 * P, 3 * P, 6 * P, 8 * P);
            g.setColor(new Color(255, 250, 200)); g.fillRect(6 * P, 4 * P, 4 * P, 6 * P);
            g.setColor(new Color(255, 240, 120, 120)); g.fillRect(3 * P, 2 * P, 10 * P, 10 * P);
            g.setColor(new Color(120, 80, 45)); g.fillRect(5 * P, 11 * P, 6 * P, P);
            g.drawRect(5 * P, 3 * P, 6 * P, 8 * P);
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
            g.setColor(new Color(90, 60, 30)); g.drawRect(2 * P, 3 * P, 12 * P, 6 * P);
            for (int i = 0; i < 3; i++) g.fillRect(4 * P, (4 + i) * P, 8 * P, P / 2);
        }
    }
}