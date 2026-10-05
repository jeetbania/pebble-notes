import AppKit

// AppKit owns row tracking, submenu corridors and copying text context menus.
// Never install custom item views globally: NSTextView archives those views.
@MainActor enum MenuAppearance {
    static var observers: [NSObjectProtocol] = []
    static var updating = false
    static func install() {
        guard observers.isEmpty else { return }
        for name in [NSMenu.didBeginTrackingNotification, NSMenu.didAddItemNotification] {
            observers.append(NotificationCenter.default.addObserver(forName: name, object: nil, queue: nil) { notice in
                guard Thread.isMainThread else { return }
                MainActor.assumeIsolated {
                    guard !updating, let menu = notice.object as? NSMenu else { return }
                    var root = menu
                    while let parent = root.supermenu { root = parent }
                    guard root !== NSApp.mainMenu else { return }
                    updating = true
                    defer { updating = false }
                    for item in menu.items {
                        if let original = item.image {
                            let image = NSImage(size: original.size)
                            image.lockFocus(); original.draw(in: NSRect(origin: .zero, size: image.size)); NSColor.labelColor.setFill(); NSRect(origin: .zero, size: image.size).fill(using: .sourceAtop); image.unlockFocus()
                            image.isTemplate = false
                            item.image = image
                        }
                    }
                }
            })
        }
    }
}
