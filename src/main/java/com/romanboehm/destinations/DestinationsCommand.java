package com.romanboehm.destinations;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@QuarkusMain
@Command(name = "destinations", mixinStandardHelpOptions = true)
public class DestinationsCommand implements Runnable, QuarkusApplication {

    @Inject
    CommandLine.IFactory factory;

    @Inject
    @RestClient
    DestinationService destinationService;

    @CommandLine.Option(names = {"--local-trains-only", "-l"}, defaultValue = "false",
            description = "Exclude IC(E) connections")
    private boolean localTrainsOnly;

    @CommandLine.Option(names = {"--consider-self", "-s"}, defaultValue = "false",
            description = "Consider starting points as destinations")
    private boolean considerSelf;

    @CommandLine.Option(names = {"--max-duration", "-m"}, defaultValue = CommandLine.Option.NULL_VALUE,
            description = "Maximum duration in minutes")
    private Integer maxDuration;

    @CommandLine.Parameters(paramLabel = "<location>", description = "location ID as per direkt.bahn.guru")
    private List<String> destinationCodes = new ArrayList<>();

    @Override
    public int run(String... args) {
        return new CommandLine(this, factory).execute(args);
    }

    @Override
    public void run() {
        var destinations = new Destinations(
                destinationService,
                localTrainsOnly,
                considerSelf,
                maxDuration,
                destinationCodes
        );

        var destinationMap = destinations.calculate();
        destinationMap.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getValue().duration()))
                .forEachOrdered(e -> System.out.printf("%s: %d%n", e.getValue().name(), e.getValue().duration()));

    }
}
