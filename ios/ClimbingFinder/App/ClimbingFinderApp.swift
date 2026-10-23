import SwiftUI

@main
@MainActor
struct ClimbingFinderApp: App {
    @State private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(model)
                .tint(.brand)
                .task {
                    await model.refreshBankHolidays()
                    await model.refreshTravel()
                }
        }
    }
}

struct RootView: View {
    @AppStorage("tab") private var tab = "cheapest"

    var body: some View {
        TabView(selection: $tab) {
            stack { CheapestView() }
                .tabItem { Label("Cheapest", systemImage: "sterlingsign.circle") }.tag("cheapest")
            stack { MapTab() }
                .tabItem { Label("Map", systemImage: "map") }.tag("map")
            stack { GymsListView() }
                .tabItem { Label("Gyms", systemImage: "figure.climbing") }.tag("gyms")
            stack { DealsView() }
                .tabItem { Label("Deals", systemImage: "tag") }.tag("deals")
            stack { MoreView() }
                .tabItem { Label("More", systemImage: "ellipsis.circle") }.tag("more")
        }
    }

    private func stack<V: View>(@ViewBuilder _ content: () -> V) -> some View {
        NavigationStack {
            content().navigationDestination(for: Gym.self) { GymDetailView(gym: $0) }
        }
    }
}
