import SwiftUI
import AppKit

struct NoteStyle: Codable, Equatable {
    var document: String? = nil; var backdrop: String? = nil; var backdropEnd: String? = nil; var text: String? = nil
    var framed: Bool { backdrop != nil }; var customised: Bool { document != nil || framed || text != nil }
    static let backdropPalette = ["#FDD1A0", "#FCB122", "#4F97FD", "#1F33AD", "#1FB29C", "#0C4534", "#156989", "#FB5F1F", "#B60A18", "#FDC2C3", "#BDABFE", "#9FBEFD"]
    static let backdropGradients = [["#71CBA2", "#BFE9A5"], ["#CFF0B7", "#F1BEB7"], ["#DADFE2", "#9DA9B3"], ["#D5AC93", "#AFC1BB"], ["#A997E5", "#E7B6D5"], ["#E1C092", "#D3AB7B"], ["#CDB4B3", "#807D9F"], ["#CAEAC9", "#E89990"], ["#DFC7E2", "#F1ACB3"], ["#848CA2", "#C0C7D5"], ["#FEE9A5", "#FDC990"], ["#71A5D5", "#D7E9F4"]]
    static let documentPalette = ["#FFFFFF", "#1F1F1F", "#F9E4FF", "#D7E7FD", "#C9F9FD", "#C6FAEE", "#CBF9E1", "#FDF8BC", "#FEDEDE", "#FBE3F1"]
    static let textPalette = ["#1D1F21", "#F2F2F2", "#66136C", "#1E3181", "#164458", "#134541", "#0E553C", "#9A5514", "#75181C", "#79123C"]
    static func colour(_ hex: String?) -> Color? { guard let hex, let value = rgb(hex) else { return nil }; return Color(red: Double(value >> 16 & 255)/255, green: Double(value >> 8 & 255)/255, blue: Double(value & 255)/255) }
    static func rgb(_ hex: String) -> UInt32? { hex.count == 7 && hex.first == "#" ? UInt32(hex.dropFirst(), radix: 16) : nil }
    static func isLight(_ hex: String) -> Bool { guard let rgb = rgb(hex) else { return true }; func ch(_ n: UInt32) -> Double { let v=Double(n)/255; return v <= 0.04045 ? v/12.92 : pow((v+0.055)/1.055,2.4) }; return 0.2126*ch(rgb>>16&255)+0.7152*ch(rgb>>8&255)+0.0722*ch(rgb&255)>0.179 }
    var ink: NSColor { if let text, let c=Self.colour(text) { return NSColor(c) }; if let document { return Self.isLight(document) ? NSColor(calibratedWhite:0.10,alpha:1) : NSColor(calibratedWhite:0.97,alpha:1) }; return .labelColor }
    var foreground: Color { Color(nsColor:ink) }; var paper: Color { Self.colour(document) ?? Color(nsColor:.textBackgroundColor) }
    var chromeScheme: ColorScheme? { (backdrop ?? document).map { Self.isLight($0) ? .light : .dark } }; var documentScheme: ColorScheme? { document.map { Self.isLight($0) ? .light : .dark } }
    func background() -> LinearGradient { LinearGradient(colors:[Self.colour(backdrop) ?? paper,Self.colour(backdropEnd ?? backdrop) ?? paper],startPoint:.top,endPoint:.bottom) }
    static func hex(_ colour:Color)->String { let c=NSColor(colour).usingColorSpace(.sRGB) ?? .white; return String(format:"#%02X%02X%02X",Int((c.redComponent*255).rounded()),Int((c.greenComponent*255).rounded()),Int((c.blueComponent*255).rounded())) }
}

struct NoteStyleButton: View {
    @ObservedObject var store:NoteStore; @LeafState private var presented=false; var interfaceScheme:ColorScheme?=nil; @Environment(\.colorScheme) private var scheme
    var body:some View { ZStack(alignment:.topTrailing) {
        GlassIcon(icon:"paintbrush",label:"Note style") { withAnimation(.spring(response:0.28,dampingFraction:1)){presented.toggle()} }
        if presented { NoteStylePicker(store:store,onClose:{presented=false}).id(store.selected).frame(width:330).padding(18)
            .background(.regularMaterial,in:RoundedRectangle(cornerRadius:24,style:.continuous)).overlay(RoundedRectangle(cornerRadius:24).strokeBorder(Color.primary.opacity(0.14),lineWidth:0.7)).shadow(color:.black.opacity(0.22),radius:24,y:12)
            .environment(\.colorScheme,interfaceScheme ?? scheme).offset(y:48).transition(.opacity.combined(with:.scale(scale:0.97,anchor:.topTrailing))).zIndex(100).onExitCommand { presented=false } }
    }.zIndex(presented ? 100:0).onChange(of:store.selected){_,_ in presented=false} }
}
private struct StyleSectionTitle:View { var text:String; var body:some View { Text(text.uppercased()).font(.system(size:11,weight:.semibold)).foregroundStyle(.secondary).tracking(0.6) } }

struct NoteStylePicker:View {
    @ObservedObject var store:NoteStore; var onClose:()->Void={}; @LeafState private var picker:String?=nil; @LeafState private var gradientMode=false
    var style:NoteStyle { store.current?.style ?? NoteStyle() }
    func value(_ key:String)->String? { key=="Document" ? style.document : key=="Backdrop" ? style.backdrop : style.text }
    func set(_ key:String,_ value:String?){store.update(undoKey:"noteStyle"){n in var s=n.style ?? NoteStyle();if key=="Document"{s.document=value}else if key=="Backdrop"{s.backdrop=value;if value==nil{s.backdropEnd=nil}}else{s.text=value};n.style=s.customised ? s:nil}}
    func setGradient(_ pair:[String]){guard pair.count==2 else{return};store.update(undoKey:"noteStyle"){n in var s=n.style ?? NoteStyle();s.backdrop=pair[0];s.backdropEnd=pair[1];n.style=s}}
    var body:some View { VStack(alignment:.leading,spacing:0){
        HStack{if picker != nil{GlassIcon(icon:"chevron.left",label:"Back to style"){picker=nil}};Text(picker ?? "Style").font(.system(size:19,weight:.semibold)).tracking(-0.2);Spacer();GlassIcon(icon:"xmark",label:"Close"){if picker==nil{onClose()}else{picker=nil}}}.padding(.bottom,12)
        if picker==nil { StyleMiniature(style:style).frame(height:142).padding(.bottom,16);StyleSectionTitle(text:"Color").padding(.bottom,5)
            ForEach(["Backdrop","Document","Text"],id:\.self){key in Button{picker=key;gradientMode=key=="Backdrop" && style.backdropEnd != nil}label:{HStack(spacing:12){Image(systemName:key=="Document" ? "doc.fill":key=="Backdrop" ? "square.stack.3d.up.fill":"textformat").frame(width:22);Text(key=="Document" ? "Document Color":key=="Text" ? "Text Color":key).font(.system(size:15,weight:.medium));Spacer();styleChip(key);Image(systemName:"chevron.right").font(.system(size:11,weight:.semibold)).foregroundStyle(.secondary)}.frame(minHeight:48).contentShape(Rectangle())}.buttonStyle(SoftButtonStyle(radius:12))}
            SubtleDivider().padding(.vertical,8);Button{store.update{$0.style=nil}}label:{Label("Reset style",systemImage:"arrow.counterclockwise").font(.system(size:15,weight:.medium)).frame(maxWidth:.infinity,minHeight:42,alignment:.leading)}.buttonStyle(SoftButtonStyle(radius:12)).disabled(!style.customised)
        } else if let key=picker { SubtleDivider().padding(.bottom,16)
            if key=="Backdrop" { HStack(spacing:0){mode("rectangle.slash",style.backdrop==nil){set("Backdrop",nil);gradientMode=false};mode("square.fill",style.backdrop != nil && style.backdropEnd==nil){gradientMode=false;if style.backdrop==nil{set("Backdrop",NoteStyle.backdropPalette[1])}else{store.update{$0.style?.backdropEnd=nil}}};mode("square.fill.on.square.fill",style.backdropEnd != nil){gradientMode=true;if style.backdropEnd==nil{setGradient(NoteStyle.backdropGradients[10])}}}.padding(3).background(Color.primary.opacity(0.08),in:Capsule()).padding(.bottom,14) }
            swatchGrid(key)
            if key=="Backdrop",style.backdropEnd != nil { StyleSectionTitle(text:"Gradient").padding(.top,14).padding(.bottom,8);HStack{Text("Start Color");Spacer();chip(style.backdrop)}.frame(height:34);HStack{Text("End Color");Spacer();chip(style.backdropEnd)}.frame(height:34);HStack{Text("Direction");Spacer();Text("Top to Bottom").foregroundStyle(.secondary).padding(.horizontal,12).frame(height:32).background(Color.primary.opacity(0.06),in:RoundedRectangle(cornerRadius:10))}.frame(height:38) }
            SubtleDivider().padding(.vertical,12);ColorPicker("Custom Color",selection:Binding(get:{NoteStyle.colour(value(key)) ?? .white},set:{c in set(key,NoteStyle.hex(c));if key=="Backdrop"{store.update{$0.style?.backdropEnd=nil};gradientMode=false}}),supportsOpacity:false)
        }
    }}
    @ViewBuilder func styleChip(_ key:String)->some View { let hex=value(key);RoundedRectangle(cornerRadius:10).fill(NoteStyle.colour(hex) ?? Color.primary.opacity(0.08)).frame(width:36,height:32).overlay(RoundedRectangle(cornerRadius:10).strokeBorder(Color.primary.opacity(0.09),lineWidth:0.7)).overlay{if hex==nil{Image(systemName:key=="Backdrop" ? "minus":"circle.lefthalf.filled").font(.system(size:13))}} }
    @ViewBuilder func chip(_ hex:String?)->some View { RoundedRectangle(cornerRadius:9).fill(NoteStyle.colour(hex) ?? .clear).frame(width:38,height:30).overlay(RoundedRectangle(cornerRadius:9).strokeBorder(Color.primary.opacity(0.1),lineWidth:0.7)) }
    func mode(_ icon:String,_ selected:Bool,_ action:@escaping()->Void)->some View { Button(action:action){Image(systemName:icon).frame(maxWidth:.infinity).frame(height:34).background(selected ? Color(nsColor:.controlBackgroundColor):.clear,in:Capsule()).shadow(color:selected ? .black.opacity(0.08):.clear,radius:5,y:2)}.buttonStyle(.plain) }
    @ViewBuilder func swatchGrid(_ key:String)->some View { let solids=key=="Document" ? NoteStyle.documentPalette:key=="Text" ? NoteStyle.textPalette:NoteStyle.backdropPalette
        LazyVGrid(columns:Array(repeating:GridItem(.flexible(),spacing:8),count:6),spacing:10){if key != "Backdrop"{automatic(key)};if key=="Backdrop" && gradientMode{ForEach(Array(NoteStyle.backdropGradients.enumerated()),id:\.offset){_,p in gradient(p)}}else{ForEach(solids,id:\.self){solid(key,$0)}}} }
    func automatic(_ key:String)->some View { Button{set(key,nil)}label:{Circle().fill(LinearGradient(colors:[.white,.black],startPoint:.bottomLeading,endPoint:.topTrailing)).frame(width:40,height:40).overlay(Circle().strokeBorder(value(key)==nil ? LeafPalette.accent:Color.primary.opacity(0.12),lineWidth:value(key)==nil ? 3:0.7))}.buttonStyle(.plain).help("Automatic") }
    func solid(_ key:String,_ hex:String)->some View { Button{set(key,hex);if key=="Backdrop"{store.update{$0.style?.backdropEnd=nil};gradientMode=false}}label:{Circle().fill(NoteStyle.colour(hex)!).frame(width:40,height:40).overlay(Circle().strokeBorder(value(key)==hex ? LeafPalette.accent:Color.primary.opacity(0.12),lineWidth:value(key)==hex ? 3:0.7))}.buttonStyle(.plain).help(hex) }
    func gradient(_ p:[String])->some View { Button{setGradient(p);gradientMode=true}label:{Circle().fill(LinearGradient(colors:p.compactMap(NoteStyle.colour),startPoint:.top,endPoint:.bottom)).frame(width:40,height:40).overlay(Circle().strokeBorder(style.backdrop==p[0] && style.backdropEnd==p[1] ? LeafPalette.accent:Color.primary.opacity(0.12),lineWidth:style.backdrop==p[0] && style.backdropEnd==p[1] ? 3:0.7))}.buttonStyle(.plain) }
}

struct StyleMiniature:View { var style:NoteStyle;var body:some View{ZStack(alignment:.bottom){RoundedRectangle(cornerRadius:18).fill(style.background());VStack(alignment:.leading,spacing:9){Text("Note").font(.system(size:18,weight:.semibold));RoundedRectangle(cornerRadius:2).fill(style.foreground.opacity(0.20)).frame(height:5);RoundedRectangle(cornerRadius:2).fill(style.foreground.opacity(0.13)).frame(width:90,height:5);Spacer()}.foregroundStyle(style.foreground).padding(16).frame(maxWidth:.infinity,maxHeight:.infinity,alignment:.topLeading).background(style.paper,in:RoundedRectangle(cornerRadius:14)).padding(.horizontal,style.framed ? 12:0).padding(.top,style.framed ? 12:0).offset(y:style.framed ? 7:0)}.clipShape(RoundedRectangle(cornerRadius:18)).overlay(RoundedRectangle(cornerRadius:18).strokeBorder(Color.primary.opacity(0.08),lineWidth:0.7))} }

struct NotePreviewSurface:ViewModifier {var style:NoteStyle?;@Environment(\.colorScheme)private var scheme;func body(content:Content)->some View{if let style,style.document != nil || style.framed{if style.framed{content.foregroundStyle(style.foreground).background(style.paper,in:RoundedRectangle(cornerRadius:17)).overlay(RoundedRectangle(cornerRadius:17).strokeBorder(style.foreground.opacity(0.07),lineWidth:0.6)).padding(.horizontal,9).offset(y:9).background(style.background(),in:RoundedRectangle(cornerRadius:22)).clipShape(RoundedRectangle(cornerRadius:22)).shadow(color:.black.opacity(0.10),radius:6,y:3).environment(\.colorScheme,style.documentScheme ?? scheme)}else{content.foregroundStyle(style.foreground).background(style.paper,in:RoundedRectangle(cornerRadius:22)).overlay(RoundedRectangle(cornerRadius:22).strokeBorder(style.foreground.opacity(0.07),lineWidth:0.6)).shadow(color:.black.opacity(0.10),radius:6,y:3).environment(\.colorScheme,style.documentScheme ?? scheme)}}else{content.foregroundStyle(style?.foreground ?? .primary).background(CardMaterial())}}}
