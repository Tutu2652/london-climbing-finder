import SwiftUI

extension Color {
    static let brand = Color(red: 0x5d / 255, green: 0xb0 / 255, blue: 0x7a / 255)
    static let amber = Color(red: 0xe2 / 255, green: 0xa1 / 255, blue: 0x3c / 255)
    static let peakPurple = Color(red: 0xa7 / 255, green: 0x7b / 255, blue: 0xc0 / 255)
    static let warn = Color(red: 0xc9 / 255, green: 0x6a / 255, blue: 0x5a / 255)
}

extension PriceKind {
    var color: Color {
        switch self {
        case .off: return .brand
        case .magic: return .amber
        case .peak, .none: return .peakPurple
        }
    }
}

/// Coloured capsule: green off-peak, amber super off-peak / deal, purple peak.
@MainActor
struct Pill: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 7).padding(.vertical, 2)
            .background(color.opacity(0.18), in: Capsule())
            .foregroundStyle(color)
            .lineLimit(1)
    }
}

@MainActor
struct OpenBadge: View {
    let state: OpenState?
    var body: some View {
        if let state { Pill(text: state.text, color: state.open ? .brand : .secondary) }
    }
}

@MainActor
struct VerifyFlag: View {
    let gym: Gym
    var body: some View {
        if !gym.verified {
            Text("verify").font(.caption2.weight(.semibold)).foregroundStyle(Color.warn)
        }
    }
}

@MainActor
struct Stars: View {
    let rating: Int
    var body: some View {
        Text(String(repeating: "★", count: rating) + String(repeating: "☆", count: 5 - rating))
            .font(.caption).foregroundStyle(Color.amber)
            .accessibilityLabel("\(rating) of 5 stars")
    }
}

/// Personal note + rating summary shown under a gym's name in lists.
@MainActor
struct NoteSummary: View {
    @Environment(AppModel.self) private var model
    let gymID: String
    var body: some View {
        if let r = model.ratings[gymID] { Stars(rating: r) }
        if let n = model.notes[gymID] {
            Text("Note: " + n).font(.caption).foregroundStyle(Color.brand).lineLimit(2)
        }
    }
}

/// Renders the data's `[label](https://url)` snippets as tappable links.
@MainActor
struct LinkedText: View {
    let source: String
    init(_ source: String) { self.source = source }
    var body: some View {
        if let a = try? AttributedString(markdown: source, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace)) {
            Text(a)
        } else {
            Text(source)
        }
    }
}

/// Footer-style explanation text under a list section.
@MainActor
struct Explainer: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View { Text(text).font(.footnote).foregroundStyle(.secondary) }
}

/// Icon and text sitting close together, for inline metadata like times and travel.
struct CompactLabelStyle: LabelStyle {
    func makeBody(configuration: Configuration) -> some View {
        HStack(spacing: 4) { configuration.icon; configuration.title }
    }
}

extension LabelStyle where Self == CompactLabelStyle {
    static var compact: CompactLabelStyle { CompactLabelStyle() }
}

extension View {
    /// Small-title navigation bars on iPhone; no-op elsewhere.
    func inlineTitle() -> some View {
        #if os(iOS)
        return navigationBarTitleDisplayMode(.inline)
        #else
        return self
        #endif
    }
}
