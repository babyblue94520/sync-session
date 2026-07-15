package pers.clare.test.session;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Store {
    private static final Map<Integer, AtomicInteger> countMap = new ConcurrentHashMap<>();
    private static final Map<String, Map<Integer, AtomicInteger>> sessionCountMap = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> sessionPortsMap = new ConcurrentHashMap<>();

    public static void increment(Integer type) {
        countMap.computeIfAbsent(type, (key) -> new AtomicInteger()).incrementAndGet();
    }

    public static void increment(String id, String username, Integer type) {
        increment(id, username, type, "");
    }

    public static void increment(String id, String username, Integer type, String port) {
        increment(type);
        if (id == null) return;
        sessionCountMap
                .computeIfAbsent(id, (key) -> new ConcurrentHashMap<>())
                .computeIfAbsent(type, (key) -> new AtomicInteger())
                .incrementAndGet();
        sessionPortsMap
                .computeIfAbsent(id, (key) -> ConcurrentHashMap.newKeySet())
                .add(port == null ? "" : port);
    }

    public static int getCount(String id) {
        var countMap = sessionCountMap.get(id);
        if (countMap == null) return 0;
        return countMap.values().stream().mapToInt(AtomicInteger::get).sum();
    }

    public static int getCount(String id, int type) {
        var countMap = sessionCountMap.get(id);
        if (countMap == null) return 0;
        var count = countMap.get(type);
        if (count == null) return 0;
        return count.get();
    }

    public static Set<String> getPorts(String id) {
        var ports = sessionPortsMap.get(id);
        if (ports == null) return Set.of();
        return Set.copyOf(ports);
    }

    public static void clear() {
        countMap.clear();
        sessionCountMap.clear();
        sessionPortsMap.clear();
    }
}
