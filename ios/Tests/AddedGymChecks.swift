import Foundation
import SwiftData

@main
struct AddedGymChecks {
    @MainActor static func main() throws {
        // Android omits default-valued fields; iOS must still restore a usable gym.
        let json = #"{"version":4,"exported":"2026-11-26T10:00:00Z","sessions":[],"addedGyms":[{"id":"added-tokyo","name":"Tokyo wall","lat":35.7,"lng":139.7,"timeZone":"Asia/Tokyo","hours":{"Mon":["13:00","23:00"]}}]}"#
        let backup = try LogbookBackup.decoder.decode(LogbookBackup.self, from: Data(json.utf8))
        try backup.validate()
        let gym = backup.addedGyms!.first!
        assert(gym.isValid && gym.area.isEmpty && gym.type == "bouldering")
        let mapped = gym.gym()!
        assert(mapped.isAddedByYou && mapped.outsideLondon)
        let instant = ISO8601DateFormatter().date(from: "2026-11-23T05:00:00Z")!
        assert(mapped.openState(gym.localMoment(instant))?.open == true)
        assert(mapped.openState(Moment(instant))?.open == false)

        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let session = ClimbSession(gymID: gym.id, start: instant, end: instant.addingTimeInterval(3600), price: 12)
        container.mainContext.insert(session)
        let exported = LogbookBackup([session], addedGyms: [gym])
        let decoded = try LogbookBackup.decoder.decode(LogbookBackup.self, from: LogbookBackup.encoder.encode(exported))
        assert(decoded.addedGyms == [gym] && decoded.sessions.first?.gymID == gym.id)
        let destination = try ModelContainer(for: ClimbSession.self, Climb.self,
            configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let restored = try decoded.restore(into: destination.mainContext, existing: [])
        assert(restored == 1)
        assert(AddedGym.mergeFromBackup([gym], decoded.addedGyms).count == 1)

        var invalid = decoded
        invalid.addedGyms![0].lat = 999
        do {
            _ = try invalid.restore(into: destination.mainContext, existing: [])
            assertionFailure("Invalid gym must reject the entire backup")
        } catch LogbookBackup.RestoreError.invalidRecord { }
        let stored = try destination.mainContext.fetch(FetchDescriptor<ClimbSession>())
        assert(stored.count == 1)
        var banana = gym
        banana.name = "香蕉攀岩"
        banana.boulderGrading = .banana
        assert(banana.alternateGrades(for: .boulder) == (0...20).map(String.init))
        banana.boulderGradeLabels = GymGrading.parseLabels("入门，G1, G2")
        assert(banana.isValid && banana.alternateGrades(for: .boulder) == ["入门", "G1", "G2"])
        let local = Climb(discipline: .boulder, grade: 1, result: .send, gymGrade: "G1")
        let next = Climb(discipline: .boulder, grade: 2, result: .send, gymGrade: "G2")
        for climb in [local, next] {
            container.mainContext.insert(climb)
            climb.session = session
        }
        assert(session.hardest(.boulder) == nil && session.sends.count == 2)
        assert(tallyGroups(session.climbs, scale: []).count == 2)
        // Editing the gym's scale must preserve the original climb labels.
        banana.boulderGrading = .font
        banana.boulderGradeLabels = nil
        let withLabels = LogbookBackup([session], addedGyms: [banana])
        let roundTrip = try LogbookBackup.decoder.decode(LogbookBackup.self, from: LogbookBackup.encoder.encode(withLabels))
        assert(roundTrip.version == 5 && roundTrip.addedGyms?.first?.grading(for: .boulder) == .font)
        let restoredCount = try roundTrip.restore(into: destination.mainContext, existing: [])
        assert(restoredCount == 1)
        let allClimbs = try destination.mainContext.fetch(FetchDescriptor<Climb>())
        assert(Set(allClimbs.map(\.gradeLabel)) == Set(["G1", "G2"]))
        print("Added gym conversion, local hours, custom grades, cross-platform backup and restore checks passed")
    }
}
