package terrariamini;

import java.io.Serializable;

public final class ItemStack implements Serializable {
    private static final long serialVersionUID = 3L;

    public int id;
    public int count;
    public CompoundTag nbt = new CompoundTag();
    public int durability;
    public int maxDurability;

    public ItemStack(int id, int count) { this.id = id; this.count = count; }

    public static ItemStack of(int id, int count) { return new ItemStack(id, count); }
    public static ItemStack of(int id, int count, CompoundTag nbt) {
        ItemStack s = new ItemStack(id, count); s.nbt = nbt; return s;
    }

    public ItemStack copy() {
        ItemStack s = new ItemStack(id, count);
        s.nbt = nbt.copy(); s.durability = durability; s.maxDurability = maxDurability;
        return s;
    }

    public boolean isDamageable() { return maxDurability > 0; }
    public boolean damage(int amount) {
        if (!isDamageable() || nbt.getBoolean("Unbreakable")) return false;
        durability -= amount;
        if (durability <= 0) { durability = 0; return true; }
        return false;
    }
    public String getCustomName() { return nbt.getString("display.name"); }
    public void setCustomName(String n) { nbt.putString("display.name", n); }
}