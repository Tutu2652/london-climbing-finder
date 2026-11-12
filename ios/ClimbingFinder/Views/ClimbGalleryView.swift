import SwiftUI
import SwiftData
import Photos

// Highlights: every route photo and video linked to a logged climb, as a strip at the
// top of the logbook and a full grid. Tapping one opens that climb's own screen, where
// a missing original can be relinked; tapping a video also plays it full screen, muted,
// over that screen. Only thumbnails are loaded here.

/// Climbs that link a route photo or a video, newest first.
private let climbsWithMedia = FetchDescriptor<Climb>(
    predicate: #Predicate { $0.routePhotoAssetID != nil || $0.videoAssetID != nil },
    sortBy: [SortDescriptor(\.loggedAt, order: .reverse)])

/// Shared so the strip and the grid reuse each other's thumbnails.
private let thumbnailManager = PHCachingImageManager()
/// Big enough for a cell at 3x; small enough that Photos serves it from its own thumbnail cache.
private let thumbnailSize = CGSize(width: 360, height: 360)
private let thumbnailOptions: PHImageRequestOptions = {
    let o = PHImageRequestOptions()
    o.deliveryMode = .opportunistic
    o.resizeMode = .fast
    o.isNetworkAccessAllowed = true
    return o
}()

/// Finds the linked assets in Photos. It never asks for access itself: the full
/// gallery does that when it opens, like linking does on a climb's screen.
@MainActor
@Observable
final class ClimbMediaLibrary {
    private(set) var authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    private(set) var assets: [String: PHAsset] = [:]
    @ObservationIgnored private var ids: [String] = []
    @ObservationIgnored private var watcher: LibraryWatcher?

    var canRead: Bool { authorization == .authorized || authorization == .limited }

    func requestAccessIfNeeded() async {
        if PHPhotoLibrary.authorizationStatus(for: .readWrite) == .notDetermined {
            _ = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        }
    }

    /// Look the items up again: on appearing, after access changes, or when Photos changes.
    func refresh(_ items: [ClimbMediaItem]) {
        ids = Array(Set(items.map(\.assetID)))
        reload()
    }

    private func reload() {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        if status != authorization { authorization = status }
        // Fetching without access would prompt for it, so only look once it's granted.
        guard canRead, !ids.isEmpty else { assets = [:]; return }
        if watcher == nil { watcher = LibraryWatcher { [weak self] in self?.reload() } }
        var found: [String: PHAsset] = [:]
        PHAsset.fetchAssets(withLocalIdentifiers: ids, options: nil).enumerateObjects { asset, _, _ in
            found[asset.localIdentifier] = asset
        }
        if found != assets { assets = found }
        thumbnailManager.startCachingImages(for: Array(found.values), targetSize: thumbnailSize,
                                            contentMode: .aspectFill, options: thumbnailOptions)
    }

    /// The item's original, or nil if it's deleted, outside limited access, or the wrong kind.
    func asset(for item: ClimbMediaItem) -> PHAsset? {
        guard let asset = assets[item.assetID],
              asset.mediaType == (item.kind == .photo ? .image : .video) else { return nil }
        return asset
    }
}

// MARK: - Strip at the top of the logbook

/// Opens the full grid; the logbook registers where it goes.
struct HighlightsRoute: Hashable {}

/// A climb opened from Highlights. `playVideo` plays its video full screen, muted, over the climb.
struct OpenedClimb: Identifiable, Hashable {
    let climb: Climb
    let playVideo: Bool
    init(_ item: ClimbMediaItem) { climb = item.climb; playVideo = item.kind == .video }
    var id: PersistentIdentifier { climb.persistentModelID }
}

/// The ten newest photos and videos, with See all. Hidden when no climb has media.
@MainActor
struct HighlightsSection: View {
    @Environment(\.scenePhase) private var scenePhase
    @Query(climbsWithMedia) private var climbs: [Climb]
    @State private var library = ClimbMediaLibrary()
    let open: (OpenedClimb) -> Void

    var body: some View {
        let items = Array(climbMediaItems(climbs).prefix(10))
        if !items.isEmpty {
            Section {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(alignment: .top, spacing: 10) {
                        ForEach(items) { item in
                            Button { open(OpenedClimb(item)) } label: {
                                ClimbMediaCell(item: item, library: library).frame(width: 112)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 12)
                }
                .listRowInsets(EdgeInsets())
            } header: {
                HStack(alignment: .firstTextBaseline) {
                    Text("Highlights")
                    Spacer()
                    NavigationLink("See all", value: HighlightsRoute()).font(.subheadline)
                }
                .textCase(nil)
            }
            .onAppear { library.refresh(items) }
            .onChange(of: items.map(\.assetID)) { library.refresh(items) }
            .onChange(of: scenePhase) { _, phase in if phase == .active { library.refresh(items) } }
        }
    }
}

// MARK: - Full grid

@MainActor
struct ClimbGalleryView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @Query(climbsWithMedia) private var climbs: [Climb]
    @State private var library = ClimbMediaLibrary()
    @State private var kind = KindFilter.all
    @State private var gymID: String?
    @State private var sendsOnly = false
    @State private var managingAccess = false
    @State private var opened: OpenedClimb?

    enum KindFilter: String, CaseIterable, Identifiable {
        case all = "All", photos = "Photos", videos = "Videos"
        var id: Self { self }
    }

    var body: some View {
        let all = climbMediaItems(climbs)
        let shown = all.filter { item in
            (kind == .all || (kind == .photos) == (item.kind == .photo))
                && (gymID == nil || item.climb.session?.gymID == gymID)
                && (!sendsOnly || item.climb.result.isSend)
        }
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Picker("Show", selection: $kind) {
                    ForEach(KindFilter.allCases) { Text($0.rawValue).tag($0) }
                }
                .pickerStyle(.segmented)

                accessNote(missing: shown.contains { library.asset(for: $0) == nil })

                HStack(spacing: 8) {
                    Menu {
                        Picker("Gym", selection: $gymID) {
                            Text("All gyms").tag(String?.none)
                            ForEach(gyms(all), id: \.id) { Text($0.name).tag(String?.some($0.id)) }
                        }
                    } label: {
                        FilterChip(title: gymID.map { model.gym($0)?.shortName ?? "Unknown gym" } ?? "All gyms",
                                   systemImage: "building.2", on: gymID != nil, menu: true)
                    }
                    Button { sendsOnly.toggle() } label: {
                        FilterChip(title: "Sends only", systemImage: sendsOnly ? "checkmark.circle.fill" : "circle", on: sendsOnly, menu: false)
                    }
                    .accessibilityAddTraits(sendsOnly ? .isSelected : [])
                    Spacer(minLength: 0)
                }
                .buttonStyle(.plain)

                LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 10, alignment: .top)], spacing: 16) {
                    ForEach(shown) { item in
                        Button { opened = OpenedClimb(item) } label: {
                            ClimbMediaCell(item: item, library: library)
                        }
                        .buttonStyle(.plain)
                    }
                }

                if shown.isEmpty {
                    ContentUnavailableView(all.isEmpty ? "No photos or videos yet" : "Nothing matches",
                                           systemImage: "photo.on.rectangle",
                                           description: Text(all.isEmpty
                                                             ? "Open a climb in a session and link a route photo or video from Photos."
                                                             : "Try showing all gyms, or attempts too."))
                } else {
                    Text(summary(shown))
                        .font(.footnote).foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding()
        }
        .background(Color(.systemGroupedBackground))
        .navigationDestination(item: $opened) { ClimbMediaView(climb: $0.climb, playVideo: $0.playVideo) }
        .navigationTitle("Highlights")
        .inlineTitle()
        .task {
            await library.requestAccessIfNeeded()
            library.refresh(all)
        }
        // Coming back from a climb, its link (or Photos access) may have changed.
        .onAppear { library.refresh(all) }
        .onChange(of: all.map(\.assetID)) { library.refresh(all) }
        .onChange(of: scenePhase) { _, phase in if phase == .active { library.refresh(all) } }
        .sheet(isPresented: $managingAccess, onDismiss: { library.refresh(all) }) {
            LimitedPhotosAccessPicker { managingAccess = false }
        }
    }

    @ViewBuilder
    private func accessNote(missing: Bool) -> some View {
        switch library.authorization {
        case .denied, .restricted:
            note("Photos access is off, so your photos and videos can't be shown. Tap one to see its climb.") {
                Button("Open Photos access settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
            }
        case .limited where missing:
            note("Some items aren't in the photos you've allowed this app to see.") {
                Button("Manage allowed photos & videos") { managingAccess = true }
            }
        default:
            if missing && library.canRead {
                note("Some originals are missing from Photos. Tap one to relink or remove it.") { EmptyView() }
            }
        }
    }

    private func note<B: View>(_ text: String, @ViewBuilder button: () -> B) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(text).font(.footnote).foregroundStyle(.secondary)
            button().font(.footnote.weight(.semibold))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(.quaternary.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
    }

    /// Gyms that have media, by name.
    private func gyms(_ items: [ClimbMediaItem]) -> [Gym] {
        Set(items.compactMap { $0.climb.session?.gymID }).compactMap(model.gym).sorted { $0.name < $1.name }
    }

    private func summary(_ items: [ClimbMediaItem]) -> String {
        let photos = items.filter { $0.kind == .photo }.count, videos = items.count - photos
        let parts = [photos > 0 ? "\(photos) photo\(photos == 1 ? "" : "s")" : nil,
                     videos > 0 ? "\(videos) video\(videos == 1 ? "" : "s")" : nil].compactMap { $0 }
        let climbCount = Set(items.map { $0.climb.persistentModelID }).count
        return parts.joined(separator: " and ") + " from \(climbCount) climb\(climbCount == 1 ? "" : "s")"
    }
}

/// A rounded filter button: green when it's narrowing the grid.
private struct FilterChip: View {
    let title: String
    let systemImage: String
    let on: Bool
    let menu: Bool

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: systemImage).font(.caption.weight(.semibold))
            Text(title).lineLimit(1)
            if menu { Image(systemName: "chevron.down").font(.caption2.weight(.semibold)) }
        }
        .font(.subheadline.weight(.medium))
        .padding(.horizontal, 12).padding(.vertical, 7)
        .foregroundStyle(on ? Color.white : Color.primary)
        .background(on ? Color.brand : Color(.secondarySystemGroupedBackground), in: Capsule())
        .contentShape(Capsule())
    }
}

// MARK: - Cell

/// A square thumbnail with the climb under it: colour and grade, outcome, gym and date.
@MainActor
struct ClimbMediaCell: View {
    @Environment(AppModel.self) private var model
    let item: ClimbMediaItem
    let library: ClimbMediaLibrary

    var body: some View {
        let climb = item.climb
        let asset = library.asset(for: item)
        let gym = climb.session.flatMap { model.gym($0.gymID) }
        VStack(alignment: .leading, spacing: 3) {
            Color.clear
                .aspectRatio(1, contentMode: .fit)
                .overlay {
                    if let asset { MediaThumbnail(asset: asset) } else { placeholder }
                }
                .overlay(alignment: .bottomTrailing) {
                    if item.kind == .video { videoBadge(asset?.duration) }
                }
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .padding(.bottom, 3)
            HStack(spacing: 4) {
                ClimbGradeLabel(climb: climb, font: .subheadline)
                Spacer(minLength: 0)
                Image(systemName: climb.result.icon)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(climb.result == .flash ? Color.amber : climb.result == .send ? Color.brand : .secondary)
            }
            Text(gym?.shortName ?? "Unknown gym").font(.caption).lineLimit(1)
            Text(dateText(climb.loggedAt)).font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([item.kind == .photo ? "Route photo" : "Video",
                             asset == nil ? "unavailable" : nil,
                             [climb.colour, climb.listGrade].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " "),
                             climb.result.title, gym?.shortName, climb.loggedAt.formatted(date: .abbreviated, time: .omitted)]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", "))
        .accessibilityHint(item.kind == .video ? "Plays the video" : "Opens the climb")
        .accessibilityAddTraits(.isButton)
    }

    /// Shown for a deleted original, one outside limited access, or no access at all.
    private var placeholder: some View {
        ZStack {
            Rectangle().fill(.quaternary)
            VStack(spacing: 4) {
                Image(systemName: library.canRead ? "exclamationmark.triangle" : "lock")
                    .font(.title3)
                Text(library.canRead ? (item.kind == .photo ? "Photo missing" : "Video missing") : "No Photos access")
                    .font(.caption2.weight(.semibold))
                    .multilineTextAlignment(.center)
            }
            .foregroundStyle(.secondary)
            .padding(6)
        }
    }

    private func videoBadge(_ duration: TimeInterval?) -> some View {
        HStack(spacing: 3) {
            Image(systemName: "play.fill").font(.system(size: 8))
            if let duration {
                Text(Duration.seconds(duration.rounded()).formatted(.time(pattern: .minuteSecond)))
            }
        }
        .font(.caption2.weight(.semibold).monospacedDigit())
        .foregroundStyle(.white)
        .padding(.horizontal, 5).padding(.vertical, 2)
        .background(.black.opacity(0.55), in: Capsule())
        .padding(5)
    }

    private func dateText(_ date: Date) -> String {
        Calendar.current.isDate(date, equalTo: .now, toGranularity: .year)
            ? date.formatted(.dateTime.weekday(.abbreviated).day().month())
            : date.formatted(.dateTime.day().month().year())
    }
}

/// A thumbnail from Photos' cache: a quick low-quality image first, then a sharper one.
@MainActor
private struct MediaThumbnail: View {
    let asset: PHAsset
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Rectangle().fill(.quaternary)
            if let image {
                Image(uiImage: image).resizable().scaledToFill()
            }
        }
        .task(id: asset.localIdentifier) {
            image = nil
            for await next in thumbnails(asset) { image = next }
        }
    }

    private func thumbnails(_ asset: PHAsset) -> AsyncStream<UIImage> {
        AsyncStream { continuation in
            let request = thumbnailManager.requestImage(for: asset, targetSize: thumbnailSize, contentMode: .aspectFill,
                                                        options: thumbnailOptions) { image, info in
                if let image { continuation.yield(image) }
                if (info?[PHImageResultIsDegradedKey] as? Bool) != true { continuation.finish() }
            }
            continuation.onTermination = { _ in thumbnailManager.cancelImageRequest(request) }
        }
    }
}
