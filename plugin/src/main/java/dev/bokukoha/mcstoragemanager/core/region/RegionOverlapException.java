package dev.bokukoha.mcstoragemanager.core.region;

import java.util.List;

/** Raised when a registration would overlap one or more regions in the same world. */
public final class RegionOverlapException extends IllegalArgumentException {
    private final List<RegionConflict> conflicts;

    public RegionOverlapException(List<RegionConflict> conflicts) {
        super("Region overlaps " + conflicts.size() + " existing region(s)");
        this.conflicts = List.copyOf(conflicts);
    }

    public List<RegionConflict> conflicts() {
        return conflicts;
    }
}
