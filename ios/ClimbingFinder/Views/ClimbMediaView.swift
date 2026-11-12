import SwiftUI
import SwiftData
import Photos
import PhotosUI
import AVKit

@MainActor
struct ClimbMediaRow: View {
    let climb: Climb
    var body: some View {
        // One line: outcome icon, colour dot and the grade that counts, outcome, media, time.
        HStack(spacing: 8) {
            Image(systemName: climb.result.icon)
                .foregroundStyle(climb.result == .flash ? Color.amber : climb.result == .send ? Color.brand : .secondary)
                .frame(width: 22)
            ClimbGradeLabel(climb: climb)
            Text(climb.result.title).foregroundStyle(.secondary)
            if climb.isComp { Pill(text: "Comp", color: .amber) }
            Spacer(minLength: 8)
            if climb.routePhotoAssetID != nil {
                Image(systemName: "photo").foregroundStyle(.secondary).accessibilityLabel("Route photo linked")
            }
            if climb.videoAssetID != nil {
                Image(systemName: "video").foregroundStyle(.secondary).accessibilityLabel("Video linked")
            }
            Text(climb.loggedAt.formatted(date: .omitted, time: .shortened))
                .font(.caption.monospacedDigit()).foregroundStyle(.secondary)
        }
        .lineLimit(1)
    }
}

/// A colour dot (for colour climbs) and the grade that counts: "● V1–V3", "● V2 felt" or "V4".
struct ClimbGradeLabel: View {
    let climb: Climb
    var font: Font = .body

    var body: some View {
        HStack(spacing: 6) {
            if let colour = climb.colour { ColourSwatch(name: colour, size: 14) }
            if !climb.listGrade.isEmpty {
                Text(climb.listGrade).font(font.weight(.semibold).monospacedDigit())
            }
            if climb.listGradeIsFelt {
                Text("felt").font(.caption).foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([climb.colour, climb.listGrade, climb.listGradeIsFelt ? "felt" : nil]
            .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " "))
    }
}

@MainActor
struct ClimbMediaView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.modelContext) private var context
    @Environment(\.dismiss) private var dismiss
    @Bindable var climb: Climb
    /// Opened by tapping a video in Highlights: play it full screen, muted, straight away.
    var playVideo = false
    @State private var playedVideo = false
    @State private var photoMedia = LinkedMediaLoader()
    @State private var videoMedia = LinkedMediaLoader()
    @State private var fullScreen: LinkedMediaKind?
    @State private var photoLink = MediaLinkState()
    @State private var videoLink = MediaLinkState()

    /// Boulders and ropes only matter where the gym has ropes, or the climb is already a rope.
    private var showsDiscipline: Bool {
        climb.discipline == .rope || climb.session.flatMap { model.gym($0.gymID) }?.type.contains("ropes") == true
    }

    /// The gym's colours, with this climb's own colour (as logged) in place of the chart's
    /// if the chart has changed since, so picking it again changes nothing.
    private var colourChoices: [ColourGrade] {
        let chart = climb.session.map { model.colourScale($0.gymID) } ?? []
        guard let mine = climb.colourGrade else { return chart }
        return chart.contains { $0.name == mine.name } ? chart.map { $0.name == mine.name ? mine : $0 } : chart + [mine]
    }

    var body: some View {
        List {
            Section {
                Picker("Outcome", selection: $climb.resultRaw) {
                    ForEach(ClimbResult.allCases) { Label($0.title, systemImage: $0.icon).tag($0.rawValue) }
                }
                .pickerStyle(.segmented)
                LabeledContent("Logged", value: climb.loggedAt.formatted(date: .abbreviated, time: .shortened))
                if showsDiscipline {
                    Picker("Discipline", selection: Binding(get: { climb.discipline }, set: { climb.setDiscipline($0) })) {
                        ForEach(Discipline.allCases) { Text($0.title).tag($0) }
                    }
                }
                if climb.discipline == .boulder && !colourChoices.isEmpty {
                    Picker("Colour", selection: Binding(get: { climb.colourGrade }, set: { climb.setColour($0) })) {
                        Text("None (V grade)").tag(ColourGrade?.none)
                        ForEach(colourChoices) { c in
                            Label { Text(c.name) } icon: { ColourSwatch(name: c.name) }.tag(ColourGrade?.some(c))
                        }
                    }
                }
                if let c = climb.colourGrade {
                    LabeledContent("Range", value: c.hasRange ? c.range : "No V grade")
                    Picker("Felt grade", selection: $climb.feltGrade) {
                        Text("Not set").tag(Int?.none)
                        ForEach(climb.discipline.grades.indices, id: \.self) { Text(climb.discipline.grades[$0]).tag(Int?.some($0)) }
                    }
                } else {
                    SwitchRow(isOn: $climb.isComp) { Text("Comp route") }
                        .disabled(climb.isComp && !climb.isGraded)
                    Picker(climb.isComp ? "Felt grade" : "Grade", selection: $climb.grade) {
                        if climb.isComp { Text("No grade").tag(Climb.ungraded) }
                        ForEach(climb.discipline.grades.indices, id: \.self) { Text(climb.discipline.grades[$0]).tag($0) }
                    }
                }
            } footer: {
                if let c = climb.colourGrade {
                    if c.low != nil {
                        Text(climb.feltGrade != nil
                             ? "Stats count this climb as \(climb.gradeLabel), the grade you said it felt."
                             : "Stats count this climb as \(climb.gradeLabel), the bottom of the range. Set a felt grade if it felt different.")
                    } else {
                        Text(climb.isGraded
                             ? "This gym's colours have no V grades. Stats count this climb as \(climb.gradeLabel), the grade you said it felt."
                             : "This gym's colours have no V grades, so this climb counts as a send but not in grade stats. Set a felt grade to count it.")
                    }
                } else if climb.isComp && !climb.isGraded {
                    Text("Add a grade to count this climb in grade stats, or to untag it as comp.")
                }
            }
            LinkedMediaSection(assetID: $climb.routePhotoAssetID, kind: .photo, media: photoMedia, link: photoLink) {
                fullScreen = .photo
            }
            LinkedMediaSection(assetID: $climb.videoAssetID, kind: .video, media: videoMedia, link: videoLink) {
                fullScreen = .video
            }
            Section {
                Text("Only links are saved here. Originals stay in Photos. iCloud items may download for viewing. If you delete an original or remove Photos access, its link becomes unavailable. Links may need replacing after moving to another phone.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button("Delete climb", systemImage: "trash", role: .destructive) {
                    context.delete(climb)
                    dismiss()
                }
            }
        }
        .navigationTitle(climb.label + " · " + climb.result.title)
        .inlineTitle()
        .background(MediaPickerHost(link: photoLink, kind: .photo))
        .background(MediaPickerHost(link: videoLink, kind: .video))
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
        .task {
            guard playVideo, !playedVideo, climb.videoAssetID != nil else { return }
            playedVideo = true
            videoMedia.autoplayMuted = true
            videoMedia.load(climb.videoAssetID, kind: .video)
            fullScreen = .video
        }
        .onDisappear {
            // Covering this screen must not tear down the viewer's media.
            if fullScreen == nil && !videoMedia.inSystemFullScreen && !photoLink.picking && !videoLink.picking && !photoLink.managingAccess && !videoLink.managingAccess {
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
@Observable
private final class MediaLinkState {
    var selection: PhotosPickerItem?
    var picking = false
    var managingAccess = false
    var message: String?
    var pendingID: String?
    var authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
    var accessRevision = 0
    var libraryRevision = 0
    @ObservationIgnored private var watcher: LibraryWatcher?

    /// iOS's own "Select More Photos…" prompt changes the allowed items without
    /// backgrounding the app, so watch the library to retry a pending link.
    func watchLibrary() {
        guard watcher == nil else { return }
        watcher = LibraryWatcher { [weak self] in self?.libraryRevision += 1 }
    }
}

final class LibraryWatcher: NSObject, PHPhotoLibraryChangeObserver {
    private let changed: @MainActor () -> Void
    init(changed: @escaping @MainActor () -> Void) {
        self.changed = changed
        super.init()
        PHPhotoLibrary.shared().register(self)
    }
    deinit { PHPhotoLibrary.shared().unregisterChangeObserver(self) }
    func photoLibraryDidChange(_ changeInstance: PHChange) {
        Task { @MainActor in changed() }
    }
}

/// Stable presentation host outside the lazy List rows.
@MainActor
private struct MediaPickerHost: View {
    @Bindable var link: MediaLinkState
    let kind: LinkedMediaKind
    var body: some View {
        Color.clear.frame(width: 0, height: 0)
            .photosPicker(isPresented: $link.picking, selection: $link.selection,
                          matching: kind.filter, photoLibrary: .shared())
            .sheet(isPresented: $link.managingAccess, onDismiss: { link.accessRevision += 1 }) {
                LimitedPhotosAccessPicker { link.managingAccess = false }
            }
    }
}

@MainActor
private struct LinkedMediaSection: View {
    @Environment(\.modelContext) private var context
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.openURL) private var openURL
    @Binding var assetID: String?
    let kind: LinkedMediaKind
    let media: LinkedMediaLoader
    @Bindable var link: MediaLinkState
    let showFullScreen: () -> Void

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
                // Tap it for the controls, which include iOS's own full-screen button.
                InlineVideoPlayer(player: player, media: media).frame(height: 260)
            }
            if media.image != nil {
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
            if let message = link.message { Text(message).font(.callout).foregroundStyle(.secondary) }
            Button(assetID == nil ? "Link from Photos" : "Replace link", systemImage: "photo.on.rectangle") {
                Task { await choose() }
            }
            if assetID != nil {
                Button("Reload", systemImage: "arrow.clockwise") { reload() }
                Button("Remove link", systemImage: "link.badge.plus", role: .destructive) {
                    save(nil)
                }
            }
            if link.authorization == .limited {
                Button("Manage allowed photos & videos") { link.managingAccess = true }
            }
            if link.authorization == .denied || link.authorization == .restricted {
                Button("Open Photos access settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
            }
        } header: {
            Text(kind.title)
        } footer: {
            if link.authorization == .limited {
                Text("Allow access to the item first using Manage allowed photos & videos, then link it here.")
            }
        }
        .onChange(of: link.selection) { _, item in
            guard let item else { return }
            guard let id = item.itemIdentifier else {
                link.message = "This item has no Photos link. Choose an item saved in your Photos library."
                return
            }
            link.pendingID = id
            linkSelection()
        }
        .onChange(of: link.accessRevision) {
            link.authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
            if link.pendingID != nil { linkSelection() } else { reload() }
        }
        .onChange(of: link.libraryRevision) {
            // Only retry; reloading here would restart a playing video on every iCloud sync.
            link.authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
            if link.pendingID != nil { linkSelection() }
        }
        .task(id: assetID) {
            if media.image == nil && media.player == nil && !media.loading { reload() }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                let current = PHPhotoLibrary.authorizationStatus(for: .readWrite)
                let accessChanged = current != link.authorization
                link.authorization = current
                if link.pendingID != nil {
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
        link.authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        if link.authorization == .notDetermined {
            link.authorization = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        }
        guard link.authorization == .authorized || link.authorization == .limited else {
            link.message = "Photos access is needed to keep a link to the original. You can change access in Settings."
            return
        }
        link.watchLibrary()
        link.message = nil
        link.pendingID = nil
        link.selection = nil
        link.picking = true
    }

    private func linkSelection() {
        guard let id = link.pendingID else { return }
        guard let asset = PHAsset.fetchAssets(withLocalIdentifiers: [id], options: nil).firstObject,
              asset.mediaType == kind.mediaType else {
            link.message = "This item isn't accessible. Allow it using Manage allowed photos & videos, or choose another item."
            return
        }
        save(id)
        link.pendingID = nil
    }

    private func save(_ id: String?) {
        let previous = assetID
        assetID = id
        do {
            try context.save()
            link.message = nil
            link.pendingID = nil
            reload()
        } catch {
            assetID = previous
            link.message = "Couldn't save this link. Please try again."
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
                Button { dismiss() } label: {
                    Image(systemName: "xmark").font(.headline)
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("Close full-screen viewer")
            }
            .foregroundStyle(.white).padding(.horizontal)
            .background(.black)
        }
        .preferredColorScheme(.dark)
    }
}

/// The system player, unlike SwiftUI's VideoPlayer, shows a full-screen button with its controls.
private struct InlineVideoPlayer: UIViewControllerRepresentable {
    let player: AVPlayer
    let media: LinkedMediaLoader

    func makeCoordinator() -> Coordinator { Coordinator(media: media) }

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let controller = AVPlayerViewController()
        controller.player = player
        controller.videoGravity = .resizeAspect
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ controller: AVPlayerViewController, context: Context) {
        if controller.player !== player { controller.player = player }
    }

    /// Notes when the system's full screen covers the climb, so leaving the climb doesn't stop the video then.
    final class Coordinator: NSObject, AVPlayerViewControllerDelegate {
        let media: LinkedMediaLoader
        init(media: LinkedMediaLoader) { self.media = media }

        func playerViewController(_ controller: AVPlayerViewController,
                                  willBeginFullScreenPresentationWithAnimationCoordinator coordinator: UIViewControllerTransitionCoordinator) {
            Task { @MainActor in media.inSystemFullScreen = true }
        }

        func playerViewController(_ controller: AVPlayerViewController,
                                  willEndFullScreenPresentationWithAnimationCoordinator coordinator: UIViewControllerTransitionCoordinator) {
            // A swipe to close can be called off halfway.
            coordinator.animate(alongsideTransition: nil) { [media] context in
                guard !context.isCancelled else { return }
                Task { @MainActor in media.inSystemFullScreen = false }
            }
        }
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
    private var muteObservation: NSKeyValueObservation?
    private var volumeObservation: NSKeyValueObservation?
    private var audioActive = false
    /// The inline player's own full screen is showing.
    var inSystemFullScreen = false
    /// Start the next video playing, muted. Muted, it mixes with other audio rather than stopping it.
    var autoplayMuted = false

    func stop() {
        generation = UUID()
        if requestID != PHInvalidImageRequestID {
            PHImageManager.default().cancelImageRequest(requestID)
            requestID = PHInvalidImageRequestID
        }
        playbackObservation?.invalidate()
        playbackObservation = nil
        muteObservation?.invalidate()
        muteObservation = nil
        volumeObservation?.invalidate()
        volumeObservation = nil
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
        let muted = autoplayMuted
        autoplayMuted = false
        let player = AVPlayer(playerItem: item)
        player.isMuted = muted
        player.volume = 1
        setAudioCategory(muted: muted)
        let token = generation
        // Activate only on playback with sound, so opening a route photo or a muted video doesn't interrupt music.
        playbackObservation = player.observe(\.rate, options: [.new]) { [weak self] _, _ in
            Task { @MainActor in
                guard let self, self.generation == token else { return }
                self.activateAudioIfPlayingWithSound()
            }
        }
        // Unmuting in the player's controls switches to normal video audio.
        muteObservation = player.observe(\.isMuted, options: [.new]) { [weak self] observed, _ in
            Task { @MainActor in
                guard let self, self.generation == token else { return }
                self.setAudioCategory(muted: observed.isMuted)
                self.activateAudioIfPlayingWithSound()
            }
        }
        self.player = player
        if muted {
            watchVolumeButtons(token: token)
            player.play()
        }
    }

    /// Pressing volume up turns the sound on, as in Photos. iOS only reports the volume
    /// while the audio session is active, so activate it: muted, it's ambient, so music keeps playing.
    private func watchVolumeButtons(token: UUID) {
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setActive(true)
            audioActive = true
        } catch {
            return
        }
        volumeObservation = session.observe(\.outputVolume, options: [.old, .new]) { [weak self] _, change in
            guard let old = change.oldValue, let new = change.newValue, new > old else { return }
            Task { @MainActor in
                guard let self, self.generation == token, let player = self.player, player.isMuted else { return }
                player.isMuted = false
            }
        }
    }

    private func setAudioCategory(muted: Bool) {
        do {
            // With sound, video audio should follow media volume, including in Silent mode.
            if muted {
                try AVAudioSession.sharedInstance().setCategory(.ambient)
            } else {
                try AVAudioSession.sharedInstance().setCategory(.playback, mode: .moviePlayback)
            }
        } catch {
            self.error = "Couldn't enable video audio. Tap Reload to try again."
        }
    }

    private func activateAudioIfPlayingWithSound() {
        guard let player, player.rate > 0, !player.isMuted else { return }
        do {
            try AVAudioSession.sharedInstance().setActive(true)
            audioActive = true
        } catch {
            self.error = "Couldn't activate video audio. Pause other audio or calls, then tap Reload."
        }
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
struct LimitedPhotosAccessPicker: UIViewControllerRepresentable {
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
