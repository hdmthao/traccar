package org.traccar.handler;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.traccar.model.Position;
import org.traccar.model.Trip;
import org.traccar.storage.Storage;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class TripHandler extends BasePositionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TripHandler.class);
    private static final String STATUS_STARTED = "STARTED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ENDED = "ENDED";
    private static final String ATTRIBUTE_SERVER_COST = "serverCost";
    private final Storage storage;

    @Inject
    public TripHandler(Storage storage) {
        this.storage = storage;
    }

    @Override
    public void onPosition(Position position, Callback callback) {
        String tripStatus = position.getString("trip");
        if (tripStatus != null) {
            try {
                if ("start".equals(tripStatus)) {
                    List<Trip> activeTrips = storage.getObjects(Trip.class, new Request(
                        new Columns.All(),
                        new Condition.And(
                            new Condition.Equals("deviceId", position.getDeviceId()),
                            new Condition.Or(
                                new Condition.Equals("status", STATUS_STARTED),
                                new Condition.Equals("status", STATUS_PENDING)
                            )
                        ),
                        new Order("startTime", true, 1)
                    ));
                    if (!activeTrips.isEmpty()) {
                        LOGGER.info("Trip already active for device {}", position.getDeviceId());
                        callback.processed(false);
                        return;
                    }
                    Trip trip = new Trip();
                    trip.setDeviceId(position.getDeviceId());
                    trip.setStartTime(position.getFixTime());
                    trip.setStartOdometer(position.getDouble(Position.KEY_TOTAL_DISTANCE));
                    trip.setStatus(STATUS_STARTED);
                    storage.addObject(trip, new Request(new Columns.Exclude("id")));
                    LOGGER.info("Trip started for device {}", position.getDeviceId());
                } else if ("end".equals(tripStatus)) {
                    List<Trip> trips = storage.getObjects(Trip.class, new Request(
                        new Columns.All(),
                        new Condition.And(
                            new Condition.Equals("deviceId", position.getDeviceId()),
                            new Condition.Equals("status", STATUS_STARTED)
                        ),
                        new Order("startTime", true, 1)
                    ));

                    if (trips.isEmpty()) {
                        LOGGER.info("No active trip for device {}", position.getDeviceId());
                        callback.processed(false);
                        return;
                    }

                    Trip trip = trips.get(0);
                    trip.setEndTime(position.getFixTime());
                    trip.setEndOdometer(position.getDouble(Position.KEY_TOTAL_DISTANCE));

                    double distance = trip.getEndOdometer() - trip.getStartOdometer();
                    if (distance < 0) distance = 0;
                    trip.setDistance(distance);
                    double serverCost = (distance / 1000.0) * 2.0;
                    trip.setCost(serverCost);
                    trip.set(ATTRIBUTE_SERVER_COST, serverCost);
                    trip.setStatus(STATUS_PENDING);

                    storage.updateObject(trip, new Request(
                        new Columns.Exclude("id"),
                        new Condition.Equals("id", trip.getId())
                    ));
                    LOGGER.info("Trip ended for device {}. Distance: {}, Server Cost: {}", position.getDeviceId(), trip.getDistance(), serverCost);
                } else if ("confirm".equals(tripStatus)) {
                    List<Trip> trips = storage.getObjects(Trip.class, new Request(
                        new Columns.All(),
                        new Condition.And(
                            new Condition.Equals("deviceId", position.getDeviceId()),
                            new Condition.Equals("status", STATUS_PENDING)
                        ),
                        new Order("startTime", true, 1)
                    ));

                    if (trips.isEmpty()) {
                        LOGGER.info("No pending trip for device {}", position.getDeviceId());
                        callback.processed(false);
                        return;
                    }

                    Trip trip = trips.get(0);
                    double serverCost = trip.getCost();
                    if (!trip.hasAttribute(ATTRIBUTE_SERVER_COST)) {
                        trip.set(ATTRIBUTE_SERVER_COST, serverCost);
                    }

                    double clientCost = position.getDouble("cost");
                    if (clientCost <= 0) {
                        clientCost = serverCost;
                    }
                    trip.setCost(clientCost);
                    trip.setStatus(STATUS_ENDED);

                    storage.updateObject(trip, new Request(
                        new Columns.Exclude("id"),
                        new Condition.Equals("id", trip.getId())
                    ));
                    LOGGER.info("Trip confirmed for device {}. Client Cost: {}", position.getDeviceId(), clientCost);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to handle trip status", e);
            }
        }
        callback.processed(false);
    }
}
