import ActivityKit
import SwiftUI
import SwiftData
import OSLog

/// Observe at the app root so changes from every session screen stay in sync.
@MainActor
struct SessionActivityObserver: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase
    @Query(sort: \ClimbSession.start, order: .reverse) private var sessions: [ClimbSession]
    @State private var coordinator = SessionActivityCoordinator()

    private var snapshot: SessionActivityCoordinator.Snapshot? {
        guard let session = sessions.first(where: \.isActive) else { return nil }
        return .init(id: session.id, state: .init(gym: model.gym(session.gymID)?.shortName ?? "Climbing session",
                                                start: session.start, sends: session.sends.count,
                                                attempts: session.climbs.filter { !$0.result.isSend }.count))
    }

    var body: some View {
        Color.clear.frame(width: 0, height: 0)
            .onChange(of: snapshot, initial: true) { _, value in coordinator.schedule(value) }
            .onChange(of: scenePhase) { _, phase in
                if phase == .active { coordinator.schedule(snapshot) }
            }
    }
}

@MainActor
private final class SessionActivityCoordinator {
    struct Snapshot: Equatable {
        let id: UUID
        let state: ClimbingActivityAttributes.ContentState
    }
    private var pending: Task<Void, Never>?
    private var requested = Set<UUID>()

    func schedule(_ snapshot: Snapshot?) {
        let previous = pending
        // Serialize changes so a rapid send/end cannot leave an orphan activity.
        pending = Task {
            await previous?.value
            await synchronize(snapshot)
        }
    }

    private func synchronize(_ snapshot: Snapshot?) async {
        let activities = Activity<ClimbingActivityAttributes>.activities
        for activity in activities where activity.attributes.sessionID != snapshot?.id {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
        guard let snapshot else { return }
        let matching = activities.filter { $0.attributes.sessionID == snapshot.id }
        if let activity = matching.first {
            requested.insert(snapshot.id)
            await activity.update(ActivityContent(state: snapshot.state, staleDate: nil))
            for duplicate in matching.dropFirst() { await duplicate.end(nil, dismissalPolicy: .immediate) }
        } else if !requested.contains(snapshot.id), ActivityAuthorizationInfo().areActivitiesEnabled {
            do {
                _ = try Activity.request(attributes: ClimbingActivityAttributes(sessionID: snapshot.id),
                                         content: ActivityContent(state: snapshot.state, staleDate: nil), pushType: nil)
                requested.insert(snapshot.id)
            } catch {
                Logger(subsystem: "com.linquanyao.londonclimbingfinder", category: "LiveActivity")
                    .error("Couldn't start session Live Activity: \(error.localizedDescription)")
            }
        }
    }
}
