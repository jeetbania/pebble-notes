import SwiftUI
import AppKit
import CryptoKit
import UniformTypeIdentifiers

struct Wallpaper: Codable, Identifiable { var id:String; var name:String }
private struct NoteMediaKey: EnvironmentKey { static let defaultValue:URL? = nil }
extension EnvironmentValues { var noteMedia:URL? { get{self[NoteMediaKey.self]} set{self[NoteMediaKey.self]=newValue} } }
struct NoteBackdrop: View {
    var style:NoteStyle
    @Environment(\.noteMedia) private var media
    var body: some View {
        GeometryReader { g in
            ZStack {
                LinearGradient(colors:[NoteStyle.colour(style.backdrop) ?? style.paper,NoteStyle.colour(style.backdropEnd ?? style.backdrop) ?? style.paper],startPoint:style.gradientDirection == "up" ? .bottom:.top,endPoint:style.gradientDirection == "up" ? .top:.bottom)
                if let id=style.backdropImage, let url=WallpaperLibrary.url(id,media:media) { Thumbnail(url:url, pixels:g.size.width > 600 ? 1500 : 600).frame(width:g.size.width,height:g.size.height).blur(radius:style.blurImage == true ? 14:0) }
            }.clipped()
        }
    }
}
enum WallpaperLibrary {
    static func prominent(_ url: URL) -> String? {
        guard let image = NSImage(contentsOf: url), let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: 32, pixelsHigh: 32, bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0), let context = NSGraphicsContext(bitmapImageRep: bitmap) else { return nil }
        NSGraphicsContext.saveGraphicsState(); NSGraphicsContext.current = context; image.draw(in: NSRect(x: 0, y: 0, width: 32, height: 32)); NSGraphicsContext.restoreGraphicsState()
        var bins: [Int: (Int, Double, Double, Double)] = [:]
        for y in 0..<32 { for x in 0..<32 { if let c = bitmap.colorAt(x: x, y: y)?.usingColorSpace(.sRGB), c.alphaComponent > 0.5 { let key = Int(c.redComponent*7)*64+Int(c.greenComponent*7)*8+Int(c.blueComponent*7); let old = bins[key] ?? (0,0,0,0); bins[key] = (old.0+1,old.1+c.redComponent,old.2+c.greenComponent,old.3+c.blueComponent) } } }
        guard let v = bins.values.max(by: { $0.0 < $1.0 }) else { return nil }; return String(format: "#%02X%02X%02X", Int(v.1/Double(v.0)*255),Int(v.2/Double(v.0)*255),Int(v.3/Double(v.0)*255))
    }
    static var items:[Wallpaper] { guard let url=Bundle.main.url(forResource:"index",withExtension:"json",subdirectory:"Wallpapers"),let data=try? Data(contentsOf:url) else{return []};return (try? JSONDecoder().decode([Wallpaper].self,from:data)) ?? [] }
    static func url(_ id:String,media:URL?)->URL? {
        if let media { let url=media.appendingPathComponent(id); if FileManager.default.fileExists(atPath:url.path) { return url } }
        return Bundle.main.url(forResource:id,withExtension:"webp",subdirectory:"Wallpapers")
    }
}
extension NoteStore {
    func useBackdrop(_ url:URL) {
        do { guard NSImage(contentsOf:url) != nil else {throw LeafError.message("Choose a supported image")};var a=try stageFiles([url])[0];a.mime="application/x-pebble-backdrop"
            update(undoKey:"noteStyle"){n in var s=n.style ?? NoteStyle();s.backdrop=nil;s.backdropEnd=nil;s.backdropImage=a.id;s=s.lightDocument(tone:WallpaperLibrary.prominent(url),defaultDark: !NoteStyle.isLight(NoteStyle.hex(Color(nsColor:.textBackgroundColor))));n.style=s;n.attachments.append(a)};flush()
        } catch {self.error=error.localizedDescription}
    }
    func uploadBackdrop(){let panel=NSOpenPanel();panel.allowedContentTypes=[.image];if panel.runModal() == .OK,let url=panel.url{useBackdrop(url)}}
}
struct WallpaperSelector:View {
    @ObservedObject var store:NoteStore
    var body:some View {
        VStack(spacing:12){LazyVGrid(columns:Array(repeating:GridItem(.flexible(),spacing:8),count:3),spacing:8){ForEach(WallpaperLibrary.items){w in
            Button {if let url=Bundle.main.url(forResource:w.id,withExtension:"webp",subdirectory:"Wallpapers"){store.useBackdrop(url)}} label:{
                if let url=WallpaperLibrary.url(w.id,media:nil){Thumbnail(url:url).frame(width:94,height:70).clipShape(RoundedRectangle(cornerRadius:12)).overlay(RoundedRectangle(cornerRadius:12).strokeBorder(Color.primary.opacity(store.current?.style?.backdropImage==w.id ? 0.9:0.14),lineWidth:store.current?.style?.backdropImage==w.id ? 3:0.7)).contentShape(Rectangle())}
            }.buttonStyle(.plain).help(w.name)
        }}
        Button {store.uploadBackdrop()} label:{Label("Upload image",systemImage:"photo.badge.plus").frame(maxWidth:.infinity,minHeight:42).contentShape(Rectangle())}.buttonStyle(GhostButtonStyle())
        Toggle("Blur Image",isOn:Binding(get:{store.current?.style?.blurImage ?? false},set:{v in store.update{$0.style?.blurImage=v}})).toggleStyle(.switch).disabled(store.current?.style?.backdropImage==nil)
        }
    }
}
struct GhostButtonStyle:ButtonStyle {
    func makeBody(configuration:Configuration)->some View { Surface(configuration:configuration) }
    struct Surface:View {let configuration:Configuration;@LeafState private var hover=false
        var body:some View {configuration.label.padding(.horizontal,12).frame(minHeight:36).contentShape(Rectangle()).background(Color.primary.opacity(hover ? 0.10:0.04),in:RoundedRectangle(cornerRadius:12)).overlay(RoundedRectangle(cornerRadius:12).strokeBorder(Color.primary.opacity(hover ? 0.18:0.1),lineWidth:0.7)).opacity(configuration.isPressed ? 0.65:1).onHover{hover=$0}}
    }
}

struct BlockDragMaterial:NSViewRepresentable {
    func makeNSView(context:Context)->NSVisualEffectView {let view=NSVisualEffectView();view.material = .popover;view.blendingMode = .withinWindow;view.state = .active;return view}
    func updateNSView(_ view:NSVisualEffectView,context:Context){}
}
