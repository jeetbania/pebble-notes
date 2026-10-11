import SwiftUI

/// Shared by the main window and detached note windows.
struct ConflictReview: View {
    @ObservedObject var store: NoteStore
    var onClose: () -> Void
    var versions: [Revision] { store.heads.filter { $0.noteId == store.selected }.sorted { $0.createdAt > $1.createdAt } }
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack(alignment: .top, spacing: 14) {
                Image(systemName: "arrow.triangle.branch").font(.system(size: 25, weight: .medium)).foregroundStyle(PebblePalette.accent)
                    .frame(width: 52, height: 52).background(PebblePalette.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 16))
                VStack(alignment: .leading, spacing: 6) {
                    Text("One note, two directions").font(.system(size: 24, weight: .semibold)).tracking(-0.4)
                    Text("Changes were made on more than one device. Choose what to continue with; earlier versions stay in History.").font(.system(size: 14)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                }
                GlassIcon(icon: "xmark", label: "Close version comparison", action: onClose)
            }
            PebbleScrollView {
                // An explicit stack is essential: multiple ScrollView children overlap.
                VStack(spacing: 14) {
                    ForEach(Array(versions.enumerated()), id: \.element.id) { index, revision in
                        versionCard(revision, number: index + 1)
                    }
                }.padding(.vertical, 2)
            }
            HStack {
                Label("Every saved version is preserved", systemImage: "checkmark.shield").font(.system(size: 12)).foregroundStyle(.secondary)
                Spacer()
                Button("Decide later", action: onClose).buttonStyle(MaterialActionStyle())
            }
        }.padding(24).frame(width: 560, height: 590)
    }
    func versionCard(_ revision: Revision, number: Int) -> some View {
        let deleted = revision.note.deleted
        let local = revision.deviceId == store.deviceId
        return VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                Image(systemName: deleted ? "trash" : local ? "desktopcomputer" : "icloud")
                Text(local ? "This Mac" : "Another device").fontWeight(.medium)
                Spacer()
                Text("Version \(number)").foregroundStyle(.secondary)
            }.font(.system(size: 12))
            Text(revision.note.displayTitle).font(.system(size: 18, weight: .semibold)).lineLimit(2)
            Text(Date(timeIntervalSince1970: Double(revision.createdAt) / 1000), format: .dateTime.month(.abbreviated).day().hour().minute()).font(.system(size: 12)).foregroundStyle(.secondary)
            SubtleDivider()
            if deleted {
                Label("Moved to Trash", systemImage: "trash").font(.system(size: 14, weight: .medium))
                Text("Choosing this version keeps the note in Trash.").font(.system(size: 13)).foregroundStyle(.secondary)
            } else {
                Text(revision.note.text.isEmpty ? "No body text in this version." : String(revision.note.text.prefix(400)))
                    .font(.system(size: 14)).foregroundStyle(.secondary).lineLimit(6).fixedSize(horizontal: false, vertical: true)
            }
            Button { store.resolve(revision); onClose() } label: {
                HStack { Text(deleted ? "Use deleted version" : "Use this version"); Spacer(); Image(systemName: "arrow.right") }.frame(maxWidth: .infinity)
            }.buttonStyle(MaterialActionStyle())
        }.padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20))
            .overlay(RoundedRectangle(cornerRadius: 20).strokeBorder(Color.primary.opacity(PebbleTokens.actionBorderOpacity), lineWidth: 0.7))
    }
}
