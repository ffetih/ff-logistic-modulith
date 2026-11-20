package com.siose.ff.logistics.flows;

import com.siose.ff.logistics.routing.RouteStopCompleted;
import com.siose.ff.logistics.transportation.ShipmentDeliveries;
import com.siose.ff.logistics.transportation.StopDelivery;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Flow 3 in the modulith: a completed route stop delivers the shipment it carried. After routing's
 * commit, on another thread; the outbox keeps the event until this has run (decision 12).
 *
 * <p>The delivery publishes {@code ShipmentDelivered} like any other, so if the shipment went to a
 * warehouse, {@link DeliveriesToWarehouses} opens its arrival next (flow 2): three areas, two
 * events, two outbox rows, each step in its own transaction.
 *
 * <p>The services do the same over Kafka: the routing service sends the event, the transportation
 * service reads it.
 */
@Component
class StopsToShipments {

    private final ShipmentDeliveries deliveries;

    StopsToShipments(ShipmentDeliveries deliveries) {
        this.deliveries = deliveries;
    }

    @Async
    @TransactionalEventListener
    void on(RouteStopCompleted completed) {
        if (completed.shipmentNumber() == null) {
            return;
        }
        deliveries.deliver(new StopDelivery(completed.tenantId(), completed.shipmentNumber(), completed.receivedBy(),
                completed.completedAt(), completed.routeNumber(), completed.stopCode()));
    }
}
