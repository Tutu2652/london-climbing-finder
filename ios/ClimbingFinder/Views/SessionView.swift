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
    @State private var comp = false
    @State private var confirmDelete = false
    /// At a gym with colour grades: log by colour (true) or by V grade.
    @State private var byColour = true

    private var scale: [ColourGrade] { model.colourScale(session.gymID) }
    /// Colour grades only apply to boulders, and comps are logged by V grade.
    private var colourGradable: Bool { discipline == .boulder && !comp }
    private var usingColours: Bool { colourGradable && byColour && !scale.isEmpty }

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
                SwitchRow(isOn: $comp) { Label("Comp routes", systemImage: "trophy") }
                if colourGradable && !scale.isEmpty {
                    Picker("Grade by", selection: $byColour) {
                        Text("Colour").tag(true)
                        Text("V grade").tag(false)
                    }
                    .pickerStyle(.segmented)
                }
                if usingColours { colourGrid } else { gradeGrid }
                HStack {
                    if !usingColours {
                        Button(allGrades ? "Fewer grades" : "More grades") { allGrades.toggle() }
                    }
                    Spacer()
                    if let last = session.lastClimb {
                        Button("Undo \(last.result.title.lowercased()) \(last.label)", role: .destructive) {
                            withAnimation { context.delete(last) }
                        }
                    }
                }
                .font(.callout)
                .buttonStyle(.borderless)
                if colourGradable {
                    NavigationLink { ColourScaleEditor(gymID: session.gymID) } label: {
                        Label(scale.isEmpty ? "Gym grades by colour? Set up colours" : "Edit colour grades",
                              systemImage: "paintpalette")
                    }
                    .font(.callout)
                }
            } header: {
                Text(comp ? "Tap the grade it felt, or No grade, to log a comp \(mode.title.lowercased())"
                          : "Tap \(usingColours ? "a colour" : "a grade") to log \(mode == .attempt ? "an" : "a") \(mode.title.lowercased())")
            }

            if !session.climbs.isEmpty {
                Section("This session") {
                    SessionTally(session: session)
                }
                Section {
                    let ordered = session.climbs.sorted { $0.loggedAt > $1.loggedAt }
                    ForEach(ordered) { climb in
                        NavigationLink { ClimbMediaView(climb: climb) } label: { ClimbMediaRow(climb: climb) }
                    }
                    .onDelete { offsets in withAnimation { offsets.map { ordered[$0] }.forEach(context.delete) } }
                } header: {
                    Text("Climbs, newest first")
                } footer: {
                    Text("Swipe left to delete a climb. Tap one to change it or link a route photo or video.")
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
            // Carry on in comp mode, or by V grade, if that's what you were last logging.
            let last = session.lastClimb
            comp = last?.isComp ?? false
            if let last, last.discipline == .boulder, !last.isComp { byColour = last.colour != nil }
        }
        .sensoryFeedback(.success, trigger: session.climbs.count)
    }

    private var gradeGrid: some View {
        let grades = discipline.grades
        let shown = allGrades ? grades.count : min(discipline.commonCount, grades.count)
        return LazyVGrid(columns: [GridItem(.adaptive(minimum: 58), spacing: 8)], spacing: 8) {
            if comp {
                gradeTile(Climb.ungraded, title: "No grade", accessibility: "Log comp \(mode.title.lowercased()) with no grade")
            }
            ForEach(0..<shown, id: \.self) { i in
                gradeTile(i, title: grades[i], accessibility: "Log \(comp ? "comp " : "")\(mode.title.lowercased()) at \(grades[i])")
            }
        }
        .padding(.vertical, 4)
    }

    private var colourGrid: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 88), spacing: 8)], spacing: 8) {
            ForEach(scale) { colourTile($0) }
        }
        .padding(.vertical, 4)
    }

    private func colourTile(_ c: ColourGrade) -> some View {
        let count = session.climbs.filter { $0.colour == c.name && $0.result.isSend }.count
        // A tap gesture rather than a Button, for the same reason as gradeTile.
        return VStack(spacing: 2) {
            HStack(spacing: 5) {
                ColourSwatch(name: c.name)
                Text(c.name).font(.subheadline.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.7)
            }
            Text(c.range).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                .opacity(c.hasRange ? 1 : 0)
            Text(count > 0 ? "\(count)" : " ").font(.caption2.weight(.semibold)).foregroundStyle(Color.brand)
        }
        .frame(maxWidth: .infinity, minHeight: 58)
        .background(Color.brand.opacity(count > 0 ? 0.16 : 0.07), in: RoundedRectangle(cornerRadius: 10))
        .contentShape(Rectangle())
        .onTapGesture { log(colour: c) }
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Log \(mode.title.lowercased()) on \(c.name)" + (c.hasRange ? ", \(c.range)" : ""))
    }

    private func log(colour: ColourGrade) {
        let climb = Climb(colour: colour, result: mode, at: session.nextLogTime())
        climb.session = session
        context.insert(climb)
    }


    private func gradeTile(_ grade: Int, title: String, accessibility: String) -> some View {
        let count = session.climbs.filter { $0.discipline == discipline && $0.grade == grade && $0.isComp == comp && $0.colour == nil && $0.result.isSend }.count
        // A plain tap gesture, not a Button: Buttons inside a List row drop
        // quick repeat taps, and logging three V2s in a row should just work.
        return VStack(spacing: 2) {
            Text(title).font(.headline).lineLimit(1).minimumScaleFactor(0.7)
            Text(count > 0 ? "\(count)" : " ").font(.caption2.weight(.semibold)).foregroundStyle(Color.brand)
        }
        .frame(maxWidth: .infinity, minHeight: 50)
        .background(Color.brand.opacity(count > 0 ? 0.16 : 0.07), in: RoundedRectangle(cornerRadius: 10))
        .contentShape(Rectangle())
        .onTapGesture { log(grade: grade) }
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel(accessibility)
    }

    private func log(grade: Int) {
        let climb = Climb(discipline: discipline, grade: grade, result: mode, at: session.nextLogTime(), comp: comp)
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
        let comps = climbs.filter(\.isComp).count
        VStack(alignment: .leading, spacing: 6) {
            Text("\(sends.count) sends" + (flashes > 0 ? " (\(flashes) flashed)" : "") + (attempts > 0 ? " · \(attempts) attempt\(attempts == 1 ? "" : "s")" : "")
                 + (comps > 0 ? " · \(comps) comp" : ""))
                .font(.subheadline.weight(.semibold))
            ForEach(rows, id: \.id) { row in
                HStack(spacing: 10) {
                    if let colour = row.colour {
                        HStack(spacing: 5) {
                            ColourSwatch(name: colour)
                            Text(row.label).font(.callout.weight(.semibold))
                        }
                    } else {
                        Text(row.label).font(.callout.weight(.semibold).monospacedDigit()).frame(width: 44, alignment: .leading)
                    }
                    if row.comp { Image(systemName: "trophy").foregroundStyle(.secondary).accessibilityLabel("Comp") }
                    if row.flash > 0 { Label("\(row.flash)", systemImage: "bolt.fill").foregroundStyle(Color.amber) }
                    if row.send > 0 { Label("\(row.send)", systemImage: "checkmark").foregroundStyle(Color.brand) }
                    if row.attempt > 0 { Label("\(row.attempt)", systemImage: "xmark").foregroundStyle(.secondary) }
                }
                .font(.callout)
                .labelStyle(.compact)
            }
        }
    }

    private struct Row {
        let id: String; let label: String; let colour: String?; let comp: Bool
        let flash: Int; let send: Int; let attempt: Int
    }

    /// Regular climbs first, then comp problems; hardest first within each, ungraded last.
    /// Colour climbs group by colour and, if you set one, the grade they felt.
    private var rows: [Row] {
        let groups = Dictionary(grouping: session.climbs) { "\($0.disciplineRaw)#\($0.grade)#\($0.isComp)#\($0.colour ?? "")" }
        return groups.compactMap { key, cs -> (Discipline, Int, Row)? in
            guard let c = cs.first else { return nil }
            let label = c.colour.map { c.hasFeltGrade ? "\($0) (felt \(c.gradeLabel))" : $0 } ?? c.gradeLabel
            return (c.discipline, c.grade, Row(id: key, label: label, colour: c.colour, comp: c.isComp,
                                               flash: cs.filter { $0.result == .flash }.count,
                                               send: cs.filter { $0.result == .send }.count,
                                               attempt: cs.filter { $0.result == .attempt }.count))
        }
        .sorted { ($0.0 == .rope ? 1 : 0, $0.2.comp ? 1 : 0, -$0.1, $0.2.label) < ($1.0 == .rope ? 1 : 0, $1.2.comp ? 1 : 0, -$1.1, $1.2.label) }
        .map(\.2)
    }
}
