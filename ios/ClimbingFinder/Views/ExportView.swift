import SwiftUI

/// Exports gyms, with your notes, ratings and favourites, as a KML file that
/// Google My Maps can import. The map then shows up in Google Maps.
@MainActor
struct ExportView: View {
    @Environment(AppModel.self) private var model
    @AppStorage("exportOnlyMine") private var onlyMine = false
    @State private var file: URL?

    private var gyms: [Gym] {
        model.data.gyms.filter { g in
            !onlyMine || model.isFavourite(g.id) || model.notes[g.id] != nil || model.ratings[g.id] != nil
        }
    }

    var body: some View {
        List {
            Section {
                Toggle("Only favourites and gyms with notes", isOn: $onlyMine)
                LabeledContent("Gyms in the file", value: "\(gyms.count)")
                if let file, !gyms.isEmpty {
                    ShareLink(item: file) { Label("Export for Google My Maps", systemImage: "square.and.arrow.up") }
                }
            } footer: {
                Text("Each gym's pin includes your rating, note, favourite heart, prices and address, grouped by chain.")
            }
            Section("Getting it into Google Maps") {
                Text("1. Tap Export and save or AirDrop the file to a computer.")
                Text("2. On the computer, open mymaps.google.com, create a map, then choose Import on its layer and pick the file.")
                Text("3. In the Google Maps app the map appears under You → Maps.")
            }
            .font(.callout)
        }
        .navigationTitle("Export to Google Maps")
        .inlineTitle()
        .task(id: "\(onlyMine)\(model.notes.count)\(model.ratings.count)\(model.favourites.count)") { write() }
    }

    private func write() {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("London climbing gyms.kml")
        do {
            try kml().write(to: url, atomically: true, encoding: .utf8)
            file = url
        } catch {
            file = nil
        }
    }

    private func kml() -> String {
        var out = """
        <?xml version="1.0" encoding="UTF-8"?>
        <kml xmlns="http://www.opengis.net/kml/2.2"><Document><name>London climbing gyms</name>

        """
        for b in brandGroups(gyms) {
            out += "<Folder><name>\(esc(b.brand))</name>\n"
            for g in b.sites { out += placemark(g) }
            out += "</Folder>\n"
        }
        return out + "</Document></kml>\n"
    }

    private func placemark(_ g: Gym) -> String {
        var lines: [String] = []
        var mine: [String] = []
        if model.isFavourite(g.id) { mine.append("♥ Favourite") }
        if let r = model.ratings[g.id] { mine.append(String(repeating: "★", count: r) + String(repeating: "☆", count: 5 - r)) }
        if !mine.isEmpty { lines.append(mine.joined(separator: " · ")) }
        if let n = model.notes[g.id] { lines.append("My note: " + n) }
        lines.append("Peak " + formatPrice(g.payg.adultPeak) + " (" + formatPrice(g.payg.concessionPeak) + " conc)"
                     + (g.bestOffpeak(conc: false).map { " · deals from " + formatPrice($0) } ?? ""))
        lines.append(g.typeLabel + " · Zone \(g.zone)")
        if let a = g.address { lines.append(a) }
        if let w = g.website { lines.append(w) }
        return """
        <Placemark><name>\(esc(g.name))</name><description>\(esc(lines.joined(separator: "\n")))</description>\
        <Point><coordinates>\(g.lng),\(g.lat),0</coordinates></Point></Placemark>

        """
    }

    private func esc(_ s: String) -> String {
        s.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
    }
}
