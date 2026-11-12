import SwiftUI

/// The gym page's "Prices through the week": one row per day, each split into blocks of
/// one price across the opening hours. Tap a block for its times and what the rate is.
/// It shows the week of the date picked above, so bank holidays are priced correctly.
@MainActor
struct WeekPriceGrid: View {
    @Environment(AppModel.self) private var model
    let gym: Gym
    @State private var picked: PriceBand?

    /// Block colours, cheapest first: (background, text). Same as the website's gym pages.
    private static let ramp: [(Color, Color)] = [
        ("#75ca91", "#06210f"), ("#5aa877", "#06210f"), ("#2f7350", "#ffffff"),
        ("#2b5a4a", "#ffffff"), ("#2f4a4d", "#ffffff"), ("#343a47", "#e8ebf0"),
    ].map { (Color(hex: $0.0), Color(hex: $0.1)) }

    private static let dayWidth: CGFloat = 52
    private static let rowHeight: CGFloat = 30

    var body: some View {
        let days = model.dayMoment.week
        let week = days.map { model.pricer.dayBands(gym, $0, conc: model.concession) }
        let all = week.flatMap { $0 }
        if let cheapest = all.min(by: { $0.price < $1.price }) {
            let prices = Array(Set(all.map(\.price))).sorted()
            let first = all.map(\.start).min()! / 60 * 60
            let last = (all.map(\.end).max()! + 59) / 60 * 60
            let shown = picked.flatMap { all.contains($0) ? $0 : nil }
            VStack(alignment: .leading, spacing: 12) {
                Readout(band: shown ?? cheapest, cheapest: shown == nil)
                VStack(spacing: 3) {
                    HStack(spacing: 0) {
                        Color.clear.frame(width: Self.dayWidth, height: 1)
                        GeometryReader { geo in
                            ForEach(Array(stride(from: (first + 179) / 180 * 3, through: last / 60, by: 3)), id: \.self) { h in
                                let x = geo.size.width * CGFloat(h * 60 - first) / CGFloat(last - first)
                                if x < geo.size.width - 14 {
                                    Text("\(h)").font(.caption2).foregroundStyle(.secondary)
                                        .padding(.leading, 3)
                                        .overlay(alignment: .leading) { Rectangle().fill(.quaternary).frame(width: 1) }
                                        .offset(x: x)
                                }
                            }
                        }
                        .frame(height: 14)
                    }
                    .accessibilityHidden(true)
                    ForEach(Array(days.enumerated()), id: \.offset) { i, day in
                        HStack(spacing: 0) {
                            Text("\(day.weekday) \(day.day)")
                                .font(.caption.monospacedDigit())
                                .fontWeight(day == model.dayMoment ? .bold : .regular)
                                .foregroundStyle(model.pricer.isBankHoliday(day) ? Color.amber : day == model.dayMoment ? .primary : .secondary)
                                .frame(width: Self.dayWidth, alignment: .leading)
                            if week[i].isEmpty {
                                Text("Closed").font(.caption).foregroundStyle(.secondary)
                                    .frame(maxWidth: .infinity, minHeight: Self.rowHeight, alignment: .leading)
                            } else {
                                DayRow(bands: week[i], first: first, last: last, shown: shown,
                                       colour: { Self.colour($0, prices) }, pick: { picked = $0 })
                                    .frame(height: Self.rowHeight)
                            }
                        }
                    }
                }
            }
            .padding(.vertical, 4)
        } else {
            Text("No opening hours this week.").font(.callout).foregroundStyle(.secondary)
        }
    }

    /// Colour follows the price's rank this week: the cheapest is brightest, the dearest slate.
    private static func colour(_ price: Double, _ prices: [Double]) -> (Color, Color) {
        guard prices.count > 1, let i = prices.firstIndex(of: price) else { return ramp[ramp.count - 1] }
        return ramp[Int((Double(i * (ramp.count - 1)) / Double(prices.count - 1)).rounded())]
    }

    /// Which days of the shown week are bank holidays, for the section footer.
    static func bankHolidayNote(_ model: AppModel) -> String {
        let days = model.dayMoment.week.filter(model.pricer.isBankHoliday).map { "\($0.weekday) \($0.day)" }
        if days.isEmpty { return "" }
        return " " + days.joined(separator: " and ") + (days.count > 1 ? " are bank holidays" : " is a bank holiday") + ", so some deals don't apply."
    }
}

/// The picked block (or the week's cheapest) in words.
@MainActor
private struct Readout: View {
    let band: PriceBand
    let cheapest: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(cheapest ? "Cheapest this week" : "Selected").font(.caption).foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(formatPrice(band.price)).font(.title2.bold().monospacedDigit())
                Pill(text: band.label, color: band.kind.color)
            }
            Label("\(band.day.weekday) \(band.day.day) · \(hhmm(band.start))–\(hhmm(band.end))", systemImage: "clock")
                .labelStyle(.compact).font(.subheadline).foregroundStyle(.secondary)
        }
        .accessibilityElement(children: .combine)
    }
}

/// One day's blocks, laid out to scale between the week's earliest opening and latest closing.
@MainActor
private struct DayRow: View {
    let bands: [PriceBand]
    let first: Int, last: Int
    let shown: PriceBand?
    let colour: (Double) -> (Color, Color)
    let pick: (PriceBand) -> Void

    var body: some View {
        GeometryReader { geo in
            let perMinute = geo.size.width / CGFloat(last - first)
            ForEach(bands, id: \.self) { b in
                let width = max(CGFloat(b.end - b.start) * perMinute - 2, 4)
                let text = shortPrice(b.price)
                let (fill, ink) = colour(b.price)
                RoundedRectangle(cornerRadius: 4).fill(fill)
                    .overlay {
                        if width >= CGFloat(text.count) * 7 + 6 {
                            Text(text).font(.caption2.weight(.semibold).monospacedDigit()).foregroundStyle(ink).lineLimit(1)
                        }
                    }
                    .overlay {
                        if b == shown { RoundedRectangle(cornerRadius: 4).strokeBorder(ink, lineWidth: 2) }
                    }
                    .frame(width: width, height: geo.size.height)
                    .contentShape(Rectangle())
                    .onTapGesture { pick(b) }
                    .offset(x: CGFloat(b.start - first) * perMinute)
                    .accessibilityElement()
                    .accessibilityLabel("\(b.day.weekday) \(hhmm(b.start)) to \(hhmm(b.end)), \(formatPrice(b.price)), \(b.label)")
                    .accessibilityAddTraits(b == shown ? [.isButton, .isSelected] : .isButton)
            }
        }
    }
}
