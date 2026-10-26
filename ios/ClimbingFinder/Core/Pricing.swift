import Foundation

// Price logic. Mirrors assets/app.js (which mirrors london_climbing_prices.py);
// tests/test_pricing.py checks all three agree for every gym.

let weekdays = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

/// A wall-clock moment in London: the calendar day plus minutes since midnight.
/// Kept separate from `Date` so pricing never depends on time-zone arithmetic.
struct Moment: Hashable {
    let year: Int, month: Int, day: Int
    let minutes: Int

    init(year: Int, month: Int, day: Int, minutes: Int = 0) {
        self.year = year; self.month = month; self.day = day; self.minutes = minutes
    }

    init(_ date: Date, calendar: Calendar = .london) {
        let c = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
        self.init(year: c.year!, month: c.month!, day: c.day!, minutes: c.hour! * 60 + c.minute!)
    }

    func at(_ minutes: Int) -> Moment { Moment(year: year, month: month, day: day, minutes: minutes) }

    var ymd: String { String(format: "%04d-%02d-%02d", year, month, day) }

    /// 0 = Monday ... 6 = Sunday.
    var weekdayIndex: Int {
        var c = DateComponents(); c.year = year; c.month = month; c.day = day; c.hour = 12
        let cal = Calendar.london
        let wd = cal.component(.weekday, from: cal.date(from: c)!)   // Sun = 1
        return (wd + 5) % 7
    }
    var weekday: String { weekdays[weekdayIndex] }
}

extension Calendar {
    static let london: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Europe/London")!
        c.locale = Locale(identifier: "en_GB")
        return c
    }()
}

func toMin(_ s: String) -> Int {
    let p = s.split(separator: ":").compactMap { Int($0) }
    return p[0] * 60 + p[1]
}

func hhmm(_ m: Int) -> String { String(format: "%02d:%02d", m / 60, m % 60) }

func inWindow(_ day: String, _ mins: Int, days: [String], start: String, end: String) -> Bool {
    days.contains(day) && toMin(start) <= mins && mins < toMin(end)
}

enum PriceKind { case peak, off, magic, none }

struct PriceResult {
    let price: Double?
    let label: String
    let kind: PriceKind
}

struct DayBest {
    let price: Double
    let label: String
    let kind: PriceKind
    let when: String
}

struct DealOption {
    let price: Double
    let label: String
    let when: String
}

/// Holds the bank-holiday set the pricing consults (England & Wales, "YYYY-MM-DD").
struct Pricer {
    var bankHolidays: Set<String> = []

    func isBankHoliday(_ m: Moment) -> Bool { bankHolidays.contains(m.ymd) }

    func applicable(_ gym: Gym, _ when: Moment, conc: Bool) -> PriceResult {
        let day = when.weekday, mins = when.minutes, p = gym.payg
        let bh = isBankHoliday(when)
        var bestMagic: (Double, String)?
        for s in gym.magicSlots where inWindow(day, mins, days: s.days, start: s.start, end: s.end) {
            if let months = s.months, !months.contains(when.month) { continue }
            if s.exceptBankHolidays && bh { continue }
            let lbl = s.label
            if lbl.lowercased().contains("concession") && !conc { continue }
            if lbl.range(of: "board[- ]?only", options: [.regularExpression, .caseInsensitive]) != nil { continue }
            let full = lbl.range(of: "off[- ]?peak", options: [.regularExpression, .caseInsensitive]) != nil
                ? lbl + (s.prebook ? " (pre-book)" : "")
                : "super off-peak (" + lbl + (s.prebook ? ", pre-book" : "") + ")"
            if bestMagic == nil || s.price < bestMagic!.0 { bestMagic = (s.price, full) }
        }
        if let m = bestMagic { return PriceResult(price: m.0, label: m.1, kind: .magic) }
        let wins = gym.offpeakWindows
        let isOff = wins.contains { inWindow(day, mins, days: $0.days, start: $0.start, end: $0.end) && !($0.exceptBankHolidays && bh) }
        let concOnly = wins.contains { $0.concessionOnly }
        var off: Double?, peak: Double?
        if conc { off = p.concessionOffpeak; peak = p.concessionPeak }
        else { off = p.adultOffpeak; peak = p.adultPeak; if concOnly { off = nil } }
        if isOff, let off { return PriceResult(price: off, label: "off-peak", kind: .off) }
        if let peak { return PriceResult(price: peak, label: "peak", kind: .peak) }
        if let off, wins.isEmpty { return PriceResult(price: off, label: "off-peak", kind: .off) }
        return PriceResult(price: nil, label: "no data", kind: .none)
    }

    /// Cheapest price achievable any time on the day, and the window(s) it applies.
    func bestOnDay(_ gym: Gym, _ date: Moment, conc: Bool, step: Int = 15) -> DayBest? {
        var samples: [(Int, Double, String, PriceKind)] = []
        for m in stride(from: 0, to: 24 * 60, by: step) {
            let r = applicable(gym, date.at(m), conc: conc)
            if let price = r.price { samples.append((m, price, r.label, r.kind)) }
        }
        guard let minp = samples.map(\.1).min(), let first = samples.first(where: { $0.1 == minp }) else { return nil }
        var ranges: [(Int, Int)] = [], cur: (Int, Int)?
        for s in samples {
            if s.1 == minp { cur = cur.map { ($0.0, s.0) } ?? (s.0, s.0) }
            else if let c = cur { ranges.append(c); cur = nil }
        }
        if let c = cur { ranges.append(c) }
        let when: String
        if ranges.count == 1 && ranges[0].0 == 0 && ranges[0].1 >= 24 * 60 - step { when = "any time" }
        else { when = ranges.map { hhmm($0.0) + "-" + hhmm(min($0.1 + step, 24 * 60 - 1)) }.joined(separator: "; ") }
        return DayBest(price: minp, label: first.2, kind: first.3, when: when)
    }

    /// Every distinct deal option on the day (magic slots + off-peak), cheapest first.
    func slotsOnDay(_ gym: Gym, _ when: Moment, conc: Bool) -> [DealOption] {
        let day = when.weekday, bh = isBankHoliday(when), p = gym.payg
        var opts: [DealOption] = []
        for s in gym.magicSlots where s.days.contains(day) {
            if let months = s.months, !months.contains(when.month) { continue }
            if s.exceptBankHolidays && bh { continue }
            if s.isConcession && !conc { continue }
            let w = s.isAllDay ? "any time" : (s.start + "-" + s.end) + (s.prebook ? " (pre-book)" : "")
            opts.append(DealOption(price: s.price, label: s.label.replacingFirst(" (concession)", with: ""), when: w))
        }
        let wins = gym.offpeakWindows
        let w = wins.first { $0.days.contains(day) && !($0.exceptBankHolidays && bh) }
        let concOnly = wins.contains { $0.concessionOnly }
        var off = conc ? (p.concessionOffpeak ?? p.adultOffpeak) : p.adultOffpeak
        if !conc && concOnly { off = nil }
        if let w, let off { opts.append(DealOption(price: off, label: "off-peak", when: w.start + "-" + w.end)) }
        return opts.enumerated().sorted { ($0.element.price, $0.offset) < ($1.element.price, $1.offset) }.map(\.element)
    }

    /// The contiguous window on the day during which the price at `when` stays available.
    func windowForTime(_ gym: Gym, _ when: Moment, conc: Bool, step: Int = 15) -> String {
        let target = applicable(gym, when, conc: conc)
        guard target.price != nil else { return "" }
        let same = { (m: Int) -> Bool in
            let r = applicable(gym, when.at(m), conc: conc)
            return r.price == target.price && r.label == target.label
        }
        let m0 = (when.minutes / step) * step
        var lo = m0, hi = m0
        while lo - step >= 0 && same(lo - step) { lo -= step }
        while hi + step < 24 * 60 && same(hi + step) { hi += step }
        if lo <= 0 && hi >= 24 * 60 - step { return "any time" }
        return hhmm(lo) + "-" + hhmm(min(hi + step, 24 * 60 - 1))
    }
}

// MARK: - Display helpers (mirror the site's view code)

func formatPrice(_ p: Double?) -> String {
    guard let p else { return "n/a" }
    return "£" + String(format: "%.2f", p)
}

extension String {
    func replacingFirst(_ target: String, with replacement: String) -> String {
        guard let r = range(of: target) else { return self }
        return replacingCharacters(in: r, with: replacement)
    }
}

/// [Mon,Tue,Wed,Thu,Fri] -> "Mon-Fri"; [Mon,Fri] -> "Mon,Fri".
func formatDays(_ days: [String]) -> String {
    let ds = days.sorted { (weekdays.firstIndex(of: $0) ?? 9) < (weekdays.firstIndex(of: $1) ?? 9) }
    var runs: [[Int]] = []
    for d in ds {
        guard let i = weekdays.firstIndex(of: d) else { continue }
        if let last = runs.last?.last, i == last + 1 { runs[runs.count - 1].append(i) } else { runs.append([i]) }
    }
    return runs.map { r in r.count >= 3 ? weekdays[r.first!] + "-" + weekdays[r.last!] : r.map { weekdays[$0] }.joined(separator: ",") }
        .joined(separator: ",")
}

struct OpenState {
    let open: Bool
    let text: String
}

extension Gym {
    var acLevel: String { ac ?? "none" }

    func matchesAC(_ filter: String) -> Bool {
        if filter.isEmpty { return true }
        if filter == "full" { return acLevel == "full" }
        return acLevel != "none"
    }

    var shortName: String {
        name.replacingFirst(" (LCC)", with: "")
            .replacingFirst("Climbing District - ", with: "")
            .replacingFirst("City Bouldering ", with: "")
            .replacingFirst("The Font ", with: "Font ")
            .replacingFirst(" (City Bouldering)", with: "")
    }

    var typeLabel: String { type.replacingOccurrences(of: "+", with: " + ") }

    var instagramURL: URL? { instagram.flatMap { URL(string: "https://www.instagram.com/\($0)/") } }

    var brandRank: Int { brand == "London Climbing Centres" ? 0 : (brand == "Independent" ? 2 : 1) }

    func openState(_ when: Moment) -> OpenState? {
        if temporarilyClosed != nil { return OpenState(open: false, text: "temporarily closed") }
        let hours = temporaryHours ?? self.hours
        if hours.isEmpty { return nil }
        guard let t = hours[when.weekday] else { return OpenState(open: false, text: "closed today") }
        let o = toMin(t[0]), c = toMin(t[1])
        if when.minutes < o { return OpenState(open: false, text: "opens " + t[0]) }
        if when.minutes >= c { return OpenState(open: false, text: "closed") }
        return OpenState(open: true, text: "open till " + t[1])
    }

    var hoursSummary: String {
        if hours.isEmpty { return "—" }
        var parts: [String] = [], i = 0
        while i < 7 {
            let cur = hours[weekdays[i]]
            var j = i
            while j + 1 < 7 && hours[weekdays[j + 1]] == cur { j += 1 }
            let lbl = i == j ? weekdays[i] : weekdays[i] + "-" + weekdays[j]
            parts.append(lbl + " " + (cur.map { $0[0] + "-" + $0[1] } ?? "closed"))
            i = j + 1
        }
        return parts.joined(separator: "; ") + (hoursAssumed ? " (approx)" : "")
    }

    func peakPrice(conc: Bool) -> Double? { conc ? payg.concessionPeak : payg.adultPeak }

    /// Cheapest off-peak / deal price the selected climber can actually use.
    func bestOffpeak(conc: Bool) -> Double? {
        var ps: [Double] = []
        if let off = conc ? (payg.concessionOffpeak ?? payg.adultOffpeak) : payg.adultOffpeak { ps.append(off) }
        for s in magicSlots where conc || !s.isConcession { ps.append(s.price) }
        return ps.min()
    }

    struct DealLine: Identifiable {
        let id = UUID()
        let price: Double
        let label: String
        let when: String
        let extra: String
        let approx: Bool
        let magic: Bool
    }

    /// Off-peak plus every special cheap slot, one line each, cheapest first.
    func dealLines(conc: Bool) -> [DealLine] {
        var items: [DealLine] = []
        let ao = payg.adultOffpeak, co = payg.concessionOffpeak
        if ao != nil || co != nil {
            var main: Double, extra = ""
            if ao == nil { main = co!; extra = " conc" }
            else if co == nil || co == ao { main = ao! }
            else { main = conc ? co! : ao!; extra = conc ? " (\(formatPrice(ao)) adult)" : " (\(formatPrice(co)) conc)" }
            let whens = offpeakWindows.map { w in
                formatDays(w.days) + " " + w.start + "-" + w.end + (w.exceptBankHolidays ? " (not bank hols)" : "")
            }
            if !whens.isEmpty {
                items.append(DealLine(price: main, label: "off-peak", when: whens.joined(separator: ", "),
                                      extra: extra, approx: offpeakAssumed, magic: false))
            }
        }
        struct Group { let price: Double; let label: String; let prebook: Bool; let conc: Bool; var whens: [String] }
        var groups: [Group] = []
        for s in magicSlots {
            var when = formatDays(s.days) + (s.isAllDay ? " all day" : " " + s.start + "-" + s.end)
            if s.exceptBankHolidays { when += " (not bank hols)" }
            let label = s.label.replacingFirst(" (concession)", with: "")
            if let i = groups.firstIndex(where: { $0.label == label && $0.price == s.price && $0.prebook == s.prebook && $0.conc == s.isConcession }) {
                groups[i].whens.append(when)
            } else {
                groups.append(Group(price: s.price, label: label, prebook: s.prebook, conc: s.isConcession, whens: [when]))
            }
        }
        for g in groups {
            items.append(DealLine(price: g.price, label: g.label, when: g.whens.joined(separator: ", "),
                                  extra: (g.prebook ? " (pre-book)" : "") + (g.conc ? " conc" : ""), approx: false, magic: true))
        }
        return items.enumerated().sorted { ($0.element.price, $0.offset) < ($1.element.price, $1.offset) }.map(\.element)
    }
}

func haversineKm(_ a: (Double, Double), _ b: (Double, Double)) -> Double {
    let r = 6371.0, dLat = (b.0 - a.0) * .pi / 180, dLng = (b.1 - a.1) * .pi / 180
    let s = pow(sin(dLat / 2), 2) + cos(a.0 * .pi / 180) * cos(b.0 * .pi / 180) * pow(sin(dLng / 2), 2)
    return r * 2 * atan2(sqrt(s), sqrt(1 - s))
}
