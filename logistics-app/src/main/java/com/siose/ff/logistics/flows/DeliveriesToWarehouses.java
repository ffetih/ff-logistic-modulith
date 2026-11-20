package com.siose.ff.logistics.flows;

import com.siose.ff.logistics.transportation.ShipmentDelivered;
import com.siose.ff.logistics.warehousing.InboundArrivals;
import com.siose.ff.logistics.warehousing.ShipmentArrival;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Flow 2 in the modulith: a shipment delivered to a warehouse opens an inbound arrival there.
 *
 * <p>Heard after transportation's transaction has committed, on a thread of its own. The registry
 * wrote one row for this listener in that same transaction (schema {@code modulith}); it marks the
 * row completed when this method returns. If it throws, or the pod stops first, the row stays open
 * and the outbox upkeep delivers the event again; opening an arrival twice changes nothing, so a
 * repeat is harmless.
 *
 * <p>Not {@code @ApplicationModuleListener}, which would open a transaction around the method:
 * Hibernate takes the tenant when a transaction starts, and the tenant comes from the event.
 * {@link InboundArrivals} binds it and then opens its own transaction.
 *
 * <p>The services do the same with a Kafka consumer in the warehousing service: the same
 * translation, the same call.
 */
@Component
class DeliveriesToWarehouses {

    private final InboundArrivals arrivals;

    DeliveriesToWarehouses(InboundArrivals arrivals) {
        this.arrivals = arrivals;
    }

    @Async
    @TransactionalEventListener
    void on(ShipmentDelivered delivered) {
        if (delivered.destinationGln() == null) {
            return;
        }
        arrivals.open(new ShipmentArrival(delivered.tenantId(), delivered.shipmentId(), delivered.shipmentNumber(),
                delivered.destinationGln(), delivered.sscc(), delivered.grossWeightKg(), delivered.deliveredAt()));
    }
}
