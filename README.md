# TerrariaMini

> ⛏️ A mini Terraria-like sandbox game in Java — with mods, datapacks, LAN multiplayer, and a full world to explore.

![Status](https://img.shields.io/badge/status-beta-orange)
![Version](https://img.shields.io/badge/version-1.3-blue)
![Java](https://img.shields.io/badge/java-25%2B-red)
![License](https://img.shields.io/badge/license-MIT-green)

---

## 📖 About

**TerrariaMini** is a 2D sandbox game written in pure Java (Swing + AWT). Dig, build, craft, fight bosses, trade with villagers, and explore a procedurally generated world — or invite friends over LAN and play together. And if the base game isn't enough, write your own **mods** in Java or tweak world generation with **datapacks**.

---

## ✨ Features

### 🌍 World
- Procedurally generated world **2400×400** blocks
- **7 biomes**: Forest, Desert, Tundra, Mountains, Jungle, Corruption, Hell
- Rivers with water, worm caves, ore veins
- Villages with houses, mines, temples, ruins
- **Hell** at the bottom: hellstone, demonite, lava, altars

### ⚔️ Gameplay
- Tools: pickaxes, axes, swords (wood → diamond)
- Crafting: 3×3 grid, workbench required for advanced recipes
- Furnace smelting, chests, workbenches
- **Hunger**, regeneration, fall damage, lava damage
- Potions: healing, strength, speed, regeneration, night vision, fire resistance
- Trading with **12 villager professions**
- **18 achievements**

### 👹 Bosses
- 🟢 **King Slime** (300 HP)
- 👁️ **Eye of Cthulhu** (500 HP)
- 💀 **Skeletron** (800 HP)
- 🔥 **King of Hell** (2000 HP) — final boss

### 🌐 Multiplayer (LAN)
- Host / client via `MultiplayerManager`
- Sync of world, blocks, background, chat, players, damage
- Separate IDs for clients

### 🎨 UI
- Pixel font, animated menu background
- Creative block palette
- HUD: hearts, hunger, XP, time of day, FPS
- Chat with commands (`/give`, `/heal`, `/tp`, `/summon`, `/fill`, etc.)

### 🧩 Mods & Datapacks
- **JAR mods** via `TerrariaMiniAPI`
- **JSON/ZIP datapacks** for world generation and gamerules
- Recipe registration via `RecipeRegistry`

---

## 🛠️ Installation

### Requirements
- **Java 11+**

### Run
```bash
java -jar TerrariaMini.jar
```

On first launch, the following folders are created automatically:
- `saves/` — worlds and player progress
- `datapacks/` — world generation datapacks
- `mods/` — mod JARs

---

## 🎮 Controls

| Key | Action |
|---|---|
| `A/D` or `←/→` | Move |
| `Space` / `W` / `↑` | Jump |
| `LMB` | Dig / Attack |
| `RMB` | Place block / Open / Trade |
| `E` | Inventory + crafting |
| `T` | Chat |
| `M` | Creative / Survival |
| `C` | Block palette (creative) |
| `1–9` | Select hotbar slot |
| `F5` | Save world |
| `F11` | Fullscreen |
| `Esc` | Pause / Close |

---

## 💬 Chat Commands

```
/give <block> [N]        — give items
/heal                    — restore HP
/god /godmode            — toggle immortality
/fly                     — toggle flight
/tp <x> <y>              — teleport
/time day|night          — set time
/weather clear|rain      — weather (stub)
/setspawn                — set spawn point
/spawnpoint              — teleport to spawn
/spawn zombie|slime|skeleton [N]
/summon slime|eye|skeleton|hell
/killall                 — remove all monsters
/clear /clearinv         — clear inventory
/giveall                 — give one of every item
/fill <block> [R]        — fill area
/clean                   — remove liquids around you
/seed                    — show world seed
/coords /where           — show coordinates and biome
/biome <name>            — teleport to biome
/save                    — save world
/xp <N>                  — give XP
/unlock all              — unlock all achievements
/speed <N> /jump <N>     — movement multipliers
/nightvision             — night vision for 10 minutes
/bossreset               — reset defeated bosses
/gamemode survival|creative
/help                    — command list
```

---

## 🧩 Writing a Mod

Create a class implementing `TerrariaMiniAPI`:

```java
public class MyMod implements TerrariaMiniAPI {
    @Override public String getName() { return "MyMod"; }
    @Override public String getVersion() { return "1.0"; }

    @Override
    public void onLoad(GameContext ctx) {
        ctx.chat("§aMyMod loaded!");
    }

    @Override
    public void onBlockBreak(GameContext ctx, int x, int y, int blockId) {
        if (blockId == TerrariaMini.World.DIAMOND) {
            ctx.giveItem(TerrariaMini.World.COIN_GOLD, 10);
        }
    }

    @Override
    public void registerRecipes(RecipeRegistry registry) {
        registry.addRecipe(
            TerrariaMini.World.SWORD_DIAMOND, 1,
            new int[]{ TerrariaMini.World.DIAMOND_GEM, TerrariaMini.World.WOOD },
            new int[]{ 5, 1 },
            true
        );
    }
}
```

**Available events:**
- `onLoad` — mod loaded
- `onTick` — every game tick
- `onPlayerJoin` — player joined
- `onBlockBreak` / `onBlockPlace` — block events
- `onMonsterKilled` — monster killed
- `onCommand` — `/mod <name> ...` command
- `registerRecipes` — register crafting recipes

Compile into a `.jar` → drop into `mods/` → done!

---

## 📦 Datapacks

Create a JSON file in `datapacks/`:

```json
{
  "treeDensity": 0.09,
  "oreDensity": 1.0,
  "caveCount": 18,
  "villageCount": 2,
  "mineCount": 2,
  "templeCount": 1,
  "ruinCount": 2,
  "mountainCount": 3,
  "riverCount": 2,
  "allowSnow": true,
  "allowDesert": true,
  "allowMountains": true
}
```

ZIP datapacks with gamerules are also supported:
```
data/terrariamini/gamerules/rules.json
```

Gamerules: `keepInventory`, `pvp`, `maxPlayers`, `spawnMonsters`, `hungerRate`.

---

## 📁 Project Structure

```
TerrariaMini/
├── saves/           # worlds + player progress
│   └── <world>/
│       ├── data/region.world
│       ├── datapacks/
│       └── playerdata/<id>.dat
├── datapacks/       # world generation datapacks
├── mods/            # mod JARs
└── TerrariaMini.jar
```

---

## 🗺️ Roadmap

- [ ] Weather visuals
- [ ] More bosses and biomes
- [ ] Full multiplayer over the internet
- [ ] Mobile / web port
- [ ] Public mod hub

---

## 🐛 Known Limitations

- Multiplayer works only over LAN
- Weather is a stub (`/weather`)
- Mods load only from `mods/*.jar`

---

## 🤝 Contributing

Pull requests, bug reports, and ideas are welcome!

- **Bug reports** → [Issues](https://github.com/wassss337/TerrariaMini/issues)
- **Ideas & discussions** → [Discussions](https://github.com/wassss337/TerrariaMini/discussions)

---

## 📜 License

MIT License — do whatever you want, just credit the original.

---

## 🙏 Thanks for playing!

**Happy modding! 🎮**
