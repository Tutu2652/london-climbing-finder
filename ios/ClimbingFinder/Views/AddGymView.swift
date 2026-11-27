import SwiftUI
import SwiftData
import MapKit

/// Add a gym that isn't listed (usually one you're visiting while travelling, so you can log
/// sessions there), or edit one you added. It stays on this iPhone.
///
/// Only a name and a pin are needed. "I'm here now" or a search drops the pin and fills in the
/// area, address, country and the gym's time zone, which you can still change.
@MainActor
struct AddGymView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    /// The gym being edited; nil to add a new one.
    let editing: AddedGym?
    /// Called with the saved gym's id.
    var onSave: (String) -> Void

    @State private var name: String
    @State private var area = ""
    @State private var country = ""
    @State private var postcode = ""
    @State private var address = ""
    @State private var timeZone: String?
    @State private var pin: CLLocationCoordinate2D?
    @State private var camera: MapCameraPosition = .region(MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 51.515, longitude: -0.12),
        span: MKCoordinateSpan(latitudeDelta: 0.2, longitudeDelta: 0.26)))
    @State private var type = "bouldering"
    @State private var boulderGrading: GymGrading = .v
    @State private var ropeGrading: GymGrading = .french
    @State private var boulderLabels = ""
    @State private var ropeLabels = ""
    @State private var adultPrice = ""
    @State private var concessionPrice = ""
    @State private var open: [String: Bool] = [:]
    @State private var from: [String: Date] = [:]
    @State private var to: [String: Date] = [:]
    @State private var website = ""
    @State private var notes = ""
    @State private var query = ""
    @State private var busy = false
    @State private var findError: String?
    /// What the last lookup filled in, so a later one replaces those but never what you typed.
    @State private var filled: [String: String] = [:]

    init(editing: AddedGym? = nil, name: String = "", defaultBoulderGrading: GymGrading = .v, onSave: @escaping (String) -> Void = { _ in }) {
        self.editing = editing
        self.onSave = onSave
        _name = State(initialValue: editing?.name ?? name)
        guard let g = editing else { return }
        _area = State(initialValue: g.area)
        _country = State(initialValue: g.country ?? "")
        _postcode = State(initialValue: g.postcode ?? "")
        _address = State(initialValue: g.address ?? "")
        _timeZone = State(initialValue: g.timeZone)
        let c = CLLocationCoordinate2D(latitude: g.lat, longitude: g.lng)
        _pin = State(initialValue: c)
        _camera = State(initialValue: .region(MKCoordinateRegion(center: c, latitudinalMeters: 1500, longitudinalMeters: 1500)))
        _type = State(initialValue: g.type)
        _boulderGrading = State(initialValue: g.boulderGrading ?? defaultBoulderGrading)
        _ropeGrading = State(initialValue: g.ropeGrading ?? .french)
        _boulderLabels = State(initialValue: g.boulderGradeLabels?.joined(separator: ", ") ?? "")
        _ropeLabels = State(initialValue: g.ropeGradeLabels?.joined(separator: ", ") ?? "")
        _adultPrice = State(initialValue: g.adultPrice.map(Self.priceText) ?? "")
        _concessionPrice = State(initialValue: g.concessionPrice.map(Self.priceText) ?? "")
        _open = State(initialValue: g.hours.mapValues { _ in true })
        _from = State(initialValue: g.hours.compactMapValues { Self.date($0[0]) })
        _to = State(initialValue: g.hours.compactMapValues { Self.date($0[1]) })
        _website = State(initialValue: g.website ?? "")
        _notes = State(initialValue: g.notes ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Gym name", text: $name)
                }

                Section {
                    Button { Task { await useMyLocation() } } label: {
                        Label("I'm here now: use my location", systemImage: "location.fill")
                    }
                    .disabled(busy)
                    HStack {
                        TextField("Or search: gym name, address or city", text: $query)
                            .onSubmit { Task { await search() } }
                            .submitLabel(.search)
                        if busy { ProgressView() }
                        else if !query.trimmingCharacters(in: .whitespaces).isEmpty {
                            Button("Find") { Task { await search() } }
                        }
                    }
                    if let findError { Text(findError).font(.footnote).foregroundStyle(Color.warn) }
                    MapReader { proxy in
                        Map(position: $camera) {
                            if let pin { Marker(name.isEmpty ? "Gym" : name, coordinate: pin).tint(Color.brand) }
                        }
                        .onTapGesture { point in
                            if let c = proxy.convert(point, from: .local) { Task { await place(c, move: false) } }
                        }
                    }
                    .frame(height: 220)
                    .listRowInsets(EdgeInsets())
                } header: {
                    Text("Where is it?")
                } footer: {
                    Text(pin == nil ? "Use your location if you're at the gym, search for it, or tap the map."
                                    : "Tap the map to move the pin.")
                }

                if pin != nil {
                    Section {
                        TextField("Area or city", text: $area)
                        TextField("Address", text: $address, axis: .vertical)
                        TextField("Postcode", text: $postcode).textInputAutocapitalization(.characters)
                        TextField("Country", text: $country)
                        if let tz = timeZone.flatMap(TimeZone.init(identifier:)), tz.identifier != TimeZone.current.identifier {
                            LabeledContent("Local time", value: Self.localTimeText(tz))
                        }
                    } header: {
                        Text("Filled in from the pin")
                    } footer: {
                        Text("Change anything that's wrong.")
                    }
                }

                Section {
                    Picker("Type", selection: $type) {
                        Text("Bouldering").tag("bouldering")
                        Text("Ropes + bouldering").tag("ropes+bouldering")
                        Text("Ropes").tag("ropes")
                    }
                    priceField("Adult day pass", text: $adultPrice)
                    priceField("Student / concession", text: $concessionPrice)
                    TextField("Website", text: $website)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                    TextField("Notes", text: $notes, axis: .vertical)
                } header: {
                    Text("Details (optional)")
                } footer: {
                    Text("Prices are in pounds, and fill in a session's entry price. A gym outside London isn't compared with London gyms in Cheapest.")
                }

                Section("Grading") {
                    if type != "ropes" {
                        gradingPicker("Bouldering", selection: $boulderGrading, choices: GymGrading.boulderChoices, labels: $boulderLabels)
                    }
                    if type.contains("ropes") {
                        gradingPicker("Ropes", selection: $ropeGrading, choices: GymGrading.ropeChoices, labels: $ropeLabels)
                    }
                    if type != "ropes" && boulderGrading == .colour {
                        Text("Set up the circuit colours when you log your first session.").font(.footnote).foregroundStyle(.secondary)
                    }
                }

                Section {
                    ForEach(AddedGym.days, id: \.self) { day in
                        Toggle(Self.dayNames[day] ?? day, isOn: Binding(get: { open[day] ?? false }, set: { open[day] = $0 }))
                        if open[day] == true {
                            HStack {
                                DatePicker("From", selection: fromTime(day), displayedComponents: .hourAndMinute).labelsHidden()
                                Text("to")
                                DatePicker("To", selection: toTime(day), displayedComponents: .hourAndMinute).labelsHidden()
                            }
                        }
                    }
                    if let first = AddedGym.days.first(where: { open[$0] == true }), AddedGym.days.contains(where: { open[$0] != true }) {
                        Button("Same hours every day") { copyHours(from: first) }
                    }
                } header: {
                    Text("Opening hours (optional)")
                } footer: {
                    Text(hoursProblem ?? "In the gym's local time. Leave every day off if you don't know; once any day is on, days left off count as closed.")
                        .foregroundStyle(hoursProblem == nil ? Color.secondary : Color.warn)
                }

                Section {
                } footer: {
                    Text("Kept on this iPhone only, and in your logbook backups. It isn't sent anywhere or added to the gym list.")
                }
            }
            .navigationTitle(editing == nil ? "Add a gym" : "Edit gym")
            .inlineTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save).disabled(draft == nil) }
            }
        }
    }

    // MARK: Finding the gym

    private func useMyLocation() async {
        findError = nil; busy = true
        defer { busy = false }
        do {
            let l = try await LocationService.shared.currentLocation()
            // You're standing there, so the phone's own time zone is right if the lookup fails.
            if timeZone == nil { timeZone = TimeZone.current.identifier }
            await place(l.coordinate, move: true)
        } catch {
            findError = error.localizedDescription
        }
    }

    /// Apple Maps search: finds gyms by name as well as addresses and places.
    private func search() async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        let nameBeforeSearch = name
        findError = nil; busy = true
        defer { busy = false }
        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = q
        guard let item = try? await MKLocalSearch(request: request).start().mapItems.first else {
            findError = "Couldn't find that. Try the address or city, or tap the map."
            return
        }
        // Use the map's name even when the session picker supplied a search label.
        // Keep any edit made while the lookup was in flight; the field stays editable.
        if name == nameBeforeSearch,
           let n = item.name?.trimmingCharacters(in: .whitespacesAndNewlines), !n.isEmpty {
            name = n
        }
        if website.isEmpty, let u = item.url { website = u.absoluteString }
        fill(from: item.placemark, timeZone: item.timeZone)
        await place(item.placemark.coordinate, move: true, lookUp: false)
    }

    /// Moves the pin, then fills in the address, country and time zone from where it is.
    private func place(_ c: CLLocationCoordinate2D, move: Bool, lookUp: Bool = true) async {
        pin = c
        if move { camera = .region(MKCoordinateRegion(center: c, latitudinalMeters: 1500, longitudinalMeters: 1500)) }
        guard lookUp else { return }
        let marks = try? await CLGeocoder().reverseGeocodeLocation(CLLocation(latitude: c.latitude, longitude: c.longitude))
        guard pin?.latitude == c.latitude, pin?.longitude == c.longitude else { return }   // moved again meanwhile
        if let p = marks?.first { fill(from: p, timeZone: p.timeZone) }
    }

    private func fill(from p: CLPlacemark, timeZone tz: TimeZone?) {
        if let tz { timeZone = tz.identifier }
        let street = [p.subThoroughfare, p.thoroughfare].compactMap { $0 }.joined(separator: " ")
        if let v = take("area", area, p.locality ?? p.subAdministrativeArea ?? p.administrativeArea) { area = v }
        if let v = take("address", address, street.isEmpty ? nil : street) { address = v }
        if let v = take("postcode", postcode, p.postalCode) { postcode = v }
        if let v = take("country", country, p.country) { country = v }
    }

    /// The looked-up value for a field, unless you've typed something of your own there.
    private func take(_ key: String, _ current: String, _ value: String?) -> String? {
        guard let value, current.isEmpty || current == filled[key] else { return nil }
        filled[key] = value
        return value
    }

    // MARK: Fields

    @ViewBuilder
    private func gradingPicker(_ title: String, selection: Binding<GymGrading>, choices: [GymGrading], labels: Binding<String>) -> some View {
        Picker(title, selection: selection) {
            ForEach(choices) { Text($0.title).tag($0) }
        }
        let system = selection.wrappedValue
        if system.usesEditableLabels {
            TextField("Grade labels, easiest first", text: labels, axis: .vertical)
                .accessibilityLabel(title + " grade labels, easiest first")
            let parsed = GymGrading.parseLabels(labels.wrappedValue)
            if let parsed = parsed.isEmpty ? nil : parsed, !GymGrading.validLabels(parsed) {
                Text("Use up to 60 distinct labels, with at most 24 characters per label.").font(.footnote).foregroundStyle(Color.warn)
            } else {
                Text(system == .banana ? "Defaults to 0–20. Enter your branch’s labels separated by commas to change them."
                                        : "Separate labels with commas, for example 1, 2, 3, 4. Defaults to 1–10.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
    }

    private func priceField(_ title: String, text: Binding<String>) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text("£").foregroundStyle(.secondary)
            TextField("—", text: text)
                .keyboardType(.decimalPad).multilineTextAlignment(.trailing).frame(maxWidth: 90)
        }
    }

    private func fromTime(_ day: String) -> Binding<Date> {
        Binding(get: { from[day] ?? Self.date("09:00")! }, set: { from[day] = $0 })
    }

    private func toTime(_ day: String) -> Binding<Date> {
        Binding(get: { to[day] ?? Self.date("22:00")! }, set: { to[day] = $0 })
    }

    private func copyHours(from day: String) {
        let f = from[day] ?? Self.date("09:00")!, t = to[day] ?? Self.date("22:00")!
        for d in AddedGym.days { open[d] = true; from[d] = f; to[d] = t }
    }

    // MARK: Saving

    private var hoursProblem: String? {
        let bad = AddedGym.days.filter { open[$0] == true }.filter { d in
            let h = hoursText(d)
            return h[0] >= h[1]
        }
        return bad.isEmpty ? nil : "Closing time must be after opening time (" + bad.joined(separator: ", ") + ")."
    }

    private func hoursText(_ day: String) -> [String] {
        [Self.text(from[day] ?? Self.date("09:00")!), Self.text(to[day] ?? Self.date("22:00")!)]
    }

    private static func price(_ s: String) -> Double?? {
        let t = s.replacingOccurrences(of: "£", with: "").replacingOccurrences(of: ",", with: ".")
            .trimmingCharacters(in: .whitespaces)
        if t.isEmpty { return .some(nil) }
        guard let p = Double(t), p.isFinite, p >= 0, p < 1000 else { return nil }
        return .some(p)
    }

    /// The gym as entered, or nil while the name or pin is missing, or something is wrong.
    private var draft: AddedGym? {
        func clean(_ s: String) -> String? {
            let t = s.trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? nil : t
        }
        guard let pin, let adult = Self.price(adultPrice), let conc = Self.price(concessionPrice), hoursProblem == nil else { return nil }
        var site = clean(website)
        if let s = site, !s.contains("://") { site = "https://" + s }
        var hours: [String: [String]] = [:]
        for d in AddedGym.days where open[d] == true { hours[d] = hoursText(d) }
        let boulderLabels = boulderGrading.usesEditableLabels ? GymGrading.parseLabels(boulderLabels) : []
        let ropeLabels = ropeGrading.usesEditableLabels ? GymGrading.parseLabels(ropeLabels) : []
        let g = AddedGym(id: editing?.id ?? AddedGym.newID(avoiding: model.takenGymIDs),
                         name: clean(name) ?? "", area: clean(area) ?? "", country: clean(country),
                         postcode: clean(postcode)?.uppercased(), address: clean(address),
                         lat: pin.latitude, lng: pin.longitude, timeZone: timeZone, type: type,
                         adultPrice: adult, concessionPrice: conc, hours: hours,
                         website: site, notes: clean(notes),
                         boulderGrading: type == "ropes" ? editing?.boulderGrading : boulderGrading,
                         ropeGrading: ropeGrading == .french ? nil : ropeGrading,
                         boulderGradeLabels: boulderLabels.isEmpty ? nil : boulderLabels,
                         ropeGradeLabels: ropeLabels.isEmpty ? nil : ropeLabels)
        return g.isValid ? g : nil
    }

    private func save() {
        guard let g = draft else { return }
        model.saveAddedGym(g)
        onSave(g.id)
        dismiss()
    }

    // MARK: Helpers

    private static let dayNames = ["Mon": "Monday", "Tue": "Tuesday", "Wed": "Wednesday", "Thu": "Thursday",
                                   "Fri": "Friday", "Sat": "Saturday", "Sun": "Sunday"]

    private static func priceText(_ p: Double) -> String { String(format: "%.2f", p) }

    /// "14:05 (1 hour ahead of you)".
    static func localTimeText(_ tz: TimeZone, now: Date = .now) -> String {
        let f = DateFormatter(); f.timeZone = tz; f.dateFormat = "HH:mm"
        let diff = (tz.secondsFromGMT(for: now) - TimeZone.current.secondsFromGMT(for: now)) / 60
        guard diff != 0 else { return f.string(from: now) }
        let h = Double(abs(diff)) / 60
        let amount = h == h.rounded() ? "\(Int(h)) hour\(h == 1 ? "" : "s")" : String(format: "%.1f hours", h)
        return f.string(from: now) + " (\(amount) \(diff > 0 ? "ahead of" : "behind") you)"
    }

    /// "09:30" as a time today (only the hour and minute matter).
    private static func date(_ hhmm: String) -> Date? {
        let p = hhmm.split(separator: ":").compactMap { Int($0) }
        guard p.count == 2 else { return nil }
        return Calendar.current.date(bySettingHour: p[0], minute: p[1], second: 0, of: .now)
    }

    private static func text(_ d: Date) -> String {
        let c = Calendar.current.dateComponents([.hour, .minute], from: d)
        return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
    }
}

/// On the page of a gym you added: its local time if that differs, and Edit / Delete.
@MainActor
struct AddedGymSection: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss
    @Query private var sessions: [ClimbSession]
    let gym: Gym
    @State private var editing = false
    @State private var confirmDelete = false

    var body: some View {
        let visits = sessions.filter { $0.gymID == gym.id }.count
        Section {
            if let tz = model.addedGym(gym.id)?.zone, tz.identifier != TimeZone.current.identifier {
                LabeledContent("Local time there", value: AddGymView.localTimeText(tz))
            }
            Button { editing = true } label: { Label("Edit this gym", systemImage: "pencil") }
            Button(role: .destructive) { confirmDelete = true } label: { Label("Delete this gym", systemImage: "trash") }
        } header: {
            Text("Added by you")
        } footer: {
            Text("Opening hours are the gym's local time. Only on this iPhone; it isn't shared or added to the gym list for anyone else.")
        }
        .sheet(isPresented: $editing) {
            if let g = model.addedGym(gym.id) {
                AddGymView(editing: g, defaultBoulderGrading: model.colourScale(g.id).isEmpty ? .v : .colour)
            }
        }
        .confirmationDialog("Delete \(gym.name)?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete gym", role: .destructive) {
                model.deleteAddedGym(gym.id)
                dismiss()
            }
        } message: {
            Text(visits == 0 ? "Your note and rating for it go too."
                 : "Your \(visits) logged visit\(visits == 1 ? "" : "s") here stay in your logbook, shown as an unknown gym.")
        }
    }
}
