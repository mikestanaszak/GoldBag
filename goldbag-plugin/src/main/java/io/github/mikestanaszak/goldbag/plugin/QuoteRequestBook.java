package io.github.mikestanaszak.goldbag.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread request identity registry for asynchronous quote reads. */
final class QuoteRequestBook {
    private final Map<UUID, Request> requests = new HashMap<>();

    synchronized UUID begin(UUID player, long revision) {
        UUID id = UUID.randomUUID();
        requests.put(player, new Request(id, revision));
        return id;
    }

    synchronized boolean active(UUID player, UUID request, long revision, long currentRevision) {
        Request value = requests.get(player);
        return value != null && value.id().equals(request) && value.revision() == revision && revision == currentRevision;
    }

    synchronized void invalidate(UUID player) { requests.remove(player); }
    synchronized void clear() { requests.clear(); }

    private record Request(UUID id, long revision) { }
}
