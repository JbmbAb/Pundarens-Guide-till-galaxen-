# Kartlager till Pundarens vän till galaxen

Punktlager i GeoJSON (WGS84, EPSG:4326) för Stockholms län, avsedda att läggas i appens `assets` och ritas med `GeoJsonLayer` eller parsas med Moshi.

## Källa och licens

Data © OpenStreetMap-bidragsgivare, tillgänglig under [Open Database License (ODbL)](https://opendatacommons.org/licenses/odbl/).

Appen **måste** ange upphov: till exempel *© OpenStreetMap-bidragsgivare* i kartans attribution, i en om-sida, eller i båda. Bearbetad data ska också följa ODbL (share-alike) om den sprids vidare.

Hämtat från den offentliga Overpass API:n den **19 november 2026, cirka 20:50–20:53 europeisk tid (Europe/Stockholm)**. OSM-basen som instansen använde var från samma kväll (ca 20:49–20:52 europeisk tid). `https://overpass-api.de/api/interpreter` och reservinstansen `https://overpass.kumi.systems/api/interpreter` svarade inte vid hämtningen, så frågorna kördes mot `https://overpass.openstreetmap.fr/api/interpreter`. Själva Overpass-frågorna är desamma som nedan.

## Filer och antal objekt

| Fil | Objekt | Innehåll |
|---|---:|---|
| `toilets.geojson` | 892 | `amenity=toilets` (723 noder, 169 vägar med centroid) |
| `rest_areas.geojson` | 17 | `highway=rest_area` (16) och `highway=services` (1) |
| `munchie_stations.geojson` | 2 292 | `amenity=fast_food` (1 137), `shop=convenience` (464), `shop=kiosk` (365), `amenity=fuel` (326) |

Alla features är `Point`. Koordinater är avrundade till sex decimaler. Kompakta egenskaper: `osm_id` (t.ex. `node/123`), `category`, samt `name` och `opening_hours` när de finns. Toaletter har även `fee`, `wheelchair` och `access` när taggarna finns. Munchie-stationer har `open_late`.

`open_late` på munchie-stationer (bästa möjliga tolkning av `opening_hours`):

- `true`: 455 (bl.a. `24/7`, stängning kl. 22:00 eller senare, eller öppet över midnatt)
- `false`: 707 (angivna tider som slutar före 22:00)
- `null`: 1 130 (saknar öppettider, eller gick inte att tolka)

**OBS:** Rastplatser är glest kartlagda i OSM i länet med just `highway=rest_area` och `highway=services`. Många rastplatser kan vara taggade som t.ex. `tourism=picnic_site` eller saknas helt. Täckningen blir bättre när PostGIS och dataportal- eller Trafikverket-lager kopplas in senare.

## Overpass-frågor

Område: Stockholms län, `admin_level=4`, `ISO3166-2=SE-AB`. `out center` ger centroid för vägar och relationer.

**Toaletter**

```
[out:json][timeout:180];
area["ISO3166-2"="SE-AB"]["admin_level"="4"]->.searchArea;
(
  nwr["amenity"="toilets"](area.searchArea);
);
out center tags;
```

**Rastplatser**

```
[out:json][timeout:180];
area["ISO3166-2"="SE-AB"]["admin_level"="4"]->.searchArea;
(
  nwr["highway"="rest_area"](area.searchArea);
  nwr["highway"="services"](area.searchArea);
);
out center tags;
```

**Munchie stations**

```
[out:json][timeout:180];
area["ISO3166-2"="SE-AB"]["admin_level"="4"]->.searchArea;
(
  nwr["shop"="convenience"](area.searchArea);
  nwr["amenity"="fast_food"](area.searchArea);
  nwr["shop"="kiosk"](area.searchArea);
  nwr["amenity"="fuel"](area.searchArea);
);
out center tags;
```

## Ladda ett lager från assets i Kotlin

Lägg filerna i `app/src/main/assets/`. Lägg till beroendet `com.google.maps.android:android-maps-utils` för klassen `GeoJsonLayer`.

```kotlin
import android.content.Context
import com.google.android.gms.maps.GoogleMap
import com.google.maps.android.data.geojson.GeoJsonLayer
import org.json.JSONObject

fun addGeoJsonFromAssets(
    context: Context,
    map: GoogleMap,
    assetName: String
): GeoJsonLayer {
    val geoJson = context.assets
        .open(assetName)
        .bufferedReader()
        .use { it.readText() }
    return GeoJsonLayer(map, JSONObject(geoJson)).also { layer ->
        layer.addLayerToMap()
    }
}

// Exempel, t.ex. i onMapReady eller i maps-compose onMapLoaded:
// addGeoJsonFromAssets(this, googleMap, "toilets.geojson")
// addGeoJsonFromAssets(context, googleMap, "munchie_stations.geojson")
```
