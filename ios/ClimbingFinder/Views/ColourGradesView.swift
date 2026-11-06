import SwiftUI

/// A small dot in a hold colour.
struct ColourSwatch: View {
    let name: String
    var size: CGFloat = 12

    var body: some View {
        Circle()
            .fill(Color(hex: HoldColour.hex(name)))
            .overlay(Circle().strokeBorder(Color.secondary.opacity(0.45), lineWidth: 1))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
    }
}

/// Set up the colours a gym grades its boulders by, and the V range each covers.
@MainActor
struct ColourScaleEditor: View {
    @Environment(AppModel.self) private var model
    let gymID: String
    @State private var scale: [ColourGrade] = []
    @State private var loaded = false

    var body: some View {
        let grades = Discipline.boulder.grades
        List {
            Section {
                ForEach($scale) { $c in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 8) {
                            ColourSwatch(name: c.name, size: 16)
                            Text(c.name).font(.headline)
                            Spacer()
                            Text(c.hasRange ? c.range : "No V grade").foregroundStyle(.secondary).monospacedDigit()
                        }
                        HStack {
                            Picker("From", selection: $c.low) {
                                Text("None").tag(Int?.none)
                                ForEach(grades.indices, id: \.self) { Text(grades[$0]).tag(Int?.some($0)) }
                            }
                            Picker("To", selection: $c.high) {
                                Text("None").tag(Int?.none)
                                ForEach(grades.indices, id: \.self) { Text(grades[$0]).tag(Int?.some($0)) }
                            }
                        }
                        .pickerStyle(.menu)
                        .font(.callout)
                    }
                    .padding(.vertical, 2)
                }
                .onDelete { scale.remove(atOffsets: $0) }
                .onMove { scale.move(fromOffsets: $0, toOffset: $1) }

                let unused = HoldColour.all.map(\.name).filter { n in !scale.contains { $0.name == n } }
                if !unused.isEmpty {
                    Menu {
                        ForEach(unused, id: \.self) { name in
                            Button(name) { add(name) }
                        }
                    } label: {
                        Label("Add a colour", systemImage: "plus.circle")
                    }
                }
            } header: {
                Text(scale.isEmpty ? "Colours" : "Easiest first")
            } footer: {
                Text("Copy the grade chart on the gym's wall. Each climb keeps the range it was logged with, so changing these after a reset won't rewrite past sessions. Climbs count in stats as the bottom of their range unless you set how they felt. Colours with no V grade count as sends, but not in grade stats.")
            }

            if model.hasOwnColourScale(gymID), case let chart = model.gymColourChart(gymID), !chart.isEmpty {
                Section {
                    Button("Use the gym's chart") { scale = chart }
                } footer: {
                    Text("You've changed these from the gym's chart in the app's data.")
                }
            }
        }
        .navigationTitle("Colour grades")
        .inlineTitle()
        .toolbar { if !scale.isEmpty { EditButton() } }
        .onAppear {
            guard !loaded else { return }
            scale = model.colourScale(gymID)
            loaded = true
        }
        .onChange(of: scale) { _, new in
            // A range has both ends or neither, and "To" can't be easier than "From":
            // fill in or move the other end rather than refuse the change.
            let fixed = new.map { c in
                var c = c
                switch (c.low, c.high) {
                case let (l?, nil): c.high = l
                case let (nil, h?): c.low = h
                case let (l?, h?) where h < l: c.high = l
                default: break
                }
                return c
            }
            if fixed != new { scale = fixed; return }
            model.setColourScale(fixed, for: gymID)
        }
    }

    private func add(_ name: String) {
        let r = ColourGrade.nextRange(after: scale)
        scale.append(ColourGrade(name: name, low: r.low, high: r.high))
    }
}

/// Sends at a gym by hold colour, in the order of that gym's scale.
@MainActor
struct ColourSendsSection: View {
    @Environment(AppModel.self) private var model
    let gymID: String
    let sends: [Climb]

    var body: some View {
        let coloured = sends.filter { $0.colour != nil }
        let order = model.colourScale(gymID).map(\.name)
        let names = Set(coloured.compactMap(\.colour)).sorted {
            (order.firstIndex(of: $0) ?? .max, $0) < (order.firstIndex(of: $1) ?? .max, $1)
        }
        Section {
            ForEach(names, id: \.self) { name in
                let cs = coloured.filter { $0.colour == name }
                let flashes = cs.filter { $0.result == .flash }.count
                HStack(spacing: 10) {
                    ColourSwatch(name: name)
                    Text(name)
                    if let range = model.colourScale(gymID).first(where: { $0.name == name })?.range, !range.isEmpty {
                        Text(range).font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer()
                    if flashes > 0 { Label("\(flashes)", systemImage: "bolt.fill").foregroundStyle(Color.amber) }
                    Label("\(cs.count - flashes)", systemImage: "checkmark").foregroundStyle(Color.brand)
                }
                .font(.callout)
                .labelStyle(.compact)
            }
            NavigationLink { ColourScaleEditor(gymID: gymID) } label: {
                Label(order.isEmpty ? "Set up colour grades" : "Edit colour grades", systemImage: "paintpalette")
            }
        } header: {
            Text(names.isEmpty ? "Colour grades" : "Sends by colour")
        } footer: {
            if order.isEmpty && names.isEmpty {
                Text("If this gym grades by hold colour, add its colours to log climbs by colour.")
            }
        }
    }
}
