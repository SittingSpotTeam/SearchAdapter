package com.sittingspot.searchadapter.controller;

import com.sittingspot.searchadapter.models.Area;
import com.sittingspot.searchadapter.models.QueryResult;
import com.sittingspot.searchadapter.models.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController("/search-adapter/api/v1")
public class SearchAdapterController {

    @GetMapping("/")
    public List<QueryResult> search(@RequestParam("location") Area location,
                                    @RequestParam(value = "tags",required = false) List<Tag> tags,
                                    @RequestParam(value = "labels",required = false) List<String> labels) {
        return List.of();
    }
}
