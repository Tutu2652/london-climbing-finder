import Foundation
import SwiftData

@main
struct LogbookMigrationChecks {
    @MainActor static func main() throws {
        let url = URL(fileURLWithPath: CommandLine.arguments[1])
        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
                                          configurations: ModelConfiguration(url: url))
        let context = container.mainContext
        #if LEGACY
        let session = ClimbSession(gymID: "migration-gym", price: 17)
        session.notes = "Keep my existing visit"
        context.insert(session)
        let climb = Climb(discipline: .boulder, grade: 3, result: .flash)
        context.insert(climb)
        climb.session = session
        try context.save()
        print("Seeded pre-media store")
        #else
        let sessions = try context.fetch(FetchDescriptor<ClimbSession>())
        assert(sessions.count == 1)
        let session = sessions[0]
        assert(session.gymID == "migration-gym" && session.notes == "Keep my existing visit")
        assert(session.climbs.count == 1 && session.climbs[0].grade == 3)
        let climb = session.climbs[0]
        if CommandLine.arguments.count > 2 {
            assert(climb.videoAssetID == "persisted-video" && climb.routePhotoAssetID == "persisted-photo")
            climb.videoAssetID = nil
            climb.routePhotoAssetID = nil
            try context.save()
            print("Verified links persist across launches and can be unlinked")
        } else {
            assert(climb.videoAssetID == nil && climb.routePhotoAssetID == nil)
            climb.videoAssetID = "persisted-video"
            climb.routePhotoAssetID = "persisted-photo"
            try context.save()
            print("Migrated existing visit and saved media links")
        }
        #endif
    }
}
