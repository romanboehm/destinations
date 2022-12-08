# Destinations

_Destinations_ calculates, for a given set of locations, or starting points, the set of locations reachable from those via a direct Deutsche Bahn (DB) train connection.

This is made possible by the great [direkt.bahn.guru](https://github.com/juliuste/direkt.bahn.guru) project.

## Example

```shell
java Destinations.java --max-duration 120 --consider-start 8000260 8000105 8000096
```

will get you 
- all destinations directly reachable within 120 minutes 
- from Frankfurt/Main Main Station, Stuttgart Main Station, and Würzburg Main Station<sup>1</sup>
- while also considering the starting stations as possible destinations
- while also considering IC(E) connections
- sorted after maximum travel duration

```
Frankfurt(Main)Hbf: 77
Frankfurt(M) Flughafen Fernbf: 84
Frankfurt(Main)Süd: 99
Hanau Hbf: 101
Mainz Hbf: 107
```

## Usage

### Prerequisites

Java 17+

### (1) Location IDs

Currently, one still needs to first obtain the relevant location IDs from bahn.guru.

1. Visit [https://direkt.bahn.guru](https://direkt.bahn.guru).
2. Use the search functionality to pick a starting point.
3. Take the location ID from the URL bar of your browser: `https://direkt.bahn.guru/?origin=<numeric-location-id-to-copy>` (after the `origin` request parameter).
