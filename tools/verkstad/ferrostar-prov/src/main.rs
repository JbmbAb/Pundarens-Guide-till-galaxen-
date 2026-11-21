//! Ferrostar-prov i verkstaden (etapp 2, G2 på skrivbordsnivå).
//! 1. Läser en Valhalla-rutt i OSRM-format med Ferrostars `OsrmResponseParser` (precision 6),
//!    samma parser som `createOsrmResponseParser(6u)` på Android.
//! 2. Kör `NavigationController` med simulerade positioner längs rutten och räknar stegbyten,
//!    utlösta talinstruktioner och om resan når `Complete`.
//! 3. Lägger in avvikelser (positioner 250 m vid sidan av rutten) och kontrollerar att varje
//!    upptäcks och att rutten återtas när positionen är tillbaka.
//! Omräkning efter avvikelse sker i Kotlin (FerrostarCore), inte här, och provas därför inte.
use ferrostar::deviation_detection::{RouteDeviation, RouteDeviationTracking};
use ferrostar::models::{CourseOverGround, GeographicCoordinate, UserLocation};
use ferrostar::navigation_controller::models::{
    CourseFiltering, NavigationControllerConfig, TripState, WaypointAdvanceMode,
};
use ferrostar::navigation_controller::step_advance::conditions::DistanceToEndOfStepCondition;
use ferrostar::navigation_controller::{NavigationController, Navigator};
use ferrostar::routing_adapters::RouteResponseParser;
use ferrostar::routing_adapters::osrm::OsrmResponseParser;
use ferrostar::simulation::{LocationBias, advance_location_simulation, location_simulation_from_route};
use std::collections::BTreeSet;
use std::sync::Arc;
use std::time::SystemTime;

fn plats(lat: f64, lng: f64) -> UserLocation {
    UserLocation {
        coordinates: GeographicCoordinate { lat, lng },
        horizontal_accuracy: 5.0,
        course_over_ground: None::<CourseOverGround>,
        timestamp: SystemTime::now(),
        speed: None,
    }
}

/// Ungefärligt avstånd i meter (ekvirektangulär projektion, räcker för några hundra meter).
fn avstand(a: GeographicCoordinate, b: GeographicCoordinate) -> f64 {
    let k = 111_320.0;
    let dx = (a.lng - b.lng) * k * a.lat.to_radians().cos();
    let dy = (a.lat - b.lat) * k;
    (dx * dx + dy * dy).sqrt()
}

/// Punkt `meter` vinkelrätt mot riktningen c→n. Väljer den sida vars närmaste ruttpunkt ligger längst bort.
fn sidoforskjuten(route: &ferrostar::models::Route, c: GeographicCoordinate, n: GeographicCoordinate, meter: f64) -> GeographicCoordinate {
    let k = 111_320.0; let cos = c.lat.to_radians().cos();
    let (dx, dy) = ((n.lng - c.lng) * k * cos, (n.lat - c.lat) * k);
    let l = (dx * dx + dy * dy).sqrt().max(1e-9);
    let (px, py) = (-dy / l * meter, dx / l * meter);
    let kandidater = [1.0, -1.0].map(|t| GeographicCoordinate { lat: c.lat + t * py / k, lng: c.lng + t * px / (k * cos) });
    let narmast = |p: GeographicCoordinate| route.geometry.iter().map(|g| avstand(p, *g)).fold(f64::MAX, f64::min);
    if narmast(kandidater[0]) >= narmast(kandidater[1]) { kandidater[0] } else { kandidater[1] }
}

fn main() {
    let filer: Vec<String> = std::env::args().skip(1).collect();
    let mut fel = 0;
    for fil in filer {
        let bytes = std::fs::read(&fil).expect("kan inte läsa filen");
        let routes = match OsrmResponseParser::new(6).parse_response(bytes) {
            Ok(r) => r,
            Err(e) => { println!("{fil}: PARSER-FEL {e:?}"); fel += 1; continue; }
        };
        let route = routes[0].clone();
        let steg = route.steps.len();
        let tal: usize = route.steps.iter().map(|s| s.spoken_instructions.len()).sum();
        let bild: usize = route.steps.iter().map(|s| s.visual_instructions.len()).sum();
        let config = NavigationControllerConfig {
            waypoint_advance: WaypointAdvanceMode::WaypointWithinRange(100.0),
            step_advance_condition: Arc::new(DistanceToEndOfStepCondition { distance: 10, minimum_horizontal_accuracy: 25 }),
            arrival_step_advance_condition: Arc::new(DistanceToEndOfStepCondition { distance: 10, minimum_horizontal_accuracy: 25 }),
            route_deviation_tracking: RouteDeviationTracking::StaticThreshold { minimum_horizontal_accuracy: 25, max_acceptable_deviation: 50.0 },
            snapped_location_course_filtering: CourseFiltering::Raw,
        };
        let nav = NavigationController::new(route.clone(), config);
        let mut sim = location_simulation_from_route(&route, Some(10.0), LocationBias::None).expect("simulering");
        let mut state = nav.get_initial_state(sim.current_location.clone());
        let mut stegbyten = 0usize;
        let mut sagda: BTreeSet<String> = BTreeSet::new();
        let mut forsta_tal: Vec<String> = vec![];
        let mut kvar_forut = steg;
        let mut avvikelser_injicerade = 0; let mut avvikelser_upptackta = 0; let mut aterfunnen = true;
        let mut forsta_upptackt: Vec<usize> = vec![]; let mut aterfunnen_efter: Vec<usize> = vec![];
        let mut i = 0usize; let mut klar = false;
        while i < 100_000 {
            i += 1;
            if let TripState::Navigating { remaining_steps, spoken_instruction, .. } = state.trip_state() {
                if remaining_steps.len() < kvar_forut { stegbyten += kvar_forut - remaining_steps.len(); kvar_forut = remaining_steps.len(); }
                if let Some(s) = spoken_instruction {
                    if sagda.insert(s.utterance_id.to_string()) && forsta_tal.len() < 3 { forsta_tal.push(s.text.clone()); }
                }
            } else if let TripState::Complete { .. } = state.trip_state() { klar = true; break; }
            // Avvikelseprov (tre försök, vid simuleringssteg 200, 600 och 1000): tre positioner
            // 250 m vinkelrätt mot färdriktningen (den sida som ligger längst från rutten), sedan tillbaka på rutten.
            if i == 200 || i == 600 || i == 1000 {
                avvikelser_injicerade += 1;
                let c = sim.current_location.coordinates;
                let n = advance_location_simulation(&sim).current_location.coordinates;
                let ut = sidoforskjuten(&route, c, n, 250.0);
                let mut sedd = false;
                for k in 0..3 {
                    state = nav.update_user_location(plats(ut.lat + 0.0001 * k as f64, ut.lng), state);
                    if let TripState::Navigating { deviation, .. } = state.trip_state() {
                        if std::env::var("FELSOK").is_ok() { println!("    försök vid {i}, avvikande position {k}: {deviation:?}"); }
                        if let RouteDeviation::Deviation { .. } = deviation { if !sedd { forsta_upptackt.push(k + 1); } sedd = true; }
                    }
                }
                if sedd { avvikelser_upptackta += 1; }
                let mut tillbaka = None;
                for k in 0..3 {
                    state = nav.update_user_location(sim.current_location.clone(), state);
                    if let TripState::Navigating { deviation: RouteDeviation::NoDeviation, .. } = state.trip_state() { tillbaka = Some(k + 1); break; }
                }
                match tillbaka { Some(n) => aterfunnen_efter.push(n), None => aterfunnen = false }
            }
            let next = advance_location_simulation(&sim);
            if next.current_location.coordinates == sim.current_location.coordinates { 
                state = nav.update_user_location(next.current_location.clone(), state);
                if let TripState::Complete { .. } = state.trip_state() { klar = true; }
                break;
            }
            sim = next;
            state = nav.update_user_location(sim.current_location.clone(), state);
        }
        let ok = klar && avvikelser_injicerade == 3 && avvikelser_upptackta == avvikelser_injicerade && aterfunnen && !sagda.is_empty();
        if !ok { fel += 1; }
        println!("{fil}: rutter={} steg={steg} talinstruktioner_i_rutten={tal} bildinstruktioner={bild} | simulering: positioner={i} stegbyten={stegbyten} utlösta_tal={} klar={klar} | avvikelse: {avvikelser_upptackta}/{avvikelser_injicerade} försök upptäckta (vid avvikande position nr {forsta_upptackt:?}), tillbaka på rutten={aterfunnen} (efter {aterfunnen_efter:?} positioner) | {}",
            routes.len(), sagda.len(), if ok {"OK"} else {"FEL"});
        for t in forsta_tal { println!("    tal: {t}"); }
    }
    println!("TOTALT FEL: {fel}");
    std::process::exit(if fel == 0 { 0 } else { 1 });
}
