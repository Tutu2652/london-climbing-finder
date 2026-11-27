import SwiftUI
import CryptoKit
import ImageIO
import LinkPresentation
import UniformTypeIdentifiers

/// The catalogue logo, or the website icon for a gym you added.
@MainActor
struct GymLogo: View {
    @Environment(AppModel.self) private var model
    let gym: Gym
    var size: CGFloat = 40

    var body: some View {
        let logo = model.logo(for: gym)
        GymBadge(name: gym.shortName, size: size, backgroundHex: logo?.background,
                 source: logo.map { .catalogue($0.url) }
                    ?? (gym.isAddedByYou ? LogoCache.websiteURL(gym.website).map { .website($0) } : nil))
    }
}

@MainActor
struct GymBadge: View {
    enum Source: Hashable {
        case catalogue(String)
        case website(URL)
    }

    let name: String
    var size: CGFloat = 40
    var backgroundHex: String? = nil
    var source: Source?
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
            .task(id: source) {
                image = nil
                let loaded: UIImage?
                switch source {
                case .catalogue(let url): loaded = await LogoCache.shared.image(for: url)
                case .website(let url):
                    // Let typing settle before fetching the form's preview.
                    do { try await Task.sleep(for: .milliseconds(350)) } catch { return }
                    loaded = await LogoCache.shared.websiteIcon(for: url)
                case nil: return
                }
                if !Task.isCancelled { image = loaded }
            }
    }

    private var background: Color {
        if image != nil, let hex = backgroundHex { return Color(hex: hex) }
        return image == nil ? Color.brand : .white
    }

    private var initials: String {
        name.split(separator: " ").filter { $0.first?.isLetter == true && $0 != "The" }
            .prefix(2).compactMap { $0.first.map(String.init) }.joined().uppercased()
    }
}

/// Logos kept in memory and in the Caches folder, keyed by URL.
@MainActor
final class LogoCache {
    static let shared = LogoCache()
    private var memory: [String: UIImage] = [:]
    private var loading: [String: Task<UIImage?, Never>] = [:]
    private var websiteRetryAfter: [URL: Date] = [:]
    private let dir = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("logos", isDirectory: true)

    /// Logos stored in the repo (`assets/logos/…`) are built into the app; the
    /// website serves the same files, so a newer one can still be downloaded.
    static let site = URL(string: "https://london-climbing-finder.pages.dev/")!

    func image(for urlString: String) async -> UIImage? {
        guard let url = URL(string: urlString, relativeTo: Self.site) else { return nil }
        if let m = memory[urlString] { return m }
        if url.host == Self.site.host, url.path.hasPrefix("/assets/logos/"),
           let file = Bundle.main.resourceURL?.appendingPathComponent("logos/" + url.lastPathComponent),
           let img = UIImage(contentsOfFile: file.path) {
            memory[urlString] = img
            return img
        }
        return await cachedImage(key: urlString) {
            guard let (data, response) = try? await URLSession.shared.data(from: url),
                  (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            return data
        }
    }

    static func websiteURL(_ input: String?) -> URL? {
        guard let input = input?.trimmingCharacters(in: .whitespacesAndNewlines), !input.isEmpty else { return nil }
        let text = input.contains("://") ? input : "https://" + input
        guard var components = URLComponents(string: text),
              let scheme = components.scheme?.lowercased(), ["https", "http"].contains(scheme),
              let host = components.host, host.contains("."), components.user == nil, components.password == nil else { return nil }
        components.scheme = "https"
        components.query = nil
        components.fragment = nil
        return components.url
    }

    func websiteIcon(for url: URL) async -> UIImage? {
        if let retryAfter = websiteRetryAfter[url], retryAfter > .now { return nil }
        return await cachedImage(key: "website-icon:" + url.absoluteString) {
            let provider = LPMetadataProvider()
            provider.timeout = 8
            guard let metadata = try? await provider.startFetchingMetadata(for: url),
                  let icon = metadata.iconProvider,
                  let type = icon.registeredTypeIdentifiers.first(where: { UTType($0)?.conforms(to: .image) == true }) else {
                self.websiteRetryAfter[url] = .now.addingTimeInterval(300)
                return nil
            }
            let data: Data? = await withCheckedContinuation { continuation in
                icon.loadDataRepresentation(forTypeIdentifier: type) { data, _ in continuation.resume(returning: data) }
            }
            guard let data, let source = CGImageSourceCreateWithData(data as CFData, nil),
                  let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                    kCGImageSourceCreateThumbnailFromImageAlways: true,
                    kCGImageSourceCreateThumbnailWithTransform: true,
                    kCGImageSourceThumbnailMaxPixelSize: 128,
                  ] as CFDictionary), let png = UIImage(cgImage: thumbnail).pngData() else {
                self.websiteRetryAfter[url] = .now.addingTimeInterval(300)
                return nil
            }
            return png
        }
    }

    private func cachedImage(key: String, fetch: @escaping @MainActor () async -> Data?) async -> UIImage? {
        if let img = memory[key] { return img }
        if let task = loading[key] { return await task.value }
        let file = dir.appendingPathComponent(SHA256.hash(data: Data(key.utf8))
            .map { String(format: "%02x", $0) }.joined())
        let task = Task<UIImage?, Never> {
            if let d = try? Data(contentsOf: file), let img = UIImage(data: d) { return img }
            guard let d = await fetch(), let img = UIImage(data: d) else { return nil }
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            try? d.write(to: file, options: .atomic)
            return img
        }
        loading[key] = task
        let img = await task.value
        loading[key] = nil
        if let img { memory[key] = img }
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
