import Foundation
import SwiftData

// Your climbing log: sessions at a gym, and the climbs in each. Stored on the
// phone with SwiftData; More → Back up logbook exports it as JSON.

enum Discipline: String, Codable, CaseIterable, Identifiable {
    case boulder, rope
    var id: String { rawValue }
    var title: String { self == .boulder ? "Bouldering" : "Ropes" }

    /// Grades in difficulty order; a climb stores its index into this list.
    var grades: [String] {
        switch self {
        case .boulder: return boulderGrades
        case .rope: return ["4", "5a", "5b", "5c", "6a", "6a+", "6b", "6b+", "6c", "6c+",
                            "7a", "7a+", "7b", "7b+", "7c", "7c+", "8a", "8a+", "8b", "8b+", "8c"]
        }
    }

    /// Grades shown on the logging grid before "More grades".
    var commonCount: Int { self == .boulder ? 11 : 14 }
}

enum ClimbResult: String, Codable, CaseIterable, Identifiable {
    case flash, send, attempt
    var id: String { rawValue }
    var title: String {
        switch self {
        case .flash: return "Flash"
        case .send: return "Send"
        case .attempt: return "Attempt"
        }
    }
    var icon: String {
        switch self {
        case .flash: return "bolt.fill"
        case .send: return "checkmark"
        case .attempt: return "xmark"
        }
    }
    /// A flash is a send on the first go.
    var isSend: Bool { self != .attempt }
}

@Model
final class ClimbSession {
    var id: UUID = UUID()
    var gymID: String = ""
    var start: Date = Date.now
    /// nil while the session is still going.
    var end: Date?
    var notes: String = ""
    /// What you paid to get in; nil when on a membership or pass.
    var price: Double?
    /// How the session felt, 1-5; 0 = not rated.
    var feeling: Int = 0
    @Relationship(deleteRule: .cascade, inverse: \Climb.session) var climbs: [Climb] = []

    init(gymID: String, start: Date = .now, end: Date? = nil, price: Double?) {
        self.gymID = gymID
        self.start = start
        self.end = end
        self.price = price
    }

    var isActive: Bool { end == nil }
    var duration: TimeInterval { max(0, (end ?? .now).timeIntervalSince(start)) }
    var sends: [Climb] { climbs.filter { $0.result.isSend } }

    /// The climb logged last, which Undo removes.
    var lastClimb: Climb? { climbs.max { $0.loggedAt < $1.loggedAt } }

    /// When a climb tapped now is logged: now in a live session, else the session's end.
    /// Always at least a second after the last climb, so the order (and Undo) stays clear
    /// even for quick taps or a past session, and survives a backup's whole-second times.
    func nextLogTime(now: Date = .now) -> Date {
        let base = isActive ? now : min(end ?? start, now)
        guard let latest = climbs.map(\.loggedAt).max(), latest.addingTimeInterval(1) > base else { return base }
        return latest.addingTimeInterval(1)
    }

    /// Hardest graded send in the session, as (discipline, grade index).
    func hardest(_ d: Discipline) -> Int? {
        sends.filter { $0.discipline == d && $0.isGraded }.map(\.grade).max()
    }
}

@Model
final class Climb {
    var id: UUID = UUID()
    var disciplineRaw: String = Discipline.boulder.rawValue
    var grade: Int = 0
    var resultRaw: String = ClimbResult.send.rawValue
    var loggedAt: Date = Date.now
    var session: ClimbSession?
    /// References only: originals remain in the user's Photos library.
    var videoAssetID: String?
    var routePhotoAssetID: String?
    /// A competition problem. Its grade is how it felt, and may be left out.
    var isComp: Bool = false
    /// Set when logged by hold colour at a colour-graded gym: the colour and the
    /// V range it stood for that day, kept even if the gym changes its colours
    /// later. `grade` is what counts in stats: the bottom of the range, unless
    /// you've said how it felt. Colours with no V range (a gym that only ranks
    /// its colours) start ungraded.
    var colour: String?
    var colourLow: Int?
    var colourHigh: Int?
    /// You picked how a colour climb felt, even if that's the bottom of its range.
    var feltGradeSet: Bool = false

    /// Grade value for a comp problem logged without a grade.
    static let ungraded = -1

    init(discipline: Discipline, grade: Int, result: ClimbResult, at: Date = .now, comp: Bool = false) {
        disciplineRaw = discipline.rawValue
        self.grade = grade
        resultRaw = result.rawValue
        loggedAt = at
        isComp = comp
    }

    /// A boulder logged by its colour; counts as the bottom of the colour's range, if it has one.
    convenience init(colour: ColourGrade, result: ClimbResult, at: Date = .now) {
        self.init(discipline: .boulder, grade: colour.low ?? Climb.ungraded, result: result, at: at)
        self.colour = colour.name
        colourLow = colour.low
        colourHigh = colour.high
    }

    var discipline: Discipline { Discipline(rawValue: disciplineRaw) ?? .boulder }
    var result: ClimbResult { ClimbResult(rawValue: resultRaw) ?? .send }
    var isGraded: Bool { discipline.grades.indices.contains(grade) }
    var gradeLabel: String { isGraded ? discipline.grades[grade] : grade == Self.ungraded ? "–" : "?" }
    /// The colour and range this climb was logged with, if it was logged by colour.
    var colourGrade: ColourGrade? {
        colour.map { ColourGrade(name: $0, low: colourLow, high: colourHigh) }
    }
    /// A felt grade has been set on a colour climb, rather than it counting as the bottom of
    /// its range. Climbs from before felt grades were remembered count if the grade moved.
    var hasFeltGrade: Bool {
        guard let c = colourGrade else { return false }
        return feltGradeSet || grade != (c.low ?? Climb.ungraded)
    }
    /// How a colour climb felt, or nil to count it as the bottom of its range (or, with no
    /// range, to leave it out of grade stats).
    var feltGrade: Int? {
        get { hasFeltGrade && isGraded ? grade : nil }
        set {
            guard let c = colourGrade else { return }
            if let newValue { grade = newValue; feltGradeSet = true } else { grade = c.low ?? Climb.ungraded; feltGradeSet = false }
        }
    }
    /// "V2–V3", "V2–V3 · felt V3" or "felt V3" for a colour climb; nil otherwise or when there's nothing to say.
    var colourDetail: String? {
        guard let c = colourGrade else { return nil }
        let parts = [c.range, hasFeltGrade ? "felt " + gradeLabel : ""].filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
    /// The grade a list shows next to a colour dot: the felt grade if you set one, else the
    /// colour's range ("" for a colour with no V grades). Plain climbs show their grade.
    var listGrade: String {
        guard let c = colourGrade else { return gradeLabel }
        return hasFeltGrade && isGraded ? gradeLabel : c.range
    }
    /// Show "felt" after `listGrade`.
    var listGradeIsFelt: Bool { colourGrade != nil && hasFeltGrade && isGraded }

    /// Grade it by a colour from the gym's chart (counting as the bottom of its range), or,
    /// with nil, by V grade again (keeping the grade it counts as).
    func setColour(_ c: ColourGrade?) {
        if let c {
            guard c != colourGrade else { return }
            colour = c.name; colourLow = c.low; colourHigh = c.high
            disciplineRaw = Discipline.boulder.rawValue
            isComp = false
            grade = c.low ?? Climb.ungraded
            feltGradeSet = false
        } else {
            guard colour != nil else { return }
            colour = nil; colourLow = nil; colourHigh = nil
            feltGradeSet = false
            if !isGraded { grade = 0 }
        }
    }

    /// Move it to boulders or ropes, keeping the grade where it can. Ropes have no colours.
    func setDiscipline(_ d: Discipline) {
        guard d != discipline else { return }
        if d == .rope { setColour(nil) }
        disciplineRaw = d.rawValue
        if isGraded || grade >= 0 { grade = min(max(grade, 0), d.grades.count - 1) }
    }

    /// "V4", "Comp V4", "Comp" or, for a colour climb, "Blue".
    var label: String {
        if let colour { return colour }
        return isComp ? (isGraded ? "Comp " + gradeLabel : "Comp") : gradeLabel
    }
}

// MARK: - Backup (JSON)

struct LogbookBackup: Codable {
    struct Session: Codable {
        var id: UUID, gymID: String, start: Date, end: Date?, notes: String, price: Double?, feeling: Int
        var climbs: [ClimbRecord]
    }
    struct ClimbRecord: Codable {
        var id: UUID, discipline: String, grade: Int, result: String, loggedAt: Date
        var videoAssetID: String?
        var routePhotoAssetID: String?
        /// Missing in version 1 and 2 backups.
        var isComp: Bool?
        /// Missing before version 4, and for climbs not logged by colour.
        var colour: String?
        var colourLow: Int?
        var colourHigh: Int?
        /// Present (true) only when you picked how a colour climb felt.
        var feltGradeSet: Bool?
    }
    var version = 4
    var exported = Date.now
    var sessions: [Session]
    /// Each gym's colour grades, by gym id. Missing before version 4.
    var colourScales: [String: [ColourGrade]]?

    init(_ sessions: [ClimbSession], colourScales: [String: [ColourGrade]] = [:]) {
        self.sessions = sessions.map { s in
            Session(id: s.id, gymID: s.gymID, start: s.start, end: s.end, notes: s.notes, price: s.price, feeling: s.feeling,
                    climbs: s.climbs.sorted { $0.loggedAt < $1.loggedAt }.map {
                        ClimbRecord(id: $0.id, discipline: $0.disciplineRaw, grade: $0.grade, result: $0.resultRaw, loggedAt: $0.loggedAt,
                                    videoAssetID: $0.videoAssetID, routePhotoAssetID: $0.routePhotoAssetID, isComp: $0.isComp,
                                    colour: $0.colour, colourLow: $0.colourLow, colourHigh: $0.colourHigh,
                                    feltGradeSet: $0.feltGradeSet ? true : nil)
                    })
        }
        self.colourScales = colourScales.isEmpty ? nil : colourScales
    }

    /// Colour scales from the file for gyms that don't have one yet; existing scales are kept.
    func mergedColourScales(into existing: [String: [ColourGrade]]) -> [String: [ColourGrade]] {
        existing.merging(colourScales ?? [:]) { mine, _ in mine }
    }

    static let encoder: JSONEncoder = {
        let e = JSONEncoder(); e.dateEncodingStrategy = .iso8601; e.outputFormatting = [.prettyPrinted, .sortedKeys]; return e
    }()
    static let decoder: JSONDecoder = { let d = JSONDecoder(); d.dateDecodingStrategy = .iso8601; return d }()

    enum RestoreError: LocalizedError {
        case unsupportedVersion, invalidRecord
        var errorDescription: String? {
            switch self {
            case .unsupportedVersion: return "This backup version isn't supported."
            case .invalidRecord: return "This backup contains invalid dates, prices or climbs. Nothing was imported."
            }
        }
    }

    /// Validate the whole file before changing the store.
    func validate() throws {
        guard (1...4).contains(version) else { throw RestoreError.unsupportedVersion }
        for (gymID, scale) in colourScales ?? [:] {
            guard !gymID.isEmpty, ColourGrade.isValidScale(scale) else { throw RestoreError.invalidRecord }
        }
        for s in sessions {
            guard !s.gymID.isEmpty, (0...5).contains(s.feeling),
                  s.end.map({ $0 >= s.start }) ?? true,
                  s.price.map({ $0.isFinite && $0 >= 0 }) ?? true else { throw RestoreError.invalidRecord }
            for c in s.climbs {
                // Only comp climbs and colours without a V range may be ungraded.
                let mayBeUngraded = c.isComp == true || (c.colour != nil && c.colourLow == nil)
                guard let discipline = Discipline(rawValue: c.discipline),
                      discipline.grades.indices.contains(c.grade) || (mayBeUngraded && c.grade == Climb.ungraded),
                      ClimbResult(rawValue: c.result) != nil else { throw RestoreError.invalidRecord }
                // A colour climb is a boulder with a valid colour; a range needs a colour.
                if let name = c.colour {
                    guard discipline == .boulder,
                          ColourGrade(name: name, low: c.colourLow, high: c.colourHigh).isValid else { throw RestoreError.invalidRecord }
                } else if c.colourLow != nil || c.colourHigh != nil || c.feltGradeSet == true {
                    throw RestoreError.invalidRecord
                }
            }
        }
    }

    /// Adds sessions not already in the logbook (matched by id). Returns how many were added.
    @MainActor
    func restore(into context: ModelContext, existing: [ClimbSession]) throws -> Int {
        try validate()
        var have = Set(existing.map(\.id))
        var added = 0
        for s in sessions where have.insert(s.id).inserted {
            let session = ClimbSession(gymID: s.gymID, start: s.start, end: s.end ?? max(s.start, s.climbs.map(\.loggedAt).max() ?? s.start), price: s.price)
            session.id = s.id; session.notes = s.notes; session.feeling = s.feeling
            context.insert(session)
            var climbIDs = Set<UUID>()
            for c in s.climbs where climbIDs.insert(c.id).inserted {
                let climb = Climb(discipline: Discipline(rawValue: c.discipline) ?? .boulder, grade: c.grade,
                                  result: ClimbResult(rawValue: c.result) ?? .send, at: c.loggedAt, comp: c.isComp ?? false)
                climb.id = c.id
                climb.videoAssetID = c.videoAssetID
                climb.routePhotoAssetID = c.routePhotoAssetID
                climb.colour = c.colour
                climb.colourLow = c.colourLow
                climb.colourHigh = c.colourHigh
                climb.feltGradeSet = c.feltGradeSet ?? false
                context.insert(climb)
                climb.session = session
            }
            added += 1
        }
        return added
    }
}
