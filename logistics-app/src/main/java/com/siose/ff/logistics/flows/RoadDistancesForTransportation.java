package com.siose.ff.logistics.flows;

import com.siose.ff.logistics.routing.RoadDistances;
import com.siose.ff.logistics.transportation.DistanceLookup;
import org.springframework.stereotype.Component;

/**
 * Flow 1 in the modulith: transportation's question about a road leg's distance, answered by
 * routing with a method call. Same JVM, same thread, no network: it can only fail where routing's
 * road matrix does (OSRM, when one is configured), and then transportation uses its estimate.
 *
 * <p>The services do the same over HTTP: the transportation service asks the routing service, with
 * a timeout.
 */
@Component
class RoadDistancesForTransportation implements DistanceLookup {

    private final RoadDistances roads;

    RoadDistancesForTransportation(RoadDistances roads) {
        this.roads = roads;
    }

    @Override
    public double roadKm(double fromLatitude, double fromLongitude, double toLatitude, double toLongitude) {
        return roads.roadKm(fromLatitude, fromLongitude, toLatitude, toLongitude);
    }
}
