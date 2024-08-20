package com.sittingspot.searchadapter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sittingspot.searchadapter.DTO.SittingSpotInDTO;
import com.sittingspot.searchadapter.DTO.SittingSpotOutDTO;
import com.sittingspot.searchadapter.models.*;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@RestController
@RequestMapping("/api/v1")
public class SearchAdapterController {

    @Value("${sittingspot.sittingspotdl.url}")
    private String sittingspotdlUrl;

    @Value("${sittingspot.osm.endpoint}")
    private String osmEndpoint;

    @GetMapping
    public List<QueryResult> search(@RequestParam("x") Double x,
                                    @RequestParam("y") Double y,
                                    @RequestParam("area") Double area,
                                    @RequestParam(value = "tags",required = false) List<Tag> tags,
                                    @RequestParam(value = "labels",required = false) List<String> labels) throws IOException, InterruptedException {
        var client = HttpClient.newHttpClient();

        List<SittingSpotInDTO> osmData = new ArrayList<>();
        var location = new Area(new Location(x,y),area);
        // as labels are unique to our system, if the query involves elements with a certain combination of labels
        // only the sitting spot already in our data layer can match the description, so, it does a request to osm
        // only if there are no restriction on labels.
            if (labels == null || labels.isEmpty()) {
                StringBuilder osmQuery = new StringBuilder("[out:json];\nnode[amenity=bench]");
                if(tags != null) {
                    for (var tag : tags) {
                        osmQuery.append("[").append(tag.key()).append("=").append(tag.value()).append("]");
                    }
                }
                osmQuery.append("(around:" + location.range() + "," + location.center().y() + "," + location.center().x() + ");\n" + "out body;");
                log.info("Sending request: " + osmEndpoint + " " + osmQuery.toString());
                var osmRequest = HttpRequest.newBuilder()
                        .uri(URI.create(osmEndpoint))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(osmQuery.toString())).build();

                var osmResult = client.send(osmRequest, HttpResponse.BodyHandlers.ofString());

                // TODO: check osm return codes
                log.info("Got response code " + osmResult.statusCode());

                if (osmResult.statusCode() == 200) {
                    OsmQueryResult data = new ObjectMapper().readValue(osmResult.body(), OsmQueryResult.class);
                    // lon = x, lat = y
                    log.info("got: "+osmResult.body());
                    // convert list of elements to list of sitting spot
                    osmData = data.getElements()
                            .stream()
                            .map(e -> new SittingSpotInDTO(String.format ("%.0f", e.id()), new Location(e.lon(), e.lat()), e.tags()
                                    .entrySet()
                                    .stream()
                                    .map(t -> new Tag(t.getKey(), t.getValue()))
                                    .toList()))
                            .toList();
                }
            }


        var searchRequestUrl = "http://" + sittingspotdlUrl  + "?x="+x+"&y="+y+"&area="+area;
        if(tags != null){
            searchRequestUrl += URLEncoder.encode("&tags="+tags, "UTF-8");
        }
        if(labels != null){
            searchRequestUrl += URLEncoder.encode("&labels="+labels, "UTF-8");
        }
        log.info("Sending request: " + searchRequestUrl);

        var dlRequest = HttpRequest.newBuilder()
                .uri(URI.create(searchRequestUrl))
                .header("Content-Type", "application/json")
                .GET()
                .build();
        
        var dlResult = client.send(dlRequest, HttpResponse.BodyHandlers.ofString());
        log.info("Got response code " + dlResult.statusCode());
        if(dlResult.statusCode() == 200) {
            List<SittingSpotOutDTO> dlData = (new ObjectMapper()).readerForListOf(SittingSpotOutDTO.class).readValue(dlResult.body());

            log.info("Sending request: " + "http://" + sittingspotdlUrl + " for each spot found");
            // update our data layer with new entries from osm
            var newSpots = osmData.stream().filter(e -> !dlData.stream().anyMatch(s -> s.id() == e.id())).toList();
            for(var newSpot : newSpots) {
                var dlPostRequest = HttpRequest.newBuilder()
                        .uri(URI.create("http://" + sittingspotdlUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(newSpot)))
                        .build();
                client.send(dlPostRequest, HttpResponse.BodyHandlers.ofString());
            }
            
            List<QueryResult> ret = new ArrayList<>();
            
            ret.addAll(newSpots.stream().map(e -> new QueryResult(e.id(),e.location())).toList());
            ret.addAll(dlData.stream().map(e -> new QueryResult(e.id(),e.location())).toList());
            
            return ret;
        }
        
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
