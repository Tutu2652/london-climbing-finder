import Foundation
import CoreLocation

/// TfL Journey Planner (free, no key) — same requests the website makes.
enum TravelService {
    static func journeyMinutes(from o: Origin, to gyms: [Gym]) async -> [String: Int] {
        let from = String(format: "%.5f,%.5f", o.lat, o.lng)
        let depart = departParams()
        return await withTaskGroup(of: (String, Int?).self) { group in
            var out: [String: Int] = [:]
            var pending = gyms[...]
            func enqueue() {
                guard let g = pending.popFirst() else { return }
                group.addTask {
                    let to = g.postcode ?? "\(g.lat),\(g.lng)"
                    let path = "https://api.tfl.gov.uk/Journey/JourneyResults/\(enc(from))/to/\(enc(to))" + (depart.isEmpty ? "" : "?" + depart)
                    return (g.id, await duration(path))
                }
            }
            for _ in 0..<4 { enqueue() }          // at most 4 requests in flight
            for await (id, mins) in group {
                if let mins { out[id] = mins }
                enqueue()
            }
            return out
        }
    }

    private static func enc(_ s: String) -> String {
        s.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/"))) ?? s
    }

    private static func duration(_ path: String) async -> Int? {
        struct Result: Decodable { struct Journey: Decodable { let duration: Int }; let journeys: [Journey]? }
        guard let url = URL(string: path),
              let (d, resp) = try? await URLSession.shared.data(from: url),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let r = try? JSONDecoder().decode(Result.self, from: d) else { return nil }
        return r.journeys?.first?.duration
    }

    /// Real-time departure between 08:00 and 23:00; otherwise a stable weekday
    /// 10:00 departure so overnight numbers stay sensible.
    private static func departParams() -> String {
        let cal = Calendar.london
        let h = cal.component(.hour, from: .now)
        if h >= 8 && h < 23 { return "" }
        var d = cal.date(bySettingHour: 10, minute: 0, second: 0, of: .now)!
        if h >= 23 { d = cal.date(byAdding: .day, value: 1, to: d)! }
        while cal.isDateInWeekend(d) { d = cal.date(byAdding: .day, value: 1, to: d)! }
        let c = cal.dateComponents([.year, .month, .day], from: d)
        return String(format: "date=%04d%02d%02d&time=1000&timeIs=Departing", c.year!, c.month!, c.day!)
    }
}

/// One-shot "use my location" plus postcode / place lookup.
@MainActor
final class LocationService: NSObject, CLLocationManagerDelegate {
    static let shared = LocationService()
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<CLLocation, Error>?

    enum Failure: LocalizedError {
        case denied, notFound
        var errorDescription: String? {
            switch self {
            case .denied: return "Location access is off. Allow it in Settings, or type a postcode instead."
            case .notFound: return "Couldn't find that place. Try a postcode, e.g. SW1A 1AA."
            }
        }
    }

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    var isAuthorized: Bool {
        [.authorizedWhenInUse, .authorizedAlways].contains(manager.authorizationStatus)
    }

    /// Asks once for location access (used by the map's blue dot).
    func requestAuthorizationIfNeeded() {
        if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
    }

    func currentLocation() async throws -> CLLocation {
        switch manager.authorizationStatus {
        case .denied, .restricted: throw Failure.denied
        default: break
        }
        return try await withCheckedThrowingContinuation { c in
            continuation?.resume(throwing: CancellationError())
            continuation = c
            if manager.authorizationStatus == .notDetermined { manager.requestWhenInUseAuthorization() }
            else { manager.requestLocation() }
        }
    }

    func geocode(_ query: String) async throws -> Origin {
        let marks = try? await CLGeocoder().geocodeAddressString(query + ", London, UK")
        guard let loc = marks?.first?.location else { throw Failure.notFound }
        return Origin(label: query, lat: loc.coordinate.latitude, lng: loc.coordinate.longitude)
    }

    nonisolated func locationManagerDidChangeAuthorization(_ m: CLLocationManager) {
        Task { @MainActor in
            guard continuation != nil else { return }
            switch manager.authorizationStatus {
            case .denied, .restricted: finish(.failure(Failure.denied))
            case .notDetermined: break
            default: manager.requestLocation()
            }
        }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        guard let l = locs.last else { return }
        Task { @MainActor in finish(.success(l)) }
    }

    nonisolated func locationManager(_ m: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in finish(.failure(error)) }
    }

    private func finish(_ r: Result<CLLocation, Error>) {
        continuation?.resume(with: r)
        continuation = nil
    }
}
