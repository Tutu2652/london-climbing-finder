import SwiftUI

/// Toolbar menu for climber, type, zone and air-con filters.
@MainActor
struct FilterMenu: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Menu {
            Toggle(isOn: $model.favouritesOnly) { Label("Favourites only", systemImage: "heart") }
            Picker("Climber", selection: $model.concession) {
                Text("Adult").tag(false)
                Text("Student / concession").tag(true)
            }
            Picker("Type", selection: $model.typeFilter) {
                Text("Any type").tag("")
                Text("Bouldering").tag("bouldering")
                Text("Ropes").tag("ropes")
            }
            Picker("Zone", selection: $model.zoneFilter) {
                Text("Any zone").tag(Int?.none)
                ForEach(model.zones, id: \.self) { Text("Zone \($0)").tag(Int?.some($0)) }
            }
            Picker("Air-conditioning", selection: $model.acFilter) {
                Text("AC: all gyms").tag("")
                Text("Any AC").tag("ac")
                Text("Full AC only").tag("full")
            }
            if model.filtersActive {
                Button("Clear filters", role: .destructive) { model.clearFilters() }
            }
        } label: {
            Label("Filters", systemImage: model.filtersActive
                  ? "line.3.horizontal.decrease.circle.fill" : "line.3.horizontal.decrease.circle")
        }
    }
}

/// Adult vs student/concession prices. Shown wherever prices are, so it's never a surprise.
@MainActor
struct ClimberPicker: View {
    @Environment(AppModel.self) private var model

    var body: some View {
        @Bindable var model = model
        Picker("Climber", selection: $model.concession) {
            Text("Adult").tag(false)
            Text("Student / conc.").tag(true)
        }
        .pickerStyle(.segmented)
    }
}

/// Date, time and adult/concession controls shown at the top of Cheapest and gym pages.
@MainActor
struct WhenControls: View {
    @Environment(AppModel.self) private var model
    var showMode = true

    var body: some View {
        @Bindable var model = model
        // Picking a date or time stops the controls following the clock until you tap Now.
        let when = Binding(get: { model.when }, set: { model.pickWhen($0) })
        VStack(spacing: 10) {
            HStack {
                DatePicker("Date", selection: when, displayedComponents: .date).labelsHidden()
                DatePicker("Time", selection: when, displayedComponents: .hourAndMinute).labelsHidden()
                Spacer()
                Button("Now") { model.resetToNow() }
                    .buttonStyle(.bordered)
                    .disabled(!model.whenPinned)
            }
            ClimberPicker()
            if showMode {
                Picker("Cheapest mode", selection: $model.mode) {
                    ForEach(CheapestMode.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)
            }
            if model.filtersActive {
                HStack {
                    Text(filterSummary).font(.footnote).foregroundStyle(.secondary)
                    Spacer()
                    Button("Clear") { model.clearFilters() }.font(.footnote)
                }
            }
        }
    }

    private var filterSummary: String {
        var parts: [String] = []
        if model.favouritesOnly { parts.append("favourites") }
        if !model.typeFilter.isEmpty { parts.append(model.typeFilter) }
        if let z = model.zoneFilter { parts.append("zone \(z)") }
        if !model.acFilter.isEmpty { parts.append(model.acFilter == "full" ? "full AC" : "AC") }
        return "Showing " + parts.joined(separator: " · ")
    }
}
