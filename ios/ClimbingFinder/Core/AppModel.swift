import Foundation
import Observation
import CoreLocation

enum CheapestMode: String, CaseIterable, Identifiable {
    case day, time
    var id: String { rawValue }
    var title: String { self == .day ? "Best price all day" : "At selected time" }
}

/// Where travel times and distances are measured from.
struct Origin: Codable, Equatable {
    static let myLocationLabel = "My location"
    var label: String
    var lat: Double
    var lng: Double
}

/// Which copy of gyms.json the app is showing.
enum DataSource: Equatable {
    case bundled            // built into the app
    case cached(Date)       // last copy downloaded from the website
    case website(Date)      // downloaded from the website this session

    var summary: String {
        switch self {
        case .bundled: return "Built into the app"
        case .cached(let d): return "From the website, " + d.formatted(.relative(presentation: .named))
        case .website(let d): return "From the website, checked " + d.formatted(date: .omitted, time: .shortened)
        }
    }
}

@MainActor
@Observable
final class AppModel {
    private(set) var data: ClimbingData
    private(set) var dataSource: DataSource
    private(set) var pricer: Pricer
    private var lastDataCheck: Date?
    /// Logos from the copy built into the app, used when a downloaded copy lacks them.
    private let bundledLogos: [String: (url: String, background: String?)]

    // Filters shared by the Cheapest, Map and Gyms tabs.
    /// The date and time being priced. Follows the clock unless you pick one yourself.
    private(set) var when: Date = .now
    private(set) var whenPinned = false
    var mode: CheapestMode = .day
    var concession: Bool = UserDefaults.standard.bool(forKey: Keys.concession) {
        didSet { UserDefaults.standard.set(concession, forKey: Keys.concession) }
    }
    var typeFilter = ""      // "" | "bouldering" | "ropes"
    var zoneFilter: Int?     // nil = any zone
    var acFilter = ""        // "" | "ac" | "full"
    var favouritesOnly: Bool = UserDefaults.standard.bool(forKey: Keys.favouritesOnly) {
        didSet { UserDefaults.standard.set(favouritesOnly, forKey: Keys.favouritesOnly) }
    }

    // Personal favourites, notes & 1-5 ratings, kept on this device.
    private(set) var favourites: Set<String> = AppModel.load(Keys.favourites) ?? []
    private(set) var notes: [String: String] = AppModel.load(Keys.notes) ?? [:]
    private(set) var ratings: [String: Int] = AppModel.load(Keys.ratings) ?? [:]

    var origin: Origin? = AppModel.load(Keys.origin) {
        didSet {
            AppModel.save(origin, Keys.origin)
            if origin != oldValue { travel = [:]; travelFor = nil; Task { await refreshTravel() } }
        }
    }
    private(set) var travel: [String: Int] = [:]   // gym id -> minutes by public transport
    private(set) var travelLoading = false
    private var travelFor: Origin?
    private var travelFetchedAt: Date?

    private enum Keys {
        static let concession = "concession", notes = "gymNotes", ratings = "gymRatings"
        static let origin = "origin", bankHolidays = "bankHolidays"
        static let favourites = "favourites", favouritesOnly = "favouritesOnly"
    }

    private static let remoteURL = URL(string: "https://london-climbing-finder.pages.dev/gyms.json")!
    private static var cacheURL: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("gyms.json")
    }

    init() {
        let bundled = try! JSONDecoder().decode(ClimbingData.self, from: Data(
            contentsOf: Bundle.main.url(forResource: "gyms", withExtension: "json")!))
        bundledLogos = Dictionary(uniqueKeysWithValues: bundled.gyms.compactMap { g in
            g.logo.map { (g.id, ($0, g.logoBackground)) }
        })
        // Start from the last website download unless this build carries newer research.
        if let d = try? Data(contentsOf: Self.cacheURL),
           let cached = try? JSONDecoder().decode(ClimbingData.self, from: d),
           (cached.meta.lastResearchDate ?? "") >= (bundled.meta.lastResearchDate ?? "") {
            let saved = (try? FileManager.default.attributesOfItem(atPath: Self.cacheURL.path))?[.modificationDate] as? Date
            data = cached
            dataSource = .cached(saved ?? .now)
        } else {
            data = bundled
            dataSource = .bundled
        }
        var bh: Set<String> = AppModel.load(Keys.bankHolidays) ?? []
        if bh.isEmpty, let u = Bundle.main.url(forResource: "bank_holidays", withExtension: "json"),
           let d = try? Data(contentsOf: u) {
            bh = AppModel.parseBankHolidays(d)
        }
        pricer = Pricer(bankHolidays: bh)
    }

    var moment: Moment { Moment(when) }
    var dayMoment: Moment { moment.at(0) }
    var isBankHoliday: Bool { pricer.isBankHoliday(moment) }
    var climberLabel: String { concession ? "Student / concession" : "Adult" }

    var filtersActive: Bool { !typeFilter.isEmpty || zoneFilter != nil || !acFilter.isEmpty || favouritesOnly }
    var zones: [Int] { Array(Set(data.gyms.map(\.zone))).sorted() }

    func matchesFilters(_ g: Gym) -> Bool {
        if favouritesOnly && !favourites.contains(g.id) { return false }
        if !typeFilter.isEmpty && !g.type.contains(typeFilter) { return false }
        if let z = zoneFilter, g.zone != z { return false }
        return g.matchesAC(acFilter)
    }

    func clearFilters() { typeFilter = ""; zoneFilter = nil; acFilter = ""; favouritesOnly = false }

    func gym(_ id: String) -> Gym? { data.gyms.first { $0.id == id } }

    func logo(for g: Gym) -> (url: String, background: String?)? {
        g.logo.map { ($0, g.logoBackground) } ?? bundledLogos[g.id]
    }

    // MARK: Date & time

    /// A date or time you picked stays put until you tap Now or the day has passed.
    func pickWhen(_ date: Date) { when = date; whenPinned = true }

    func resetToNow() { when = .now; whenPinned = false }

    /// Called every minute and whenever the app comes back to the screen.
    func followClock() {
        if !whenPinned || Moment(when).ymd < Moment(.now).ymd { resetToNow() }
    }

    func becameActive() async {
        followClock()
        if let t = travelFetchedAt, Date.now.timeIntervalSince(t) > 3600 { travel = [:] }  // departures move on
        await refreshData()
        await refreshMyLocation()
        await refreshTravel()
    }

    // MARK: Cheapest ranking

    struct Ranked: Identifiable {
        let gym: Gym
        let price: Double
        let label: String
        let kind: PriceKind
        let when: String
        let alts: [DealOption]
        var id: String { gym.id }
    }

    func cheapest() -> [Ranked] {
        var rows: [Ranked] = []
        for g in data.gyms where matchesFilters(g) && g.temporarilyClosed == nil {
            switch mode {
            case .day:
                if !g.hours.isEmpty && g.hours[dayMoment.weekday] == nil { continue }   // shut all day, e.g. Mount Palace on Mondays
                guard let b = pricer.bestOnDay(g, dayMoment, conc: concession) else { continue }
                var seen: Set<String> = [String(format: "%.2f", b.price)], alts: [DealOption] = []
                for o in pricer.slotsOnDay(g, dayMoment, conc: concession) {
                    let k = String(format: "%.2f", o.price)
                    if seen.contains(k) { continue }
                    seen.insert(k); alts.append(o)
                    if alts.count >= 2 { break }
                }
                rows.append(Ranked(gym: g, price: b.price, label: b.label, kind: b.kind, when: b.when, alts: alts))
            case .time:
                let r = pricer.applicable(g, moment, conc: concession)
                guard let p = r.price else { continue }
                rows.append(Ranked(gym: g, price: p, label: r.label, kind: r.kind,
                                   when: pricer.windowForTime(g, moment, conc: concession), alts: []))
            }
        }
        return rows.enumerated().sorted { ($0.element.price, $0.offset) < ($1.element.price, $1.offset) }.map(\.element)
    }

    // MARK: Favourites, notes & ratings

    func isFavourite(_ id: String) -> Bool { favourites.contains(id) }

    func toggleFavourite(_ id: String) {
        if favourites.remove(id) == nil { favourites.insert(id) }
        AppModel.save(favourites, Keys.favourites)
    }

    func setNote(_ text: String, for id: String) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        notes[id] = t.isEmpty ? nil : t
        AppModel.save(notes, Keys.notes)
    }

    func setRating(_ r: Int, for id: String) {
        ratings[id] = r == 0 ? nil : r
        AppModel.save(ratings, Keys.ratings)
    }

    // MARK: Live prices (gyms.json from the website, cached for offline use)

    /// Downloads the deployed gyms.json; keeps the current copy if that fails.
    /// Checks at most every 30 minutes unless `force` (pull to refresh).
    func refreshData(force: Bool = false) async {
        if !force, let last = lastDataCheck, Date.now.timeIntervalSince(last) < 1800 { return }
        var req = URLRequest(url: Self.remoteURL)
        req.cachePolicy = .reloadIgnoringLocalCacheData
        req.timeoutInterval = 15
        guard let (d, resp) = try? await URLSession.shared.data(for: req),
              (resp as? HTTPURLResponse)?.statusCode == 200,
              let fresh = try? JSONDecoder().decode(ClimbingData.self, from: d), !fresh.gyms.isEmpty else { return }
        lastDataCheck = .now
        // Never swap in a website copy older than the data we already have (e.g. a
        // build carrying a new gym before the site is redeployed).
        guard (fresh.meta.lastResearchDate ?? "") >= (data.meta.lastResearchDate ?? "") else { return }
        data = fresh
        dataSource = .website(.now)
        try? FileManager.default.createDirectory(at: Self.cacheURL.deletingLastPathComponent(),
                                                 withIntermediateDirectories: true)
        try? d.write(to: Self.cacheURL, options: .atomic)
    }

    // MARK: Location, distance & travel

    func distanceKm(_ g: Gym) -> Double? {
        origin.map { haversineKm(($0.lat, $0.lng), (g.lat, g.lng)) }
    }

    func travelText(_ g: Gym) -> String? {
        guard origin != nil else { return nil }
        if let m = travel[g.id] { return "\(m) min" }
        return travelLoading ? "…" : nil
    }

    /// Public-transport journey time to every gym via the free TfL Journey Planner.
    /// Only fetches gyms still missing a time, so a failed lookup is retried next time.
    func refreshTravel() async {
        guard let o = origin, !travelLoading else { return }
        if travelFor != o { travel = [:]; travelFor = o }
        let missing = data.gyms.filter { travel[$0.id] == nil }
        guard !missing.isEmpty else { return }
        travelLoading = true
        let results = await TravelService.journeyMinutes(from: o, to: missing)
        if origin == o {
            if travel.isEmpty { travelFetchedAt = .now }
            travel.merge(results) { $1 }
        }
        travelLoading = false
        if origin != o { await refreshTravel() }   // location changed while we were fetching
    }

    /// Keeps "My location" current when you've moved, without asking for permission again.
    func refreshMyLocation() async {
        guard let o = origin, o.label == Origin.myLocationLabel, LocationService.shared.isAuthorized,
              let l = try? await LocationService.shared.currentLocation() else { return }
        let here = (l.coordinate.latitude, l.coordinate.longitude)
        if haversineKm((o.lat, o.lng), here) > 0.3 {
            origin = Origin(label: Origin.myLocationLabel, lat: here.0, lng: here.1)
        }
    }

    // MARK: Bank holidays (England & Wales, live from gov.uk, cached)

    func refreshBankHolidays() async {
        guard let url = URL(string: "https://www.gov.uk/bank-holidays.json"),
              let (d, _) = try? await URLSession.shared.data(from: url) else { return }
        let set = AppModel.parseBankHolidays(d)
        if !set.isEmpty { pricer.bankHolidays = set; AppModel.save(set, Keys.bankHolidays) }
    }

    nonisolated static func parseBankHolidays(_ d: Data) -> Set<String> {
        struct Feed: Decodable {
            struct Division: Decodable { struct Event: Decodable { let date: String }; let events: [Event] }
            let ew: Division
            enum CodingKeys: String, CodingKey { case ew = "england-and-wales" }
        }
        guard let f = try? JSONDecoder().decode(Feed.self, from: d) else { return [] }
        return Set(f.ew.events.map(\.date))
    }

    // MARK: Persistence

    nonisolated private static func load<T: Decodable>(_ key: String) -> T? {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(T.self, from: $0) }
    }

    nonisolated private static func save<T: Encodable>(_ value: T?, _ key: String) {
        guard let value else { UserDefaults.standard.removeObject(forKey: key); return }
        UserDefaults.standard.set(try? JSONEncoder().encode(value), forKey: key)
    }
}
