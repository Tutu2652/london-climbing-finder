import SwiftUI

@MainActor
struct DealsView: View {
    enum Section_: String, CaseIterable, Identifiable {
        case offers = "Offers", students = "Students", weekly = "Weekly"
        var id: String { rawValue }
    }
    @AppStorage("dealsSection") private var section: Section_ = .offers

    var body: some View {
        VStack(spacing: 0) {
            Picker("Deals", selection: $section) {
                ForEach(Section_.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .padding(.horizontal).padding(.bottom, 8)
            switch section {
            case .offers: OffersList()
            case .students: StudentsList()
            case .weekly: WeeklyDeals()
            }
        }
        .navigationTitle("Deals")
    }
}

// MARK: Limited-time offers

@MainActor
private struct OffersList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        let today = Moment(.now).ymd
        let all = model.data.limitedPromotions
        let live = all.filter { !$0.isExpired(today: today) }
        let hidden = all.count - live.count
        List {
            Section {
                if live.isEmpty { Text("No limited-time offers running right now.").foregroundStyle(.secondary) }
                ForEach(live) { p in
                    HStack(alignment: .top, spacing: 12) {
                    if let g = logoGym(p) { GymLogo(gym: g, size: 36) }
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(alignment: .firstTextBaseline) {
                            Text(p.title).font(.headline)
                            if p.status == "unverified" {
                                Text("verify").font(.caption2.weight(.semibold)).foregroundStyle(Color.warn)
                            }
                        }
                        HStack(spacing: 6) {
                            if let price = p.price { Pill(text: price, color: .amber) }
                            Text(p.brand).font(.caption).foregroundStyle(.secondary)
                        }
                        if let d = p.details { Text(d).font(.callout) }
                        if let v = p.valid, !v.isEmpty { Text(v).font(.caption).foregroundStyle(Color.amber) }
                        if let e = p.expires { Text("Ends \(e)").font(.caption).foregroundStyle(.secondary) }
                        if let s = p.source, let u = URL(string: s) {
                            Link("View offer", destination: u).font(.callout)
                        }
                    }
                    }
                    .padding(.vertical, 2)
                }
            } footer: {
                Explainer("Seasonal and limited-time offers, separate from the standing deals on each gym's page."
                          + (hidden > 0 ? " \(hidden) offer\(hidden == 1 ? "" : "s") past the published end date \(hidden == 1 ? "is" : "are") hidden." : "")
                          + " Most gyms don't publish an end date, so check the link before you buy.")
            }
        }
    }
}

extension OffersList {
    /// The gym whose logo stands for an offer: any site of its chain, or for an
    /// independent, the gym the title starts with ("Substation: …" → Substation Brixton).
    func logoGym(_ p: Promotion) -> Gym? {
        let gyms = model.data.gyms
        if p.brand != "Independent", let g = gyms.first(where: { $0.brand == p.brand }) { return g }
        guard let key = p.title.split(separator: ":").first.map(String.init), key != p.title else { return nil }
        return gyms.first { $0.brand == "Independent" && ($0.name.hasPrefix(key) || key.hasPrefix($0.name)) }
    }
}

// MARK: Student discounts

/// Within a chain, sites with identical `signature` share one row ("All sites",
/// or a list of names). Independents are never merged.
func collapseByBrand(_ sites: [Gym], brand: String, signature: (Gym) -> String) -> [(label: String, rep: Gym)] {
    if brand == "Independent" { return sites.map { ($0.shortName, $0) } }
    var groups: [(sig: String, sites: [Gym])] = []
    for g in sites {
        let s = signature(g)
        if let i = groups.firstIndex(where: { $0.sig == s }) { groups[i].sites.append(g) } else { groups.append((s, [g])) }
    }
    return groups.map { grp in
        let label = grp.sites.count == sites.count ? "All sites" : grp.sites.map(\.shortName).joined(separator: ", ")
        return (label, grp.sites[0])
    }
}

/// Gyms grouped into chain sections: LCC first, independents last.
func brandGroups(_ gyms: [Gym]) -> [(brand: String, sites: [Gym])] {
    let sorted = gyms.sorted { ($0.brandRank, $0.brand, $0.name) < ($1.brandRank, $1.brand, $1.name) }
    var out: [(brand: String, sites: [Gym])] = []
    for g in sorted {
        if out.last?.brand == g.brand { out[out.count - 1].sites.append(g) } else { out.append((g.brand, [g])) }
    }
    return out
}

@MainActor
private struct StudentsList: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            ForEach(brandGroups(model.data.gyms), id: \.brand) { b in
                Section(b.brand) {
                    ForEach(collapseByBrand(b.sites, brand: b.brand, signature: {
                        "\($0.payg.concessionPeak ?? -1)|\($0.payg.concessionOffpeak ?? -1)|\($0.studentNote ?? "")"
                    }), id: \.rep.id) { row in
                        NavigationLink(value: row.rep) {
                            HStack(alignment: .top, spacing: 12) {
                            GymLogo(gym: row.rep, size: 36)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(row.label).font(.headline)
                                HStack(spacing: 6) {
                                    Pill(text: "peak " + formatPrice(row.rep.payg.concessionPeak), color: .peakPurple)
                                    if let off = row.rep.payg.concessionOffpeak {
                                        Pill(text: "off-peak " + formatPrice(off), color: .brand)
                                    }
                                }
                                if let n = row.rep.studentNote { Text(n).font(.caption).foregroundStyle(.secondary) }
                            }
                            }
                        }
                    }
                }
            }
            if !model.data.studentPromotions.isEmpty {
                Section("Group-wide student promotions") {
                    ForEach(model.data.studentPromotions) { p in
                        VStack(alignment: .leading, spacing: 3) {
                            Text(p.offer).font(.callout.weight(.semibold))
                            Text(p.scope).font(.caption).foregroundStyle(.secondary)
                            if let s = p.source, let u = URL(string: s) { Link("Source", destination: u).font(.caption) }
                        }
                    }
                }
            }
        }
    }
}

// MARK: Weekly deals calendar

@MainActor
private struct WeeklyDeals: View {
    @Environment(AppModel.self) private var model
    @Environment(\.openURL) private var openURL

    private static let site = "https://london-climbing-finder.pages.dev"
    private static let fullDays = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]

    struct Item: Identifiable {
        let id = UUID()
        let gym: Gym
        let slot: MagicSlot
    }

    var body: some View {
        let today = model.moment.weekdayIndex
        List {
            Section {
                subscribe("All deals", "london-climbing-deals-all.ics")
                subscribe("Without student-only deals", "london-climbing-deals-no-student.ics")
            } header: {
                Text("Subscribe in Calendar")
            } footer: {
                Explainer("Adds every special cheap slot as weekly repeating events, and updates when they change.")
            }
            ForEach(0..<7, id: \.self) { i in
                let day = (today + i) % 7
                Section(i == 0 ? "Today · " + Self.fullDays[day] : Self.fullDays[day]) {
                    let items = items(on: weekdays[day])
                    if items.isEmpty { Text("—").foregroundStyle(.secondary) }
                    ForEach(items) { it in
                        NavigationLink(value: it.gym) {
                            HStack(spacing: 12) {
                                GymLogo(gym: it.gym, size: 36)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(it.slot.label.replacingFirst(" (concession)", with: "") + " · " + it.gym.shortName)
                                    Text(detail(it.slot)).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer(minLength: 4)
                                Pill(text: formatPrice(it.slot.price), color: .amber)
                            }
                        }
                    }
                }
            }
        }
    }

    private func items(on day: String) -> [Item] {
        // Women+ and local-resident slots appear here once your settings qualify for them.
        model.data.gyms.flatMap { g in g.slots(model.eligibility).filter { $0.days.contains(day) }.map { Item(gym: g, slot: $0) } }
            .enumerated().sorted { ($0.element.slot.price, $0.offset) < ($1.element.slot.price, $1.offset) }.map(\.element)
    }

    private func detail(_ s: MagicSlot) -> String {
        (s.isAllDay ? "all day" : s.start + "–" + s.end)
            + (s.prebook ? " (pre-book)" : "") + (s.isConcession ? " · conc only" : "")
            + (s.exceptBankHolidays ? " · not bank hols" : "")
            + (s.condition.isEmpty ? "" : " · " + s.condition + " only")
    }

    private func subscribe(_ title: String, _ file: String) -> some View {
        Button {
            if let u = URL(string: "webcal://" + Self.site.replacingFirst("https://", with: "") + "/" + file) { openURL(u) }
        } label: {
            Label(title, systemImage: "calendar.badge.plus")
        }
    }
}
