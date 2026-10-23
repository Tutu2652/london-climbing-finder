import SwiftUI

@MainActor
struct GymDetailView: View {
    @Environment(AppModel.self) private var model
    @State private var editing = false
    let gym: Gym

    var body: some View {
        let conc = model.concession
        let now = model.pricer.applicable(gym, model.moment, conc: conc)
        let best = model.pricer.bestOnDay(gym, model.dayMoment, conc: conc)
        List {
            Section {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 12) {
                        GymLogo(gym: gym, size: 56)
                        Text([gym.brand, gym.area].compactMap { $0 }.joined(separator: " · "))
                            .font(.subheadline).foregroundStyle(.secondary)
                    }
                    HStack(spacing: 8) {
                        Pill(text: gym.typeLabel, color: .secondary)
                        Pill(text: "Zone \(gym.zone)", color: .secondary)
                        OpenBadge(state: gym.openState(model.moment))
                        VerifyFlag(gym: gym)
                    }
                    HStack {
                        DirectionsButton(gym: gym).buttonStyle(.bordered)
                        if let t = model.travelText(gym) {
                            Label(t, systemImage: "tram.fill").labelStyle(.compact).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.top, 4)
                }
            }

            Section {
                WhenControls(showMode: false)
                if let best {
                    PriceLine(title: "Cheapest on \(model.when.formatted(.dateTime.weekday(.wide).day().month()))",
                              price: best.price, label: best.label, kind: best.kind, detail: best.when)
                }
                if let p = now.price {
                    PriceLine(title: "At \(model.when.formatted(date: .omitted, time: .shortened))",
                              price: p, label: now.label, kind: now.kind,
                              detail: model.pricer.windowForTime(gym, model.moment, conc: conc))
                }
            } header: {
                Text(conc ? "Your price · student / concession" : "Your price · adult")
            }

            Section("Day-pass prices") {
                PriceRow(name: "Adult peak", value: gym.payg.adultPeak)
                PriceRow(name: "Adult off-peak", value: gym.payg.adultOffpeak)
                PriceRow(name: "Concession peak", value: gym.payg.concessionPeak)
                PriceRow(name: "Concession off-peak", value: gym.payg.concessionOffpeak)
                if let fee = gym.registrationFee { PriceRow(name: "Registration fee (first visit)", value: fee) }
            }

            let deals = gym.dealLines(conc: conc)
            if !deals.isEmpty {
                Section("Off-peak & cheap slots") {
                    ForEach(deals) { d in
                        HStack(alignment: .firstTextBaseline) {
                            Pill(text: formatPrice(d.price), color: d.magic ? .amber : .brand)
                            VStack(alignment: .leading) {
                                Text(d.label)
                                Text(d.when + d.extra + (d.approx ? " ~approx" : ""))
                                    .font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
            }

            if let note = gym.studentNote, !note.isEmpty {
                Section("Students & concessions") { Text(note).font(.callout) }
            }

            Section("Membership & passes") {
                if let m = gym.membership, let full = m.fullMonthly {
                    LabeledContent("Monthly", value: formatPrice(full))
                    if let c = m.concessionMonthly { LabeledContent("Monthly (concession)", value: formatPrice(c)) }
                }
                if let passes = gym.passes { Text(passes).font(.callout) }
            }

            Section("Opening hours" + (gym.hoursAssumed ? " (approx)" : "")) {
                ForEach(weekdays, id: \.self) { d in
                    LabeledContent(d, value: gym.hours[d].map { $0[0] + "–" + $0[1] } ?? "closed")
                        .fontWeight(d == model.moment.weekday ? .semibold : .regular)
                }
            }

            Section("Facilities") {
                if let f = gym.facilities { Text(f).font(.callout) }
                if let ac = gym.acNote {
                    Label(ac, systemImage: "snowflake").font(.callout)
                }
            }

            if let info = gym.classInfo {
                Section("Classes") {
                    ForEach(info.split(separator: ";").map { $0.trimmingCharacters(in: .whitespaces) }, id: \.self) {
                        LinkedText($0).font(.callout)
                    }
                }
            }

            if !gym.socials.isEmpty {
                Section("Regular socials & sessions") {
                    ForEach(gym.socials, id: \.self) { LinkedText($0).font(.callout) }
                }
            }

            Section("Links") {
                LinkRow(title: "Prices page", url: gym.pricesURL, icon: "sterlingsign.circle")
                LinkRow(title: "Website", url: gym.website, icon: "safari")
                LinkRow(title: "Classes", url: gym.classesURL, icon: "graduationcap")
                LinkRow(title: "Events", url: gym.eventsURL, icon: "calendar")
                LinkRow(title: "Route-setting schedule", url: gym.routeSettingURL, icon: "arrow.triangle.2.circlepath")
                if let ig = gym.instagram { LinkRow(title: "@" + ig, url: gym.instagramURL?.absoluteString, icon: "camera") }
                if let a = gym.address { Label(a, systemImage: "mappin.and.ellipse").font(.callout) }
            }

            Section {
                if let r = model.ratings[gym.id] { Stars(rating: r) }
                if let n = model.notes[gym.id] { Text(n).font(.callout) }
                Button(model.notes[gym.id] == nil && model.ratings[gym.id] == nil ? "Add note & rating" : "Edit note & rating") {
                    editing = true
                }
            } header: {
                Text("Your note")
            } footer: {
                Text("Saved on this iPhone only." + (gym.lastChecked.map { " Prices last checked \($0)." } ?? ""))
            }
        }
        .navigationTitle(gym.name)
        .inlineTitle()
        .toolbar { ToolbarItem(placement: .primaryAction) { FavouriteButton(gymID: gym.id) } }
        .sheet(isPresented: $editing) { NoteEditor(gym: gym) }
    }
}

@MainActor
private struct PriceLine: View {
    let title: String
    let price: Double
    let label: String
    let kind: PriceKind
    let detail: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(formatPrice(price)).font(.title2.bold().monospacedDigit())
                Pill(text: label, color: kind.color)
            }
            if !detail.isEmpty { Label(detail, systemImage: "clock").labelStyle(.compact).font(.subheadline).foregroundStyle(.secondary) }
        }
    }
}

@MainActor
private struct PriceRow: View {
    let name: String
    let value: Double?
    var body: some View {
        LabeledContent(name, value: value.map(formatPrice) ?? "—")
    }
}

@MainActor
private struct LinkRow: View {
    let title: String
    let url: String?
    let icon: String
    var body: some View {
        if let url, let u = URL(string: url) {
            Link(destination: u) { Label(title, systemImage: icon) }
        }
    }
}

@MainActor
struct NoteEditor: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    let gym: Gym
    @State private var rating = 0
    @State private var note = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Your rating") {
                    HStack(spacing: 14) {
                        ForEach(1...5, id: \.self) { k in
                            Button { rating = rating == k ? 0 : k } label: {
                                Image(systemName: k <= rating ? "star.fill" : "star")
                                    .font(.title2).foregroundStyle(Color.amber)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("\(k) star\(k > 1 ? "s" : "")")
                        }
                    }
                }
                Section("Your note") {
                    TextField("e.g. great cafe, quiet in the mornings…", text: $note, axis: .vertical)
                        .lineLimit(3...8)
                }
            }
            .navigationTitle(gym.shortName)
            .inlineTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        model.setRating(rating, for: gym.id)
                        model.setNote(note, for: gym.id)
                        dismiss()
                    }
                }
            }
            .onAppear {
                rating = model.ratings[gym.id] ?? 0
                note = model.notes[gym.id] ?? ""
            }
        }
    }
}
