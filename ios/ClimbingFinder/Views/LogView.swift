import SwiftUI
import SwiftData

/// The Log tab: start or resume a session, see recent sessions and a summary.
@MainActor
struct LogView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.modelContext) private var context
    @Query(sort: \ClimbSession.start, order: .reverse) private var sessions: [ClimbSession]
    @State private var starting: StartMode?
    @State private var opened: ClimbSession?

    enum StartMode: String, Identifiable { case live, past; var id: String { rawValue } }

    var body: some View {
        List {
            if let active = sessions.first(where: \.isActive) {
                Section {
                    Button { opened = active } label: { ActiveSessionCard(session: active) }
                        .buttonStyle(.plain)
                }
            } else {
                Section {
                    Button { starting = .live } label: {
                        Label("Start a session", systemImage: "play.circle.fill").font(.headline)
                    }
                    Button { starting = .past } label: {
                        Label("Log a past session", systemImage: "clock.arrow.circlepath")
                    }
                }
            }

            if !sessions.isEmpty {
                Section {
                    NavigationLink { StatsView() } label: { MonthSummary(sessions: sessions) }
                    NavigationLink { VisitsByGymView() } label: {
                        LabeledContent {
                            Text("\(Set(sessions.map(\.gymID)).count) gyms")
                        } label: {
                            Label("Visits by gym", systemImage: "building.2")
                        }
                    }
                }
            }

            ForEach(monthGroups, id: \.title) { group in
                Section(group.title) {
                    ForEach(group.sessions) { s in
                        Button { opened = s } label: { SessionRow(session: s) }
                            .buttonStyle(.plain)
                    }
                    .onDelete { offsets in offsets.map { group.sessions[$0] }.forEach(context.delete) }
                }
            }

            if sessions.isEmpty {
                ContentUnavailableView("No sessions yet", systemImage: "figure.climbing",
                                       description: Text("Start a session when you get to the wall, then tap a grade each time you send."))
            }
        }
        .navigationTitle("Logbook")
        .navigationDestination(item: $opened) { SessionScreen(session: $0) }
        .sheet(item: $starting) { mode in
            StartSessionSheet(past: mode == .past) { opened = $0 }
        }
    }

    private var monthGroups: [(title: String, sessions: [ClimbSession])] {
        var out: [(title: String, sessions: [ClimbSession])] = []
        for s in sessions where !s.isActive {
            let title = s.start.formatted(.dateTime.month(.wide).year())
            if out.last?.title == title { out[out.count - 1].sessions.append(s) } else { out.append((title, [s])) }
        }
        return out
    }
}

// MARK: - Rows & cards

@MainActor
private struct ActiveSessionCard: View {
    @Environment(AppModel.self) private var model
    let session: ClimbSession

    var body: some View {
        HStack(spacing: 12) {
            if let g = model.gym(session.gymID) { GymLogo(gym: g, size: 48) }
            VStack(alignment: .leading, spacing: 4) {
                Text("Climbing now").font(.caption.weight(.semibold)).foregroundStyle(Color.brand)
                Text(model.gym(session.gymID)?.name ?? "Session").font(.headline)
                TimelineView(.periodic(from: .now, by: 30)) { _ in
                    Text("\(formatDuration(session.duration)) · \(session.sends.count) sends")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
            }
            Spacer()
            Image(systemName: "chevron.right").foregroundStyle(.tertiary)
        }
        .padding(.vertical, 4)
    }
}

@MainActor
struct SessionRow: View {
    @Environment(AppModel.self) private var model
    let session: ClimbSession

    var body: some View {
        HStack(spacing: 12) {
            if let g = model.gym(session.gymID) { GymLogo(gym: g, size: 40) }
            VStack(alignment: .leading, spacing: 3) {
                Text(model.gym(session.gymID)?.shortName ?? "Unknown gym").font(.headline)
                Text(session.start.formatted(.dateTime.weekday(.abbreviated).day().month()) + " · " + formatDuration(session.duration))
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 3) {
                Text("\(session.sends.count) sends").font(.subheadline.weight(.semibold))
                if let top = session.hardest(.boulder) ?? session.hardest(.rope).map({ $0 + 1000 }) {
                    let label = top >= 1000 ? Discipline.rope.grades[top - 1000] : Discipline.boulder.grades[top]
                    Pill(text: "top " + label, color: .amber)
                }
            }
        }
        .contentShape(Rectangle())
    }
}

@MainActor
private struct MonthSummary: View {
    let sessions: [ClimbSession]

    var body: some View {
        let monthAgo = Calendar.london.date(byAdding: .day, value: -30, to: .now)!
        let recent = sessions.filter { $0.start >= monthAgo }
        let sends = recent.flatMap(\.sends)
        let top = sends.filter { $0.discipline == .boulder }.map(\.grade).max()
        VStack(alignment: .leading, spacing: 8) {
            Text("Last 30 days").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            HStack(spacing: 0) {
                Stat(value: "\(recent.count)", label: "sessions")
                Stat(value: "\(sends.count)", label: "sends")
                Stat(value: top.map { "V\($0)" } ?? "–", label: "hardest")
                Stat(value: formatPrice(recent.compactMap(\.price).reduce(0, +)).replacingOccurrences(of: ".00", with: ""), label: "spent")
            }
        }
        .padding(.vertical, 4)
    }

    struct Stat: View {
        let value: String, label: String
        var body: some View {
            VStack(spacing: 2) {
                Text(value).font(.title3.weight(.bold).monospacedDigit())
                Text(label).font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Start a session

@MainActor
struct StartSessionSheet: View {
    @Environment(AppModel.self) private var model
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Query(sort: \ClimbSession.start, order: .reverse) private var sessions: [ClimbSession]
    let past: Bool
    var gymID: String?
    let onStart: (ClimbSession) -> Void

    @State private var chosen = ""
    @State private var when = Date.now
    @State private var hours = 2.0
    @State private var onPass = false
    @State private var price: Double?

    init(past: Bool, gymID: String? = nil, onStart: @escaping (ClimbSession) -> Void) {
        self.past = past; self.gymID = gymID; self.onStart = onStart
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    NavigationLink {
                        SessionGymPicker(selection: $chosen)
                    } label: {
                        LabeledContent("Gym", value: model.gym(chosen)?.name ?? "Choose a gym")
                    }
                    if past {
                        DatePicker("Started", selection: $when, in: ...Date.now)
                        Stepper("Length: \(formatDuration(hours * 3600))", value: $hours, in: 0.5...8, step: 0.25)
                    }
                }
                Section {
                    Toggle("Membership or pass", isOn: $onPass)
                    if !onPass {
                        LabeledContent("Entry price") {
                            TextField("£", value: $price, format: .currency(code: "GBP"))
                                .keyboardType(.decimalPad)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                } footer: {
                    Text(onPass ? "Logged as £0, so it isn't counted in what you spend on day passes."
                                : "Filled in from the gym's \(model.concession ? "concession" : "adult") price at that time. Change it if you paid something else.")
                }
            }
            .navigationTitle(past ? "Log a past session" : "Start a session")
            .inlineTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(past ? "Add" : "Start") { start() }.disabled(chosen.isEmpty)
                }
            }
            .onAppear {
                if chosen.isEmpty {
                    chosen = gymID
                        ?? sessions.first(where: { model.gym($0.gymID) != nil })?.gymID
                        ?? model.data.gyms.filter { model.isFavourite($0.id) }.sorted { $0.name < $1.name }.first?.id
                        ?? model.data.gyms.sorted { ($0.brandRank, $0.brand, $0.name) < ($1.brandRank, $1.brand, $1.name) }.first?.id
                        ?? ""
                    updatePrice()
                }
            }
            .onChange(of: chosen) { updatePrice() }
            .onChange(of: when) { updatePrice() }
        }
    }

    private func updatePrice() {
        guard let g = model.gym(chosen) else { return }
        price = model.pricer.applicable(g, Moment(past ? when : .now), conc: model.concession).price
    }

    private func start() {
        let s = ClimbSession(gymID: chosen, start: past ? when : .now,
                             end: past ? min(when.addingTimeInterval(hours * 3600), .now) : nil,
                             price: onPass ? nil : price)
        if !past {
            for active in sessions where active.isActive { active.end = .now }
        }
        context.insert(s)
        dismiss()
        onStart(s)
    }

}

/// Shared gym selection for both live and past sessions.
@MainActor
private struct SessionGymPicker: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Binding var selection: String
    @State private var search = ""

    var body: some View {
        List {
            ForEach(sections, id: \.title) { section in
                Section(section.title) {
                    ForEach(section.gyms) { gym in
                        Button {
                            selection = gym.id
                            dismiss()
                        } label: {
                            HStack(spacing: 12) {
                                GymLogo(gym: gym, size: 36)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(gym.name).foregroundStyle(.primary)
                                    Text([gym.area, gym.postcode].compactMap { $0 }.joined(separator: " · "))
                                        .font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                if selection == gym.id {
                                    Image(systemName: "checkmark").foregroundStyle(Color.brand)
                                }
                            }
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(selection == gym.id ? [.isSelected] : [])
                    }
                }
            }
        }
        .overlay {
            if sections.isEmpty {
                ContentUnavailableView.search(text: search)
            }
        }
        .navigationTitle("Choose a gym")
        .inlineTitle()
        .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always),
                    prompt: "Gym, chain, area or postcode")
    }

    private var sections: [(title: String, gyms: [Gym])] {
        let normalize = { (text: String) in
            text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: .current)
                .filter { $0.isLetter || $0.isNumber }
        }
        let words = search.split(whereSeparator: { $0.isWhitespace })
            .map { normalize(String($0)) }.filter { !$0.isEmpty }
        let gyms = model.data.gyms.filter { gym in
            let text = normalize([gym.name, gym.brand, gym.area, gym.borough, gym.postcode, gym.address]
                .compactMap { $0 }.joined(separator: " "))
            return words.allSatisfy { text.contains($0) }
        }.sorted { ($0.brandRank, $0.brand, $0.name) < ($1.brandRank, $1.brand, $1.name) }
        var groups: [(title: String, gyms: [Gym])] = []
        for gym in gyms {
            if groups.last?.title == gym.brand {
                groups[groups.count - 1].gyms.append(gym)
            } else {
                groups.append((title: gym.brand, gyms: [gym]))
            }
        }
        return groups
    }
}
