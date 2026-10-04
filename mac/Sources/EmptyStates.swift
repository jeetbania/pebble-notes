import SwiftUI

// Quiet, static previews distinguish empty content from loading content.
struct GhostArtwork: View {
    var kind: String
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        ZStack {
            ghost.rotationEffect(.degrees(-7)).offset(x: -34, y: -24)
            ghost.rotationEffect(.degrees(8)).offset(x: 40, y: 18)
            ghost.rotationEffect(.degrees(-3)).offset(x: -12, y: 12)
        }.frame(width: 340, height: 180).accessibilityHidden(true).allowsHitTesting(false)
    }
    var ghost: some View {
        HStack(spacing: 14) {
            if kind == "tasks" || kind == "checklists" {
                VStack(spacing: 14) { ForEach(0..<3) { _ in Circle().fill(Color.purple.opacity(0.09)).overlay(Circle().stroke(Color.primary.opacity(0.13), lineWidth: 0.7)).frame(width: 13, height: 13) } }
            } else {
                RoundedRectangle(cornerRadius: 7).fill(Color.purple.opacity(0.08)).overlay { Image(systemName: icon).font(.system(size: 24, weight: .ultraLight)).foregroundStyle(Color.primary.opacity(0.12)) }.frame(width: kind == "images" ? 62 : 34)
            }
            VStack(alignment: .leading, spacing: 14) { line(kind == "images" ? 105 : 142); line(kind == "images" ? 85 : 112); line(kind == "images" ? 95 : 130) }
        }.padding(16).frame(width: 232, height: 112).background((scheme == .dark ? Color(red: 0.16, green: 0.16, blue: 0.18) : Color(red: 0.97, green: 0.97, blue: 0.985)), in: RoundedRectangle(cornerRadius: 14)).overlay(RoundedRectangle(cornerRadius: 14).stroke(Color.primary.opacity(0.075), lineWidth: 0.7)).shadow(color: Color.black.opacity(0.035), radius: 14, y: 5)
    }
    var icon: String { switch kind { case "images": return "photo"; case "trash": return "trash"; case "archive": return "archivebox"; case "pinned": return "pin"; case "search": return "magnifyingglass"; case "collection": return "folder"; default: return "note.text" } }
    func line(_ width: CGFloat) -> some View { Capsule().fill(Color.primary.opacity(0.07)).frame(width: width, height: 5) }
}
struct GhostEmpty: View {
    var kind: String; var title: String; var detail: String
    var actionLabel: String? = nil; var action: (() -> Void)? = nil
    var body: some View {
        VStack(spacing: 10) {
            GhostArtwork(kind: kind)
            Text(title).font(.system(size: 21, weight: .semibold))
            Text(detail).font(.system(size: 15)).foregroundStyle(.secondary).multilineTextAlignment(.center)
            if let actionLabel, let action { Button(action: action) { Text(actionLabel).font(.system(size: 14, weight: .medium)).padding(.horizontal, 22).frame(minHeight: 40) }.buttonStyle(SoftButtonStyle(radius: 22)).background(Color.primary.opacity(0.065), in: Capsule()).padding(.top, 12) }
        }.frame(maxWidth: .infinity).padding(.vertical, 24)
    }
}
