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

    enum CodingKeys: String, CodingKey {
        case id, name, brand, type, area, borough, zone, postcode, lat, lng, website, payg
        case pricesURL = "prices_url", offpeakWindow = "offpeak_window", offpeakAssumed = "offpeak_assumed"
        case magicSlots = "magic_slots", studentNote = "student_note", registrationFee = "registration_fee"
        case verified, lastChecked = "last_checked", facilities, membership, address, passes, hours
        case hoursAssumed = "hours_assumed", instagram, eventsURL = "events_url", classesURL = "classes_url"
        case classInfo = "class_info", routeSettingURL = "route_setting_url", socials, ac, acNote = "ac_note"
    }

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
    enum CodingKeys: String, CodingKey {
        case days, start, end, price, label, months, prebook, exceptBankHolidays = "except_bank_holidays"
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
    }
    var isConcession: Bool { label.range(of: "concession", options: .caseInsensitive) != nil }
    var isAllDay: Bool { start == "00:00" && end == "23:59" }
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
