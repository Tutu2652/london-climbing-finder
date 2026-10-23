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
    var label: String
    var lat: Double
    var lng: Double
}

@MainActor
@Observable
final class AppModel {
    let data: ClimbingData
    private(set) var pricer: Pricer

    // Filters shared by the Cheapest, Map and Gyms tabs.
    var when: Date = .now
    var mode: CheapestMode = .day
    var concession: Bool = UserDefaults.standard.bool(forKey: Keys.concession) {
        didSet { UserDefaults.standard.set(concession, forKey: Keys.concession) }
    }
    var typeFilter = ""      // "" | "bouldering" | "ropes"
    var zoneFilter: Int?     // nil = any zone
    var acFilter = ""        // "" | "ac" | "full"

    // Personal notes & 1-5 ratings, kept on this device.
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

    private enum Keys {
        static let concession = "concession", notes = "gymNotes", ratings = "gymRatings"
        static let origin = "origin", bankHolidays = "bankHolidays"
    }

    init() {
        let url = Bundle.main.url(forResource: "gyms", withExtension: "json")!
        data = try! JSONDecoder().decode(ClimbingData.self, from: Data(contentsOf: url))
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

    var filtersActive: Bool { !typeFilter.isEmpty || zoneFilter != nil || !acFilter.isEmpty }
    var zones: [Int] { Array(Set(data.gyms.map(\.zone))).sorted() }

    func matchesFilters(_ g: Gym) -> Bool {
        if !typeFilter.isEmpty && !g.type.contains(typeFilter) { return false }
        if let z = zoneFilter, g.zone != z { return false }
        return g.matchesAC(acFilter)
    }

    func clearFilters() { typeFilter = ""; zoneFilter = nil; acFilter = "" }

    func gym(_ id: String) -> Gym? { data.gyms.first { $0.id == id } }

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
        for g in data.gyms where matchesFilters(g) {
            switch mode {
            case .day:
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

    // MARK: Notes & ratings

    func setNote(_ text: String, for id: String) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        notes[id] = t.isEmpty ? nil : t
        AppModel.save(notes, Keys.notes)
    }

    func setRating(_ r: Int, for id: String) {
        ratings[id] = r == 0 ? nil : r
        AppModel.save(ratings, Keys.ratings)
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
    func refreshTravel() async {
        guard let o = origin, travelFor != o, !travelLoading else { return }
        travelLoading = true
        let results = await TravelService.journeyMinutes(from: o, to: data.gyms)
        if origin == o { travel = results; travelFor = o }
        travelLoading = false
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
