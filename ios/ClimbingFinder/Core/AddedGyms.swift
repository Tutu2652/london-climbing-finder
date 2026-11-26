import Foundation

/// A gym you added yourself, anywhere in the world (mainly to log sessions while travelling).
/// It is kept on this phone (and in logbook backups) only: it is never uploaded or added to
/// gyms.json. The JSON is the same in the Android app (android/core/.../AddedGym.kt), so a backup
/// from either app restores on the other.
struct AddedGym: Codable, Identifiable, Equatable {
    /// Every id starts with this; no gym in gyms.json may (tests/test_added_gyms.py checks).
    static let idPrefix = "added-"
    /// Shown as the gym's chain, so lists group your gyms under it.
    static let brand = "Added by you"
    static let types = ["bouldering", "ropes+bouldering", "ropes"]
    static let days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    /// An added gym this far from Charing Cross or more counts as outside London.
    static let londonKm = 25.0

    var id: String
    var name: String
    /// Town or city; may be blank.
    var area: String
    /// e.g. "France". Filled in from the pin, and can be changed.
    var country: String?
    var postcode: String?
    var address: String?
    /// Where the pin is: latitude and longitude, in degrees.
    var lat: Double
    var lng: Double
    /// The gym's time zone, e.g. "Europe/Paris", found from the pin. Its opening hours are in this local time.
    var timeZone: String?
    /// One of `types`, as in gyms.json.
    var type: String = "bouldering"
    /// A day pass at the usual (peak) price, in pounds.
    var adultPrice: Double?
    var concessionPrice: Double?
    /// Opening hours by day in the gym's local time, as in gyms.json: ["Mon": ["09:00", "22:00"]].
    /// A missing day is closed; none at all means not known.
    var hours: [String: [String]] = [:]
    var website: String?
    var notes: String?

    init(id: String, name: String, area: String, country: String?, postcode: String?, address: String?,
         lat: Double, lng: Double, timeZone: String?, type: String, adultPrice: Double?, concessionPrice: Double?,
         hours: [String: [String]], website: String?, notes: String?) {
        self.id = id; self.name = name; self.area = area; self.country = country
        self.postcode = postcode; self.address = address
        self.lat = lat; self.lng = lng; self.timeZone = timeZone; self.type = type
        self.adultPrice = adultPrice; self.concessionPrice = concessionPrice; self.hours = hours
        self.website = website; self.notes = notes
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, area, country, postcode, address, lat, lng, timeZone, type, adultPrice, concessionPrice, hours, website, notes
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        area = try c.decodeIfPresent(String.self, forKey: .area) ?? ""
        country = try c.decodeIfPresent(String.self, forKey: .country)
        postcode = try c.decodeIfPresent(String.self, forKey: .postcode)
        address = try c.decodeIfPresent(String.self, forKey: .address)
        lat = try c.decode(Double.self, forKey: .lat)
        lng = try c.decode(Double.self, forKey: .lng)
        timeZone = try c.decodeIfPresent(String.self, forKey: .timeZone)
        type = try c.decodeIfPresent(String.self, forKey: .type) ?? "bouldering"
        adultPrice = try c.decodeIfPresent(Double.self, forKey: .adultPrice)
        concessionPrice = try c.decodeIfPresent(Double.self, forKey: .concessionPrice)
        hours = try c.decodeIfPresent([String: [String]].self, forKey: .hours) ?? [:]
        website = try c.decodeIfPresent(String.self, forKey: .website)
        notes = try c.decodeIfPresent(String.self, forKey: .notes)
    }

    /// Everything needed to show, map and price it safely. Only a name and a pin are required:
    /// the rest is filled in from the pin when possible, but may be blank (e.g. offline abroad).
    var isValid: Bool {
        func filled(_ s: String?) -> Bool { !(s ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        /// "HH:MM", 00:00 to 23:59.
        func isTime(_ s: String) -> Bool {
            let p = s.split(separator: ":", omittingEmptySubsequences: false)
            guard s.count == 5, p.count == 2, let h = Int(p[0]), let m = Int(p[1]), p[0].count == 2, p[1].count == 2 else { return false }
            return (0...23).contains(h) && (0...59).contains(m)
        }
        return id.hasPrefix(Self.idPrefix) && id.count > Self.idPrefix.count
            && filled(name)
            && lat.isFinite && lng.isFinite && (-90...90).contains(lat) && (-180...180).contains(lng)
            && (timeZone == nil || zone != nil) && Self.types.contains(type)
            && [adultPrice, concessionPrice].compactMap { $0 }.allSatisfy { $0.isFinite && $0 >= 0 && $0 < 1000 }
            && hours.allSatisfy { day, t in
                Self.days.contains(day) && t.count == 2 && t.allSatisfy(isTime) && t[0] < t[1]
            }
    }

    /// The time zone, if it's one this phone knows.
    var zone: TimeZone? { timeZone.flatMap(TimeZone.init(identifier:)) }

    /// The date and time at the gym, for its opening hours. London time if its time zone isn't known.
    func localMoment(_ date: Date) -> Moment {
        guard let zone else { return Moment(date) }
        var cal = Calendar.london
        cal.timeZone = zone
        return Moment(date, calendar: cal)
    }

    /// As a gym from gyms.json, so the map, lists, logbook and Pricer treat it like any other.
    func gym() -> Gym? {
        func text(_ s: String?) -> Any {
            let t = (s ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? NSNull() : t
        }
        let json: [String: Any] = [
            "id": id, "name": name, "brand": Self.brand, "type": type, "area": text(area),
            // An added gym has no travelcard zone; its country goes where a London gym's borough
            // does, which lists only search (so you can search by country). See Gym.zoneLabel.
            "zone": 0, "borough": text(country),
            "postcode": text(postcode), "address": text(address), "lat": lat, "lng": lng,
            "website": text(website), "about": text(notes),
            "payg": ["adult_peak": adultPrice.map { $0 as Any } ?? NSNull(),
                     "concession_peak": concessionPrice.map { $0 as Any } ?? NSNull()],
            "hours": hours, "verified": false,
        ]
        guard let d = try? JSONSerialization.data(withJSONObject: json) else { return nil }
        return try? JSONDecoder().decode(Gym.self, from: d)
    }

    /// A new id that isn't in `taken` (gyms.json ids and gyms you've already added).
    static func newID(avoiding taken: Set<String>) -> String {
        while true {
            let id = idPrefix + UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(8).lowercased()
            if !taken.contains(id) { return id }
        }
    }

    /// Gyms from gyms.json followed by yours. One of yours whose id is somehow already taken,
    /// or that isn't valid, is left out rather than replacing a listed gym.
    static func merge(_ listed: [Gym], _ added: [AddedGym]) -> [Gym] {
        let ids = Set(listed.map(\.id))
        return listed + added.filter { $0.isValid && !ids.contains($0.id) }.compactMap { $0.gym() }
    }

    /// Gyms from a backup that aren't here yet (matched by id); yours are kept as they are.
    static func mergeFromBackup(_ existing: [AddedGym], _ fromBackup: [AddedGym]?) -> [AddedGym] {
        var have = Set(existing.map(\.id))
        return existing + (fromBackup ?? []).filter { have.insert($0.id).inserted }
    }
}

extension Gym {
    /// A gym you added on this phone, not one from gyms.json.
    var isAddedByYou: Bool { id.hasPrefix(AddedGym.idPrefix) }

    /// A gym you added outside London (elsewhere in the UK, or abroad). It is left out of the
    /// Cheapest ranking and TfL travel times, and only priced on its own page.
    var outsideLondon: Bool { isAddedByYou && haversineKm((51.5080, -0.1247), (lat, lng)) >= AddedGym.londonKm }

    /// "Zone 2"; for a gym you added, its country (or "Added by you" if none is set).
    var zoneLabel: String { isAddedByYou ? borough ?? AddedGym.brand : "Zone \(zone)" }
}
