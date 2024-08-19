package com.sittingspot.searchadapter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sittingspot.searchadapter.DTO.SittingSpotInDTO;
import com.sittingspot.searchadapter.DTO.SittingSpotOutDTO;
import com.sittingspot.searchadapter.models.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@RestController("/api/v1")
public class SearchAdapterController {

    @Value("${sittingspot.sittingspotdl.url}")
    private String sittingspotdlUrl;

    @Value("${sittingspot.osm.host}")
    private String osmHost;

    @Value("${sittingspot.osm.port}")
    private String osmPort;

    @Value("${sittingspot.osm.overpass.api}")
    private String osmOverpassApi;

    @GetMapping("/")
    public List<QueryResult> search(@RequestParam("location") Area location,
                                    @RequestParam(value = "tags",required = false) List<Tag> tags,
                                    @RequestParam(value = "labels",required = false) List<String> labels) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();

        List<SittingSpotInDTO> osmData = new ArrayList<>();

        // as labels are unique to our system, if the query involves elements with a certain combination of labels
        // only the sitting spot already in our data layer can match the description, so, it does a request to osm
        // only if there are no restriction on labels.
        if(labels.isEmpty()) {
            StringBuilder osmQuery = new StringBuilder("[out:json];\nnode[amenity=bench]");
            for(var tag : tags) {
                osmQuery.append("[").append(tag.key()).append("=").append(tag.value()).append("]");
            }
            osmQuery.append("(around:" + location.range() + "," + location.center().y() + ","+location.center().x()+");\n" +"out body;");

            var osmRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + osmHost + ":" + osmPort + "/" + osmOverpassApi))
                    .POST(HttpRequest.BodyPublishers.ofString(osmQuery.toString())).build();

            var osmResult = client.send(osmRequest, HttpResponse.BodyHandlers.ofString());

            // TODO: check osm return codes

            if(osmResult.statusCode() == 200) {
                OsmQueryResult data = new ObjectMapper().readValue(osmResult.body(), OsmQueryResult.class);
                // lon = x, lat = y

                // convert list of elements to list of sitting spot
                osmData = data.getElements()
                        .stream()
                        .map(x -> new SittingSpotInDTO(x.id().toString(), new Location(x.lon(),x.lat()), x.tags()
                                .entrySet()
                                .stream()
                                .map(t -> new Tag(t.getKey(), t.getValue()))
                                .toList()))
                        .toList();
            }
        }

        var dlRequest = HttpRequest.newBuilder()
                .uri(URI.create("http://" + sittingspotdlUrl + "/find" + "?location="+location+"&tags="+tags+"&labels="+labels)).GET().build();
        
        var dlResult = client.send(dlRequest, HttpResponse.BodyHandlers.ofString());
        
        if(dlResult.statusCode() == 200) {
            List<SittingSpotOutDTO> dlData = (new ObjectMapper()).readerForListOf(SittingSpotOutDTO.class).readValue(dlResult.body());

            // update our data layer with new entries from osm
            var newSpots = osmData.stream().filter(x -> !dlData.stream().anyMatch(s -> s.id() == x.id())).toList();
            for(var newSpot : newSpots) {
                var dlPostRequest = HttpRequest.newBuilder().uri(URI.create("http://" + sittingspotdlUrl + "/")).POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(newSpot))).build();
                client.send(dlPostRequest, HttpResponse.BodyHandlers.ofString());
            }
            
            List<QueryResult> ret = new ArrayList<>();
            
            ret.addAll(newSpots.stream().map(x -> new QueryResult(x.id(),x.location())).toList());
            ret.addAll(dlData.stream().map(x -> new QueryResult(x.id(),x.location())).toList());
            
            return ret;
        }
        
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
