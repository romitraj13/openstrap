import Foundation
import OpenStrapShared
import WebKit

/// What the sign-in sheet shows.
enum ZeppSignInStep: Equatable {
    case signingIn
    case fetching
    case found(ZeppFoundStrap)
    case failed(ZeppSignInFailure)
}

/// The Helio Strap the account holds, as the found panel shows it.
struct ZeppFoundStrap: Equatable {
    let serial: String?
    let firmware: String?
    let authKey: String?
}

/// Why the sign-in couldn't hand over a key. Each case carries the plain words the sheet shows.
enum ZeppSignInFailure: Equatable {
    case thirdParty(provider: String)
    case noHelioStrap(deviceCount: Int)
    case noKey
    case rejected
    case network
    case unreadable

    var title: String {
        switch self {
        case .thirdParty:
            return "Use email sign-in"
        case .noHelioStrap:
            return "No Helio Strap on this account"
        case .noKey:
            return "This strap has no auth key on the account"
        case .rejected:
            return "Zepp didn't accept the sign-in"
        case .network, .unreadable:
            return "Couldn't get your devices"
        }
    }

    var message: String {
        switch self {
        case .thirdParty:
            return "Sign in with email and password. Google, Apple and other sign-ins don't work here."
        case .noHelioStrap(let deviceCount):
            if deviceCount == 0 {
                return "This account has no devices. Link the strap in the Zepp app first, then sign in "
                    + "with the email of the account you use there."
            }
            return "This account has \(deviceCount) device(s), but none is an Amazfit Helio Strap. Link the strap "
                + "in the Zepp app, then sign in again."
        case .noKey:
            return "Zepp didn't return an auth key for this strap. Open the Zepp app and check the strap is still "
                + "linked (unlink and link it again if needed), then sign in here again."
        case .rejected:
            return "Zepp didn't accept the sign-in. It may have expired, or the account is in another region. "
                + "Sign in again."
        case .network:
            return "Couldn't reach Zepp's servers. Check your internet connection and try again."
        case .unreadable:
            return "Zepp's device list came back in a shape this app doesn't understand. Try again, or paste "
                + "the key instead."
        }
    }
}

/// Owns the sign-in web view, the one-second cookie poll and the one device-list request.
///
/// The session (and so the app token) lives in memory only. The web view sits on
/// `WKWebsiteDataStore.nonPersistent()`; `close()` drops the session, the cookies and the web
/// view, so nothing survives the sheet. Nothing here logs the token, the cookie string or the key.
final class ZeppSignInModel: NSObject, ObservableObject {
    /// Sign-in providers that refuse to run in an embedded web view, copied from `THIRD_PARTY_HOSTS`
    /// in `app/src/main/kotlin/org/openstrap/app/ui/zepp/ZeppSignIn.kt` @ 19f2865.
    static let thirdPartyHosts: [String: String] = [
        "accounts.google.com": "Google",
        "appleid.apple.com": "Apple",
        "www.facebook.com": "Facebook",
        "m.facebook.com": "Facebook",
        "account.xiaomi.com": "Xiaomi",
        "access.line.me": "LINE",
    ]

    @Published private(set) var step: ZeppSignInStep = .signingIn
    /// Bumped when the sign-in restarts, so SwiftUI builds a new web view instead of reusing the old.
    @Published private(set) var attempt = 0
    private(set) var webView: WKWebView?

    /// The one diagnostic line the plan allows ("signed in, N devices, Helio Strap found").
    /// The view points this at the app's diagnostics log.
    var onLog: (String) -> Void = { _ in }

    private let dataStore = WKWebsiteDataStore.nonPersistent()
    private var zeppSession: ZeppSession?
    private var pollTimer: Timer?

    override init() {
        super.init()
        start()
    }

    deinit {
        pollTimer?.invalidate()
    }

    // MARK: - The web view and the cookie poll

    /// A fresh web view on the non-persistent store, loading Zepp's sign-in page.
    private func start() {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = dataStore
        // Zepp's sign-in is a single-page app: it needs script and DOM storage, and no popups.
        let preferences = WKWebpagePreferences()
        preferences.allowsContentJavaScript = true
        configuration.defaultWebpagePreferences = preferences

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = self
        webView.allowsBackForwardNavigationGestures = false
        if let url = URL(string: ZeppCloud.shared.SIGN_IN_URL) {
            webView.load(URLRequest(url: url))
        }
        self.webView = webView
        startPolling()
    }

    /// Every second, while the user signs in, read the web view's cookies and look for a session.
    private func startPolling() {
        pollTimer?.invalidate()
        pollTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            self?.pollCookies()
        }
        pollCookies()
    }

    private func pollCookies() {
        guard case .signingIn = step,
              let store = webView?.configuration.websiteDataStore.httpCookieStore else { return }
        store.getAllCookies { [weak self] cookies in
            guard let self, case .signingIn = self.step else { return }
            guard let header = Self.cookieHeader(from: cookies),
                  let session = ZeppCloud.shared.sessionFromCookies(cookies: header) else { return }
            // Stop as soon as the session is there; keep polling no longer than needed.
            self.pollTimer?.invalidate()
            self.pollTimer = nil
            self.zeppSession = session
            self.setStep(.fetching)
            Task { await self.fetchDevices(session: session) }
        }
    }

    /// The `Cookie`-style header Android builds from `CookieManager.getCookie(url)` for the
    /// `COOKIE_URLS` domains: `name=value` pairs joined with `"; "`.
    static func cookieHeader(from cookies: [HTTPCookie]) -> String? {
        let hosts = ZeppCloud.shared.COOKIE_URLS.compactMap { URL(string: $0)?.host }
        let matching = cookies.filter { cookie in
            var domain = cookie.domain.lowercased()
            if domain.hasPrefix(".") { domain.removeFirst() }
            return hosts.contains { host in domain == host || domain.hasSuffix("." + host) }
        }
        guard !matching.isEmpty else { return nil }
        return matching.map { "\($0.name)=\($0.value)" }.joined(separator: "; ")
    }

    // MARK: - The device list

    private func fetchDevices(session: ZeppSession) async {
        defer { onMain { self.zeppSession = nil } }
        do {
            let devices = try await ZeppDeviceListClient().listDevices(session: session, preferredBase: nil)
            let strap = ZeppCloud.shared.pickHelioStrap(devices: devices)
            if let strap {
                onLog("signed in, \(devices.count) devices, Helio Strap found")
                if let key = strap.authKey {
                    setStep(.found(ZeppFoundStrap(
                        serial: strap.serial,
                        firmware: strap.firmwareVersion,
                        authKey: key
                    )))
                } else {
                    setStep(.failed(.noKey))
                }
            } else {
                onLog("signed in, \(devices.count) devices, no Helio Strap")
                setStep(.failed(.noHelioStrap(deviceCount: devices.count)))
            }
        } catch ZeppDeviceListClient.Failure.rejected {
            setStep(.failed(.rejected))
        } catch ZeppDeviceListClient.Failure.malformedReply {
            setStep(.failed(.unreadable))
        } catch {
            setStep(.failed(.network))
        }
    }

    // MARK: - The sheet's buttons

    /// "Other account": drop the cookies and the session and load the sign-in page again.
    func restart() {
        onMain {
            self.dropWebView()
            self.step = .signingIn
            self.attempt += 1
            self.start()
        }
    }

    /// Closing the sheet: the token, the cookies and the web view all go.
    func close() {
        onMain {
            self.dropWebView()
        }
    }

    private func dropWebView() {
        zeppSession = nil
        pollTimer?.invalidate()
        pollTimer = nil
        clearWebData()
        webView?.stopLoading()
        webView?.navigationDelegate = nil
        webView = nil
    }

    /// The store is non-persistent already, so this is belt and braces.
    private func clearWebData() {
        dataStore.removeData(ofTypes: WKWebsiteDataStore.allWebsiteDataTypes(), modifiedSince: .distantPast) {}
    }

    /// The key with everything but its last four characters hidden.
    static func maskedKey(_ key: String) -> String {
        String(repeating: "•", count: 8) + String(key.suffix(4))
    }

    // MARK: - Threading

    private func setStep(_ newStep: ZeppSignInStep) {
        onMain { self.step = newStep }
    }

    /// WKWebView callbacks arrive on the main thread; the URLSession continuation does not.
    private func onMain(_ work: @escaping () -> Void) {
        if Thread.isMainThread {
            work()
        } else {
            DispatchQueue.main.async(execute: work)
        }
    }
}

// MARK: - WKNavigationDelegate

extension ZeppSignInModel: WKNavigationDelegate {
    /// Only https stays inside the web view, and the third-party providers are stopped before
    /// their page loads so the user sees the explanation rather than the provider's error.
    func webView(
        _ webView: WKWebView,
        decidePolicyFor navigationAction: WKNavigationAction,
        decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
    ) {
        guard let url = navigationAction.request.url else {
            decisionHandler(.cancel)
            return
        }
        if let provider = Self.provider(for: url) {
            decisionHandler(.cancel)
            setStep(.failed(.thirdParty(provider: provider)))
            return
        }
        decisionHandler(url.scheme?.lowercased() == "https" ? .allow : .cancel)
    }

    /// Covers a redirect that starts on its own rather than from a tap.
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        guard let url = webView.url, let provider = Self.provider(for: url) else { return }
        webView.stopLoading()
        setStep(.failed(.thirdParty(provider: provider)))
    }

    private static func provider(for url: URL) -> String? {
        guard let host = url.host?.lowercased() else { return nil }
        return thirdPartyHosts[host]
    }
}
