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
        case .boulder: return (0...17).map { "V\($0)" }
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

    /// Hardest send in the session, as (discipline, grade index).
    func hardest(_ d: Discipline) -> Int? {
        sends.filter { $0.discipline == d && d.grades.indices.contains($0.grade) }.map(\.grade).max()
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

    init(discipline: Discipline, grade: Int, result: ClimbResult, at: Date = .now) {
        disciplineRaw = discipline.rawValue
        self.grade = grade
        resultRaw = result.rawValue
        loggedAt = at
    }

    var discipline: Discipline { Discipline(rawValue: disciplineRaw) ?? .boulder }
    var result: ClimbResult { ClimbResult(rawValue: resultRaw) ?? .send }
    var gradeLabel: String { discipline.grades.indices.contains(grade) ? discipline.grades[grade] : "?" }
}

func formatDuration(_ t: TimeInterval) -> String {
    let m = Int(max(0, t) / 60)
    return m >= 60 ? "\(m / 60)h \(String(format: "%02d", m % 60))m" : "\(m)m"
}
