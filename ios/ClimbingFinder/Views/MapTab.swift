import SwiftUI
import MapKit

@MainActor
struct MapTab: View {
    @Environment(AppModel.self) private var model
    @State private var selection: String?
    @State private var camera: MapCameraPosition = .region(MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 51.515, longitude: -0.12),
        span: MKCoordinateSpan(latitudeDelta: 0.28, longitudeDelta: 0.36)))

    var body: some View {
        let gyms = model.data.gyms.filter(model.matchesFilters)
        let best = model.cheapest().first?.gym.id
        Map(position: $camera, selection: $selection) {
            ForEach(gyms) { g in
                Marker(g.shortName, systemImage: "figure.climbing",
                       coordinate: CLLocationCoordinate2D(latitude: g.lat, longitude: g.lng))
                    .tint(g.id == best ? Color.brand : Color.peakPurple)
                    .tag(g.id)
            }
            if let o = model.origin {
                Marker(o.label, systemImage: "location.fill",
                       coordinate: CLLocationCoordinate2D(latitude: o.lat, longitude: o.lng))
                    .tint(.blue)
            }
        }
        .mapControls { MapCompass(); MapScaleView() }
        .safeAreaInset(edge: .bottom) {
            if let id = selection, let g = model.gym(id) {
                MapGymCard(gym: g) { selection = nil }
                    .padding()
            }
        }
        .navigationTitle("Map")
        .inlineTitle()
        .toolbar {
            ToolbarItem(placement: .primaryAction) { FilterMenu() }
            ToolbarItem(placement: .primaryAction) {
                NavigationLink { NearbyList() } label: { Label("Nearest", systemImage: "list.bullet") }
            }
        }
    }
}

/// Card shown over the map for the tapped marker.
@MainActor
private struct MapGymCard: View {
    @Environment(AppModel.self) private var model
    let gym: Gym
    let close: () -> Void

    var body: some View {
        let best = model.pricer.bestOnDay(gym, model.dayMoment, conc: model.concession)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(gym.name).font(.headline)
                Spacer()
                Button(action: close) { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }
                    .buttonStyle(.plain).accessibilityLabel("Close")
            }
            if let best {
                HStack(spacing: 6) {
                    Text(formatPrice(best.price)).font(.title3.bold())
                    Pill(text: best.label, color: best.kind.color)
                    Text(best.when).font(.caption).foregroundStyle(.secondary)
                }
            }
            HStack(spacing: 8) {
                OpenBadge(state: gym.openState(model.moment))
                if let t = model.travelText(gym) { Label(t, systemImage: "tram.fill").labelStyle(.compact).font(.caption) }
                if let d = model.distanceKm(gym) { Text(String(format: "%.1f km", d)).font(.caption).foregroundStyle(.secondary) }
            }
            HStack {
                NavigationLink(value: gym) { Text("Details") }.buttonStyle(.borderedProminent)
                DirectionsButton(gym: gym).buttonStyle(.bordered)
            }
        }
        .padding()
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
    }
}

/// Gyms sorted by distance from your start location.
@MainActor
private struct NearbyList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let gyms = model.data.gyms.filter(model.matchesFilters)
            .sorted { (model.distanceKm($0) ?? .infinity, $0.name) < (model.distanceKm($1) ?? .infinity, $1.name) }
        List {
            Section {
                OriginPicker()
            }
            Section {
                ForEach(gyms) { g in
                    NavigationLink(value: g) {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(g.name)
                                Text(g.area ?? "").font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            VStack(alignment: .trailing) {
                                if let t = model.travelText(g) { Text(t).font(.subheadline) }
                                if let d = model.distanceKm(g) {
                                    Text(String(format: "%.1f km", d)).font(.caption).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            } footer: {
                Explainer("Travel = public-transport journey time from TfL's Journey Planner; distance is as the crow flies.")
            }
        }
        .navigationTitle("Nearest gyms")
        .inlineTitle()
    }
}

/// Opens public-transport directions in the Google Maps app, or on the
/// Google Maps website if the app isn't installed.
@MainActor
struct DirectionsButton: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL
    let gym: Gym

    var body: some View {
        Button {
            guard let app = appURL else { return }
            openURL(app) { opened in
                if !opened, let web = webURL { openURL(web) }
            }
        } label: {
            Label("Directions", systemImage: "arrow.triangle.turn.up.right.diamond")
        }
    }

    private var destination: String { gym.address ?? gym.postcode ?? "\(gym.lat),\(gym.lng)" }

    /// With no start location set, Google Maps starts from where you are.
    private var origin: String? { model.origin.map { "\($0.lat),\($0.lng)" } }

    private var appURL: URL? {
        var c = URLComponents(string: "comgooglemaps://")!
        c.queryItems = (origin.map { [URLQueryItem(name: "saddr", value: $0)] } ?? [])
            + [URLQueryItem(name: "daddr", value: destination), URLQueryItem(name: "directionsmode", value: "transit")]
        return c.url
    }

    private var webURL: URL? {
        var c = URLComponents(string: "https://www.google.com/maps/dir/")!
        c.queryItems = [URLQueryItem(name: "api", value: "1")]
            + (origin.map { [URLQueryItem(name: "origin", value: $0)] } ?? [])
            + [URLQueryItem(name: "destination", value: destination), URLQueryItem(name: "travelmode", value: "transit")]
        return c.url
    }
}
