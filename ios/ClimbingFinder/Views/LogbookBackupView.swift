import SwiftUI
import SwiftData
import UniformTypeIdentifiers

/// Exports the logbook as a JSON file, and adds sessions back from one.
@MainActor
struct LogbookBackupView: View {
    @Environment(\.modelContext) private var context
    @Query(sort: \ClimbSession.start) private var sessions: [ClimbSession]
    @State private var file: URL?
    @State private var importing = false
    @State private var message: String?

    var body: some View {
        List {
            Section {
                LabeledContent("Sessions", value: "\(sessions.count)")
                LabeledContent("Climbs", value: "\(sessions.reduce(0) { $0 + $1.climbs.count })")
                if let file, !sessions.isEmpty {
                    ShareLink(item: file) { Label("Export backup file", systemImage: "square.and.arrow.up") }
                }
                Button { importing = true } label: { Label("Restore from a backup file", systemImage: "square.and.arrow.down") }
                if let message { Text(message).font(.footnote).foregroundStyle(.secondary) }
            } footer: {
                Text("Your logbook is kept on this iPhone and survives reinstalls. Deleting the app deletes it, so save a backup to Files or iCloud Drive now and then. Restoring only adds sessions you don't already have.")
            }
        }
        .navigationTitle("Back up logbook")
        .inlineTitle()
        .task(id: sessions.count) { write() }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.json]) { result in
            do {
                let url = try result.get()
                let ok = url.startAccessingSecurityScopedResource()
                defer { if ok { url.stopAccessingSecurityScopedResource() } }
                let backup = try LogbookBackup.decoder.decode(LogbookBackup.self, from: Data(contentsOf: url))
                let added = try backup.restore(into: context, existing: sessions)
                try context.save()
                write()
                message = added == 0 ? "Nothing new in that file." : "Added \(added) session\(added == 1 ? "" : "s")."
            } catch {
                message = "Couldn't read that file: \(error.localizedDescription)"
            }
        }
    }

    private func write() {
        let name = "Climbing logbook \(Moment(.now).ymd).json"
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        guard let data = try? LogbookBackup.encoder.encode(LogbookBackup(sessions)),
              (try? data.write(to: url, options: .atomic)) != nil else { file = nil; return }
        file = url
    }
}

/// On a gym page: your visit history there, and a button to start a session.
@MainActor
struct GymVisitsSection: View {
    @Environment(AppModel.self) private var model
    @Query(sort: \ClimbSession.start, order: .reverse) private var sessions: [ClimbSession]
    let gym: Gym
    @State private var starting = false
    @State private var opened: ClimbSession?

    var body: some View {
        let here = sessions.filter { $0.gymID == gym.id }
        Section {
            if let active = sessions.first(where: \.isActive), active.gymID == gym.id {
                Button { opened = active } label: { Label("Back to your session", systemImage: "play.circle.fill") }
            } else {
                Button { starting = true } label: { Label("Start a session here", systemImage: "play.circle.fill") }
            }
            if !here.isEmpty {
                let sends = here.flatMap(\.sends)
                LabeledContent("Visits", value: "\(here.count)")
                LabeledContent("Last visit", value: here[0].start.formatted(.dateTime.day().month().year()))
                if let top = sends.filter({ $0.discipline == .boulder }).map(\.grade).max() {
                    LabeledContent("Hardest boulder here", value: "V\(top)")
                }
                ForEach(here.prefix(3)) { s in
                    Button { opened = s } label: { VisitRow(session: s) }.buttonStyle(.plain)
                }
                NavigationLink { GymVisitsView(gym: gym) } label: {
                    Text(here.count > 3 ? "See all \(here.count) visits" : "Visit history & totals")
                }
            }
        } header: {
            Text("Your visits")
        }
        .sheet(isPresented: $starting) { StartSessionSheet(past: false, gymID: gym.id) { opened = $0 } }
        .navigationDestination(item: $opened) { SessionScreen(session: $0) }
    }
}
