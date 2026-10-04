import SwiftUI
import AppKit
import Security
import CryptoKit

struct UpdateAsset: Codable { var url: String; var sha256: String; var size: Int; var build: Int }
struct PebbleRelease: Codable { var version: String; var title: String; var changes: [String] }
struct PebbleUpdate: Codable { var schema: Int; var version: String; var releases: [PebbleRelease]; var mac: UpdateAsset; var android: UpdateAsset }
struct UpdateEnvelope: Codable { var payload: String; var signature: String }
enum UpdateValidation {
    static let feed = URL(string: "https://github.com/jeetbania/pebble-notes/releases/latest/download/updates.json")!
    static func parse(_ bytes: Data) throws -> PebbleUpdate {
        guard bytes.count <= 1_048_576 else { throw LeafError.message("Update information is too large") }
        let envelope = try JSONDecoder().decode(UpdateEnvelope.self, from: bytes)
        guard let payload = Data(base64Encoded: envelope.payload), let signature = Data(base64Encoded: envelope.signature), let keyURL = Bundle.main.url(forResource: "update-public", withExtension: "der") else { throw LeafError.message("Missing update signature") }
        return try parse(payload: payload, signature: signature, publicKey: Data(contentsOf: keyURL))
    }
    static func parse(payload: Data, signature: Data, publicKey: Data) throws -> PebbleUpdate {
        let attributes: [CFString: Any] = [kSecAttrKeyType: kSecAttrKeyTypeRSA, kSecAttrKeyClass: kSecAttrKeyClassPublic]
        guard let key = SecKeyCreateWithData(publicKey as CFData, attributes as CFDictionary, nil), SecKeyVerifySignature(key, .rsaSignatureMessagePKCS1v15SHA256, payload as CFData, signature as CFData, nil) else { throw LeafError.message("Update signature could not be verified") }
        let update = try JSONDecoder().decode(PebbleUpdate.self, from: payload)
        guard update.schema == 1, update.releases.first?.version == update.version else { throw LeafError.message("Invalid update information") }
        for asset in [update.mac, update.android] {
            guard asset.url.hasPrefix("https://github.com/jeetbania/pebble-notes/releases/download/"), asset.size > 0, asset.size <= 200_000_000, asset.build > 0, asset.sha256.count == 64, asset.sha256.allSatisfy({ "0123456789abcdef".contains($0) }) else { throw LeafError.message("Unexpected update package") }
        }
        return update
    }
    static func unseen(_ update: PebbleUpdate, current: String) -> [PebbleRelease] { if let index = update.releases.firstIndex(where: { $0.version == current }) { return Array(update.releases.prefix(index)) }; return update.releases }
}
@MainActor final class PebbleUpdater: ObservableObject {
    static let shared = PebbleUpdater()
    @Published var available: PebbleUpdate?
    @Published var visible = false
    @Published var checking = false
    @Published var downloading = false
    @Published var progress = 0.0
    @Published var message = ""
    @Published var ready: URL?
    private var timer: Timer?
    private var stage: URL?
    var currentBuild: Int { Int(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "0") ?? 0 }
    func start() { guard timer == nil else { return }; Task { await check() }; timer = Timer.scheduledTimer(withTimeInterval: 6 * 3600, repeats: true) { _ in Task { @MainActor in await PebbleUpdater.shared.check() } } }
    func check(manual: Bool = false) async {
        guard !checking, !downloading else { return }
        if !manual, Date().timeIntervalSince1970 - UserDefaults.standard.double(forKey: "pebbleUpdateCheck") < 6 * 3600 { return }
        checking = true; message = "Checking for updates…"
        defer { checking = false }
        do {
            var request = URLRequest(url: UpdateValidation.feed); request.timeoutInterval = 20; request.cachePolicy = .reloadIgnoringLocalCacheData
            let (bytes, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { throw LeafError.message("The release server could not be reached") }
            let update = try UpdateValidation.parse(bytes)
            UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "pebbleUpdateCheck")
            if update.mac.build > currentBuild {
                if available?.mac.build != update.mac.build { ready = nil }
                available = update; message = "Pebble Notes \(update.version) is available"
                if manual || UserDefaults.standard.integer(forKey: "pebbleUpdateLater") != update.mac.build { visible = true }
            } else { available = nil; message = "You’re up to date · \(ReleaseNotes.version)" }
        } catch { message = manual ? "Could not check: \(error.localizedDescription)" : "Check for updates when you’re online." }
    }
    func later() { if let update = available { UserDefaults.standard.set(update.mac.build, forKey: "pebbleUpdateLater") }; visible = false }
    func download() async {
        guard let update = available, !downloading else { return }
        downloading = true; progress = 0; ready = nil; message = "Downloading…"
        defer { downloading = false }
        do {
            let directory = FileManager.default.temporaryDirectory.appendingPathComponent("pebble-update-" + UUID().uuidString, isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            if let old = stage { try? FileManager.default.removeItem(at: old) }; stage = directory
            let zip = directory.appendingPathComponent("update.zip")
            var request = URLRequest(url: URL(string: update.mac.url)!); request.timeoutInterval = 60
            let (bytes, response) = try await URLSession.shared.bytes(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200, response.url?.scheme == "https" else { throw LeafError.message("Download failed") }
            FileManager.default.createFile(atPath: zip.path, contents: nil)
            let handle = try FileHandle(forWritingTo: zip); defer { try? handle.close() }
            var hash = SHA256(); var buffer = Data(); var count = 0
            for try await byte in bytes { buffer.append(byte); count += 1; guard count <= update.mac.size else { throw LeafError.message("Unexpected download size") }; if buffer.count >= 65536 { try handle.write(contentsOf: buffer); hash.update(data: buffer); buffer.removeAll(keepingCapacity: true); progress = Double(count) / Double(update.mac.size) } }
            if !buffer.isEmpty { try handle.write(contentsOf: buffer); hash.update(data: buffer) }; try handle.close()
            guard count == update.mac.size, hash.finalize().map({ String(format: "%02x", $0) }).joined() == update.mac.sha256 else { throw LeafError.message("Downloaded update did not pass verification") }
            let unpacked = directory.appendingPathComponent("unpacked", isDirectory: true)
            try await run("/usr/bin/ditto", ["-x", "-k", zip.path, unpacked.path])
            let app = unpacked.appendingPathComponent("Pebble Notes.app")
            guard let bundle = Bundle(url: app), bundle.bundleIdentifier == "dev.leafnotes.mac", bundle.object(forInfoDictionaryKey: "CFBundleVersion") as? String == String(update.mac.build), bundle.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String == update.version else { throw LeafError.message("Update does not match Pebble Notes") }
            try await run("/usr/bin/codesign", ["--verify", "--deep", "--strict", app.path])
            ready = app; progress = 1; message = "Verified. Ready to install and restart."
        } catch { message = "Could not download: \(error.localizedDescription)"; if let stage { try? FileManager.default.removeItem(at: stage) }; ready = nil }
    }
    func install() {
        guard let ready else { return }
        let destination = Bundle.main.bundleURL
        guard destination.pathExtension == "app", FileManager.default.isWritableFile(atPath: destination.deletingLastPathComponent().path), !destination.path.contains("AppTranslocation") else { message = "Move Pebble Notes to a writable folder, such as your Applications folder, then try again."; return }
        do {
            let helper = FileManager.default.temporaryDirectory.appendingPathComponent("pebble-install-" + UUID().uuidString + ".sh")
            // Positional arguments keep user-chosen paths out of shell source.
            let script = """
            #!/bin/sh
            set -eu
            pid="$1"; source="$2"; destination="$3"
            count=0
            while kill -0 "$pid" 2>/dev/null; do count=$((count+1)); [ "$count" -lt 120 ] || exit 1; sleep 1; done
            temporary="${destination}.pebble-new-$$"
            backup="${destination}.pebble-old-$$"
            /usr/bin/ditto "$source" "$temporary"
            /usr/bin/codesign --verify --deep --strict "$temporary"
            mv "$destination" "$backup"
            if mv "$temporary" "$destination"; then /usr/bin/open "$destination"; rm -rf "$backup"; else mv "$backup" "$destination"; /usr/bin/open "$destination"; exit 1; fi
            rm -f "$0"
            """
            try script.write(to: helper, atomically: true, encoding: .utf8)
            let process = Process(); process.executableURL = URL(fileURLWithPath: "/bin/sh"); process.arguments = [helper.path, String(ProcessInfo.processInfo.processIdentifier), ready.path, destination.path]; process.standardOutput = FileHandle.nullDevice; process.standardError = FileHandle.nullDevice
            try process.run(); NSApp.terminate(nil)
        } catch { message = "Could not start installation: \(error.localizedDescription)" }
    }
    private func run(_ executable: String, _ arguments: [String]) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            let p = Process(); p.executableURL = URL(fileURLWithPath: executable); p.arguments = arguments; p.standardOutput = FileHandle.nullDevice; p.standardError = FileHandle.nullDevice
            p.terminationHandler = { process in if process.terminationStatus == 0 { continuation.resume() } else { continuation.resume(throwing: LeafError.message("Package validation failed")) } }
            do { try p.run() } catch { continuation.resume(throwing: error) }
        }
    }
}
struct PebbleUpdateDialog: View {
    @ObservedObject var updater = PebbleUpdater.shared
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            HStack { Text("Pebble Notes " + (updater.available?.version ?? "")).font(.title2.bold()); Spacer(); if !updater.downloading { GlassIcon(icon: "xmark", label: "Later") { updater.later() } } }
            LeafScrollView { VStack(alignment: .leading, spacing: 16) { if let update = updater.available { ForEach(UpdateValidation.unseen(update, current: ReleaseNotes.version), id: \.version) { release in Text(release.version + " · " + release.title).font(.headline); ForEach(release.changes, id: \.self) { Text("• " + $0).fixedSize(horizontal: false, vertical: true) } } } } }.frame(maxHeight: 300)
            Text(updater.message).font(.callout).foregroundStyle(.secondary)
            if updater.downloading { ProgressView(value: updater.progress) } else { HStack { Button("Later") { updater.later() }; Spacer(); Button(updater.ready == nil ? "Download now" : "Install and restart") { if updater.ready != nil { updater.install() } else { Task { await updater.download() } } } } }
        }.padding(28).frame(width: 500).buttonStyle(MaterialActionStyle())
    }
}
