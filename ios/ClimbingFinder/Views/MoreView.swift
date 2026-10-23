import SwiftUI

@MainActor
struct MoreView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            Section {
                NavigationLink { OriginSettings() } label: {
                    LabeledContent {
                        Text(model.origin?.label ?? "Not set")
                    } label: {
                        Label("Your location", systemImage: "location")
                    }
                }
            } footer: {
                Text("Used for public-transport travel times and sorting by distance.")
            }
            Section {
                NavigationLink { MembershipsView() } label: { Label("Memberships & passes", systemImage: "creditcard") }
                NavigationLink { ClassesView() } label: { Label("Classes", systemImage: "graduationcap") }
                NavigationLink { EventsView() } label: { Label("Socials, events & route setting", systemImage: "person.3") }
            }
            Section("About the data") {
                if let d = model.data.meta.lastResearchDate { LabeledContent("Prices researched", value: d) }
                LabeledContent("Gyms", value: "\(model.data.gyms.count)")
                Link(destination: URL(string: "https://london-climbing-finder.pages.dev/")!) {
                    Label("Open the website", systemImage: "safari")
                }
                Link(destination: URL(string: "https://docs.google.com/forms/d/e/1FAIpQLSdWqho0guto0OG0XumGWdg8GeAbNj2ulPUFpVbSth3MdW83sA/viewform")!) {
                    Label("Report a wrong price or missing deal", systemImage: "exclamationmark.bubble")
                }
            }
            Section("Price colours") {
                HStack { Pill(text: "peak", color: .peakPurple); Text("evenings & weekends").font(.callout) }
                HStack { Pill(text: "off-peak", color: .brand); Text("weekday daytime").font(.callout) }
                HStack { Pill(text: "super off-peak", color: .amber); Text("special cheap slots & deals").font(.callout) }
            }
        }
        .navigationTitle("More")
    }
}

/// Lets you set where travel times are measured from.
@MainActor
struct OriginSettings: View {
    var body: some View {
        List { Section { OriginPicker() } footer: {
            Text("Travel times come from TfL's Journey Planner; your location is only sent to TfL to plan the journeys.")
        } }
        .navigationTitle("Your location")
        .inlineTitle()
    }
}

@MainActor
struct OriginPicker: View {
    @Environment(AppModel.self) private var model
    @State private var query = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        if let o = model.origin {
            LabeledContent("From", value: o.label)
        }
        HStack {
            TextField("Postcode or place, e.g. WC2N 5DU", text: $query)
                .onSubmit(lookUp)
                #if os(iOS)
                .textInputAutocapitalization(.characters)
                #endif
            if busy { ProgressView() }
        }
        Button { Task { await useCurrentLocation() } } label: {
            Label("Use my current location", systemImage: "location.fill")
        }
        if model.origin != nil {
            Button("Clear location", role: .destructive) { model.origin = nil }
        }
        if let error { Text(error).font(.footnote).foregroundStyle(Color.warn) }
    }

    private func lookUp() {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        Task {
            busy = true; error = nil
            do { model.origin = try await LocationService.shared.geocode(q); query = "" }
            catch { self.error = error.localizedDescription }
            busy = false
        }
    }

    private func useCurrentLocation() async {
        busy = true; error = nil
        do {
            let l = try await LocationService.shared.currentLocation()
            model.origin = Origin(label: "My location", lat: l.coordinate.latitude, lng: l.coordinate.longitude)
        } catch {
            self.error = error.localizedDescription
        }
        busy = false
    }
}

// MARK: Memberships

@MainActor
private struct MembershipsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            ForEach(brandGroups(model.data.gyms), id: \.brand) { b in
                Section(b.brand) {
                    ForEach(collapseByBrand(b.sites, brand: b.brand, signature: {
                        "\($0.membership?.fullMonthly ?? -1)|\($0.membership?.concessionMonthly ?? -1)|\($0.passes ?? "")"
                    }), id: \.rep.id) { row in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(row.label).font(.headline)
                            HStack(spacing: 6) {
                                if let f = row.rep.membership?.fullMonthly { Pill(text: formatPrice(f) + "/mo", color: .peakPurple) }
                                if let c = row.rep.membership?.concessionMonthly { Pill(text: formatPrice(c) + "/mo conc", color: .brand) }
                            }
                            if let p = row.rep.passes { Text(p).font(.caption).foregroundStyle(.secondary) }
                        }
                    }
                }
            }
            Section {} footer: {
                Explainer("Standard monthly rolling membership (unlimited climbing) plus multi-visit passes. Sites in a chain share a row when prices match. Annual memberships are usually cheaper than monthly.")
            }
        }
        .navigationTitle("Memberships")
        .inlineTitle()
    }
}

// MARK: Classes

@MainActor
private struct ClassesView: View {
    @Environment(AppModel.self) private var model

    private var rows: [(label: String, info: String?, url: String?)] {
        var out: [(label: String, info: String?, url: String?)] = [], seen: Set<String> = []
        for b in brandGroups(model.data.gyms) {
            for g in b.sites {
                if g.brand == "Independent" { out.append((g.name, g.classInfo, g.classesURL)); continue }
                if seen.contains(g.brand) { continue }
                seen.insert(g.brand)
                var rep = g, label = g.brand
                if g.brand == "The Font" { rep = model.gym("the_font_wandsworth") ?? g; label = "The Font (Wandsworth)" }
                out.append((label, rep.classInfo, rep.classesURL))
            }
        }
        return out
    }

    var body: some View {
        List {
            ForEach(rows, id: \.label) { r in
                Section(r.label) {
                    ForEach((r.info ?? "Intro sessions & courses").split(separator: ";").map { $0.trimmingCharacters(in: .whitespaces) }, id: \.self) {
                        LinkedText($0).font(.callout)
                    }
                    if let s = r.url, let u = URL(string: s) { Link("Class details", destination: u) }
                }
            }
        }
        .navigationTitle("Classes")
        .inlineTitle()
    }
}

// MARK: Socials & events

@MainActor
private struct EventsView: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        List {
            ForEach(brandGroups(model.data.gyms), id: \.brand) { b in
                Section(b.brand) {
                    ForEach(b.sites) { g in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(g.shortName).font(.headline)
                            ForEach(g.socials, id: \.self) { LinkedText("• " + $0).font(.callout) }
                            HStack(spacing: 16) {
                                if let s = g.eventsURL, let u = URL(string: s) { Link("Events", destination: u) }
                                if let s = g.routeSettingURL, let u = URL(string: s) { Link("Route setting", destination: u) }
                                if let u = g.instagramURL { Link("@" + (g.instagram ?? ""), destination: u) }
                            }
                            .font(.callout)
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
            Section {} footer: {
                Explainer("Times can vary by site and season, so check the gym's events page or Instagram. Only a few gyms publish a route-setting schedule; the rest post resets on Instagram.")
            }
        }
        .navigationTitle("Socials & events")
        .inlineTitle()
    }
}
