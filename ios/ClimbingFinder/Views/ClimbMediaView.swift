import SwiftUI
import SwiftData
import Photos
import PhotosUI
import AVKit

@MainActor
struct ClimbMediaRow: View {
    let climb: Climb
    var body: some View {
        HStack {
            Image(systemName: climb.result.icon)
                .foregroundStyle(climb.result == .flash ? Color.amber : Color.brand)
                .frame(width: 22)
            Text(climb.gradeLabel).font(.body.weight(.semibold).monospacedDigit())
            Text(climb.result.title).foregroundStyle(.secondary)
            Spacer()
            if climb.routePhotoAssetID != nil {
                Image(systemName: "photo").accessibilityLabel("Route photo linked")
            }
            if climb.videoAssetID != nil {
                Image(systemName: "video").accessibilityLabel("Video linked")
            }
            Text(climb.loggedAt.formatted(date: .omitted, time: .shortened))
                .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
        }
    }
}

@MainActor
struct ClimbMediaView: View {
    @Bindable var climb: Climb
    @State private var photoMedia = LinkedMediaLoader()
    @State private var videoMedia = LinkedMediaLoader()
    @State private var fullScreen: LinkedMediaKind?

    var body: some View {
        List {
            Section {
                LabeledContent("Outcome", value: climb.result.title)
                LabeledContent("Logged", value: climb.loggedAt.formatted(date: .abbreviated, time: .shortened))
            }
            LinkedMediaSection(assetID: $climb.routePhotoAssetID, kind: .photo, media: photoMedia) {
                fullScreen = .photo
            }
            LinkedMediaSection(assetID: $climb.videoAssetID, kind: .video, media: videoMedia) {
                fullScreen = .video
            }
            Section {
                Text("Only links are saved here. Originals stay in Photos. iCloud items may download for viewing. If you delete an original or remove Photos access, its link becomes unavailable. Links may need replacing after moving to another phone.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .navigationTitle(climb.gradeLabel + " · " + climb.result.title)
        .inlineTitle()
        // Present from the stable screen, not a lazily managed List section.
        .fullScreenCover(item: $fullScreen, onDismiss: {
            photoMedia.player?.pause()
            videoMedia.player?.pause()
        }) { kind in
            let media = kind == .photo ? photoMedia : videoMedia
            FullScreenClimbMedia(media: media, title: kind.title) {
                media.load(kind == .photo ? climb.routePhotoAssetID : climb.videoAssetID, kind: kind)
            }
        }
        .onDisappear {
            // Covering this screen must not tear down the viewer's media.
            if fullScreen == nil {
                photoMedia.stop()
                videoMedia.stop()
            }
        }
    }
}

private enum LinkedMediaKind: Identifiable {
    var id: Self { self }
    case photo, video
    var title: String { self == .photo ? "Route photo" : "Climb video" }
    var filter: PHPickerFilter { self == .photo ? .images : .videos }
    var mediaType: PHAssetMediaType { self == .photo ? .image : .video }
}

@MainActor
private struct LinkedMediaSection: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @Binding var assetID: String?
    let kind: LinkedMediaKind
    let media: LinkedMediaLoader
    let showFullScreen: () -> Void
    @State private var selection: PhotosPickerItem?
    @State private var picking = false
    @State private var managingAccess = false
    @State private var message: String?
    @State private var pendingID: String?
    @State private var authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)

    var body: some View {
        Section {
            if let image = media.image {
                Button(action: showFullScreen) {
                    Image(uiImage: image).resizable().scaledToFit()
                        .frame(maxWidth: .infinity, maxHeight: 360)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("View route photo full screen")
            }
            if let player = media.player {
                VideoPlayer(player: player).frame(height: 260)
            }
            if media.image != nil || media.player != nil {
                Button("Full screen", systemImage: "arrow.up.left.and.arrow.down.right") {
                    showFullScreen()
                }
            }
            if media.loading {
                ProgressView("Loading from Photos…")
                Button("Cancel loading") { media.stop() }
            }
            if let error = media.error {
                Text(error).font(.callout).foregroundStyle(.secondary)
            }
            if let message { Text(message).font(.callout).foregroundStyle(.secondary) }
            Button(assetID == nil ? "Link from Photos" : "Replace link", systemImage: "photo.on.rectangle") {
                Task { await choose() }
            }
            if assetID != nil {
                Button("Reload", systemImage: "arrow.clockwise") { reload() }
                Button("Remove link", systemImage: "link.badge.plus", role: .destructive) {
                    save(nil)
                }
            }
            if authorization == .limited {
                Button("Manage allowed photos & videos") { managingAccess = true }
            }
            if authorization == .denied || authorization == .restricted {
                Button("Open Photos access settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
            }
        } header: {
            Text(kind.title)
        } footer: {
            if authorization == .limited {
                Text("Allow access to the item first using Manage allowed photos & videos, then link it here.")
            }
        }
        .photosPicker(isPresented: $picking, selection: $selection, matching: kind.filter, photoLibrary: .shared())
        .onChange(of: selection) { _, item in
            guard let item else { return }
            guard let id = item.itemIdentifier else {
                message = "This item has no Photos link. Choose an item saved in your Photos library."
                return
            }
            pendingID = id
            linkSelection()
        }
        .sheet(isPresented: $managingAccess, onDismiss: {
            authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
            if pendingID != nil { linkSelection() } else { reload() }
        }) {
            LimitedPhotosAccessPicker { managingAccess = false }
        }
        .task(id: assetID) {
            if media.image == nil && media.player == nil { reload() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                let current = PHPhotoLibrary.authorizationStatus(for: .readWrite)
                let accessChanged = current != authorization
                authorization = current
                if pendingID != nil {
                    linkSelection()
                } else if accessChanged || (media.image == nil && media.player == nil) {
                    reload()
                }
            } else if phase == .background {
                media.stop()
            }
        }
    }

    private func choose() async {
        authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        if authorization == .notDetermined {
            authorization = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        }
        guard authorization == .authorized || authorization == .limited else {
            message = "Photos access is needed to keep a link to the original. You can change access in Settings."
            return
        }
        message = nil
        pendingID = nil
        selection = nil
        picking = true
    }

    private func linkSelection() {
        guard let id = pendingID else { return }
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject,
              asset.mediaType == kind.mediaType else {
            message = "This item isn't accessible. Allow it using Manage allowed photos & videos, or choose another item."
            return
        }
        save(id)
        pendingID = nil
    }

    private func save(_ id: String?) {
        let previous = assetID
        assetID = id
        do {
            try context.save()
            message = nil
            pendingID = nil
            reload()
        } catch {
            assetID = previous
            message = "Couldn't save this link. Please try again."
        }
    }

    private func reload() {
        media.load(assetID, kind: kind)
    }
}

/// Reuses the loaded media and player, retaining the video's playback position.
@MainActor
private struct FullScreenClimbMedia: View {
    @Environment(\.dismiss) private var dismiss
    let media: LinkedMediaLoader
    let title: String
    let reload: () -> Void

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if let image = media.image {
                ZoomableRoutePhoto(image: image)
                    .accessibilityLabel("Route photo. Pinch to zoom or double-tap to zoom in and out.")
            } else if let player = media.player {
                VideoPlayer(player: player)
            } else if media.loading {
                ProgressView("Loading from Photos…").tint(.white).foregroundStyle(.white)
            } else {
                VStack(spacing: 16) {
                    Text(media.error ?? "Media unavailable.").multilineTextAlignment(.center)
                    Button("Reload", action: reload).buttonStyle(.bordered)
                }
                .foregroundStyle(.white).padding()
            }
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            HStack {
                Text(title).font(.headline)
                Spacer()
                Button("Done") { dismiss() }
                    .font(.headline).padding(.vertical, 12)
                    .accessibilityLabel("Close full-screen viewer")
            }
            .foregroundStyle(.white).padding(.horizontal)
            .background(.black)
        }
        .preferredColorScheme(.dark)
    }
}

/// UIScrollView provides bounded pinch zoom and panning, plus double-tap zoom.
private struct ZoomableRoutePhoto: UIViewControllerRepresentable {
    let image: UIImage

    func makeUIViewController(context: Context) -> Controller {
        let controller = Controller()
        controller.image = image
        return controller
    }

    func updateUIViewController(_ controller: Controller, context: Context) {
        if controller.image !== image {
            controller.image = image
            controller.resetLayout()
        }
    }

    final class Controller: UIViewController, UIScrollViewDelegate {
        var image: UIImage? {
            didSet { imageView.image = image }
        }
        private let scrollView = UIScrollView()
        private let imageView = UIImageView()
        private var lastSize = CGSize.zero

        override func viewDidLoad() {
            super.viewDidLoad()
            view.backgroundColor = .black
            scrollView.delegate = self
            scrollView.minimumZoomScale = 1
            scrollView.maximumZoomScale = 6
            scrollView.showsHorizontalScrollIndicator = false
            scrollView.showsVerticalScrollIndicator = false
            scrollView.contentInsetAdjustmentBehavior = .never
            scrollView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            scrollView.frame = view.bounds
            imageView.contentMode = .scaleAspectFit
            view.addSubview(scrollView)
            scrollView.addSubview(imageView)
            let doubleTap = UITapGestureRecognizer(target: self, action: #selector(toggleZoom(_:)))
            doubleTap.numberOfTapsRequired = 2
            scrollView.addGestureRecognizer(doubleTap)
        }

        override func viewDidLayoutSubviews() {
            super.viewDidLayoutSubviews()
            guard view.bounds.size != lastSize else { return }
            resetLayout()
        }

        func resetLayout() {
            guard isViewLoaded, let image, image.size.width > 0, image.size.height > 0,
                  view.bounds.width > 0, view.bounds.height > 0 else { return }
            lastSize = view.bounds.size
            scrollView.setZoomScale(1, animated: false)
            let fit = min(view.bounds.width / image.size.width, view.bounds.height / image.size.height)
            imageView.frame = CGRect(origin: .zero, size: CGSize(width: image.size.width * fit, height: image.size.height * fit))
            scrollView.contentSize = imageView.bounds.size
            centerImage()
        }

        func viewForZooming(in scrollView: UIScrollView) -> UIView? { imageView }
        func scrollViewDidZoom(_ scrollView: UIScrollView) { centerImage() }

        private func centerImage() {
            let horizontal = max(0, (scrollView.bounds.width - scrollView.contentSize.width) / 2)
            let vertical = max(0, (scrollView.bounds.height - scrollView.contentSize.height) / 2)
            scrollView.contentInset = UIEdgeInsets(top: vertical, left: horizontal, bottom: vertical, right: horizontal)
        }

        @objc private func toggleZoom(_ gesture: UITapGestureRecognizer) {
            if scrollView.zoomScale > 1.01 {
                scrollView.setZoomScale(1, animated: true)
            } else {
                let point = gesture.location(in: imageView)
                let size = CGSize(width: scrollView.bounds.width / 3, height: scrollView.bounds.height / 3)
                scrollView.zoom(to: CGRect(x: point.x - size.width / 2, y: point.y - size.height / 2,
                                          width: size.width, height: size.height), animated: true)
            }
        }
    }
}

/// Fetches playback/display representations without exporting files into the app.
@MainActor
@Observable
private final class LinkedMediaLoader {
    var image: UIImage?
    var player: AVPlayer?
    var loading = false
    var error: String?
    private var requestID: PHImageRequestID = PHInvalidImageRequestID
    private var generation = UUID()
    private var playbackObservation: NSKeyValueObservation?
    private var audioActive = false

    func stop() {
        generation = UUID()
        if requestID != PHInvalidImageRequestID {
            PHImageManager.default().cancelImageRequest(requestID)
            requestID = PHInvalidImageRequestID
        }
        playbackObservation?.invalidate()
        playbackObservation = nil
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        if audioActive {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            audioActive = false
        }
        player = nil
        image = nil
        loading = false
    }

    private func preparePlayer(_ item: AVPlayerItem) {
        do {
            // Video audio should follow media volume, including in Silent mode.
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
        } catch {
            self.error = "Couldn't enable video audio. Tap Reload to try again."
        }
        let player = AVPlayer(playerItem: item)
        player.isMuted = false
        player.volume = 1
        let token = generation
        // Activate only on playback, so opening a route photo doesn't interrupt music.
        playbackObservation = player.observe(\.rate, options: [.new]) { [weak self] observed, _ in
            guard observed.rate > 0 else { return }
            Task { @MainActor in
                guard let self, self.generation == token, self.player?.rate ?? 0 > 0 else { return }
                do {
                    try AVAudioSession.sharedInstance().setActive(true)
                    self.audioActive = true
                } catch {
                    self.error = "Couldn't activate video audio. Pause other audio or calls, then tap Reload."
                }
            }
        }
        self.player = player
    }

    func load(_ id: String?, kind: LinkedMediaKind) {
        stop()
        error = nil
        guard let id else { return }
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard status == .authorized || status == .limited else {
            error = "Photos access is unavailable. Use Replace link to allow access, or update access in Settings."
            return
        }
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject,
              asset.mediaType == kind.mediaType else {
            error = "Original unavailable. It may have been deleted, moved to another library, or excluded from Photos access. You can replace or remove this link."
            return
        }
        loading = true
        let token = generation
        if kind == .video {
            let options = PHVideoRequestOptions()
            options.isNetworkAccessAllowed = true
            requestID = PHImageManager.default().requestPlayerItem(forVideo: asset, options: options) { [weak self] item, _ in
                Task { @MainActor in
                    guard let self, self.generation == token else { return }
                    self.loading = false
                    self.requestID = PHInvalidImageRequestID
                    if let item { self.preparePlayer(item) }
                    else { self.error = "Couldn't load the video. Check your connection if it's in iCloud, then tap Reload." }
                }
            }
        } else {
            let options = PHImageRequestOptions()
            options.isNetworkAccessAllowed = true
            options.deliveryMode = .highQualityFormat
            options.resizeMode = .fast
            requestID = PHImageManager.default().requestImage(for: asset, targetSize: CGSize(width: 1600, height: 1600), contentMode: .aspectFit, options: options) { [weak self] image, info in
                let degraded = (info?[PHImageResultIsDegradedKey] as? Bool) == true
                Task { @MainActor in
                    guard let self, self.generation == token, !degraded else { return }
                    self.loading = false
                    self.requestID = PHInvalidImageRequestID
                    self.image = image
                    if image == nil { self.error = "Couldn't load the photo. Check your connection if it's in iCloud, then tap Reload." }
                }
            }
        }
    }
}

/// Uses a real presenting controller so limited access can be updated in-app.
private struct LimitedPhotosAccessPicker: UIViewControllerRepresentable {
    let finished: () -> Void
    func makeUIViewController(context: Context) -> Controller {
        let controller = Controller()
        controller.finished = finished
        return controller
    }
    func updateUIViewController(_ controller: Controller, context: Context) {}
    final class Controller: UIViewController {
        var finished: (() -> Void)?
        private var presented = false
        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            guard !presented else { return }
            presented = true
            PHPhotoLibrary.shared().presentLimitedLibraryPicker(from: self) { [weak self] _ in
                Task { @MainActor in self?.finished?() }
            }
        }
    }
}
