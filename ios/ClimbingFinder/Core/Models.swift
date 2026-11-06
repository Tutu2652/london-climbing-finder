import Foundation

// Decoded straight from gyms.json (the repo's single source of truth, bundled
// into the app at build time). Field names mirror the JSON; see README.md.

struct ClimbingData: Decodable {
    let meta: Meta
    let gyms: [Gym]
    let studentPromotions: [StudentPromotion]
    let limitedPromotions: [Promotion]

    enum CodingKeys: String, CodingKey {
        case meta = "_meta", gyms
        case studentPromotions = "student_promotions"
        case limitedPromotions = "limited_promotions"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        meta = try c.decode(Meta.self, forKey: .meta)
        gyms = try c.decode([Gym].self, forKey: .gyms)
        studentPromotions = try c.decodeIfPresent([StudentPromotion].self, forKey: .studentPromotions) ?? []
        limitedPromotions = try c.decodeIfPresent([Promotion].self, forKey: .limitedPromotions) ?? []
    }

    struct Meta: Decodable {
        let lastResearchDate: String?
        enum CodingKeys: String, CodingKey { case lastResearchDate = "last_research_date" }
    }
}

struct Gym: Decodable, Identifiable, Hashable {
    let id: String
    let name: String
    let brand: String
    let type: String
    let area: String?
    let borough: String?
    let zone: Int
    let postcode: String?
    let lat: Double
    let lng: Double
    let website: String?
    let pricesURL: String?
    let payg: Payg
    let offpeakWindows: [OffpeakWindow]
    let offpeakAssumed: Bool
    let magicSlots: [MagicSlot]
    let studentNote: String?
    let registrationFee: Double?
    let verified: Bool
    let lastChecked: String?
    let facilities: String?
    let membership: Membership?
    let address: String?
    let passes: String?
    let hours: [String: [String]]
    let hoursAssumed: Bool
    let instagram: String?
    let eventsURL: String?
    let classesURL: String?
    let classInfo: String?
    let routeSettingURL: String?
    let socials: [String]
    let ac: String?
    let acNote: String?
    let logo: String?
    let logoBackground: String?
    /// What the place is like, in the gym's own words (set for smaller, less-known gyms).
    let about: String?
    /// Set while a gym is shut until further notice; it is left out of rankings.
    let temporarilyClosed: String?
    /// A temporary note such as reduced hours, with the hours that apply meanwhile.
    let notice: String?
    let temporaryHours: [String: [String]]?
    /// The gym's bouldering colours, easiest first, from its grade chart; empty if it doesn't grade by colour.
    let colourGrades: [ColourGrade]
    /// Cheap slots only some climbers can get (women+ sessions, local residents). Priced only
    /// for climbers who qualify; see `Eligibility`. Kept apart from `magicSlots` so older app
    /// versions, which don't know the conditions, never offer them to everyone.
    let restrictedSlots: [MagicSlot]
    /// One-off events (comps, watch parties). Each hides itself once its day has passed.
    let events: [GymEvent]

    enum CodingKeys: String, CodingKey {
        case id, name, brand, type, area, borough, zone, postcode, lat, lng, website, payg
        case pricesURL = "prices_url", offpeakWindow = "offpeak_window", offpeakAssumed = "offpeak_assumed"
        case magicSlots = "magic_slots", studentNote = "student_note", registrationFee = "registration_fee"
        case verified, lastChecked = "last_checked", facilities, membership, address, passes, hours
        case hoursAssumed = "hours_assumed", instagram, eventsURL = "events_url", classesURL = "classes_url"
        case classInfo = "class_info", routeSettingURL = "route_setting_url", socials, ac, acNote = "ac_note"
        case logo, logoBackground = "logo_background", about, temporarilyClosed = "temporarily_closed", notice, temporaryHours = "temporary_hours"
        case colourGrades = "colour_grades"
        case restrictedSlots = "restricted_slots", events
    }

    /// One entry of `colour_grades` in gyms.json: a colour and, if the chart gives one, its V range.
    private struct ColourEntry: Decodable { let colour: String; let grades: String? }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        brand = try c.decode(String.self, forKey: .brand)
        type = try c.decode(String.self, forKey: .type)
        area = try c.decodeIfPresent(String.self, forKey: .area)
        borough = try c.decodeIfPresent(String.self, forKey: .borough)
        zone = try c.decode(Int.self, forKey: .zone)
        postcode = try c.decodeIfPresent(String.self, forKey: .postcode)
        lat = try c.decode(Double.self, forKey: .lat)
        lng = try c.decode(Double.self, forKey: .lng)
        website = try c.decodeIfPresent(String.self, forKey: .website)
        pricesURL = try c.decodeIfPresent(String.self, forKey: .pricesURL)
        payg = try c.decode(Payg.self, forKey: .payg)
        // offpeak_window may be a single object, a list of objects, or null.
        if let list = try? c.decodeIfPresent([OffpeakWindow].self, forKey: .offpeakWindow) {
            offpeakWindows = list
        } else if let one = try? c.decodeIfPresent(OffpeakWindow.self, forKey: .offpeakWindow) {
            offpeakWindows = [one]
        } else {
            offpeakWindows = []
        }
        offpeakAssumed = try c.decodeIfPresent(Bool.self, forKey: .offpeakAssumed) ?? false
        magicSlots = try c.decodeIfPresent([MagicSlot].self, forKey: .magicSlots) ?? []
        studentNote = try c.decodeIfPresent(String.self, forKey: .studentNote)
        registrationFee = try c.decodeIfPresent(Double.self, forKey: .registrationFee)
        verified = try c.decodeIfPresent(Bool.self, forKey: .verified) ?? false
        lastChecked = try c.decodeIfPresent(String.self, forKey: .lastChecked)
        facilities = try c.decodeIfPresent(String.self, forKey: .facilities)
        membership = try c.decodeIfPresent(Membership.self, forKey: .membership)
        address = try c.decodeIfPresent(String.self, forKey: .address)
        passes = try c.decodeIfPresent(String.self, forKey: .passes)
        let rawHours = try c.decodeIfPresent([String: [String]?].self, forKey: .hours) ?? [:]
        hours = rawHours.compactMapValues { $0 }.filter { $0.value.count == 2 }
        hoursAssumed = try c.decodeIfPresent(Bool.self, forKey: .hoursAssumed) ?? false
        instagram = try c.decodeIfPresent(String.self, forKey: .instagram)
        eventsURL = try c.decodeIfPresent(String.self, forKey: .eventsURL)
        classesURL = try c.decodeIfPresent(String.self, forKey: .classesURL)
        classInfo = try c.decodeIfPresent(String.self, forKey: .classInfo)
        routeSettingURL = try c.decodeIfPresent(String.self, forKey: .routeSettingURL)
        socials = try c.decodeIfPresent([String].self, forKey: .socials) ?? []
        ac = try c.decodeIfPresent(String.self, forKey: .ac)
        acNote = try c.decodeIfPresent(String.self, forKey: .acNote)
        about = try c.decodeIfPresent(String.self, forKey: .about)
        temporarilyClosed = try c.decodeIfPresent(String.self, forKey: .temporarilyClosed)
        notice = try c.decodeIfPresent(String.self, forKey: .notice)
        temporaryHours = try c.decodeIfPresent([String: [String]].self, forKey: .temporaryHours)
        logo = try c.decodeIfPresent(String.self, forKey: .logo)
        logoBackground = try c.decodeIfPresent(String.self, forKey: .logoBackground)
        // A colour whose range can't be read is still offered, without a range.
        // A malformed list is ignored rather than failing the whole gym.
        let entries = (try? c.decodeIfPresent([ColourEntry].self, forKey: .colourGrades)) ?? nil
        colourGrades = (entries ?? []).map { e in
            let r = e.grades.flatMap(ColourGrade.parseRange)
            return ColourGrade(name: e.colour, low: r?.low, high: r?.high)
        }
        // Entries this version can't read are dropped rather than failing the gym.
        restrictedSlots = ((try? c.decodeIfPresent([Lenient<MagicSlot>].self, forKey: .restrictedSlots)) ?? nil)?
            .compactMap(\.value).filter(\.isRestricted) ?? []
        events = ((try? c.decodeIfPresent([Lenient<GymEvent>].self, forKey: .events)) ?? nil)?.compactMap(\.value) ?? []
    }

    static func == (a: Gym, b: Gym) -> Bool { a.id == b.id }
    func hash(into h: inout Hasher) { h.combine(id) }
}

struct Payg: Decodable {
    let adultPeak: Double?
    let adultOffpeak: Double?
    let concessionPeak: Double?
    let concessionOffpeak: Double?
    enum CodingKeys: String, CodingKey {
        case adultPeak = "adult_peak", adultOffpeak = "adult_offpeak"
        case concessionPeak = "concession_peak", concessionOffpeak = "concession_offpeak"
    }
}

struct OffpeakWindow: Decodable {
    let days: [String]
    let start: String
    let end: String
    let concessionOnly: Bool
    let exceptBankHolidays: Bool
    enum CodingKeys: String, CodingKey {
        case days, start, end, concessionOnly = "concession_only", exceptBankHolidays = "except_bank_holidays"
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        days = try c.decode([String].self, forKey: .days)
        start = try c.decode(String.self, forKey: .start)
        end = try c.decode(String.self, forKey: .end)
        concessionOnly = try c.decodeIfPresent(Bool.self, forKey: .concessionOnly) ?? false
        exceptBankHolidays = try c.decodeIfPresent(Bool.self, forKey: .exceptBankHolidays) ?? false
    }
}

struct MagicSlot: Decodable {
    let days: [String]
    let start: String
    let end: String
    let price: Double
    let label: String
    let months: [Int]?
    let prebook: Bool
    let exceptBankHolidays: Bool
    /// Only for women+ climbers (restricted slots only).
    let womenOnly: Bool
    /// Only for people living in these postcode districts, e.g. ["SE16"] (restricted slots only).
    let postcodes: [String]
    enum CodingKeys: String, CodingKey {
        case days, start, end, price, label, months, prebook, exceptBankHolidays = "except_bank_holidays"
        case womenOnly = "women_only", postcodes
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        days = try c.decode([String].self, forKey: .days)
        start = try c.decode(String.self, forKey: .start)
        end = try c.decode(String.self, forKey: .end)
        price = try c.decode(Double.self, forKey: .price)
        label = try c.decodeIfPresent(String.self, forKey: .label) ?? "magic"
        months = try c.decodeIfPresent([Int].self, forKey: .months)
        prebook = try c.decodeIfPresent(Bool.self, forKey: .prebook) ?? false
        exceptBankHolidays = try c.decodeIfPresent(Bool.self, forKey: .exceptBankHolidays) ?? false
        womenOnly = try c.decodeIfPresent(Bool.self, forKey: .womenOnly) ?? false
        postcodes = (try c.decodeIfPresent([String].self, forKey: .postcodes) ?? []).map { $0.uppercased() }
    }
    var isRestricted: Bool { womenOnly || !postcodes.isEmpty }
    /// Who can get it, e.g. "women+" or "SE16 residents"; empty for everyone.
    var condition: String {
        var parts: [String] = []
        if womenOnly { parts.append("women+") }
        if !postcodes.isEmpty { parts.append(postcodes.joined(separator: "/") + " residents") }
        return parts.joined(separator: ", ")
    }
    var isConcession: Bool { label.range(of: "concession", options: .caseInsensitive) != nil }
    var isAllDay: Bool { start == "00:00" && end == "23:59" }
}

/// A one-off event at a gym. Shown until the end of `endDate` (or `date`), then hidden.
struct GymEvent: Decodable, Hashable, Identifiable {
    let date: String          // YYYY-MM-DD
    let endDate: String?
    let start: String?
    let end: String?
    let title: String
    let details: String?
    let url: String?
    let price: String?
    var id: String { date + title }
    enum CodingKeys: String, CodingKey {
        case date, endDate = "end_date", start, end, title, details, url, price
    }

    /// Still to come (or on) the given London day, "YYYY-MM-DD".
    func isUpcoming(today: String) -> Bool { (endDate ?? date) >= today }

    /// "Sat 14 Nov · from 18:00".
    var whenText: String {
        func day(_ ymd: String) -> String {
            let p = ymd.split(separator: "-").compactMap { Int($0) }
            guard p.count == 3 else { return ymd }
            let m = Moment(year: p[0], month: p[1], day: p[2])
            let months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
            return "\(m.weekday) \(p[2]) \(months[p[1] - 1])"
        }
        var s = day(date)
        if let endDate, endDate != date { s += " – " + day(endDate) }
        switch (start, end) {
        case let (a?, b?): s += " · \(a)–\(b)"
        case let (a?, nil): s += " · from \(a)"
        default: break
        }
        return s
    }
}

/// Decodes a list entry, or nil if it doesn't fit, so one bad entry can't hide the rest.
struct Lenient<T: Decodable>: Decodable {
    let value: T?
    init(from decoder: Decoder) throws { value = try? T(from: decoder) }
}

struct Membership: Decodable {
    let fullMonthly: Double?
    let concessionMonthly: Double?
    enum CodingKeys: String, CodingKey { case fullMonthly = "full_monthly", concessionMonthly = "concession_monthly" }
}

struct StudentPromotion: Decodable, Identifiable {
    let scope: String
    let offer: String
    let source: String?
    var id: String { scope + offer }
}

struct Promotion: Decodable, Identifiable {
    let id: String
    let brand: String
    let title: String
    let details: String?
    let price: String?
    let valid: String?
    let expires: String?
    let expiresSource: String?
    let source: String?
    let status: String
    enum CodingKeys: String, CodingKey {
        case id, brand, title, details, price, valid, expires, source, status, expiresSource = "expires_source"
    }

    /// Past it if marked so, or if its published end date has gone by. A nil
    /// `expires` means "no end date known", not "runs forever".
    func isExpired(today: String) -> Bool {
        if status == "expired" { return true }
        guard let expires else { return false }
        return expires < today
    }
}
