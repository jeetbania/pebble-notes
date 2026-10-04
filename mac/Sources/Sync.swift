import SwiftUI
import CryptoKit
import Security
import LocalAuthentication
import Network

struct GoogleTokens: Codable { var refreshToken: String; var clientId: String; var clientSecret: String }
enum TokenVault {
    static let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "dev.leafnotes.google", kSecAttrAccount as String: "personal"]
    static func load(allowPrompt: Bool = false) -> GoogleTokens? {
        var q = query; q[kSecReturnData as String] = true; q[kSecMatchLimit as String] = kSecMatchLimitOne; let context = LAContext(); context.interactionNotAllowed = !allowPrompt; q[kSecUseAuthenticationContext as String] = context
        var result: CFTypeRef?; guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(GoogleTokens.self, from: data)
    }
    static func save(_ tokens: GoogleTokens) throws {
        let data = try leafEncoder.encode(tokens)
        let result = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if result == errSecItemNotFound { var q = query; q[kSecValueData as String] = data; q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly; guard SecItemAdd(q as CFDictionary, nil) == errSecSuccess else { throw LeafError.message("Could not save Google sign-in in Keychain") } }
        else if result != errSecSuccess { throw LeafError.message("Could not update Google sign-in in Keychain") }
    }
    static func remove() { SecItemDelete(query as CFDictionary) }
}
func formData(_ values: [String: String]) -> Data {
    var chars = CharacterSet.alphanumerics; chars.insert(charactersIn: "-._~")
    return Data(values.map { $0.key.addingPercentEncoding(withAllowedCharacters: chars)! + "=" + $0.value.addingPercentEncoding(withAllowedCharacters: chars)! }.joined(separator: "&").utf8)
}
func requestJSON(_ request: URLRequest) async throws -> [String: Any] {
    let (data, response) = try await URLSession.shared.data(for: request)
    guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        throw LeafError.message(code == 401 ? "Google sign-in expired. Reconnect in Settings; your notes remain saved locally." : "Google request failed (\(code)). Your notes remain saved locally.")
    }
    return try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
}
final class OAuthCallback: @unchecked Sendable {
    let listener: NWListener; let state: String
    var continuation: CheckedContinuation<String, Error>?
    var timeout: DispatchWorkItem?
    init(state: String) throws {
        self.state = state
        let parameters = NWParameters.tcp; parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        listener = try NWListener(using: parameters)
    }
    func code(open: @escaping @Sendable (Int) -> Void) async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
            self.listener.stateUpdateHandler = { status in
                switch status {
                case .ready: if let port = self.listener.port { open(Int(port.rawValue)) }
                case .failed(let error): self.finish(.failure(error))
                default: break
                }
            }
            self.listener.newConnectionHandler = { connection in
                connection.start(queue: .main)
                connection.receive(minimumIncompleteLength: 1, maximumLength: 16384) { data, _, _, _ in
                    guard let data, let text = String(data: data, encoding: .utf8), let path = text.components(separatedBy: " ").dropFirst().first, let url = URLComponents(string: "http://127.0.0.1" + path) else { connection.cancel(); return }
                    let values = Dictionary(url.queryItems?.map { ($0.name, $0.value ?? "") } ?? [], uniquingKeysWith: { a, _ in a })
                    guard url.path == "/callback", values["state"] == self.state else { connection.cancel(); return }
                    let body = "You can return to Pebble Notes."
                    connection.send(content: Data("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: \(body.utf8.count)\r\nConnection: close\r\n\r\n\(body)".utf8), completion: .contentProcessed { _ in connection.cancel() })
                    if let code = values["code"] { self.finish(.success(code)) } else { self.finish(.failure(LeafError.message("Google sign-in was cancelled"))) }
                }
            }
            let timeout = DispatchWorkItem { self.finish(.failure(LeafError.message("Google sign-in timed out. Try again in Settings."))) }
            self.timeout = timeout; DispatchQueue.main.asyncAfter(deadline: .now() + 180, execute: timeout)
            self.listener.start(queue: .main)
        }
    }
    func finish(_ result: Result<String, Error>) { guard let c = continuation else { return }; continuation = nil; timeout?.cancel(); listener.cancel(); c.resume(with: result) }
}
@MainActor enum GoogleAuth {
    static let scope = "https://www.googleapis.com/auth/drive.appdata openid email"
    static func connect(clientId: String, secret: String) async throws {
        guard clientId.hasSuffix(".apps.googleusercontent.com") else { throw LeafError.message("Enter your Desktop OAuth client ID first. See the Google setup guide.") }
        let verifier = (UUID().uuidString + UUID().uuidString).replacingOccurrences(of: "-", with: "")
        let challenge = Data(SHA256.hash(data: Data(verifier.utf8))).base64EncodedString().replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_").replacingOccurrences(of: "=", with: "")
        let state = UUID().uuidString; let server = try OAuthCallback(state: state)
        var redirect = ""
        let code = try await server.code { port in
            Task { @MainActor in
                redirect = "http://127.0.0.1:\(port)/callback"
                var url = URLComponents(string: "https://accounts.google.com/o/oauth2/v2/auth")!
                url.queryItems = ["client_id": clientId, "redirect_uri": redirect, "response_type": "code", "scope": scope, "access_type": "offline", "prompt": "consent", "state": state, "code_challenge": challenge, "code_challenge_method": "S256"].map { URLQueryItem(name: $0.key, value: $0.value) }
                NSWorkspace.shared.open(url.url!)
            }
        }
        var request = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!); request.httpMethod = "POST"; request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = formData(["code": code, "client_id": clientId, "client_secret": secret, "redirect_uri": redirect, "grant_type": "authorization_code", "code_verifier": verifier])
        let response = try await requestJSON(request)
        guard let refresh = response["refresh_token"] as? String else { throw LeafError.message("Google did not return ongoing access. Try reconnecting.") }
        try TokenVault.save(GoogleTokens(refreshToken: refresh, clientId: clientId, clientSecret: secret))
    }
    static func accessToken(allowPrompt: Bool = false) async throws -> String {
        guard let credentials = await Task.detached { TokenVault.load(allowPrompt: allowPrompt) }.value else { throw LeafError.message("Connect Google Drive in Settings to enable sync") }
        var request = URLRequest(url: URL(string: "https://oauth2.googleapis.com/token")!); request.httpMethod = "POST"; request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = formData(["refresh_token": credentials.refreshToken, "client_id": credentials.clientId, "client_secret": credentials.clientSecret, "grant_type": "refresh_token"])
        let response = try await requestJSON(request)
        guard let access = response["access_token"] as? String else { throw LeafError.message("Reconnect Google Drive in Settings") }; return access
    }
}
struct DriveFile { let id: String; let name: String }
struct DriveClient {
    let token: String
    func request(_ url: URL) -> URLRequest { var r = URLRequest(url: url); r.timeoutInterval = 45; r.setValue("Bearer " + token, forHTTPHeaderField: "Authorization"); return r }
    func files() async throws -> [DriveFile] {
        var files: [DriveFile] = []; var page: String?
        repeat {
            var url = URLComponents(string: "https://www.googleapis.com/drive/v3/files")!
            var items = [URLQueryItem(name: "spaces", value: "appDataFolder"), .init(name: "q", value: "trashed=false and appProperties has { key='leafApp' and value='1' }"), .init(name: "pageSize", value: "1000"), .init(name: "fields", value: "nextPageToken,files(id,name)")]
            if let page { items.append(.init(name: "pageToken", value: page)) }; url.queryItems = items
            let object = try await requestJSON(request(url.url!)); page = object["nextPageToken"] as? String
            for f in object["files"] as? [[String: Any]] ?? [] { if let id = f["id"] as? String, let name = f["name"] as? String { files.append(DriveFile(id: id, name: name)) } }
        } while page != nil
        return files
    }
    func download(_ id: String) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(for: request(URL(string: "https://www.googleapis.com/drive/v3/files/\(id)?alt=media")!))
        guard let response = response as? HTTPURLResponse, response.statusCode == 200 else { throw LeafError.message("Could not download from Google Drive; will retry") }; return data
    }
    func remove(_ id: String) async throws { var r = request(URL(string: "https://www.googleapis.com/drive/v3/files/\(id)")!); r.httpMethod = "DELETE"; let (_, response) = try await URLSession.shared.data(for: r); guard let response = response as? HTTPURLResponse, [204, 404].contains(response.statusCode) else { throw LeafError.message("Cloud deletion will retry during the next sync") } }
    func upload(_ name: String, data: Data, mime: String) async throws {
        let boundary = "leaf-" + UUID().uuidString
        let metadata: [String: Any] = ["name": name, "parents": ["appDataFolder"], "appProperties": ["leafApp": "1"]]
        var body = Data("--\(boundary)\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".utf8)
        body.append(try JSONSerialization.data(withJSONObject: metadata)); body.append(Data("\r\n--\(boundary)\r\nContent-Type: \(mime)\r\n\r\n".utf8)); body.append(data); body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        var r = request(URL(string: "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")!); r.httpMethod = "POST"; r.setValue("multipart/related; boundary=\(boundary)", forHTTPHeaderField: "Content-Type"); r.httpBody = body
        _ = try await requestJSON(r)
    }
}
extension NoteStore {
    func syncIfConnected() async { guard allowsAutomaticSync else { return }; let connected = await Task.detached { TokenVault.load() != nil }.value; if connected { await sync(silent: true) } }
    func sync(silent: Bool = false) async {
        guard !busy else { return }; flush(); guard draft == nil else { return }
        busy = true; defer { busy = false }
        do {
            status = "Syncing with Google Drive…"
            let token = try await GoogleAuth.accessToken(allowPrompt: !silent); let drive = DriveClient(token: token)
            let user = try await requestJSON(drive.request(URL(string: "https://www.googleapis.com/oauth2/v3/userinfo")!))
            guard let subject = user["sub"] as? String else { throw LeafError.message("Could not identify Google account") }
            let bindingURL = root.appendingPathComponent("google-account.txt")
            if let existing = try? String(contentsOf: bindingURL, encoding: .utf8), existing != subject { throw LeafError.message("This library belongs to a different Google account. Reconnect the original account to keep libraries separate.") }
            try Data(subject.utf8).write(to: bindingURL, options: .atomic)
            let remote = try await drive.files(); var names = Set(remote.map(\.name))
            let originals = try JSONSerialization.jsonObject(with: Data(rawRevisions().utf8)) as! [[String: Any]]
            let rawById = Dictionary(uniqueKeysWithValues: originals.map { (($0["id"] as! String), $0) })
            for revision in try revisions(pending: true) {
                for a in revision.note.attachments {
                    let name = "leaf-b-" + a.id
                    if !names.contains(name) {
                        let data = try Data(contentsOf: media.appendingPathComponent(a.id))
                        guard SHA256.hash(data: data).map({ String(format: "%02x", $0) }).joined() == a.id else { throw LeafError.message("An image is damaged. Restore a backup before syncing.") }
                        try await drive.upload(name, data: data, mime: "application/octet-stream"); names.insert(name)
                    }
                }
                let name = "leaf-r-" + revision.id + ".json"
                if !names.contains(name) { try await drive.upload(name, data: JSONSerialization.data(withJSONObject: rawById[revision.id]!, options: [.sortedKeys]), mime: "application/json"); names.insert(name) }
                try acknowledge(revision.id)
            }
            let known = Set(try revisions().map(\.id))
            for file in remote where file.name.hasPrefix("leaf-r-") && file.name.hasSuffix(".json") {
                let claimedId = String(file.name.dropFirst(7).dropLast(5)); if known.contains(claimedId) { continue }
                let data = try await drive.download(file.id); let revision = try JSONDecoder().decode(Revision.self, from: data)
                guard revision.id == claimedId, [1, 2].contains(revision.schema) else { throw LeafError.message("Cloud note has an unsupported revision format") }
                // Core validation must run before constructing any attachment path.
                guard let validator = leaf_open(":memory:") else { throw LeafError.message("Could not validate cloud note") }
                defer { leaf_close(validator) }
                let raw = String(data: data, encoding: .utf8)!
                guard leaf_put(validator, raw, 1) != 0 else { throw LeafError.message(String(cString: leaf_error(validator))) }
                if heads.contains(where: { $0.noteId == revision.noteId && $0.note.purgedAt != nil }) { continue }
                for a in revision.note.attachments {
                    let path = media.appendingPathComponent(a.id)
                    if !FileManager.default.fileExists(atPath: path.path) {
                        guard let blob = remote.first(where: { $0.name == "leaf-b-" + a.id }) else { throw LeafError.message("An image upload is still arriving. Sync will retry.") }
                        let bytes = try await drive.download(blob.id)
                        guard SHA256.hash(data: bytes).map({ String(format: "%02x", $0) }).joined() == a.id else { throw LeafError.message("Downloaded image failed its integrity check") }
                        try bytes.write(to: path, options: .atomic)
                    }
                }
                try ingest(raw, remote: true)
            }
            reload()
            let purged = Set(heads.filter { $0.note.purgedAt != nil }.map(\.noteId))
            if !purged.isEmpty {
                let current = try await drive.files(); let local = Dictionary(uniqueKeysWithValues: try revisions().map { ($0.id, $0) }); var erasedFiles: [DriveFile] = []; var candidates = Set<String>(); var referenced = Set<String>()
                for file in current where file.name.hasPrefix("leaf-r-") && file.name.hasSuffix(".json") {
                    let id = String(file.name.dropFirst(7).dropLast(5)); let revision: Revision
                    if let value = local[id] { revision = value } else { revision = try JSONDecoder().decode(Revision.self, from: await drive.download(file.id)) }
                    if purged.contains(revision.noteId) && revision.note.purgedAt == nil { erasedFiles.append(file); candidates.formUnion(revision.note.attachments.map(\.id)) } else { referenced.formUnion(revision.note.attachments.map(\.id)) }
                }
                for file in erasedFiles { try await drive.remove(file.id) }
                for file in current where file.name.hasPrefix("leaf-b-") && candidates.subtracting(referenced).contains(String(file.name.dropFirst(7))) { try await drive.remove(file.id) }
                permanentlyDelete([])
            }
            scheduleBackup(); status = "Synced · " + Date().formatted(date: .omitted, time: .shortened)
        } catch {
            status = "Saved locally · sync pending"
            if !silent { self.error = error.localizedDescription }
        }
    }
}
struct SyncSettings: View {
    @EnvironmentObject var store: NoteStore
    @AppStorage("googleClientId") private var clientId = ""
    @AppStorage("googleClientSecret") private var secret = ""
    @AppStorage("appearance") private var appearance = "system"
    @LeafState<Bool> private var connecting = false
    @LeafState<String> private var message = ""
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Picker("Appearance", selection: $appearance) { Text("System").tag("system"); Text("Light").tag("light"); Text("Dark").tag("dark") }.pickerStyle(.segmented)
            Label("Google Drive sync", systemImage: "arrow.triangle.2.circlepath").font(.title2.bold())
            Text("Your notes work offline. Connect your personal Google account to sync them with Android.").foregroundStyle(.secondary)
            DisclosureGroup("Advanced connection settings") {
                TextField("Desktop OAuth client ID", text: $clientId).textFieldStyle(.roundedBorder)
                SecureField("Desktop OAuth client secret", text: $secret).textFieldStyle(.roundedBorder)
            }.font(.caption).foregroundStyle(.secondary)
            HStack {
                Button(connecting ? "Connecting…" : "Connect Google Drive") { connecting = true; Task { do { try await GoogleAuth.connect(clientId: clientId.trimmingCharacters(in: .whitespacesAndNewlines), secret: secret.trimmingCharacters(in: .whitespacesAndNewlines)); message = "Connected"; await store.sync() } catch { message = error.localizedDescription }; connecting = false } }.disabled(connecting)
                Button("Sync now") { Task { await store.sync() } }.disabled(store.busy)
                Button("Disconnect") { TokenVault.remove(); store.status = "Saved on this Mac"; message = "Disconnected. Local notes are kept." }.disabled(store.busy || connecting)
            }
            Text(message).font(.caption).foregroundStyle(.secondary)
            Text(store.status).font(.caption).foregroundStyle(.secondary)
            Text("Changes save on this Mac first and sync when you reconnect.").font(.caption).foregroundStyle(.secondary)
            Spacer()
        }.padding(28)
    }
}
