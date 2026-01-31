package org.traccar.api.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.traccar.api.SimpleObjectResource;
import org.traccar.model.Device;
import org.traccar.model.Position;
import org.traccar.model.Trip;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.stream.Stream;

@Path("trips")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TripResource extends SimpleObjectResource<Trip> {

    private static final String STATUS_STARTED = "STARTED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_ENDED = "ENDED";
    private static final String ATTRIBUTE_SERVER_COST = "serverCost";

    @Context
    private UriInfo uriInfo;

    public TripResource() {
        super(Trip.class, "startTime");
    }

    @Override
    public Stream<Trip> get(
            @QueryParam("all") boolean all, @QueryParam("userId") long userId,
            @QueryParam("excludeAttributes") boolean excludeAttributes) throws StorageException {
        long deviceId = 0;
        if (uriInfo.getQueryParameters().containsKey("deviceId")) {
            deviceId = Long.parseLong(uriInfo.getQueryParameters().getFirst("deviceId"));
        }
        return getInternal(all, userId, deviceId, excludeAttributes).stream();
    }

    private Collection<Trip> getInternal(
            boolean all, long userId, long deviceId, boolean excludeAttributes) throws StorageException {
        
        var conditions = new LinkedList<Condition>();

        if (all) {
            if (permissionsService.notAdmin(getUserId())) {
                conditions.add(new Condition.Permission(User.class, getUserId(), baseClass));
            }
        } else {
            if (userId == 0) {
                userId = getUserId();
            } else {
                permissionsService.checkUser(getUserId(), userId);
            }
            conditions.add(new Condition.Permission(User.class, userId, baseClass));
        }
        
        if (deviceId > 0) {
            conditions.add(new Condition.Equals("deviceId", deviceId));
        }

        Columns columns = excludeAttributes ? new Columns.Exclude("attributes") : new Columns.All();
        return storage.getObjects(baseClass, new Request(
                columns, Condition.merge(conditions), new Order("startTime")));
    }

    @Override
    public Response add(Trip entity) throws Exception {
        if (STATUS_STARTED.equals(entity.getStatus())) {
            Collection<Trip> activeTrips = storage.getObjects(Trip.class, new Request(
                    new Columns.All(),
                    new Condition.And(
                            new Condition.Equals("deviceId", entity.getDeviceId()),
                            new Condition.Or(
                                    new Condition.Equals("status", STATUS_STARTED),
                                    new Condition.Equals("status", STATUS_PENDING))
                    ),
                    new Order("startTime", true, 1)));
            if (!activeTrips.isEmpty()) {
                return Response.ok(activeTrips.iterator().next()).build();
            }
            entity.setStartTime(new Date());
            
            long deviceId = entity.getDeviceId();
            Device device = storage.getObject(Device.class, new Request(
                    new Columns.All(), new Condition.Equals("id", deviceId)));
            
            if (device != null && device.getPositionId() > 0) {
                Position position = storage.getObject(Position.class, new Request(
                        new Columns.All(), new Condition.Equals("id", device.getPositionId())));
                if (position != null) {
                    entity.setStartOdometer(position.getDouble(Position.KEY_TOTAL_DISTANCE));
                }
            }
        }
        return super.add(entity);
    }

    @Override
    public Response update(Trip entity) throws Exception {
         Trip existing = storage.getObject(Trip.class, new Request(
                new Columns.All(), new Condition.Equals("id", entity.getId())));
         
         if (existing != null) {
             entity.setStartTime(existing.getStartTime());
             entity.setStartOdometer(existing.getStartOdometer());
             entity.setDeviceId(existing.getDeviceId());
             entity.setDriverId(existing.getDriverId());
             entity.setAttributes(existing.getAttributes());
             
             if (STATUS_ENDED.equals(entity.getStatus()) && !STATUS_ENDED.equals(existing.getStatus())) {
                 if (STATUS_PENDING.equals(existing.getStatus())) {
                     entity.setEndTime(existing.getEndTime());
                     entity.setEndOdometer(existing.getEndOdometer());
                     entity.setDistance(existing.getDistance());
                     if (!existing.hasAttribute(ATTRIBUTE_SERVER_COST)) {
                         entity.set(ATTRIBUTE_SERVER_COST, existing.getCost());
                     }
                     if (entity.getCost() <= 0) {
                         entity.setCost(existing.getCost());
                     }
                 } else {
                     entity.setEndTime(new Date());
                     
                     long deviceId = existing.getDeviceId();
                     Device device = storage.getObject(Device.class, new Request(
                        new Columns.All(), new Condition.Equals("id", deviceId)));
                     
                     if (device != null && device.getPositionId() > 0) {
                         Position position = storage.getObject(Position.class, new Request(
                                 new Columns.All(), new Condition.Equals("id", device.getPositionId())));
                         if (position != null) {
                             double endOdometer = position.getDouble(Position.KEY_TOTAL_DISTANCE);
                             entity.setEndOdometer(endOdometer);
                             
                             double startOdometer = existing.getStartOdometer();
                             double distance = endOdometer - startOdometer;
                             if (distance < 0) distance = 0;
                             
                             entity.setDistance(distance);
                             double serverCost = (distance / 1000.0) * 2.0; 
                             entity.set(ATTRIBUTE_SERVER_COST, serverCost);
                             if (entity.getCost() <= 0) {
                                 entity.setCost(serverCost);
                             }
                         }
                     }
                 }
             }
         }
         return super.update(entity);
    }
}
