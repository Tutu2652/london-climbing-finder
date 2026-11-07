import Foundation
import SwiftData

// Run with the macOS SDK; uses only an in-memory store, never the phone's data.
@main
struct LogbookChecks {
    @MainActor static func main() throws {
        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
                                          configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let context = container.mainContext
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        let session = ClimbSession(gymID: "test-gym", start: start, end: start.addingTimeInterval(3600), price: 17)
        context.insert(session)
        for (grade, result) in [(2, ClimbResult.flash), (3, .send), (5, .attempt)] {
            let climb = Climb(discipline: .boulder, grade: grade, result: result, at: start.addingTimeInterval(600))
            context.insert(climb)
            climb.session = session
        }
        session.climbs.first { $0.grade == 3 }!.videoAssetID = "test-video-reference"
        session.climbs.first { $0.grade == 3 }!.routePhotoAssetID = "test-route-reference"
        try context.save()
        assert(session.sends.count == 2)
        assert(session.hardest(.boulder) == 3)
        // Highlights: a climb with a photo and a video gives two items, newest climb first.
        let later = Climb(discipline: .boulder, grade: 1, result: .send, at: start.addingTimeInterval(1200))
        context.insert(later)
        later.session = session
        later.videoAssetID = "later-video"
        let media = climbMediaItems(session.climbs)
        assert(media.map(\.assetID) == ["later-video", "test-route-reference", "test-video-reference"])
        assert(media.map(\.kind) == [.video, .photo, .video])
        assert(media[1].climb === media[2].climb && media[1].id != media[2].id)
        context.delete(later)
        try context.save()
        assert(formatDuration(session.duration) == "1h 00m")
        var backup = try LogbookBackup.decoder.decode(LogbookBackup.self, from: LogbookBackup.encoder.encode(LogbookBackup([session])))
        assert(backup.version == 4)
        let linked = backup.sessions[0].climbs.first { $0.grade == 3 }!
        assert(linked.videoAssetID == "test-video-reference")
        assert(linked.routePhotoAssetID == "test-route-reference")
        // Old backups omit these fields entirely, not just as explicit nulls.
        var legacy = try JSONSerialization.jsonObject(with: LogbookBackup.encoder.encode(backup)) as! [String: Any]
        legacy["version"] = 1
        var legacySessions = legacy["sessions"] as! [[String: Any]]
        var legacyClimbs = legacySessions[0]["climbs"] as! [[String: Any]]
        for i in legacyClimbs.indices {
            legacyClimbs[i].removeValue(forKey: "videoAssetID")
            legacyClimbs[i].removeValue(forKey: "routePhotoAssetID")
        }
        legacySessions[0]["climbs"] = legacyClimbs
        legacy["sessions"] = legacySessions
        let decodedLegacy = try LogbookBackup.decoder.decode(LogbookBackup.self, from: JSONSerialization.data(withJSONObject: legacy))
        try decodedLegacy.validate()
        assert(decodedLegacy.sessions[0].climbs.allSatisfy { $0.videoAssetID == nil && $0.routePhotoAssetID == nil })
        backup.sessions.append(backup.sessions[0])
        let restoredContainer = try ModelContainer(for: ClimbSession.self, Climb.self,
                                                  configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let restoredContext = restoredContainer.mainContext
        let added = try backup.restore(into: restoredContext, existing: [])
        assert(added == 1, "Duplicate records must not inflate visits")
        try restoredContext.save()
        let restored = try restoredContext.fetch(FetchDescriptor<ClimbSession>())
        assert(restored.count == 1 && restored[0].climbs.count == 3)
        assert(restored[0].climbs.first { $0.grade == 3 }?.videoAssetID == "test-video-reference")
        assert(restored[0].climbs.first { $0.grade == 3 }?.routePhotoAssetID == "test-route-reference")
        let repeated = try backup.restore(into: restoredContext, existing: restored)
        assert(repeated == 0)
        var invalid = backup
        invalid.sessions[0].climbs[0].grade = 999
        do {
            _ = try invalid.restore(into: restoredContext, existing: [])
            assertionFailure("Invalid grades must be rejected")
        } catch LogbookBackup.RestoreError.invalidRecord { }
        invalid = backup
        invalid.version = 99
        do {
            try invalid.validate()
            assertionFailure("Unknown versions must be rejected")
        } catch LogbookBackup.RestoreError.unsupportedVersion { }
        backup.sessions = [backup.sessions[0]]
        backup.sessions[0].id = UUID()
        backup.sessions[0].end = nil
        let activeAdded = try backup.restore(into: restoredContext, existing: restored)
        assert(activeAdded == 1)
        let all = try restoredContext.fetch(FetchDescriptor<ClimbSession>())
        let recovered = all.first { $0.id == backup.sessions[0].id }!
        assert(!recovered.isActive && recovered.duration == 600)
        restoredContext.delete(recovered)
        try restoredContext.save()
        let remainingClimbs = try restoredContext.fetchCount(FetchDescriptor<Climb>())
        assert(remainingClimbs == 3)
        try compChecks()
        try colourChecks()
        try tallyChecks()
        print("Logbook checks passed: sends, duration, backup round trip, deduplication, validation, active-session recovery, cascade deletion, media references, legacy backups, comp routes, colour grades, log order, editing, felt grades, tally order")
    }

    @MainActor static func tallyChecks() throws {
        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
                                          configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let context = container.mainContext
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        func climbs(_ cs: [(ColourGrade, ClimbResult)]) -> [Climb] {
            cs.map { let c = Climb(colour: $0.0, result: $0.1, at: start); context.insert(c); return c }
        }
        func order(_ cs: [Climb], _ scale: [ColourGrade]) -> [String] {
            tallyGroups(cs, scale: scale).map { $0[0].colour ?? $0[0].label }
        }
        // A gym that only ranks its colours (easiest first): hardest colour first,
        // not alphabetical, and a colour that's left the scale goes last.
        let ranked = ["White", "Red", "Blue", "Teal"].map { ColourGrade(name: $0) }
        let mixed = climbs([(ranked[2], .send), (ranked[0], .flash), (ColourGrade(name: "Grey"), .send),
                            (ranked[3], .attempt), (ranked[1], .send), (ranked[2], .send)])
        assert(order(mixed, ranked) == ["Teal", "Blue", "Red", "White", "Grey"], "\(order(mixed, ranked))")
        assert(tallyGroups(mixed, scale: ranked)[1].count == 2, "Same colour is one row")
        // Ranges still come first; the scale only breaks ties (VB and V0–V1 both start at V0).
        let chart = [ColourGrade(name: "Black", low: 0, high: 0), ColourGrade(name: "Pink", low: 0, high: 1),
                     ColourGrade(name: "Blue", low: 1, high: 3)]
        let ranged = climbs([(chart[0], .send), (chart[2], .send), (chart[1], .send)])
        let plain = Climb(discipline: .boulder, grade: 2, result: .send, at: start)
        context.insert(plain)
        assert(order(ranged + [plain], chart) == ["V2", "Blue", "Pink", "Black"], "\(order(ranged + [plain], chart))")
    }

    @MainActor static func compChecks() throws {
        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
                                          configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let context = container.mainContext
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        let session = ClimbSession(gymID: "comp-gym", start: start, end: start.addingTimeInterval(3600), price: nil)
        context.insert(session)
        for (grade, comp) in [(2, false), (6, true), (Climb.ungraded, true)] {
            let climb = Climb(discipline: .boulder, grade: grade, result: .send, at: start, comp: comp)
            context.insert(climb)
            climb.session = session
        }
        try context.save()
        let ungraded = session.climbs.first { !$0.isGraded }!
        assert(ungraded.isComp && ungraded.label == "Comp" && ungraded.gradeLabel == "–")
        assert(session.climbs.first { $0.grade == 6 }!.label == "Comp V6")
        assert(session.sends.count == 3, "Ungraded comp sends still count as sends")
        assert(session.hardest(.boulder) == 6, "Felt grades count; ungraded climbs don't")
        let backup = try LogbookBackup.decoder.decode(LogbookBackup.self, from: LogbookBackup.encoder.encode(LogbookBackup([session])))
        try backup.validate()
        let restoredContainer = try ModelContainer(for: ClimbSession.self, Climb.self,
                                                  configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        _ = try backup.restore(into: restoredContainer.mainContext, existing: [])
        let restored = try restoredContainer.mainContext.fetch(FetchDescriptor<Climb>())
        assert(restored.filter(\.isComp).count == 2 && restored.contains { $0.isComp && $0.grade == Climb.ungraded })
        // Only comp climbs may be ungraded.
        var invalid = backup
        let i = invalid.sessions[0].climbs.firstIndex { $0.grade == Climb.ungraded }!
        invalid.sessions[0].climbs[i].isComp = false
        do {
            try invalid.validate()
            assertionFailure("Ungraded non-comp climbs must be rejected")
        } catch LogbookBackup.RestoreError.invalidRecord { }
    }

    @MainActor static func colourChecks() throws {
        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
                                          configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let context = container.mainContext
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        let session = ClimbSession(gymID: "colour-gym", start: start, end: start.addingTimeInterval(3600), price: nil)
        context.insert(session)
        let blue = ColourGrade(name: "Blue", low: 2, high: 3)
        let red = ColourGrade(name: "Red", low: 4, high: 6)
        assert(blue.range == "V2–V3" && ColourGrade(name: "Black", low: 7, high: 7).range == "V7")
        for (colour, result) in [(blue, ClimbResult.flash), (red, .send), (red, .attempt)] {
            let climb = Climb(colour: colour, result: result, at: start)
            context.insert(climb)
            climb.session = session
        }
        try context.save()
        let r = session.climbs.first { $0.colour == "Red" && $0.result == .send }!
        assert(r.grade == 4 && r.label == "Red" && r.colourDetail == "V4–V6" && !r.hasFeltGrade)
        assert(session.hardest(.boulder) == 4, "Colour climbs count as the bottom of their range")
        r.grade = 5
        assert(r.hasFeltGrade && r.colourDetail == "V4–V6 · felt V5")
        assert(session.hardest(.boulder) == 5, "A felt grade replaces the bottom of the range")

        // Scales: validity and the default range for a new colour.
        assert(ColourGrade.isValidScale([blue, red]))
        assert(!ColourGrade.isValidScale([blue, blue]), "Each colour appears once")
        assert(!ColourGrade.isValidScale([ColourGrade(name: "Green", low: 3, high: 1)]))
        assert(ColourGrade.nextRange(after: []) == (0, 1))
        assert(ColourGrade.nextRange(after: [blue, red]) == (7, 8))
        assert(ColourGrade.nextRange(after: [ColourGrade(name: "Black", low: 16, high: 17)]) == (17, 17))

        // Backup round trip keeps colours, the range as logged, and the gym's scale.
        let scales = ["colour-gym": [blue, red]]
        let backup = try LogbookBackup.decoder.decode(LogbookBackup.self,
                                                      from: LogbookBackup.encoder.encode(LogbookBackup([session], colourScales: scales)))
        try backup.validate()
        assert(backup.colourScales == scales)
        let restoredContainer = try ModelContainer(for: ClimbSession.self, Climb.self,
                                                  configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        _ = try backup.restore(into: restoredContainer.mainContext, existing: [])
        let restored = try restoredContainer.mainContext.fetch(FetchDescriptor<Climb>())
        let felt = restored.first { $0.colour == "Red" && $0.result == .send }!
        assert(felt.colourGrade == red && felt.grade == 5)
        assert(restored.filter { $0.colour == "Blue" }.allSatisfy { $0.colourGrade == blue && $0.grade == 2 })
        // Restoring keeps a scale you already have and adds the rest.
        let mine = ["colour-gym": [red], "other-gym": [blue]]
        let merged = backup.mergedColourScales(into: mine)
        assert(merged["colour-gym"] == [red] && merged["other-gym"] == [blue])
        assert(backup.mergedColourScales(into: [:]) == scales)

        // A version 3 file has no colour fields and still restores.
        var legacy = try JSONSerialization.jsonObject(with: LogbookBackup.encoder.encode(LogbookBackup([], colourScales: [:]))) as! [String: Any]
        legacy["version"] = 3
        assert(legacy["colourScales"] == nil)
        try LogbookBackup.decoder.decode(LogbookBackup.self, from: JSONSerialization.data(withJSONObject: legacy)).validate()

        // Half-filled colour fields, ropes and broken scales are rejected.
        let i = backup.sessions[0].climbs.firstIndex { $0.colour != nil }!
        var invalid = backup
        invalid.sessions[0].climbs[i].colourHigh = nil
        do { try invalid.validate(); assertionFailure("Partial colour fields must be rejected") } catch LogbookBackup.RestoreError.invalidRecord { }
        invalid = backup
        invalid.sessions[0].climbs[i].colourLow = 9
        do { try invalid.validate(); assertionFailure("Inverted ranges must be rejected") } catch LogbookBackup.RestoreError.invalidRecord { }
        invalid = backup
        invalid.sessions[0].climbs[i].discipline = Discipline.rope.rawValue
        do { try invalid.validate(); assertionFailure("Colour grades are for boulders") } catch LogbookBackup.RestoreError.invalidRecord { }
        invalid = backup
        invalid.colourScales = ["colour-gym": [blue, blue]]
        do { try invalid.validate(); assertionFailure("Duplicate colours must be rejected") } catch LogbookBackup.RestoreError.invalidRecord { }
        try rankedColourChecks()
        try editingChecks()
        try feltGradeChecks()
    }

    /// Picking the bottom of the range as the felt grade still counts as a felt grade.
    @MainActor static func feltGradeChecks() throws {
        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
                                          configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let context = container.mainContext
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        let session = ClimbSession(gymID: "felt-gym", start: start, end: start.addingTimeInterval(3600), price: nil)
        context.insert(session)
        let purple = ColourGrade(name: "Purple", low: 3, high: 5)
        let c = Climb(colour: purple, result: .send, at: start)
        context.insert(c); c.session = session
        assert(c.feltGrade == nil && !c.hasFeltGrade && c.colourDetail == "V3–V5")
        c.feltGrade = 3
        assert(c.grade == 3 && c.hasFeltGrade && c.colourDetail == "V3–V5 · felt V3", "Felt V3 shows, though it's the bottom")
        assert(c.listGrade == "V3" && c.listGradeIsFelt)
        assert(Climb(discipline: .boulder, grade: 4, result: .flash).listGrade == "V4")
        c.feltGrade = nil
        assert(c.grade == 3 && !c.hasFeltGrade && c.colourDetail == "V3–V5")
        assert(c.listGrade == "V3–V5" && !c.listGradeIsFelt)
        c.feltGrade = 4
        c.setColour(ColourGrade(name: "White", low: 4, high: 6))
        assert(!c.hasFeltGrade && c.grade == 4, "A new colour starts from its own range")
        let ranked = Climb(colour: ColourGrade(name: "Yellow", low: nil, high: nil), result: .send, at: start)
        context.insert(ranked); ranked.session = session
        ranked.feltGrade = 2
        assert(ranked.grade == 2 && ranked.colourDetail == "felt V2")
        ranked.feltGrade = nil
        assert(ranked.grade == Climb.ungraded && ranked.colourDetail == nil && ranked.listGrade == "")
        // Old climbs (no flag) still count a moved grade as felt.
        let old = Climb(colour: purple, result: .send, at: start)
        old.grade = 5
        assert(old.hasFeltGrade && old.feltGrade == 5)

        c.feltGrade = 4
        try context.save()
        let backup = try LogbookBackup.decoder.decode(LogbookBackup.self, from: LogbookBackup.encoder.encode(LogbookBackup([session])))
        try backup.validate()
        assert(backup.sessions[0].climbs.contains { $0.feltGradeSet == true })
        assert(backup.sessions[0].climbs.contains { $0.colour == "Yellow" && $0.feltGradeSet == nil }, "Only written when set")
        let restoredContainer = try ModelContainer(for: ClimbSession.self, Climb.self,
                                                  configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        _ = try backup.restore(into: restoredContainer.mainContext, existing: [])
        let restored = try restoredContainer.mainContext.fetch(FetchDescriptor<Climb>())
        assert(restored.contains { $0.colour == "White" && $0.feltGrade == 4 })
        var invalid = backup
        let i = invalid.sessions[0].climbs.firstIndex { $0.colour == "Yellow" }!
        invalid.sessions[0].climbs[i].colour = nil
        invalid.sessions[0].climbs[i].grade = 2
        invalid.sessions[0].climbs[i].feltGradeSet = true
        do { try invalid.validate(); assertionFailure("Felt grades are for colour climbs") } catch LogbookBackup.RestoreError.invalidRecord { }
    }

    /// Log order (so Undo removes the last tap) and changing a logged climb.
    @MainActor static func editingChecks() throws {
        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
                                          configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let context = container.mainContext
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        let end = start.addingTimeInterval(3600)
        let past = ClimbSession(gymID: "edit-gym", start: start, end: end, price: nil)
        context.insert(past)
        // In a past session every tap used to land on the end time, so "last" was a coin toss.
        var logged: [Climb] = []
        for g in [3, 1, 2] {
            let c = Climb(discipline: .boulder, grade: g, result: .send, at: past.nextLogTime(now: end.addingTimeInterval(86_400)))
            context.insert(c)
            c.session = past
            logged.append(c)
        }
        assert(past.climbs.map(\.loggedAt).sorted() == logged.map(\.loggedAt), "Each tap is logged after the last")
        assert(Set(past.climbs.map(\.loggedAt)).count == 3)
        assert(past.lastClimb === logged[2] && past.lastClimb?.grade == 2, "Undo removes the last tap")
        // A live session: two taps in the same instant still get an order.
        let live = ClimbSession(gymID: "edit-gym", start: start, price: nil)
        context.insert(live)
        let a = Climb(discipline: .boulder, grade: 4, result: .send, at: live.nextLogTime(now: end))
        context.insert(a); a.session = live
        let b = Climb(discipline: .boulder, grade: 5, result: .send, at: live.nextLogTime(now: end))
        context.insert(b); b.session = live
        assert(a.loggedAt == end && b.loggedAt == end.addingTimeInterval(1) && live.lastClimb === b)

        // Changing colour, back to V grade, and discipline.
        let blue = ColourGrade(name: "Blue", low: 2, high: 3)
        let c = logged[0]
        c.isComp = true
        c.setColour(blue)
        assert(c.colourGrade == blue && c.grade == 2 && !c.isComp, "A colour climb counts as the bottom of the range")
        c.grade = 3
        c.setColour(blue)
        assert(c.grade == 3, "Picking the same colour keeps the felt grade")
        c.setColour(nil)
        assert(c.colour == nil && c.colourLow == nil && c.grade == 3, "Back to V grade keeps the grade it counted as")
        c.setColour(ColourGrade(name: "White", low: nil, high: nil))
        assert(c.grade == Climb.ungraded)
        c.setColour(nil)
        assert(c.grade == 0 && c.isGraded, "A V-graded climb always has a grade")
        c.grade = 16
        c.setDiscipline(.rope)
        assert(c.discipline == .rope && c.gradeLabel == "8a")
        c.grade = 20
        c.setDiscipline(.boulder)
        assert(c.discipline == .boulder && c.grade == 17, "Grades past the top clamp to it")
        c.setColour(blue)
        c.setDiscipline(.rope)
        assert(c.colour == nil && c.discipline == .rope, "Ropes have no colours")
        let comp = Climb(discipline: .boulder, grade: Climb.ungraded, result: .send, comp: true)
        comp.setDiscipline(.rope)
        assert(comp.grade == Climb.ungraded && comp.isComp, "An ungraded comp climb stays ungraded")
        try context.save()
        _ = try LogbookBackup(past.climbs.isEmpty ? [] : [past]).validate()
    }

    /// Gyms that only rank their colours (no V grades), and how gyms.json writes ranges.
    @MainActor static func rankedColourChecks() throws {
        assert(ColourGrade.parseRange("VB")! == (0, 0), "VB counts as V0")
        assert(ColourGrade.parseRange("V3")! == (3, 3))
        assert(ColourGrade.parseRange("V2-V5")! == (2, 5))
        assert(ColourGrade.parseRange("V9+")! == (9, 17))
        assert(ColourGrade.parseRange("V5-V2") == nil && ColourGrade.parseRange("6A") == nil && ColourGrade.parseRange("V99") == nil)
        assert(gradeRange(9, 17) == "V9+" && gradeRange(16, 17) == "V16+" && gradeRange(17, 17) == "V17")

        let container = try ModelContainer(for: ClimbSession.self, Climb.self,
                                          configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        let context = container.mainContext
        let start = Date(timeIntervalSince1970: 1_700_000_000)
        let session = ClimbSession(gymID: "ranked-gym", start: start, end: start.addingTimeInterval(3600), price: nil)
        context.insert(session)
        let yellow = ColourGrade(name: "Yellow", low: nil, high: nil)
        assert(yellow.isValid && !yellow.hasRange && yellow.range == "")
        assert(!ColourGrade(name: "Pink", low: 2, high: nil).isValid, "A range has both ends or neither")
        assert(ColourGrade.nextRange(after: [yellow]) == (0, 1))
        let climb = Climb(colour: yellow, result: .send, at: start)
        context.insert(climb)
        climb.session = session
        try context.save()
        assert(climb.grade == Climb.ungraded && !climb.isGraded && !climb.hasFeltGrade && climb.colourDetail == nil)
        assert(session.sends.count == 1 && session.hardest(.boulder) == nil, "Counts as a send, not in grade stats")
        climb.grade = 4
        assert(climb.hasFeltGrade && climb.colourDetail == "felt V4" && session.hardest(.boulder) == 4)
        climb.grade = Climb.ungraded

        let backup = try LogbookBackup.decoder.decode(LogbookBackup.self, from: LogbookBackup.encoder.encode(
            LogbookBackup([session], colourScales: ["ranked-gym": [yellow], "cleared-gym": []])))
        try backup.validate()
        assert(backup.colourScales?["cleared-gym"] == [], "Removing a gym's chart survives a backup")
        let restoredContainer = try ModelContainer(for: ClimbSession.self, Climb.self,
                                                  configurations: ModelConfiguration(isStoredInMemoryOnly: true))
        _ = try backup.restore(into: restoredContainer.mainContext, existing: [])
        let restored = try restoredContainer.mainContext.fetch(FetchDescriptor<Climb>()).first!
        assert(restored.colourGrade == yellow && restored.grade == Climb.ungraded)
        // Ungraded is only allowed for comp climbs and colours without a range.
        var invalid = backup
        invalid.sessions[0].climbs[0].colourLow = 1
        invalid.sessions[0].climbs[0].colourHigh = 2
        do { try invalid.validate(); assertionFailure("A ranged colour climb needs a grade") } catch LogbookBackup.RestoreError.invalidRecord { }
        invalid = backup
        invalid.sessions[0].climbs[0].colour = nil
        do { try invalid.validate(); assertionFailure("An ungraded climb needs a colour or comp tag") } catch LogbookBackup.RestoreError.invalidRecord { }
    }
}
