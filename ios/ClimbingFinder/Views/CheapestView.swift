import SwiftUI

@MainActor
struct CheapestView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let rows = model.cheapest()
        List {
            Section {
                WhenControls()
            }
            if model.isBankHoliday {
                Section {
                    Label("\(model.when.formatted(date: .complete, time: .omitted)) is a UK bank holiday, so some off-peak, Magic Time and Bargain deals don't apply.",
                          systemImage: "exclamationmark.triangle")
                        .font(.footnote).foregroundStyle(Color.amber)
                }
            }
            Section {
                if rows.isEmpty {
                    Text("No priced gyms match these filters.").foregroundStyle(.secondary)
                }
                ForEach(Array(rows.enumerated()), id: \.element.id) { i, r in
                    NavigationLink(value: r.gym) { CheapestRow(rank: i + 1, row: r) }
                        .listRowBackground(i == 0 ? Color.brand.opacity(0.10) : nil)
                }
            } footer: {
                Explainer(footer)
            }
        }
        .refreshable { await model.refreshData(force: true); await model.refreshTravel() }
        .navigationTitle("Cheapest")
        .toolbar { ToolbarItem(placement: .primaryAction) { FilterMenu() } }
    }

    private var footer: String {
        let who = model.concession ? "Student / concession" : "Adult"
        let day = model.when.formatted(date: .abbreviated, time: .omitted)
        let lead = model.mode == .day
            ? "\(who) prices: the cheapest you can get on \(day). The time shown is when that price applies; \"also\" lines are other cheap options that day."
            : "\(who) prices at \(model.when.formatted(date: .omitted, time: .shortened)) on \(day). The time shown is how long that price lasts."
        return lead + " \"pre-book\" = book ahead. \"verify\" = price not confirmed when researched."
    }
}

@MainActor
struct CheapestRow: View {
    @Environment(AppModel.self) private var model
    let rank: Int
    let row: AppModel.Ranked

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Text("\(rank)")
                .font(.headline.monospacedDigit())
                .foregroundStyle(rank == 1 ? Color.brand : .secondary)
                .frame(minWidth: 22)
            GymLogo(gym: row.gym, size: 40)
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text(row.gym.name).font(.headline)
                    FavouriteMark(gymID: row.gym.id)
                    VerifyFlag(gym: row.gym)
                }
                HStack(alignment: .firstTextBaseline, spacing: 6) {
                    Text(formatPrice(row.price)).font(.title3.weight(.bold).monospacedDigit())
                    Pill(text: row.label, color: row.kind.color)
                }
                if !row.when.isEmpty {
                    Label(row.when, systemImage: "clock").labelStyle(.compact).font(.subheadline).foregroundStyle(.secondary)
                }
                ForEach(Array(row.alts.enumerated()), id: \.offset) { _, o in
                    Text("also \(formatPrice(o.price)) \(o.label) · \(o.when)")
                        .font(.caption).foregroundStyle(.secondary)
                }
                HStack(spacing: 8) {
                    OpenBadge(state: row.gym.openState(model.moment))
                    Text("Zone \(row.gym.zone)").font(.caption).foregroundStyle(.secondary)
                    if let t = model.travelText(row.gym) {
                        Label(t, systemImage: "tram.fill").labelStyle(.compact).font(.caption).foregroundStyle(.secondary)
                    }
                    if row.gym.acLevel != "none" {
                        Image(systemName: "snowflake").font(.caption).foregroundStyle(.secondary)
                            .accessibilityLabel(row.gym.acLevel == "full" ? "Full air-conditioning" : "Some air-conditioning")
                    }
                }
                NoteSummary(gymID: row.gym.id)
            }
        }
        .padding(.vertical, 2)
    }
}
