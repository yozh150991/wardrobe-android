package com.familywardrobe.app;

/** A late photo result must never replace another editor, a manual edit or a saved image. */
public final class PhotoWorkGate {
    private long generation;
    private String owner = "", source = "";
    public synchronized void activate(String key) {
        if (!owner.equals(key)) { generation++; owner = key; source = ""; }
    }
    public synchronized long begin(String key, String id) {
        owner = key; source = id; return ++generation;
    }
    public synchronized boolean accepts(long token, String key, String id) {
        return token == generation && owner.equals(key) && source.equals(id);
    }
    public synchronized void cancel() { generation++; source = ""; }
}
