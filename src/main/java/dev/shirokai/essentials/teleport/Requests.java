package dev.shirokai.essentials.teleport;

import java.util.*;

/** One outstanding request per sender; recipient chooses explicitly when requests overlap. */
public final class Requests {
    public record Request(UUID sender, UUID recipient, boolean here, long expiresAt) {}
    private final Map<UUID, Request> requests = new HashMap<>();
    public boolean add(Request r, int recipientLimit, int globalLimit, long now) {
        expire(now);
        if (requests.size() >= globalLimit || requests.containsKey(r.sender()) || requests.values().stream().filter(q -> q.recipient().equals(r.recipient())).count() >= recipientLimit) return false;
        requests.put(r.sender(), r); return true;
    }
    public Optional<Request> find(UUID recipient, UUID sender, long now) {
        return requests.values().stream().filter(r -> r.recipient().equals(recipient) && (sender == null || r.sender().equals(sender)) && r.expiresAt() > now).min(Comparator.comparingLong(Request::expiresAt));
    }
    public Request remove(UUID sender) { return requests.remove(sender); }
    public List<Request> expire(long now) {
        List<Request> expired = requests.values().stream().filter(r -> r.expiresAt() <= now).toList();
        expired.forEach(r -> requests.remove(r.sender())); return expired;
    }
    public void disconnect(UUID id) { requests.values().removeIf(r -> r.sender().equals(id) || r.recipient().equals(id)); }
    public void clear() { requests.clear(); }
}
