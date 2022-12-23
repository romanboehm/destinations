package com.romanboehm.destinations;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class Destinations {

    private final DestinationService destinationService;
    private final boolean localTrainsOnly;
    private final boolean considerSelf;
    private final int maxDuration;
    private final List<String> destinationCodes;

    Destinations(DestinationService destinationService, boolean localTrainsOnly, boolean considerSelf, int maxDuration, List<String> destinationCodes) {
        this.destinationService = destinationService;
        this.localTrainsOnly = localTrainsOnly;
        this.considerSelf = considerSelf;
        this.maxDuration = maxDuration;
        this.destinationCodes = destinationCodes;
    }

    Map<String, Destination> calculate() {
        if (destinationCodes.size() < 2 || destinationCodes.stream().allMatch(String::isBlank)) {
            throw new IllegalArgumentException("Provide at least two destinations");
        }

        var maps = destinationCodes.stream()
                .map(this::destinationsToDuration)
                .toList();

        return findCommonDestinations(maps);
    }

    private Map<String, Destination> findCommonDestinations(List<Map<String, Destination>> maps) {
        return maps.stream().reduce(maps.get(0), this::intersect);
    }

    private Map<String, Destination> intersect(Map<String, Destination> thisMap, Map<String, Destination> otherMap) {
        var intersection = new HashMap<String, Destination>();

        for (var thisEntry : thisMap.entrySet()) {
            var thisId = thisEntry.getKey();
            var thisDestination = thisEntry.getValue();

            var otherDestination = otherMap.get(thisId);
            if (otherDestination != null) {
                var destinationWithLongestDuration = thisDestination.duration() > otherDestination.duration() ? thisDestination : otherDestination;
                intersection.put(thisId, destinationWithLongestDuration);
            }
        }

        return intersection;
    }

    private Map<String, Destination> destinationsToDuration(String destinationCode) {
        var destinationsForStartingPoint = destinationService.getDestinationsForStartingPoint(destinationCode, localTrainsOnly, "4");
        var destinationsToConsider = new ArrayList<>(destinationsForStartingPoint);
        if (considerSelf) {
            var self = new Destination(destinationCode, null, 0);
            destinationsToConsider.add(self);
        }
        return destinationsToConsider.stream()
                .filter(d -> d.duration() <= maxDuration)
                .collect(Collectors.toMap(Destination::id, Function.identity()));
    }
}
