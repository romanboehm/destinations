package com.romanboehm.destinations;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.annotations.jaxrs.PathParam;
import org.jboss.resteasy.annotations.jaxrs.QueryParam;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.util.List;

@RegisterRestClient(configKey="direkt-bahn-guru-api")
public interface DestinationService {

    @GET
    @Path("{startingPoint}")
    List<Destination> getDestinationsForStartingPoint(@PathParam("startingPoint") String startingPoint, @QueryParam("localTrainsOnly") boolean localTrainsOnly, @QueryParam("v") String v);
}
