package org.traccar.api.resource;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.traccar.api.security.PermissionsService;
import org.traccar.model.Trip;
import org.traccar.storage.Storage;
import org.traccar.storage.query.Request;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TripResourceTest {

    @Test
    public void testGetWithDeviceId() throws Exception {
        TripResource resource = new TripResource();
        
        Storage storage = mock(Storage.class);
        PermissionsService permissionsService = mock(PermissionsService.class);
        UriInfo uriInfo = mock(UriInfo.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        
        injectField(resource, "storage", storage);
        injectField(resource, "permissionsService", permissionsService);
        injectField(resource, "uriInfo", uriInfo);
        injectField(resource, "securityContext", securityContext);
        
        var queryParams = new MultivaluedHashMap<String, String>();
        queryParams.add("deviceId", "123");
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        
        when(storage.getObjects(eq(Trip.class), any(Request.class)))
                .thenReturn(Collections.emptyList());

        when(permissionsService.notAdmin(any(Long.class))).thenReturn(false);

        assertDoesNotThrow(() -> resource.get(true, 0, false));
        
        verify(storage).getObjects(eq(Trip.class), any(Request.class));
    }

    @Test
    public void testGetWithoutDeviceId() throws Exception {
        TripResource resource = new TripResource();
        
        Storage storage = mock(Storage.class);
        PermissionsService permissionsService = mock(PermissionsService.class);
        UriInfo uriInfo = mock(UriInfo.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        
        injectField(resource, "storage", storage);
        injectField(resource, "permissionsService", permissionsService);
        injectField(resource, "uriInfo", uriInfo);
        injectField(resource, "securityContext", securityContext);
        
        var queryParams = new MultivaluedHashMap<String, String>();
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        
        when(storage.getObjects(eq(Trip.class), any(Request.class)))
                .thenReturn(Collections.emptyList());

        when(permissionsService.notAdmin(any(Long.class))).thenReturn(false);

        assertDoesNotThrow(() -> resource.get(true, 0, false));
        
        verify(storage).getObjects(eq(Trip.class), any(Request.class));
    }
    
    private void injectField(Object target, String fieldName, Object value) throws Exception {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }
}
