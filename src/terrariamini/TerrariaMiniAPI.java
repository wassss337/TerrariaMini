package terrariamini;
import java.awt.Color;

public interface TerrariaMiniAPI {
    String getName();
    String getVersion();

    default void onLoad(GameContext ctx) {}
    default void onTick(GameContext ctx) {}
    default void onPlayerJoin(GameContext ctx) {}
    default void onBlockBreak(GameContext ctx, int x, int y, int blockId) {}
    default void onBlockPlace(GameContext ctx, int x, int y, int blockId) {}
    default void onMonsterKilled(GameContext ctx, int monsterType) {}
    default void onCommand(GameContext ctx, String[] args) {}
    default void registerRecipes(RecipeRegistry registry) {}
}