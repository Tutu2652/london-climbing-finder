import Foundation

// Colour grades: gyms that grade boulders by hold colour, each colour covering a
// range of V grades (or just a place in an order). Used by the logbook and by
// gyms.json's colour_grades, so it stands apart from both.

/// V grades in difficulty order; boulders store an index into this list.
let boulderGrades = (0...17).map { "V\($0)" }

/// Hold colours offered when setting up a gym's colour grades, with a swatch for each.
enum HoldColour {
    static let all: [(name: String, hex: String)] = [
        ("White", "F2F2F2"), ("Yellow", "F5C518"), ("Orange", "F28C28"), ("Green", "2EA043"),
        ("Teal", "22B8C8"), ("Blue", "2F6FDB"), ("Purple", "8250DF"), ("Pink", "E85AAD"),
        ("Red", "D93636"), ("Grey", "8C8C8C"), ("Brown", "8B5A2B"), ("Black", "1F1F1F"),
    ]
    static func hex(_ name: String) -> String { all.first { $0.name == name }?.hex ?? "8C8C8C" }
}

/// One colour on a gym's circuit and the V grades it covers, as indexes into
/// `boulderGrades`; no range for a gym that only ranks its colours.
/// A gym's scale is a list of these, easiest first.
struct ColourGrade: Codable, Hashable, Identifiable {
    var name: String
    var low: Int?
    var high: Int?
    var id: String { name }
    /// "V2–V3", "V9+", or "" when the colour has no V range.
    var range: String { low.flatMap { l in high.map { gradeRange(l, $0) } } ?? "" }
    var hasRange: Bool { low != nil }
    var isValid: Bool {
        let g = boulderGrades.indices
        guard !name.isEmpty else { return false }
        switch (low, high) {
        case (nil, nil): return true
        case let (l?, h?): return g.contains(l) && g.contains(h) && l <= h
        default: return false
        }
    }

    /// Reads a range as gyms.json writes it: "VB", "V3", "V2-V5" or "V9+". VB counts as V0,
    /// the easiest grade the logbook has; "+" runs to the top. nil when unreadable.
    static func parseRange(_ text: String) -> (low: Int, high: Int)? {
        let top = boulderGrades.count - 1
        func v(_ s: Substring) -> Int? {
            let t = s.trimmingCharacters(in: .whitespaces).uppercased()
            if t == "VB" { return 0 }
            guard t.hasPrefix("V"), let n = Int(t.dropFirst()), (0...top).contains(n) else { return nil }
            return n
        }
        let t = text.trimmingCharacters(in: .whitespaces)
        if t.hasSuffix("+") { return v(t.dropLast()).map { ($0, top) } }
        let parts = t.split(separator: "-")
        guard (1...2).contains(parts.count), let l = v(parts[0]), let h = v(parts[parts.count - 1]), l <= h else { return nil }
        return (l, h)
    }

    /// A gym's scale is usable if every colour is valid and appears once.
    static func isValidScale(_ scale: [ColourGrade]) -> Bool {
        scale.allSatisfy(\.isValid) && Set(scale.map(\.name)).count == scale.count
    }

    /// The range a newly added colour starts with: just above the hardest one so far.
    static func nextRange(after scale: [ColourGrade]) -> (low: Int, high: Int) {
        let top = boulderGrades.count - 1
        guard let last = scale.compactMap(\.high).max() else { return (0, 1) }
        let low = min(top, last + 1)
        return (low, min(top, low + 1))
    }
}

/// "V3", "V2–V3", or "V9+" for a range that runs to the top grade.
func gradeRange(_ low: Int, _ high: Int) -> String {
    let g = boulderGrades
    guard g.indices.contains(low), g.indices.contains(high) else { return "?" }
    if low == high { return g[low] }
    return high == g.count - 1 ? g[low] + "+" : g[low] + "–" + g[high]
}

func formatDuration(_ t: TimeInterval) -> String {
    let m = Int(max(0, t) / 60)
    return m >= 60 ? "\(m / 60)h \(String(format: "%02d", m % 60))m" : "\(m)m"
}
