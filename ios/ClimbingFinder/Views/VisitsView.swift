import SwiftUI
import SwiftData

/// Opens a session the right way: the logging screen while it's live, the
/// read-only summary once it's finished.
@MainActor
struct SessionScreen: View {
    let session: ClimbSession
    var body: some View {
        if session.isActive { SessionView(session: session) } else { VisitDetailView(session: session) }
    }
}

/// Every gym you've visited, most visits first.
@MainActor
struct VisitsByGymView: View {
    @Environment(AppModel.self) private var model
    @Query(sort: \ClimbSession.start, order: .reverse) private var sessions: [ClimbSession]

    var body: some View {
        List {
            Section {
                ForEach(visitedGyms(sessions, model), id: \.gym.id) { row in
                    NavigationLink { GymVisitsView(gym: row.gym) } label: { VisitCountRow(row: row) }
                }
            } footer: {
                Text("\(sessions.count) visit\(sessions.count == 1 ? "" : "s") to \(visitedGyms(sessions, model).count) gym\(visitedGyms(sessions, model).count == 1 ? "" : "s") in total.")
            }
        }
        .navigationTitle("Visits by gym")
        .inlineTitle()
    }
}

struct VisitedGym {
    let gym: Gym
    let visits: Int
    let last: Date
}

@MainActor
func visitedGyms(_ sessions: [ClimbSession], _ model: AppModel) -> [VisitedGym] {
    Dictionary(grouping: sessions, by: \.gymID).compactMap { id, ss in
        model.gym(id).map { VisitedGym(gym: $0, visits: ss.count, last: ss.map(\.start).max() ?? .distantPast) }
    }
    .sorted { ($0.visits, $0.last) > ($1.visits, $1.last) }
}

@MainActor
struct VisitCountRow: View {
    let row: VisitedGym
    var body: some View {
        HStack(spacing: 12) {
            GymLogo(gym: row.gym, size: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text(row.gym.shortName)
                Text("Last visit " + row.last.formatted(.dateTime.day().month().year()))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            Text("\(row.visits)").font(.title3.weight(.bold).monospacedDigit())
            Text(row.visits == 1 ? "visit" : "visits").font(.caption).foregroundStyle(.secondary)
        }
    }
}

/// All your visits to one gym, with totals for that gym.
@MainActor
struct GymVisitsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.modelContext) private var context
    @Query(sort: \ClimbSession.start, order: .reverse) private var allSessions: [ClimbSession]
    let gym: Gym

    var body: some View {
        let visits = allSessions.filter { $0.gymID == gym.id }
        let sends = visits.flatMap(\.sends)
        let paid = visits.compactMap(\.price).filter { $0 > 0 }
        List {
            Section {
                HStack(spacing: 12) {
                    GymLogo(gym: gym, size: 48)
                    VStack(alignment: .leading) {
                        Text(gym.name).font(.headline)
                        Text(gym.area ?? "").font(.caption).foregroundStyle(.secondary)
                    }
                }
                Grid(horizontalSpacing: 12, verticalSpacing: 14) {
                    GridRow {
                        StatsView.Tile(value: "\(visits.count)", label: visits.count == 1 ? "visit" : "visits")
                        StatsView.Tile(value: formatDuration(visits.map(\.duration).reduce(0, +)), label: "climbing")
                        StatsView.Tile(value: "\(sends.count)", label: "sends")
                    }
                    GridRow {
                        StatsView.Tile(value: hardestLabel(visits), label: "hardest")
                        StatsView.Tile(value: formatPrice(paid.reduce(0, +)), label: "spent")
                        StatsView.Tile(value: paid.isEmpty ? "–" : formatPrice(paid.reduce(0, +) / Double(paid.count)), label: "per paid visit")
                    }
                }
                .padding(.vertical, 6)
                NavigationLink(value: gym) { Label("Gym details & prices", systemImage: "info.circle") }
            }

            ForEach(monthGroups(visits), id: \.title) { group in
                Section(group.title) {
                    ForEach(group.sessions) { s in
                        NavigationLink { SessionScreen(session: s) } label: { VisitRow(session: s) }
                    }
                    .onDelete { $0.map { group.sessions[$0] }.forEach(context.delete) }
                }
            }

            if visits.isEmpty {
                ContentUnavailableView("No visits yet", systemImage: "figure.climbing",
                                       description: Text("Sessions you log here will show up in this list."))
            }
        }
        .navigationTitle("Your visits")
        .inlineTitle()
    }
}

func monthGroups(_ sessions: [ClimbSession]) -> [(title: String, sessions: [ClimbSession])] {
    var out: [(title: String, sessions: [ClimbSession])] = []
    for s in sessions {
        let title = s.start.formatted(.dateTime.month(.wide).year())
        if out.last?.title == title { out[out.count - 1].sessions.append(s) } else { out.append((title, [s])) }
    }
    return out
}

/// One visit in a gym's list: date, length, sends, top grade.
@MainActor
struct VisitRow: View {
    let session: ClimbSession
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(session.start.formatted(.dateTime.weekday(.wide).day().month()))
                    .font(.headline)
                Text(session.start.formatted(date: .omitted, time: .shortened) + " · " + formatDuration(session.duration)
                     + (session.isActive ? " · now" : ""))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text("\(session.sends.count) sends").font(.subheadline.weight(.semibold))
                if let top = session.hardest(.boulder) {
                    Pill(text: "top V\(top)", color: .amber)
                } else if let top = session.hardest(.rope) {
                    Pill(text: "top " + Discipline.rope.grades[top], color: .amber)
                }
            }
        }
    }
}

/// Everything about one finished visit. Edit opens the logging screen.
@MainActor
struct VisitDetailView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    let session: ClimbSession
    @State private var confirmDelete = false

    var body: some View {
        let gym = model.gym(session.gymID)
        let climbs = session.climbs.sorted { $0.loggedAt < $1.loggedAt }
        List {
            Section {
                HStack(spacing: 12) {
                    if let gym { GymLogo(gym: gym, size: 48) }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(gym?.name ?? "Unknown gym").font(.headline)
                        Text(session.start.formatted(.dateTime.weekday(.wide).day().month(.wide).year()))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                }
                LabeledContent("Time", value: session.start.formatted(date: .omitted, time: .shortened)
                               + (session.end.map { "–" + $0.formatted(date: .omitted, time: .shortened) } ?? ""))
                LabeledContent("Length", value: formatDuration(session.duration))
                LabeledContent("Entry", value: session.price.map(formatPrice) ?? "Membership / pass")
                if session.feeling > 0 {
                    LabeledContent("Felt") { Stars(rating: session.feeling) }
                }
                if let gym, let n = visitNumber {
                    LabeledContent("Visit", value: "\(n.ordinal) of \(n.total) at \(gym.shortName)")
                }
            }

            if !session.notes.isEmpty {
                Section("Notes") { Text(session.notes) }
            }

            if climbs.isEmpty {
                Section { Text("No climbs logged.").foregroundStyle(.secondary) }
            } else {
                Section("Summary") { SessionTally(session: session) }
                Section("Climbs in order") {
                    ForEach(climbs) { c in
                        NavigationLink { ClimbMediaView(climb: c) } label: { ClimbMediaRow(climb: c) }
                    }
                    .onDelete { $0.map { climbs[$0] }.forEach(context.delete) }
                }
            }
        }
        .navigationTitle(session.start.formatted(.dateTime.day().month()))
        .inlineTitle()
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                NavigationLink { SessionView(session: session) } label: { Text("Edit") }
            }
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    if let gym { NavigationLink(value: gym) { Label("Gym details", systemImage: "info.circle") } }
                    Button("Delete visit", systemImage: "trash", role: .destructive) { confirmDelete = true }
                } label: { Label("More", systemImage: "ellipsis.circle") }
            }
        }
        .confirmationDialog("Delete this visit and its \(session.climbs.count) climbs?", isPresented: $confirmDelete,
                            titleVisibility: .visible) {
            Button("Delete", role: .destructive) { context.delete(session); dismiss() }
        }
    }

    /// Which visit to this gym this was (1st, 2nd…), counting oldest first.
    private var visitNumber: (ordinal: String, total: Int)? {
        let id = session.gymID
        let d = FetchDescriptor<ClimbSession>(predicate: #Predicate { $0.gymID == id }, sortBy: [SortDescriptor(\.start)])
        guard let all = try? context.fetch(d), let i = all.firstIndex(where: { $0.id == session.id }) else { return nil }
        return ((i + 1).formatted(.number.notation(.automatic)) + ordinalSuffix(i + 1), all.count)
    }

    private func ordinalSuffix(_ n: Int) -> String {
        if (11...13).contains(n % 100) { return "th" }
        switch n % 10 { case 1: return "st"; case 2: return "nd"; case 3: return "rd"; default: return "th" }
    }
}

/// Prefer boulders when a visit includes both disciplines.
private func hardestLabel(_ visits: [ClimbSession]) -> String {
    if let top = visits.compactMap({ $0.hardest(.boulder) }).max() { return "V\(top)" }
    if let top = visits.compactMap({ $0.hardest(.rope) }).max() { return Discipline.rope.grades[top] }
    return "–"
}
