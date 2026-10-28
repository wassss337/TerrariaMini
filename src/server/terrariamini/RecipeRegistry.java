package terrariamini;
public final class RecipeRegistry {
    private final java.util.List<TerrariaMini.Crafting.Recipe> list;
    RecipeRegistry(java.util.List<TerrariaMini.Crafting.Recipe> list) { this.list = list; }

    public void addRecipe(int resultId, int resultCount,
                          int[] ingIds, int[] ingCounts, boolean needsTable) {
        list.add(new TerrariaMini.Crafting.Recipe(
                resultId, resultCount, ingIds, ingCounts, needsTable, "mod"));
    }
}