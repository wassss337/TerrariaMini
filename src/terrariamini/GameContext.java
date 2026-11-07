package terrariamini;
import java.awt.Color;

public final class GameContext {
    final TerrariaMini game;
    GameContext(TerrariaMini game) { this.game = game; }

    // ---- чат ----
    public void chat(String text) { game.apiChat(text, Color.WHITE); }
    public void chat(String text, Color color) { game.apiChat(text, color); }

    // ---- игрок ----
    public boolean hasPlayer() { return game.apiPlayer() != null; }
    public double getPlayerX() { return game.apiPlayer().x; }
    public double getPlayerY() { return game.apiPlayer().y; }
    public void setPlayerPos(double x, double y) { game.apiPlayer().x = x; game.apiPlayer().y = y; }
    public double getPlayerHp() { return game.apiPlayer().hp; }
    public void setPlayerHp(double hp) { game.apiPlayer().hp = hp; }
    public double getPlayerMaxHp() { return game.apiPlayer().maxHp; }
    public void setPlayerMaxHp(double hp) { game.apiPlayer().maxHp = hp; }
    public void heal(double amount) { game.apiPlayer().hp = Math.min(game.apiPlayer().maxHp, game.apiPlayer().hp + amount); }
    public void giveItem(int id, int count) { game.apiGiveItem(id, count); }
    public void addXp(int amount) { game.apiAddXp(amount); }

    // ---- мир ----
    public int getBlock(int x, int y) { return game.apiWorld().getBlock(x, y); }
    public void setBlock(int x, int y, int block) { game.apiWorld().setBlock(x, y, block); }
    public int getWorldWidth() { return game.apiWorld().getWidth(); }
    public int getWorldHeight() { return game.apiWorld().getHeight(); }
    public int findSurface(int x) { return game.apiWorld().findSurface(x); }

    // ---- монстры ----
    public void spawnMonster(double x, double y, int type) {
        game.apiMonsters().add(new TerrariaMini.Monster(x, y, type));
    }
    public int getMonsterCount() { return game.apiMonsters().size(); }
    public void clearMonsters() { game.apiMonsters().clear(); }

    // ---- время ----
    public long getGameTime() { return game.apiGameTime(); }
    public boolean isNight() { return game.apiIsNight(); }
    public void setTime(long t) { game.apiSetGameTime(t); }

    // ---- состояние ----
    public boolean isPlaying() { return game.getState() == TerrariaMini.GameState.PLAYING; }
}