import AppKit

// Keep menus native for keyboard navigation and submenu behaviour, while giving
// action rows a neutral icon and a restrained semantic hover surface.
@MainActor enum MenuAppearance {
    static var observers:[NSObjectProtocol]=[]
    static func install(){guard observers.isEmpty else{return}
        for name in [NSMenu.didBeginTrackingNotification,NSMenu.didAddItemNotification,NSMenu.didChangeItemNotification] {
            observers.append(NotificationCenter.default.addObserver(forName:name,object:nil,queue:nil){notice in
                guard Thread.isMainThread else{return}
                MainActor.assumeIsolated {
                    guard let menu=notice.object as? NSMenu else{return}
                    var root=menu;while let parent=root.supermenu{root=parent}
                    guard root !== NSApp.mainMenu else{return}
                    for item in menu.items where !item.isSeparatorItem && !item.title.isEmpty {
                        if let image=item.image {image.isTemplate=false;image.lockFocus();NSColor.labelColor.setFill();NSRect(origin:.zero,size:image.size).fill(using:.sourceAtop);image.unlockFocus()}
                        if item.submenu==nil,item.view==nil {item.view=ActionMenuRow(item:item)}
                    }
                }
            })
        }
    }
}
@MainActor final class ActionMenuRow:NSView {
    weak var item:NSMenuItem?;var hovering=false
    init(item:NSMenuItem){self.item=item;super.init(frame:NSRect(x:0,y:0,width:max(220,CGFloat(item.title.count)*7+66),height:32));setAccessibilityElement(true);setAccessibilityRole(.menuItem);setAccessibilityLabel(item.title);setAccessibilityEnabled(item.isEnabled)}
    required init?(coder:NSCoder){fatalError()}
    override var isFlipped:Bool{true}
    override func updateTrackingAreas(){super.updateTrackingAreas();trackingAreas.forEach{removeTrackingArea($0)};addTrackingArea(NSTrackingArea(rect:bounds,options:[.mouseEnteredAndExited,.activeAlways],owner:self,userInfo:nil))}
    override func mouseEntered(with event:NSEvent){hovering=true;needsDisplay=true}
    override func mouseExited(with event:NSEvent){hovering=false;needsDisplay=true}
    override func draw(_ rect:NSRect){guard let item else{return};let active=hovering || item.isHighlighted
        let destructive=item.title.lowercased().contains("delete") || item.title.lowercased().contains("trash") || item.title.lowercased().contains("remove")
        let success=item.title.lowercased().contains("restore") || item.title.lowercased().contains("complete") || item.title.lowercased().contains("accept")
        let accent:NSColor=destructive ? .systemRed:success ? .systemGreen:.systemBlue
        if active && item.isEnabled {accent.withAlphaComponent(0.18).setFill();NSBezierPath(roundedRect:bounds.insetBy(dx:4,dy:2),xRadius:7,yRadius:7).fill()}
        let ink=NSColor.labelColor.withAlphaComponent(item.isEnabled ? 1:0.35)
        if item.state == .on { ("✓" as NSString).draw(at:NSPoint(x:3,y:8),withAttributes:[.font:NSFont.menuFont(ofSize:12),.foregroundColor:ink]) }
        if let image=item.image?.copy() as? NSImage {image.isTemplate=false;image.lockFocus();ink.setFill();NSRect(origin:.zero,size:image.size).fill(using:.sourceAtop);image.unlockFocus();image.draw(in:NSRect(x:12,y:8,width:16,height:16))}
        (item.title as NSString).draw(at:NSPoint(x:38,y:8),withAttributes:[.font:NSFont.menuFont(ofSize:13),.foregroundColor:ink])
    }
    override func mouseUp(with event:NSEvent){activate()}
    override func accessibilityPerformPress()->Bool{activate();return true}
    func activate(){guard let item,item.isEnabled,let menu=item.menu,let index=menu.items.firstIndex(of:item) else{return};menu.cancelTracking();menu.performActionForItem(at:index)}
}
