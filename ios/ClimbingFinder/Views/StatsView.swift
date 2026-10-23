import SwiftUI
import SwiftData
import Charts

@MainActor
struct StatsView: View {
    @Environment(AppModel.self) private var model
    @Query(sort: \ClimbSession.start) private var allSessions: [ClimbSession]
    @AppStorage("statsPeriod") private var period: Period = .month3
    @State private var discipline: Discipline = .boulder

    enum Period: String, CaseIterable, Identifiable {
        case month, month3, year, all
        var id: String { rawValue }
        var title: String {
            switch self { case .month: return "30 days"; case .month3: return "3 months"; case .year: return "Year"; case .all: return "All" }
        }
        var days: Int? {
            switch self { case .month: return 30; case .month3: return 91; case .year: return 365; case .all: return nil }
        }
    }

    private var sessions: [ClimbSession] {
        guard let d = period.days, let from = Calendar.london.date(byAdding: .day, value: -d, to: .now) else { return allSessions }
        return allSessions.filter { $0.start >= from }
    }

    var body: some View {
        let s = sessions
        let climbs = s.flatMap(\.climbs).filter { $0.discipline == discipline }
        let sends = climbs.filter(\.result.isSend)
        List {
            Section {
                Picker("Period", selection: $period) { ForEach(Period.allCases) { Text($0.title).tag($0) } }
                    .pickerStyle(.segmented)
                if allSessions.flatMap(\.climbs).contains(where: { $0.discipline == .rope }) {
                    Picker("Discipline", selection: $discipline) { ForEach(Discipline.allCases) { Text($0.title).tag($0) } }
                        .pickerStyle(.segmented)
                }
            }

            Section {
                Grid(horizontalSpacing: 12, verticalSpacing: 14) {
                    GridRow {
                        Tile(value: "\(s.count)", label: "sessions")
                        Tile(value: formatDuration(s.map(\.duration).reduce(0, +)), label: "climbing")
                        Tile(value: "\(sends.count)", label: "sends")
                    }
                    GridRow {
                        Tile(value: sends.map(\.grade).max().map { discipline.grades[$0] } ?? "–", label: "hardest")
                        Tile(value: sends.isEmpty ? "–" : "\(Int((Double(sends.filter { $0.result == .flash }.count) / Double(sends.count) * 100).rounded()))%",
                             label: "flashed")
                        Tile(value: s.isEmpty ? "–" : String(format: "%.1f", Double(sends.count) / Double(s.count)), label: "sends / session")
                    }
                }
                .padding(.vertical, 6)
            }

            if !sends.isEmpty {
                Section {
                    pyramid(sends)
                } header: {
                    Text("Grade pyramid")
                } footer: {
                    Text("Sends at each grade. A broad base under your hardest grade usually means you're ready to push it.")
                }
            }

            let progress = s.compactMap { sess in sess.hardest(discipline).map { (sess.start, $0) } }
            if progress.count >= 2 {
                Section("Hardest send per session") {
                    Chart(progress, id: \.0) { p in
                        LineMark(x: .value("Date", p.0), y: .value("Grade", p.1))
                            .foregroundStyle(Color.brand).interpolationMethod(.monotone)
                        PointMark(x: .value("Date", p.0), y: .value("Grade", p.1)).foregroundStyle(Color.brand)
                    }
                    .chartYScale(domain: max(0, (progress.map(\.1).min() ?? 0) - 1)...max(1, progress.map(\.1).max() ?? 0))
                    .chartYAxis {
                        AxisMarks(values: .stride(by: 1)) { v in
                            AxisGridLine()
                            AxisValueLabel { if let i = v.as(Int.self), discipline.grades.indices.contains(i) { Text(discipline.grades[i]) } }
                        }
                    }
                    .chartXAxis {
                        AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                            AxisGridLine()
                            AxisValueLabel(format: .dateTime.day().month(.abbreviated))
                        }
                    }
                    .frame(height: 180)
                }
            }

            if s.count >= 2 {
                Section("Sessions per week") {
                    Chart(weekly(s), id: \.0) { w in
                        BarMark(x: .value("Week", w.0, unit: .weekOfYear), y: .value("Sessions", w.1))
                            .foregroundStyle(Color.brand)
                    }
                    .frame(height: 140)
                }
            }

            if !s.isEmpty {
                Section("Where you climbed") {
                    ForEach(byGym(s), id: \.gym.id) { row in
                        NavigationLink { GymVisitsView(gym: row.gym) } label: {
                        HStack(spacing: 12) {
                            GymLogo(gym: row.gym, size: 32)
                            Text(row.gym.shortName)
                            Spacer()
                            VStack(alignment: .trailing) {
                                Text("\(row.visits) visit\(row.visits == 1 ? "" : "s")").font(.subheadline)
                                if row.spent > 0 { Text(formatPrice(row.spent)).font(.caption).foregroundStyle(.secondary) }
                            }
                        }
                        }
                    }
                }
                MembershipCheck(sessions: allSessions)
            }

            if allSessions.isEmpty {
                ContentUnavailableView("Nothing to show yet", systemImage: "chart.bar",
                                       description: Text("Log a few sessions and your stats appear here."))
            }
        }
        .navigationTitle("Stats")
        .inlineTitle()
    }

    private func pyramid(_ sends: [Climb]) -> some View {
        let top = sends.map(\.grade).max() ?? 0
        let low = max(0, (sends.map(\.grade).min() ?? 0))
        struct Bar: Identifiable { let type: String; let grade: Int; let count: Int; var id: String { "\(type)\(grade)" } }
        let data = (low...top).reversed().flatMap { g in
            [Bar(type: "Flash", grade: g, count: sends.filter { $0.grade == g && $0.result == .flash }.count),
             Bar(type: "Send", grade: g, count: sends.filter { $0.grade == g && $0.result == .send }.count)]
        }
        return Chart(data) { d in
            BarMark(x: .value("Sends", d.count), y: .value("Grade", discipline.grades[d.grade]))
                .foregroundStyle(by: .value("Type", d.type))
        }
        .chartForegroundStyleScale(["Flash": Color.amber, "Send": Color.brand])
        .chartYScale(domain: (low...top).reversed().map { discipline.grades[$0] })
        .frame(height: CGFloat(max(3, top - low + 1)) * 30 + 30)
    }

    private func weekly(_ s: [ClimbSession]) -> [(Date, Int)] {
        let cal = Calendar.london
        let groups = Dictionary(grouping: s) { cal.dateInterval(of: .weekOfYear, for: $0.start)!.start }
        return groups.map { ($0.key, $0.value.count) }.sorted { $0.0 < $1.0 }
    }

    private func byGym(_ s: [ClimbSession]) -> [(gym: Gym, visits: Int, spent: Double)] {
        Dictionary(grouping: s, by: \.gymID).compactMap { id, ss in
            model.gym(id).map { ($0, ss.count, ss.compactMap(\.price).reduce(0, +)) }
        }
        .sorted { ($0.visits, $0.spent) > ($1.visits, $1.spent) }
    }

    struct Tile: View {
        let value: String, label: String
        var body: some View {
            VStack(spacing: 2) {
                Text(value).font(.title3.weight(.bold).monospacedDigit()).minimumScaleFactor(0.7).lineLimit(1)
                Text(label).font(.caption).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

/// Compares the last 30 days of day-pass spending at each chain with its
/// monthly membership.
@MainActor
private struct MembershipCheck: View {
    @Environment(AppModel.self) private var model
    let sessions: [ClimbSession]

    var body: some View {
        let monthAgo = Calendar.london.date(byAdding: .day, value: -30, to: .now)!
        let recent = sessions.filter { $0.start >= monthAgo }
        let rows = Dictionary(grouping: recent) { session in
            guard let gym = model.gym(session.gymID) else { return session.gymID }
            return gym.brand == "Independent" ? gym.id : gym.brand
        }
            .compactMap { brand, ss -> (brand: String, visits: Int, spent: Double, membership: Double, gym: Gym)? in
                // Independents' memberships cover one gym, so compare per gym there.
                guard let g = ss.compactMap({ model.gym($0.gymID) }).first,
                      let m = model.concession ? (g.membership?.concessionMonthly ?? g.membership?.fullMonthly) : g.membership?.fullMonthly
                else { return nil }
                let paid = ss.compactMap(\.price)
                return (g.brand == "Independent" ? g.shortName : brand, ss.count, paid.reduce(0, +), m, g)
            }
            .filter { $0.visits >= 2 }
            .sorted { $0.spent > $1.spent }
        if !rows.isEmpty {
            Section {
                ForEach(rows, id: \.brand) { r in
                    let paidVisits = recent.filter { session in
                        guard let gym = model.gym(session.gymID) else { return false }
                        let sameMembership = r.gym.brand == "Independent" ? gym.id == r.gym.id : gym.brand == r.gym.brand
                        return sameMembership && (session.price ?? 0) > 0
                    }.count
                    let perVisit = paidVisits > 0 ? r.spent / Double(paidVisits) : nil
                    VStack(alignment: .leading, spacing: 4) {
                        Text(r.brand).font(.headline)
                        Text("\(r.visits) visits, \(formatPrice(r.spent)) on entry · membership \(formatPrice(r.membership))/mo")
                            .font(.callout)
                        if r.spent >= r.membership {
                            Text("Compared with the listed monthly price, potential savings are \(formatPrice(r.spent - r.membership)).")
                                .font(.callout.weight(.semibold)).foregroundStyle(Color.brand)
                        } else if let perVisit {
                            let breakEven = Int((r.membership / perVisit).rounded(.up))
                            Text("At about \(formatPrice(perVisit)) a visit, membership pays off from \(breakEven) visits a month.")
                                .font(.callout).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.vertical, 2)
                }
            } header: {
                Text("Membership check · last 30 days")
            } footer: {
                Text("Uses what you logged as your entry price. Chains' memberships usually cover all their sites.")
            }
        }
    }
}
