package net.rasanovum.roxy.legacy.terrain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Fair section queue. Movement and chunk unloads never discard unfinished observations. */
public final class CaptureQueue<T> {

    public static final class Work<T> {

        public final long key;
        public final T value;
        public final int section;
        public final boolean last;

        Work(long key, T value, int section, boolean last) {
            this.key = key;
            this.value = value;
            this.section = section;
            this.last = last;
        }
    }

    private static final class Pending<T> {

        T value;
        int remaining;
        int preferred;

        Pending(T value, int mask, int preferred) {
            this.value = value;
            remaining = mask;
            this.preferred = preferred;
        }
    }

    private final LinkedHashMap<Long, Pending<T>> pending = new LinkedHashMap<Long, Pending<T>>();

    public void offer(long key, T value, int mask, int preferred) {
        if ((mask & 65535) == 0) return;
        Pending<T> entry = pending.get(key);
        if (entry == null) pending.put(key, new Pending<T>(value, mask & 65535, preferred));
        else {
            entry.value = value;
            entry.remaining |= mask & 65535;
            entry.preferred |= preferred;
        }
    }

    public boolean contains(long key) {
        return pending.containsKey(key);
    }

    public boolean changed(long key, int mask) {
        Pending<T> item = pending.get(key);
        if (item == null) return false;
        item.remaining |= mask & 65535;
        return true;
    }

    public int size() {
        return pending.size();
    }

    public void clear() {
        pending.clear();
    }

    public Work<T> poll() {
        if (pending.isEmpty()) return null;
        Map.Entry<Long, Pending<T>> first = pending.entrySet()
            .iterator()
            .next();
        long key = first.getKey();
        Pending<T> item = first.getValue();
        pending.remove(key);
        int available = item.remaining & item.preferred;
        if (available == 0) available = item.remaining;
        // Surface first; all nonempty and empty sections are eventually submitted.
        int section = 31 - Integer.numberOfLeadingZeros(available);
        item.remaining &= ~(1 << section);
        boolean last = item.remaining == 0;
        if (!last) pending.put(key, item);
        return new Work<T>(key, item.value, section, last);
    }
}
