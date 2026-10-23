import SwiftUI

enum GymSort: String, CaseIterable, Identifiable {
    case chain, peak, offpeak, distance
    var id: String { rawValue }
    var title: String {
        switch self {
        case .chain: return "By chain"
        case .peak: return "Cheapest — peak"
        case .offpeak: return "Cheapest — off-peak"
        case .distance: return "Nearest first"
        }
    }
}

@MainActor
struct GymsListView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("gymSort") private var sort: GymSort = .chain
    @State private var search = ""

    var body: some View {
        List {
            if sort == .chain {
                ForEach(brandSections, id: \.0) { brand, gyms in
                    Section(brand) { ForEach(gyms) { GymRow(gym: $0) } }
                }
            } else {
                Section {
                    if sort == .distance && model.origin == nil {
                        NavigationLink("Set your location to sort by distance") { OriginSettings() }
                    }
                    ForEach(sorted) { GymRow(gym: $0) }
                }
            }
            if filtered.isEmpty {
                Text(search.isEmpty ? "No gyms match the current filters." : "No gyms match \"\(search)\"" + (model.filtersActive ? " with the current filters." : "."))
                    .foregroundStyle(.secondary)
            }
        }
        #if os(iOS)
        .searchable(text: $search, placement: .navigationBarDrawer(displayMode: .always),
                    prompt: "Gym, area, postcode or facility")
        #else
        .searchable(text: $search, prompt: "Gym, area, postcode or facility")
        #endif
        .navigationTitle("All gyms")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Picker("Sort", selection: $sort) { ForEach(GymSort.allCases) { Text($0.title).tag($0) } }
                } label: { Label("Sort", systemImage: "arrow.up.arrow.down") }
            }
            ToolbarItem(placement: .primaryAction) { FilterMenu() }
        }
    }

    private var filtered: [Gym] {
        // Every word must appear somewhere. Spaces and punctuation are ignored, so
        // "autobelay", "auto belay" and "auto-belay" all match, as does "se11" for "SE11 5JA".
        let squash = { (s: String) in s.lowercased().filter { $0.isLetter || $0.isNumber } }
        let words = search.split(separator: " ").map { squash(String($0)) }.filter { !$0.isEmpty }
        return model.data.gyms.filter { g in
            guard model.matchesFilters(g) else { return false }
            let haystack = squash([g.name, g.brand, g.area, g.borough, g.postcode, g.address, g.typeLabel, g.facilities]
                .compactMap { $0 }.joined(separator: " "))
            return words.allSatisfy { haystack.contains($0) }
        }
    }

    private var brandSections: [(String, [Gym])] {
        let gyms = filtered.sorted { ($0.brandRank, $0.brand, $0.zone, $0.name) < ($1.brandRank, $1.brand, $1.zone, $1.name) }
        var out: [(String, [Gym])] = []
        for g in gyms {
            if out.last?.0 == g.brand { out[out.count - 1].1.append(g) } else { out.append((g.brand, [g])) }
        }
        return out
    }

    private var sorted: [Gym] {
        let conc = model.concession
        let key: (Gym) -> Double
        switch sort {
        case .peak: key = { $0.peakPrice(conc: conc) ?? .infinity }
        case .offpeak: key = { $0.bestOffpeak(conc: conc) ?? .infinity }
        default: key = { model.distanceKm($0) ?? .infinity }
        }
        return filtered.sorted { (key($0), $0.name) < (key($1), $1.name) }
    }
}

@MainActor
private struct GymRow: View {
    @Environment(AppModel.self) private var model
    let gym: Gym

    var body: some View {
        let conc = model.concession
        NavigationLink(value: gym) {
            VStack(alignment: .leading, spacing: 3) {
                HStack(alignment: .firstTextBaseline) {
                    Text(gym.name).font(.headline)
                    VerifyFlag(gym: gym)
                }
                Text([gym.area, "Zone \(gym.zone)", gym.typeLabel].compactMap { $0 }.joined(separator: " · "))
                    .font(.caption).foregroundStyle(.secondary)
                HStack(spacing: 6) {
                    Pill(text: "peak " + formatPrice(gym.peakPrice(conc: conc)), color: .peakPurple)
                    if let off = gym.bestOffpeak(conc: conc) {
                        Pill(text: "from " + formatPrice(off), color: .amber)
                    }
                    if let d = model.distanceKm(gym) {
                        Text(String(format: "%.1f km", d)).font(.caption).foregroundStyle(.secondary)
                    }
                    if gym.acLevel != "none" { Image(systemName: "snowflake").font(.caption).foregroundStyle(.secondary) }
                }
                NoteSummary(gymID: gym.id)
            }
        }
    }
}
