import Foundation
import OpenStrapShared

/// The one device-list request the sign-in makes, copied from `ZeppCloud.listDevices` and
/// `ZeppCloud.get` in `app/src/main/kotlin/org/openstrap/app/zepp/ZeppCloud.kt` @ 19f2865: the same
/// hosts in the same fallback order, the same path, headers and query parameters.
///
/// It lives in this folder because the sign-in screen is the only place the app may use the
/// network; `.github/workflows/ios.yml` fails the build if that stops being true.
struct ZeppDeviceListClient {
    enum Failure: Error {
        /// Zepp refused the sign-in itself (HTTP 401/403); another host will not fix it.
        case rejected
        case http(Int)
        /// The reply was not the JSON the shared parser expects.
        case malformedReply
    }

    private let client: URLSession

    init() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 35
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        // The token travels in a header, not in a cookie, and nothing outlives the request.
        configuration.httpCookieStorage = nil
        configuration.httpShouldSetCookies = false
        configuration.urlCache = nil
        client = URLSession(configuration: configuration)
    }

    /// The devices from the first host that lists at least one, or the first empty listing when
    /// every host answers with none — the order and the 401/403 early exit are Android's.
    func listDevices(session: ZeppSession, preferredBase: String?) async throws -> [BoundDevice] {
        var lastError: Error = Failure.malformedReply
        var empty: [BoundDevice]?
        for base in Self.bases(preferredBase: preferredBase) {
            do {
                let body = try await get(base: base, session: session)
                let devices: [BoundDevice]
                do {
                    devices = try ZeppCloud.shared.parseDeviceList(body: body)
                } catch {
                    throw Failure.malformedReply
                }
                if !devices.isEmpty { return devices }
                if empty == nil { empty = devices }
            } catch ZeppDeviceListClient.Failure.rejected {
                throw Failure.rejected
            } catch {
                lastError = error
            }
        }
        if let empty { return empty }
        throw lastError
    }

    /// The preferred host first (Android accepts any https base), then the regional hosts, deduped.
    static func bases(preferredBase: String?) -> [String] {
        var bases: [String] = []
        if let preferred = preferredBase?.trimmingCharacters(in: .whitespacesAndNewlines),
           preferred.hasPrefix("https://") {
            bases.append(preferred.hasSuffix("/") ? preferred : preferred + "/")
        }
        for host in ZeppCloud.shared.API_HOSTS where !bases.contains(host) {
            bases.append(host)
        }
        return bases
    }

    private func get(base: String, session: ZeppSession) async throws -> String {
        let locale = Locale.current
        let language = locale.language.languageCode?.identifier ?? "en"
        let country = locale.region?.identifier ?? "US"
        let lang = "\(language)_\(country)"
        let timezone = TimeZone.current.identifier
        let version = "\(ZeppCloud.shared.APP_VERSION_CODE)_\(ZeppCloud.shared.APP_VERSION_NAME)"

        // Android's query, in its order. `device` names the operating system Android runs on; iOS
        // has no SDK level, so it says ios_<major> and keeps Zepp's android_phone client type.
        let parameters: [(String, String)] = [
            ("enableMultiDeviceOnMultiType", "true"),
            ("enableMultiDevice", "true"),
            ("userid", session.userId),
            ("appid", ZeppCloud.shared.APP_ID),
            ("channel", "play"),
            ("device", "ios_\(ProcessInfo.processInfo.operatingSystemVersion.majorVersion)"),
            ("device_type", "android_phone"),
            ("cv", version),
            ("v", "2.0"),
            ("lang", lang),
            ("country", country),
            ("timezone", timezone),
        ]
        let query = parameters.map { "\($0.0)=\(Self.percentEncode($0.1))" }.joined(separator: "&")
        guard let url = URL(string: base + "users/" + Self.percentEncode(session.userId) + "/devices?" + query) else {
            throw Failure.malformedReply
        }

        var request = URLRequest(url: url)
        request.timeoutInterval = 20
        request.setValue(session.appToken, forHTTPHeaderField: "apptoken")
        request.setValue("com.huami.midong", forHTTPHeaderField: "appname")
        request.setValue("android_phone", forHTTPHeaderField: "appplatform")
        request.setValue("play", forHTTPHeaderField: "channel")
        request.setValue(version, forHTTPHeaderField: "cv")
        request.setValue("2.0", forHTTPHeaderField: "v")
        request.setValue(lang, forHTTPHeaderField: "lang")
        request.setValue(country, forHTTPHeaderField: "country")
        request.setValue(timezone, forHTTPHeaderField: "timezone")
        request.setValue("false", forHTTPHeaderField: "hm-privacy-diagnostics")
        request.setValue("false", forHTTPHeaderField: "hm-privacy-ceip")
        request.setValue(UUID().uuidString, forHTTPHeaderField: "X-Request-Id")

        let (data, response) = try await client.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw Failure.malformedReply }
        switch http.statusCode {
        case 200...299:
            return String(decoding: data, as: UTF8.self)
        case 401, 403:
            throw Failure.rejected
        default:
            throw Failure.http(http.statusCode)
        }
    }

    /// `java.net.URLEncoder.encode(s, "UTF-8")`, which the Android call used for every value.
    static func percentEncode(_ text: String) -> String {
        let unreserved = Set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.!~*'()".utf8)
        var encoded = ""
        for byte in text.utf8 {
            if unreserved.contains(byte) {
                encoded.append(Character(UnicodeScalar(byte)))
            } else {
                encoded += String(format: "%%%02X", byte)
            }
        }
        return encoded
    }
}
