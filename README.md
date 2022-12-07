# Destinations

_Destinations_ calculates, for a given set of locations, or starting points, the set of locations reachable from those via a direct Deutsche Bahn (DB) train connection.

## Example

```shell
java Destinations.java --max-duration 80 --consider-start 8000260 8000105
```

will get you 
- all destinations directly reachable within 80 minutes 
- from **both** Frankfurt/Main Main Station and Würzburg Main Station
- while also considering the starting stations as possible destinations
- while also considering IC(E) connections
- sorted after maximum travel duration

```
Aschaffenburg Hbf: 36
Hanau Hbf: 47
Fulda: 50
Heigenbr?cken: 52
Wiesthal: 56
Partenstein: 61
Laufach: 63
Frankfurt(Main)Hbf: 65
W?rzburg Hbf: 66
H?sbach: 67
Lohr Bahnhof: 67
Kleinostheim: 70
Langenprozelten: 73
Dettingen(Main): 74
Gem?nden(Main): 76
Kahl(Main): 78
R?ckersbacher Schlucht: 80
```

## Usage

### Prerequisites

Java 17+

### Location IDs

Currently, one still needs to first obtain the relevant location IDs from bahn.guru.

1. Visit [https://direkt.bahn.guru](https://direkt.bahn.guru).
2. Use the search functionality to pick a starting point.
3. Take the location ID from the URL bar of your browser: `https://direkt.bahn.guru/?origin=<numeric-location-id-to-copy>` (after the `origin` request parameter).


