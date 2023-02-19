package com.romanboehm.destinations;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DestinationsTest {

    private DestinationService destinationService;
    private WireMockServer wireMock;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        destinationService = RestClientBuilder.newBuilder()
                .baseUri(URI.create(wireMock.baseUrl()))
                .build(DestinationService.class);
    }

    @AfterEach
    void tearDown() {
        wireMock.checkForUnmatchedRequests();
        wireMock.stop();
    }

    @Test
    public void noDestinationsReturnsNothing() {
        var destinations = new Destinations(destinationService, false, true, 20, Collections.emptyList());

        assertThatThrownBy(destinations::calculate).isInstanceOf(IllegalArgumentException.class).hasMessage("Provide at least two destinations");
    }

    @Test
    public void findsCommonDestinations() {
        wireMock.stubFor(get(urlPathEqualTo("/a")).willReturn(okJson("""
                [
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 5
                    },
                    {
                        "id": "y",
                        "name": "destination-y",
                        "duration": 5
                    }
                ]""")));
        wireMock.stubFor(get(urlPathEqualTo("/b")).willReturn(okJson("""
                [
                    {
                        "id": "a",
                        "name": "destination-a",
                        "duration": 5
                    },
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 10
                    }
                ]""")));
        wireMock.stubFor(get(urlPathEqualTo("/c")).willReturn(okJson("""
                [
                    {
                        "id": "a",
                        "name": "destination-a",
                        "duration": 5
                    },
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 15
                    }
                ]""")));

        var destinations = new Destinations(destinationService, false, false, 60, List.of("a", "b", "c"));

        assertThat(destinations.calculate()).containsAllEntriesOf(Map.of(
                "x", new Destination("x", "destination-x", 15)
        ));
    }

    @Test
    public void allowsDurationLimit() {
        wireMock.stubFor(get(urlPathEqualTo("/a")).willReturn(okJson("""
                [
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 5
                    },
                    {
                        "id": "y",
                        "name": "destination-y",
                        "duration": 30
                    }
                ]""")));
        wireMock.stubFor(get(urlPathEqualTo("/b")).willReturn(okJson("""
                [
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 5
                    },
                    {
                        "id": "y",
                        "name": "destination-y",
                        "duration": 10
                    }
                ]""")));
        wireMock.stubFor(get(urlPathEqualTo("/c")).willReturn(okJson("""
                [
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 5
                    },
                    {
                        "id": "y",
                        "name": "destination-y",
                        "duration": 10
                    }
                ]""")));

        var destinations = new Destinations(destinationService, false, false, 20, List.of("a", "b", "c"));

        assertThat(destinations.calculate()).containsAllEntriesOf(Map.of(
                "x", new Destination("x", "destination-x", 5)
        ));
    }

    @Test
    public void allowsOmittingDurationLimit() {
        wireMock.stubFor(get(urlPathEqualTo("/a")).willReturn(okJson("""
                [
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 10000000
                    }
                ]""")));
        wireMock.stubFor(get(urlPathEqualTo("/b")).willReturn(okJson("""
                [
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 20000000
                    }
                ]""")));
        wireMock.stubFor(get(urlPathEqualTo("/c")).willReturn(okJson("""
                [
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 30000000
                    }
                ]""")));

        var destinations = new Destinations(destinationService, false, false, null, List.of("a", "b", "c"));

        assertThat(destinations.calculate()).containsAllEntriesOf(Map.of(
                "x", new Destination("x", "destination-x", 30000000)
        ));
    }

    @Test
    public void considersStartingPointAsDestination() {
        wireMock.stubFor(get(urlPathEqualTo("/a")).willReturn(okJson("""
                [
                    {
                        "id": "x",
                        "name": "destination-x",
                        "duration": 5
                    }
                ]""")));
        wireMock.stubFor(get(urlPathEqualTo("/b")).willReturn(okJson("""
                [
                    {
                        "id": "a",
                        "name": "destination-a",
                        "duration": 5
                    }
                ]""")));
        wireMock.stubFor(get(urlPathEqualTo("/c")).willReturn(okJson("""
                [
                    {
                        "id": "a",
                        "name": "destination-a",
                        "duration": 5
                    }
                ]""")));

        var destinations = new Destinations(destinationService, false, true, 20, List.of("a", "b", "c"));

        assertThat(destinations.calculate()).containsAllEntriesOf(Map.of(
                "a", new Destination("a", "destination-a", 5)
        ));
    }

}