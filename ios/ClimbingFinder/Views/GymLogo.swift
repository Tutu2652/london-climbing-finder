import SwiftUI
import CryptoKit

/// The gym's official logo (linked from gyms.json), saved on the phone after the
/// first download so it also shows offline. Gyms without one get a lettered badge.
@MainActor
struct GymLogo: View {
    @Environment(AppModel.self) private var model
    let gym: Gym
    var size: CGFloat = 40
    @State private var image: UIImage?

    var body: some View {
        RoundedRectangle(cornerRadius: size * 0.22)
            .fill(background)
            .overlay {
                if let image {
                    Image(uiImage: image).resizable().scaledToFit().padding(size * 0.08)
                } else {
                    Text(initials).font(.system(size: size * 0.38, weight: .bold)).foregroundStyle(.white)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: size * 0.22))
            .overlay(RoundedRectangle(cornerRadius: size * 0.22).strokeBorder(.quaternary))
            .frame(width: size, height: size)
            .accessibilityHidden(true)
            .task(id: model.logo(for: gym)?.url) { image = await LogoCache.shared.image(for: model.logo(for: gym)?.url) }
    }

    private var background: Color {
        if image != nil, let hex = model.logo(for: gym)?.background { return Color(hex: hex) }
        return image == nil ? Color.brand : .white
    }

    private var initials: String {
        gym.shortName.split(separator: " ").filter { $0.first?.isLetter == true && $0 != "The" }
            .prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}

/// Logos kept in memory and in the Caches folder, keyed by URL.
@MainActor
final class LogoCache {
    static let shared = LogoCache()
    private var memory: [String: UIImage] = [:]
    private var loading: [String: Task<UIImage?, Never>] = [:]
    private let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("logos", isDirectory: true)

    /// Logos stored in the repo (`assets/logos/…`) are built into the app; the
    /// website serves the same files, so a newer one can still be downloaded.
    static let site = URL(string: "https://london-climbing-finder.pages.dev/")!

    func image(for urlString: String?) async -> UIImage? {
        guard let urlString, let url = URL(string: urlString, relativeTo: Self.site) else { return nil }
        if let m = memory[urlString] { return m }
        if url.host == Self.site.host, url.path.hasPrefix("/assets/logos/"),
           let file = Bundle.main.resourceURL?.appendingPathComponent("logos/" + url.lastPathComponent),
           let img = UIImage(contentsOfFile: file.path) {
            memory[urlString] = img
            return img
        }
        if let t = loading[urlString] { return await t.value }
        let file = dir.appendingPathComponent(SHA256.hash(data: Data(urlString.utf8))
            .map { String(format: "%02x", $0) }.joined())
        let task = Task<UIImage?, Never> {
            if let d = try? Data(contentsOf: file), let img = UIImage(data: d) { return img }
            guard let (d, resp) = try? await URLSession.shared.data(from: url),
                  (resp as? HTTPURLResponse)?.statusCode == 200, let img = UIImage(data: d) else { return nil }
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try? d.write(to: file, options: .atomic)
            return img
        }
        loading[urlString] = task
        let img = await task.value
        loading[urlString] = nil
        if let img { memory[urlString] = img }
        return img
    }
}

extension Color {
    /// "#rrggbb"
    init(hex: String) {
        let v = UInt64(hex.trimmingCharacters(in: CharacterSet(charactersIn: "#")), radix: 16) ?? 0
        self.init(red: Double((v >> 16) & 0xff) / 255, green: Double((v >> 8) & 0xff) / 255, blue: Double(v & 0xff) / 255)
    }
}
