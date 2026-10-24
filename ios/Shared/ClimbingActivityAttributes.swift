import ActivityKit
import Foundation

struct ClimbingActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var gym: String
        var start: Date
        var sends: Int
        var attempts: Int
    }
    var sessionID: UUID
    var url: URL { URL(string: "climbfinder://session/\(sessionID.uuidString)")! }
}
