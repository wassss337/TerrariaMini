package terrariamini;

import java.io.Serializable;
import java.util.*;

public final class CompoundTag implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Object> data = new LinkedHashMap<>();

    public void put(String k, Object v) {
        if (v instanceof Number || v instanceof String || v instanceof Boolean
            || v instanceof CompoundTag || v instanceof int[]) data.put(k, v);
    }
    public void putInt(String k, int v)        { data.put(k, v); }
    public void putLong(String k, long v)      { data.put(k, v); }
    public void putString(String k, String v)  { data.put(k, v); }
    public void putBoolean(String k, boolean v){ data.put(k, v); }
    public void putDouble(String k, double v)  { data.put(k, v); }
    public void putTag(String k, CompoundTag v){ data.put(k, v); }
    public void putIntArray(String k, int[] v) { data.put(k, v); }

    public int getInt(String k)        { return data.get(k) instanceof Integer i ? i : 0; }
    public long getLong(String k)      { return data.get(k) instanceof Long l ? l : 0L; }
    public String getString(String k)  { return data.get(k) instanceof String s ? s : ""; }
    public boolean getBoolean(String k){ return data.get(k) instanceof Boolean b && b; }
    public double getDouble(String k)  { return data.get(k) instanceof Double d ? d : 0d; }
    public CompoundTag getTag(String k){ return data.get(k) instanceof CompoundTag t ? t : new CompoundTag(); }
    public int[] getIntArray(String k) { return data.get(k) instanceof int[] a ? a : new int[0]; }

    public boolean has(String k) { return data.containsKey(k); }
    public void remove(String k) { data.remove(k); }
    public Set<String> keys()    { return data.keySet(); }
    public Object get(String k)  { return data.get(k); }

    public CompoundTag copy() {
        CompoundTag c = new CompoundTag();
        for (var e : data.entrySet()) {
            Object v = e.getValue();
            if (v instanceof CompoundTag t) c.data.put(e.getKey(), t.copy());
            else if (v instanceof int[] a) c.data.put(e.getKey(), a.clone());
            else c.data.put(e.getKey(), v);
        }
        return c;
    }
    @Override public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var e : data.entrySet()) {
            if (!first) sb.append(','); first = false;
            Object v = e.getValue();
            sb.append(e.getKey()).append(':');
            if (v instanceof String s) sb.append('"').append(s).append('"');
            else sb.append(v);
        }
        return sb.append('}').toString();
    }
}