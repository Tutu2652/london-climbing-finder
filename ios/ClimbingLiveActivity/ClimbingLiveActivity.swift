import ActivityKit
import SwiftUI
import WidgetKit

@main
struct ClimbingWidgets: WidgetBundle {
    var body: some Widget { ClimbingLiveActivity() }
}

struct ClimbingLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ClimbingActivityAttributes.self) { context in
            VStack(alignment: .leading, spacing: 12) {
                Label(context.state.gym, systemImage: "figure.climbing").font(.headline).lineLimit(1)
                HStack {
                    SessionTimer(start: context.state.start).font(.title2.bold()).fixedSize()
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text("\(context.state.sends) sends").bold()
                        Text("\(context.state.attempts) attempts").font(.caption)
                    }
                }
                Text("Tap to return to your session").font(.caption).foregroundStyle(.secondary)
            }
            .padding()
            .activityBackgroundTint(Color(red: 0.08, green: 0.16, blue: 0.14))
            .activitySystemActionForegroundColor(.white)
            .foregroundStyle(.white)
            .widgetURL(context.attributes.url)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label("Climbing", systemImage: "figure.climbing").foregroundStyle(.mint)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text("\(context.state.sends) sends").bold()
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack {
                        Text(context.state.gym).lineLimit(1)
                        Spacer()
                        SessionTimer(start: context.state.start).multilineTextAlignment(.trailing).frame(width: 90, alignment: .trailing)
                    }
                }
            } compactLeading: {
                Image(systemName: "figure.climbing").foregroundStyle(.mint)
            } compactTrailing: {
                SessionTimer(start: context.state.start).multilineTextAlignment(.trailing).frame(width: 64, alignment: .trailing)
            } minimal: {
                Image(systemName: "figure.climbing").foregroundStyle(.mint)
            }
            .widgetURL(context.attributes.url)
        }
    }
}

/// A stopwatch that stays in h:mm:ss form; `style: .timer` can fall back to spelled-out
/// "20 hours, 11 minutes", which doesn't fit the compact Dynamic Island.
private struct SessionTimer: View {
    let start: Date
    var body: some View {
        Text(timerInterval: start...Date.distantFuture, countsDown: false)
            .monospacedDigit()
    }
}
