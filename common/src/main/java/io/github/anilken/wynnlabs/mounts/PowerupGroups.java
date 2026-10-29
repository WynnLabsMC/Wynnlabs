package io.github.anilken.wynnlabs.mounts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Powerups among the oak boat item displays near a rider. A powerup is two or more displays at
 * exactly the same position; its type display is the one with the lowest custom model data float.
 * The ridden mount's own model uses such displays too: those riding with the vehicle, or within
 * {@code mountRange} blocks of it, are left out. Pure.
 */
public final class PowerupGroups {

    /**
     * One boat display: what it stands for ({@code ref}), its position, its first custom model data
     * float, whether it rides with the player's vehicle and how far it is from that vehicle
     * ({@link Double#MAX_VALUE} with none).
     */
    public record Part<T>(T ref, double x, double y, double z, float model, boolean onVehicle, double toVehicle) {}

    /** A powerup: its position, its type display and how many displays it has. */
    public record Group<T>(double x, double y, double z, T type, float model, int size) {}

    private record Spot(double x, double y, double z) {}

    private PowerupGroups() {}

    public static <T> List<Group<T>> of(List<Part<T>> parts, double mountRange) {
        Map<Spot, List<Part<T>>> bySpot = new LinkedHashMap<>();
        for (Part<T> p : parts) {
            if (p.onVehicle() || p.toVehicle() <= mountRange) {
                continue;
            }
            bySpot.computeIfAbsent(new Spot(p.x(), p.y(), p.z()), k -> new ArrayList<>()).add(p);
        }
        List<Group<T>> out = new ArrayList<>();
        for (Map.Entry<Spot, List<Part<T>>> en : bySpot.entrySet()) {
            List<Part<T>> ps = en.getValue();
            if (ps.size() < 2) {
                continue;
            }
            Part<T> type = ps.get(0);
            for (Part<T> p : ps) {
                if (p.model() < type.model()) {
                    type = p;
                }
            }
            Spot s = en.getKey();
            out.add(new Group<>(s.x(), s.y(), s.z(), type.ref(), type.model(), ps.size()));
        }
        return out;
    }
}
