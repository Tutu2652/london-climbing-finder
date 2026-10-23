import SwiftUI
import SwiftData

/// One session: tap a grade to log a climb (as a flash, send or attempt),
/// see the tally, and add notes. Works for live and past sessions.
@MainActor
struct SessionView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Bindable var session: ClimbSession

    @AppStorage("logDiscipline") private var discipline: Discipline = .boulder
    @AppStorage("logMode") private var mode: ClimbResult = .send
    @State private var allGrades = false
    @State private var confirmDelete = false

    var body: some View {
        let gym = model.gym(session.gymID)
        List {
            Section {
                HStack(spacing: 12) {
                    if let gym { GymLogo(gym: gym, size: 48) }
                    VStack(alignment: .leading, spacing: 3) {
                        Text(gym?.name ?? "Unknown gym").font(.headline)
                        Text(session.start.formatted(.dateTime.weekday(.wide).day().month().hour().minute()))
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    TimelineView(.periodic(from: .now, by: 1)) { _ in
                        Text(formatDuration(session.duration))
                            .font(.title3.weight(.semibold).monospacedDigit())
                            .foregroundStyle(session.isActive ? Color.brand : .secondary)
                    }
                }
            }

            Section {
                if gym?.type.contains("ropes") == true || session.climbs.contains(where: { $0.discipline == .rope }) {
                    Picker("Discipline", selection: $discipline) {
                        ForEach(Discipline.allCases) { Text($0.title).tag($0) }
                    }
                    .pickerStyle(.segmented)
                }
                Picker("Log as", selection: $mode) {
                    ForEach(ClimbResult.allCases) { Label($0.title, systemImage: $0.icon).tag($0) }
                }
                .pickerStyle(.segmented)
                gradeGrid
                HStack {
                    Button(allGrades ? "Fewer grades" : "More grades") { allGrades.toggle() }
                    Spacer()
                    if let last = session.climbs.max(by: { $0.loggedAt < $1.loggedAt }) {
                        Button("Undo \(last.result.title.lowercased()) \(last.gradeLabel)", role: .destructive) {
                            withAnimation { context.delete(last) }
                        }
                    }
                }
                .font(.callout)
                .buttonStyle(.borderless)
            } header: {
                Text("Tap a grade to log \(mode == .attempt ? "an" : "a") \(mode.title.lowercased())")
            }

            if !session.climbs.isEmpty {
                Section("This session") {
                    SessionTally(session: session)
                }
                Section {
                    ForEach(session.climbs.sorted { $0.loggedAt > $1.loggedAt }) { climb in
                        NavigationLink { ClimbMediaView(climb: climb) } label: { ClimbMediaRow(climb: climb) }
                    }
                } header: {
                    Text("Climbs & media")
                } footer: {
                    Text("Tap a climb to link a route photo or video from Photos.")
                }
            }

            Section("Notes") {
                HStack(spacing: 14) {
                    Text("Felt").foregroundStyle(.secondary)
                    ForEach(1...5, id: \.self) { k in
                        Image(systemName: k <= session.feeling ? "star.fill" : "star")
                            .foregroundStyle(Color.amber)
                            .contentShape(Rectangle())
                            .onTapGesture { session.feeling = session.feeling == k ? 0 : k }
                            .accessibilityLabel("Felt \(k) of 5").accessibilityAddTraits(.isButton)
                    }
                }
                TextField("Projects, beta, how it went…", text: $session.notes, axis: .vertical)
                    .lineLimit(2...6)
            }

            Section("Details") {
                DatePicker("Started", selection: $session.start, in: ...(session.end ?? .now))
                if let end = session.end {
                    DatePicker("Finished", selection: Binding(get: { end }, set: { session.end = $0 }),
                               in: session.start...)
                }
                LabeledContent("Entry") {
                    TextField("Membership / pass", value: $session.price, format: .currency(code: "GBP"))
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                }
            }

            if session.isActive {
                Section {
                    Button {
                        session.end = .now
                        dismiss()
                    } label: {
                        Label("End session", systemImage: "stop.circle.fill").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .listRowBackground(Color.clear)
                }
            }
        }
        .navigationTitle(session.isActive ? "Session" : session.start.formatted(.dateTime.day().month()))
        .inlineTitle()
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                if session.isActive {
                    Menu {
                        Button("Delete session", systemImage: "trash", role: .destructive) { confirmDelete = true }
                    } label: { Label("More", systemImage: "ellipsis.circle") }
                } else {
                    Button("Done") { dismiss() }
                }
            }
        }
        .confirmationDialog("Delete this session and its \(session.climbs.count) climbs?", isPresented: $confirmDelete,
                            titleVisibility: .visible) {
            Button("Delete", role: .destructive) { context.delete(session); dismiss() }
        }
        .onAppear {
            if gym?.type.contains("ropes") != true && !session.climbs.contains(where: { $0.discipline == .rope }) {
                discipline = .boulder
            }
        }
        .sensoryFeedback(.success, trigger: session.climbs.count)
    }

    private var gradeGrid: some View {
        let grades = discipline.grades
        let shown = allGrades ? grades.count : min(discipline.commonCount, grades.count)
        return LazyVGrid(columns: [GridItem(.adaptive(minimum: 58), spacing: 8)], spacing: 8) {
            ForEach(0..<shown, id: \.self) { i in
                let count = session.climbs.filter { $0.discipline == discipline && $0.grade == i && $0.result.isSend }.count
                // A plain tap gesture, not a Button: Buttons inside a List row drop
                // quick repeat taps, and logging three V2s in a row should just work.
                VStack(spacing: 2) {
                    Text(grades[i]).font(.headline)
                    Text(count > 0 ? "\(count)" : " ").font(.caption2.weight(.semibold)).foregroundStyle(Color.brand)
                }
                .frame(maxWidth: .infinity, minHeight: 50)
                .background(Color.brand.opacity(count > 0 ? 0.16 : 0.07), in: RoundedRectangle(cornerRadius: 10))
                .contentShape(Rectangle())
                .onTapGesture { log(grade: i) }
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Log \(mode.title.lowercased()) at \(grades[i])")
            }
        }
        .padding(.vertical, 4)
    }

    private func log(grade: Int) {
        let at = session.isActive ? Date.now : min(session.end ?? session.start, Date.now)
        let climb = Climb(discipline: discipline, grade: grade, result: mode, at: at)
        climb.session = session
        context.insert(climb)
    }
}

/// Per-grade counts, hardest first, plus totals.
@MainActor
struct SessionTally: View {
    let session: ClimbSession

    var body: some View {
        let climbs = session.climbs
        let sends = climbs.filter(\.result.isSend)
        let flashes = climbs.filter { $0.result == .flash }.count
        let attempts = climbs.count - sends.count
        VStack(alignment: .leading, spacing: 6) {
            Text("\(sends.count) sends" + (flashes > 0 ? " (\(flashes) flashed)" : "") + (attempts > 0 ? " · \(attempts) attempt\(attempts == 1 ? "" : "s")" : ""))
                .font(.subheadline.weight(.semibold))
            ForEach(rows, id: \.label) { row in
                HStack(spacing: 10) {
                    Text(row.label).font(.callout.weight(.semibold).monospacedDigit()).frame(width: 44, alignment: .leading)
                    if row.flash > 0 { Label("\(row.flash)", systemImage: "bolt.fill").foregroundStyle(Color.amber) }
                    if row.send > 0 { Label("\(row.send)", systemImage: "checkmark").foregroundStyle(Color.brand) }
                    if row.attempt > 0 { Label("\(row.attempt)", systemImage: "xmark").foregroundStyle(.secondary) }
                }
                .font(.callout)
                .labelStyle(.compact)
            }
        }
    }

    private struct Row { let label: String; let flash: Int; let send: Int; let attempt: Int }

    private var rows: [Row] {
        let groups = Dictionary(grouping: session.climbs) { "\($0.disciplineRaw)#\($0.grade)" }
        return groups.values.compactMap { cs -> (Discipline, Int, Row)? in
            guard let c = cs.first else { return nil }
            return (c.discipline, c.grade, Row(label: c.gradeLabel,
                                               flash: cs.filter { $0.result == .flash }.count,
                                               send: cs.filter { $0.result == .send }.count,
                                               attempt: cs.filter { $0.result == .attempt }.count))
        }
        .sorted { ($0.0 == .rope ? 1 : 0, -$0.1) < ($1.0 == .rope ? 1 : 0, -$1.1) }
        .map(\.2)
    }
}
