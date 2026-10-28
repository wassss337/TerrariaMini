package terrariamini;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServerMain {

    static Properties props = new Properties();
    static File configFile = new File("server.properties");
    static Map<Integer, ClientHandler> clients = new ConcurrentHashMap<>();
    static int nextId = 1;

    // п.16 — volatile, чтобы консоль/клиенты видели актуальную ссылку
    static volatile TerrariaMini.World world;
    static File worldFile;

    public static void main(String[] args) throws Exception {
        loadConfig();

        int port = Integer.parseInt(props.getProperty("server-port", "25565"));
        String worldName = props.getProperty("world-name", "server_world");
        long seed = Long.parseLong(props.getProperty("world-seed", "0"));
        String datapackName = props.getProperty("datapack", "default");
        String motd = props.getProperty("motd", "TerrariaMini Server");

        System.out.println("========================================");
        System.out.println("  " + motd);
        System.out.println("========================================");
        System.out.println("  Порт:      " + port);
        System.out.println("  Мир:       " + worldName);
        System.out.println("  Сид:       " + seed);
        System.out.println("  Датапак:   " + datapackName);
        System.out.println("========================================");

        // Загрузка/создание мира
        File savesDir = new File("saves");
        savesDir.mkdirs();
        worldFile = new File(savesDir, worldName + ".world");

        if (worldFile.exists()) {
            System.out.println("[Мир] Загрузка " + worldFile.getName() + "...");
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(worldFile))) {
                TerrariaMini.World w = (TerrariaMini.World) in.readObject();
                w.ensureRuntimeState();   // п.3/10/20 — восстановить transient-поля
                world = w;
                System.out.println("[Мир] Загружен: " + world.getWidth() + "x" + world.getHeight());
            } catch (Exception e) {
                System.out.println("[Мир] Ошибка загрузки: " + e.getMessage());
                System.out.println("[Мир] Создаём новый...");
                world = createWorld(seed, datapackName);
                saveWorld();
            }
        } else {
            System.out.println("[Мир] Создание нового мира...");
            world = createWorld(seed, datapackName);
            saveWorld();
        }

        // Серверный сокет
        ServerSocket serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        System.out.println("[Сервер] Запущен на порту " + port);
        System.out.println("[Сервер] Подключение: localhost:" + port);
        System.out.println("[Сервер] Команды: stop, save, players, kick <id>, seed, help");
        System.out.println("========================================");

        // Консольные команды
        startConsoleThread(serverSocket);

        // Приём подключений
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                int id = nextId++;
                ClientHandler handler = new ClientHandler(id, socket);
                clients.put(id, handler);
                handler.start();
                System.out.println("[Сервер] Игрок #" + id + " подключился: " + socket.getInetAddress());
            } catch (Exception e) {
                if (!serverSocket.isClosed()) {
                    System.out.println("[Сервер] Ошибка accept: " + e.getMessage());
                }
            }
        }
    }

    // ==================== КОНФИГ ====================
    static void loadConfig() {
        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            } catch (Exception e) {
                System.out.println("[Конфиг] Ошибка загрузки: " + e.getMessage());
            }
        } else {
            props.setProperty("server-port", "25565");
            props.setProperty("world-seed", String.valueOf(new Random().nextLong()));
            props.setProperty("world-name", "server_world");
            props.setProperty("max-players", "8");
            props.setProperty("datapack", "default");
            props.setProperty("motd", "TerrariaMini Server");
            props.setProperty("pvp", "false");
            props.setProperty("spawn-protection", "16");
            try (FileOutputStream out = new FileOutputStream(configFile)) {
                props.store(out, "TerrariaMini Server Config");
                System.out.println("[Конфиг] Создан server.properties");
            } catch (Exception e) {
                System.out.println("[Конфиг] Ошибка создания: " + e.getMessage());
            }
        }
    }

    // ==================== МИР ====================
    static TerrariaMini.World createWorld(long seed, String datapackName) {
        File dpFile = new File("datapacks/" + datapackName + ".json");
        new File("datapacks").mkdirs();
        if (!dpFile.exists()) {
            TerrariaMini.DataPack.saveDefault(new File("datapacks/default.json"));
        }
        TerrariaMini.DataPack dp = dpFile.exists()
            ? TerrariaMini.DataPack.load(dpFile)
            : TerrariaMini.DataPack.load(new File("datapacks/default.json"));

        TerrariaMini.World w = new TerrariaMini.World(800, 200, seed, dp);
        w.name = props.getProperty("world-name", "server_world");
        w.ensureRuntimeState();   // п.20
        System.out.println("[Мир] Сгенерирован: " + w.getWidth() + "x" + w.getHeight());
        return w;
    }

    static void saveWorld() {
        TerrariaMini.World w = world;
        if (w == null || worldFile == null) return;
        // п.16 — синхронизируемся по миру, чтобы не сохранить полу-изменённый тайл
        synchronized (w) {
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(worldFile))) {
                out.writeObject(w);
                System.out.println("[Мир] Сохранён в " + worldFile.getPath());
            } catch (Exception e) {
                System.out.println("[Мир] Ошибка сохранения: " + e.getMessage());
            }
        }
    }

    // ==================== КОНСОЛЬ ====================
    static void startConsoleThread(ServerSocket serverSocket) {
        Thread t = new Thread(() -> {
            Scanner sc = new Scanner(System.in);
            while (sc.hasNextLine()) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                switch (cmd) {
                    case "stop":
                        System.out.println("[Сервер] Остановка...");
                        saveWorld();
                        try { serverSocket.close(); } catch (Exception ignored) {}
                        for (ClientHandler c : clients.values()) c.close();
                        System.out.println("[Сервер] Остановлен");
                        System.exit(0);
                        break;
                    case "save":
                        saveWorld();
                        break;
                    case "players":
                        System.out.println("[Сервер] Игроков: " + clients.size());
                        for (ClientHandler c : clients.values()) {
                            System.out.println("  #" + c.id + " " + c.address);
                        }
                        break;
                    case "kick":
                        if (parts.length < 2) {
                            System.out.println("Использование: kick <id>");
                        } else {
                            try {
                                int id = Integer.parseInt(parts[1]);
                                ClientHandler c = clients.get(id);
                                if (c != null) {
                                    c.close();
                                    System.out.println("[Сервер] Игрок #" + id + " кикнут");
                                } else {
                                    System.out.println("[Сервер] Игрок #" + id + " не найден");
                                }
                            } catch (Exception e) {
                                System.out.println("Неверный id");
                            }
                        }
                        break;
                    case "seed":
                        System.out.println("[Мир] Сид: " + world.seed);
                        break;
                    case "help":
                        System.out.println("Команды: stop, save, players, kick <id>, seed, help");
                        break;
                    default:
                        System.out.println("Неизвестная команда: " + cmd);
                }
            }
        }, "Console");
        t.setDaemon(true);
        t.start();
    }

    // п.1 — без double-brace, единый хелпер
    static TerrariaMini.ChatPacket chat(String text) {
        TerrariaMini.ChatPacket p = new TerrariaMini.ChatPacket();
        p.text = text;
        return p;
    }

    // ==================== КЛИЕНТ ====================
    static class ClientHandler extends Thread {
        int id;
        Socket socket;
        String address;
        ObjectOutputStream out;
        ObjectInputStream in;
        volatile boolean running = true;
        // п.17 — защита от двойного close()
        private final AtomicBoolean closed = new AtomicBoolean(false);

        ClientHandler(int id, Socket socket) {
            this.id = id;
            this.socket = socket;
            this.address = socket.getInetAddress().toString();
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(socket.getInputStream());

                // 1) Отправляем мир
                TerrariaMini.WorldSyncPacket wsp = new TerrariaMini.WorldSyncPacket();
                wsp.world = world;
                int sx = world.getWidth() / 2;
                int sy = world.findSurface(sx);
                wsp.spawnX = sx * 32;
                wsp.spawnY = (sy - 3) * 32;
                synchronized (out) {
                    out.writeObject(wsp);
                    out.flush();
                    out.reset();
                }

                // 2) п.5 — сообщаем клиенту его собственный id
                TerrariaMini.AssignIdPacket aid = new TerrariaMini.AssignIdPacket();
                aid.playerId = id;
                synchronized (out) {
                    out.writeObject(aid);
                    out.flush();
                    out.reset();
                }

                // 3) п.4 — сообщаем новому клиенту о уже подключённых
                for (ClientHandler other : clients.values()) {
                    if (other.id == id) continue;
                    TerrariaMini.PlayerJoinPacket j = new TerrariaMini.PlayerJoinPacket();
                    j.playerId = other.id;
                    j.name = "Игрок #" + other.id;
                    synchronized (out) {
                        out.writeObject(j);
                        out.flush();
                        out.reset();
                    }
                }

                // 4) п.4 — сообщаем всем о новом игроке
                TerrariaMini.PlayerJoinPacket join = new TerrariaMini.PlayerJoinPacket();
                join.playerId = id;
                join.name = "Игрок #" + id;
                broadcast(join, id);

                // 5) чат-уведомление
                broadcast(chat("§eИгрок #" + id + " подключился"), id);

                // Цикл чтения пакетов
                while (running && !socket.isClosed()) {
                    Object obj = in.readObject();
                    processPacket(obj);
                }
            } catch (EOFException e) {
                // нормальное отключение
            } catch (Exception e) {
                if (running) {
                    System.out.println("[Сервер] Ошибка клиента #" + id + ": " + e.getMessage());
                }
            } finally {
                close();
            }
        }

        void processPacket(Object obj) {
            if (obj instanceof TerrariaMini.PlayerUpdatePacket) {
                broadcast(obj, id);

            } else if (obj instanceof TerrariaMini.ChatPacket) {
                TerrariaMini.ChatPacket c = (TerrariaMini.ChatPacket) obj;
                System.out.println("[Чат] #" + id + ": " + c.text);
                broadcast(c, id);

            } else if (obj instanceof TerrariaMini.BlockChangePacket) {
                TerrariaMini.BlockChangePacket b = (TerrariaMini.BlockChangePacket) obj;
                // п.16 — синхронизация по миру
                synchronized (world) {
                    if (world.inBounds(b.x, b.y)) {
                        world.setBlock(b.x, b.y, b.block);
                        broadcast(b, id);
                    }
                }

            } else if (obj instanceof TerrariaMini.BgChangePacket) {
                TerrariaMini.BgChangePacket b = (TerrariaMini.BgChangePacket) obj;
                synchronized (world) {
                    if (world.inBounds(b.x, b.y)) {
                        world.setBackground(b.x, b.y, b.block);
                        broadcast(b, id);
                    }
                }

            } else if (obj instanceof TerrariaMini.DamagePacket) {
                broadcast(obj, id);
            }
        }

        void close() {
            // п.17 — идемпотентно
            if (!closed.compareAndSet(false, true)) return;
            running = false;
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            clients.remove(id);

            // п.4 — leave-пакет
            TerrariaMini.PlayerLeavePacket leave = new TerrariaMini.PlayerLeavePacket();
            leave.playerId = id;
            broadcast(leave, id);

            broadcast(chat("§cИгрок #" + id + " отключился"), id);
            System.out.println("[Сервер] Игрок #" + id + " отключился");
        }
    }

    static void broadcast(Object packet, int exceptId) {
        for (Map.Entry<Integer, ClientHandler> e : clients.entrySet()) {
            if (e.getKey() == exceptId) continue;
            ClientHandler c = e.getValue();
            if (c.out == null) continue;
            try {
                synchronized (c.out) {
                    c.out.writeObject(packet);
                    c.out.flush();
                    c.out.reset();
                }
            } catch (Exception ignored) {}
        }
    }
}