import Foundation
@preconcurrency import UserNotifications

final class LeafTaskNotificationDelegate: NSObject, UNUserNotificationCenterDelegate {
    static let shared = LeafTaskNotificationDelegate()
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) { completionHandler([.banner, .sound]) }
}
@MainActor enum TaskAlerts {
    static func configure() { UNUserNotificationCenter.current().delegate = LeafTaskNotificationDelegate.shared }
    static var signature: String? = nil
    static var generation = 0
    static func refresh(_ records: [Revision]) {
        #if !LEAF_TEST
        let active = records.filter { r in let t = r.note.task; return t?.remind == true && t?.completed == false && !r.note.deleted && !r.note.archived && (t?.alertDate ?? .distantPast) > Date() }
        let next = active.map { $0.noteId + ":" + String($0.note.task!.dueAt) + ":" + String($0.note.task!.hasTime) + ":" + $0.note.title + ":" + $0.note.task!.list }.sorted().joined(separator: "|")
        guard signature != next else { return }; signature = next; generation += 1; let revision = generation
        Task {
            let center = UNUserNotificationCenter.current()
            let requests = await center.pendingNotificationRequests()
            guard generation == revision else { return }
            center.removePendingNotificationRequests(withIdentifiers: requests.filter { $0.identifier.hasPrefix("leaf-task-") }.map(\.identifier))
            for r in active {
                let content = UNMutableNotificationContent(); content.title = r.note.displayTitle; content.body = r.note.task!.list; content.sound = .default
                let components = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: r.note.task!.alertDate)
                let request = UNNotificationRequest(identifier: "leaf-task-" + r.noteId, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false))
                try? await center.add(request)
                guard generation == revision else { return }
            }
        }
        #endif
    }
    static func authorize() async -> Bool { (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound])) ?? false }
}
