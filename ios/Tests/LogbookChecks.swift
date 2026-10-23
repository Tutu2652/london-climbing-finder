import Foundation
import SwiftData

// Run with the macOS SDK; uses only an in-memory store, never the phone's data.
@main
struct LogbookChecks {
    @MainActor static func main() throws {
        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
                                          configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let context = container.mainContext
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        let session = ClimbSession(gymID: "test-gym", start: start, end: start.addingTimeInterval(3600), price: 17)
        context.insert(session)
        for (grade, result) in [(2, ClimbResult.flash), (3, .send), (5, .attempt)] {
            let climb = Climb(discipline: .boulder, grade: grade, result: result, at: start.addingTimeInterval(600))
            context.insert(climb)
            climb.session = session
        }
        session.climbs.first { $0.grade == 3 }!.videoAssetID = "test-video-reference"
        session.climbs.first { $0.grade == 3 }!.routePhotoAssetID = "test-route-reference"
        try context.save()
        assert(session.sends.count == 2)
        assert(session.hardest(.boulder) == 3)
        assert(formatDuration(session.duration) == "1h 00m")
        var backup = try LogbookBackup.decoder.decode(LogbookBackup.self, from: LogbookBackup.encoder.encode(LogbookBackup([session])))
        assert(backup.version == 2)
        let linked = backup.sessions[0].climbs.first { $0.grade == 3 }!
        assert(linked.videoAssetID == "test-video-reference")
        assert(linked.routePhotoAssetID == "test-route-reference")
        // Old backups omit these fields entirely, not just as explicit nulls.
        var legacy = try JSONSerialization.jsonObject(with: LogbookBackup.encoder.encode(backup)) as! [String: Any]
        legacy["version"] = 1
        var legacySessions = legacy["sessions"] as! [[String: Any]]
        var legacyClimbs = legacySessions[0]["climbs"] as! [[String: Any]]
        for i in legacyClimbs.indices {
            legacyClimbs[i].removeValue(forKey: "videoAssetID")
            legacyClimbs[i].removeValue(forKey: "routePhotoAssetID")
        }
        legacySessions[0]["climbs"] = legacyClimbs
        legacy["sessions"] = legacySessions
        let decodedLegacy = try LogbookBackup.decoder.decode(LogbookBackup.self, from: JSONSerialization.data(withJSONObject: legacy))
        try decodedLegacy.validate()
        assert(decodedLegacy.sessions[0].climbs.allSatisfy { $0.videoAssetID == nil && $0.routePhotoAssetID == nil })
        backup.sessions.append(backup.sessions[0])
        let restoredContainer = try ModelContainer(for: ClimbSession.self, Climb.self,
                                                  configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let restoredContext = restoredContainer.mainContext
        let added = try backup.restore(into: restoredContext, existing: [])
        assert(added == 1, "Duplicate records must not inflate visits")
        try restoredContext.save()
        let restored = try restoredContext.fetch(FetchDescriptor<ClimbSession>())
        assert(restored.count == 1 && restored[0].climbs.count == 3)
        assert(restored[0].climbs.first { $0.grade == 3 }?.videoAssetID == "test-video-reference")
        assert(restored[0].climbs.first { $0.grade == 3 }?.routePhotoAssetID == "test-route-reference")
        let repeated = try backup.restore(into: restoredContext, existing: restored)
        assert(repeated == 0)
        var invalid = backup
        invalid.sessions[0].climbs[0].grade = 999
        do {
            _ = try invalid.restore(into: restoredContext, existing: [])
            assertionFailure("Invalid grades must be rejected")
        } catch LogbookBackup.RestoreError.invalidRecord { }
        invalid = backup
        invalid.version = 99
        do {
            try invalid.validate()
            assertionFailure("Unknown versions must be rejected")
        } catch LogbookBackup.RestoreError.unsupportedVersion { }
        backup.sessions = [backup.sessions[0]]
        backup.sessions[0].id = UUID()
        backup.sessions[0].end = nil
        let activeAdded = try backup.restore(into: restoredContext, existing: restored)
        assert(activeAdded == 1)
        let all = try restoredContext.fetch(FetchDescriptor<ClimbSession>())
        let recovered = all.first { $0.id == backup.sessions[0].id }!
        assert(!recovered.isActive && recovered.duration == 600)
        restoredContext.delete(recovered)
        try restoredContext.save()
        let remainingClimbs = try restoredContext.fetchCount(FetchDescriptor<Climb>())
        assert(remainingClimbs == 3)
        print("Logbook checks passed: sends, duration, backup round trip, deduplication, validation, active-session recovery, cascade deletion, media references, legacy backups")
    }
}
